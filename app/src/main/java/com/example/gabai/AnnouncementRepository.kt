package com.example.gabai

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object AnnouncementRepository {

    private const val TAG = "AnnouncementRepo"
    private const val PREFS_NAME = "GabAI_Prefs"
    private val db = FirebaseFirestore.getInstance()

    // -------------------------------------------------------------------------
    // ZERO-LATENCY PRE-CACHING LOGIC
    // -------------------------------------------------------------------------

    fun cacheLatestAnnouncement(context: Context, announcement: Announcement?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (announcement == null) {
            prefs.edit()
                .remove("cached_announcement_id")
                .remove("cached_announcement_title")
                .remove("cached_announcement_content")
                .remove("cached_announcement_author")
                .remove("cached_announcement_priority")
                .remove("cached_announcement_created_at")
                .remove("cached_announcement_seen_count")
                .remove("cached_announcement_reply_count")
                .apply()
            return
        }

        prefs.edit()
            .putString("cached_announcement_id", announcement.id)
            .putString("cached_announcement_title", announcement.title)
            .putString("cached_announcement_content", announcement.content)
            .putString("cached_announcement_author", announcement.authorName)
            .putString("cached_announcement_priority", announcement.priority)
            .putLong("cached_announcement_created_at", announcement.createdAt)
            .putLong("cached_announcement_seen_count", announcement.seenCount)
            .putLong("cached_announcement_reply_count", announcement.replyCount)
            .apply()
    }

    fun getCachedAnnouncement(context: Context): Announcement? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString("cached_announcement_id", null) ?: return null
        val title = prefs.getString("cached_announcement_title", "") ?: ""
        val content = prefs.getString("cached_announcement_content", "") ?: ""
        val author = prefs.getString("cached_announcement_author", "") ?: ""
        val priority = prefs.getString("cached_announcement_priority", "normal") ?: "normal"
        val createdAt = prefs.getLong("cached_announcement_created_at", 0L)
        val seenCount = prefs.getLong("cached_announcement_seen_count", 0L)
        val replyCount = prefs.getLong("cached_announcement_reply_count", 0L)

        return Announcement(
            id = id,
            title = title,
            content = content,
            authorName = author,
            priority = priority,
            createdAt = createdAt,
            seenCount = seenCount,
            replyCount = replyCount
        )
    }

    // -------------------------------------------------------------------------
    // REAL-TIME LISTENERS
    // -------------------------------------------------------------------------

    fun listenAnnouncementsForStudent(
        schoolId: String,
        classIds: List<String>,
        onUpdate: (List<Announcement>) -> Unit
    ): ListenerRegistration {
        return db.collection("announcements")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Student announcements listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val a = doc.toObject(Announcement::class.java)
                        a?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }.filter { a ->
                    val targetsStudents = a.targetRoles.isEmpty() || a.targetRoles.contains("student")
                    val matchesSchool = a.schoolId == "ALL" || a.schoolId.isEmpty() || a.schoolId == schoolId
                    val matchesClass = a.classId.isNullOrEmpty() || classIds.contains(a.classId)
                    targetsStudents && matchesSchool && matchesClass
                }.sortedByDescending { it.createdAt }

                onUpdate(list)
            }
    }

    fun listenAnnouncementsForTeacher(
        schoolId: String,
        teacherUid: String,
        onUpdate: (List<Announcement>) -> Unit
    ): ListenerRegistration {
        return db.collection("announcements")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Teacher announcements listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val a = doc.toObject(Announcement::class.java)
                        a?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }.filter { a ->
                    val isAuthor = a.authorUid == teacherUid
                    val targetsTeacher = a.targetRoles.contains("teacher")
                    val matchesSchool = a.schoolId == "ALL" || a.schoolId.isEmpty() || a.schoolId == schoolId
                    isAuthor || (targetsTeacher && matchesSchool)
                }.sortedByDescending { it.createdAt }

                onUpdate(list)
            }
    }

    // -------------------------------------------------------------------------
    // SEEN BY / READ RECEIPT TRACKING
    // -------------------------------------------------------------------------

    fun markAnnouncementSeen(
        announcementId: String,
        userUid: String,
        userName: String,
        userRole: String,
        schoolId: String
    ) {
        if (announcementId.isEmpty() || userUid.isEmpty()) return

        val receipt = AnnouncementViewReceipt(
            uid = userUid,
            userName = userName,
            userRole = userRole,
            schoolId = schoolId,
            seenAt = System.currentTimeMillis()
        )

        // 1. Write receipt subcollection document
        db.collection("announcements").document(announcementId)
            .collection("views").document(userUid)
            .set(receipt)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to record view receipt doc", e)
            }

        // 2. Atomically union userUid to parent seenUserIds array and increment seenCount
        db.collection("announcements").document(announcementId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val seenList = doc.get("seenUserIds") as? List<String> ?: emptyList()
                    if (!seenList.contains(userUid)) {
                        db.collection("announcements").document(announcementId)
                            .update(
                                mapOf(
                                    "seenUserIds" to FieldValue.arrayUnion(userUid),
                                    "seenCount" to FieldValue.increment(1L)
                                )
                            )
                    }
                }
            }
    }

    fun hasUserSeen(announcement: Announcement, userUid: String): Boolean {
        if (userUid.isEmpty()) return false
        return announcement.seenUserIds.contains(userUid)
    }

    fun fetchAnnouncementViewers(
        announcementId: String,
        onResult: (List<AnnouncementViewReceipt>) -> Unit
    ) {
        db.collection("announcements").document(announcementId)
            .collection("views")
            .orderBy("seenAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.toObject(AnnouncementViewReceipt::class.java) }
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    // -------------------------------------------------------------------------
    // DISCREET REPLIES MANAGEMENT
    // -------------------------------------------------------------------------

    fun sendReply(
        reply: AnnouncementReply,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val annDoc = db.collection("announcements").document(reply.announcementId)
        val repliesColl = annDoc.collection("replies")

        repliesColl.add(reply)
            .addOnSuccessListener {
                // Increment parent replyCount
                annDoc.update("replyCount", FieldValue.increment(1L))
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.message)
            }
    }

    fun observeRepliesForAnnouncement(
        announcementId: String,
        onUpdate: (List<AnnouncementReply>) -> Unit
    ): ListenerRegistration {
        return db.collection("announcements").document(announcementId)
            .collection("replies")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Observe replies error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AnnouncementReply::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                onUpdate(list)
            }
    }

    // -------------------------------------------------------------------------
    // TEACHER BROADCAST CREATION
    // -------------------------------------------------------------------------

    fun createAnnouncement(
        announcement: Announcement,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val data = hashMapOf(
            "title" to announcement.title,
            "content" to announcement.content,
            "authorUid" to announcement.authorUid,
            "authorName" to announcement.authorName,
            "authorRole" to announcement.authorRole,
            "authorSchoolId" to announcement.authorSchoolId,
            "scope" to announcement.scope,
            "schoolId" to announcement.schoolId,
            "classId" to announcement.classId,
            "targetRoles" to announcement.targetRoles,
            "priority" to announcement.priority,
            "allowReplies" to announcement.allowReplies,
            "replyCount" to 0L,
            "seenCount" to 1L,
            "seenUserIds" to listOf(announcement.authorUid),
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("announcements")
            .add(data)
            .addOnSuccessListener { docRef ->
                // Record teacher initial view receipt
                val receipt = AnnouncementViewReceipt(
                    uid = announcement.authorUid,
                    userName = announcement.authorName,
                    userRole = announcement.authorRole,
                    schoolId = announcement.authorSchoolId,
                    seenAt = System.currentTimeMillis()
                )
                docRef.collection("views").document(announcement.authorUid).set(receipt)
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.message)
            }
    }
}
