package com.example.gabai

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

/**
 * Shared repository for reading and replying to School Admin / SDO pedagogical remarks
 * stored under `library_materials/{materialId}/remarks` and `weekly_assessments/{assessmentId}/remarks`.
 */
object AdminRemarksRepository {

    const val TARGET_MATERIAL = "material"
    const val TARGET_ASSESSMENT = "assessment"

    private const val PREFS_NAME = "GabAI_Prefs"
    private const val MAX_REMARK_LENGTH = 2500

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun collectionPathFor(targetType: String): String {
        return if (targetType == TARGET_MATERIAL) "library_materials" else "weekly_assessments"
    }

    fun parseRemarkDoc(
        doc: DocumentSnapshot,
        targetType: String = "",
        targetId: String = "",
        targetTitle: String = ""
    ): AdminRemark {
        val createdAtVal = when (val raw = doc.get("createdAt")) {
            is Number -> raw.toLong()
            is com.google.firebase.Timestamp -> raw.toDate().time
            else -> 0L
        }
        return AdminRemark(
            id = doc.id,
            authorUid = doc.getString("authorUid") ?: "",
            authorName = doc.getString("authorName") ?: "Administrator",
            authorRole = doc.getString("authorRole") ?: "",
            authorSchoolId = doc.getString("authorSchoolId") ?: "",
            authorPosition = doc.getString("authorPosition") ?: "",
            text = doc.getString("text") ?: "",
            createdAt = createdAtVal,
            targetType = targetType,
            targetId = targetId,
            targetTitle = targetTitle
        )
    }

    /**
     * Subscribes to live remark updates on a specific Library Material or Weekly Assessment.
     */
    fun observeRemarks(
        targetType: String,
        targetId: String,
        targetTitle: String = "",
        onUpdate: (List<AdminRemark>) -> Unit
    ): ListenerRegistration {
        val collectionPath = collectionPathFor(targetType)
        return db.collection(collectionPath)
            .document(targetId)
            .collection("remarks")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                val list = snapshots.documents
                    .map { parseRemarkDoc(it, targetType, targetId, targetTitle) }
                    .filter { it.text.isNotBlank() }
                    .sortedByDescending { it.createdAt }
                onUpdate(list)
            }
    }

    /**
     * Performs a one-shot fetch of remarks for a Library Material or Weekly Assessment.
     */
    fun fetchRemarksOnce(
        targetType: String,
        targetId: String,
        targetTitle: String = "",
        onResult: (List<AdminRemark>) -> Unit
    ) {
        if (targetId.isBlank()) {
            onResult(emptyList())
            return
        }
        val collectionPath = collectionPathFor(targetType)
        db.collection(collectionPath)
            .document(targetId)
            .collection("remarks")
            .get()
            .addOnSuccessListener { snapshots ->
                val list = snapshots.documents
                    .map { parseRemarkDoc(it, targetType, targetId, targetTitle) }
                    .filter { it.text.isNotBlank() }
                    .sortedByDescending { it.createdAt }
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    /**
     * Posts a teacher acknowledgement or reply into the same `/remarks` drawer
     * so School Admins & SDO supervisors see the response in the Admin Portal.
     */
    fun postTeacherReply(
        context: Context,
        targetType: String,
        targetId: String,
        text: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onComplete(false, "Please write a reply first.")
            return
        }
        if (trimmed.length > MAX_REMARK_LENGTH) {
            onComplete(false, "Reply cannot exceed $MAX_REMARK_LENGTH characters.")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        if (uid.isNullOrBlank() || targetId.isBlank()) {
            onComplete(false, "Missing user session or target document.")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedName = prefs.getString("cached_user_name", null)
            ?: prefs.getString("cached_first_name", null)
        val cachedSchoolId = prefs.getString("cached_school_id", null) ?: ""

        fun writeReplyDoc(resolvedName: String, resolvedSchoolId: String) {
            val payload = hashMapOf(
                "authorUid" to uid,
                "authorName" to resolvedName.ifBlank { "Teacher" },
                "authorEmail" to (currentUser.email ?: ""),
                "authorRole" to "teacher",
                "authorSchoolId" to resolvedSchoolId,
                "authorPosition" to "Educator",
                "text" to trimmed,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection(collectionPathFor(targetType))
                .document(targetId)
                .collection("remarks")
                .add(payload)
                .addOnSuccessListener { onComplete(true, null) }
                .addOnFailureListener { e -> onComplete(false, e.message) }
        }

        if (!cachedName.isNullOrBlank() && cachedSchoolId.isNotBlank()) {
            writeReplyDoc(cachedName, cachedSchoolId)
        } else {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    val first = userDoc.getString("firstName") ?: userDoc.getString("first_name") ?: ""
                    val last = userDoc.getString("lastName") ?: userDoc.getString("last_name") ?: ""
                    val full = "$first $last".trim().ifEmpty { userDoc.getString("fullName") ?: cachedName ?: "Teacher" }
                    val sId = userDoc.getString("schoolId") ?: cachedSchoolId
                    prefs.edit()
                        .putString("cached_user_name", full)
                        .putString("cached_school_id", sId)
                        .apply()
                    writeReplyDoc(full, sId)
                }
                .addOnFailureListener {
                    writeReplyDoc(cachedName ?: "Teacher", cachedSchoolId)
                }
        }
    }

    /**
     * Collects School Admin & SDO remarks across a teacher's authored materials, assigned-class
     * materials, and weekly assessments so `TeacherHomeFragment` can surface a direct oversight feed.
     */
    fun fetchAdminRemarksDigestForTeacher(
        teacherUid: String,
        teacherClassIds: List<String> = emptyList(),
        onResult: (List<AdminRemark>) -> Unit
    ) {
        if (teacherUid.isBlank()) {
            onResult(emptyList())
            return
        }

        val matTask = db.collection("library_materials")
            .whereEqualTo("uploadedBy", teacherUid)
            .get()

        val matTeacherTask = db.collection("library_materials")
            .whereEqualTo("teacherId", teacherUid)
            .get()

        val assessTask = db.collection("weekly_assessments")
            .whereEqualTo("teacherId", teacherUid)
            .get()

        val rootTasks = mutableListOf<Task<*>>(matTask, matTeacherTask, assessTask)
        val assignedMatTask: Task<QuerySnapshot>? = if (teacherClassIds.isNotEmpty()) {
            db.collection("library_materials")
                .whereArrayContainsAny("assignedSections", teacherClassIds.distinct().take(10))
                .get()
                .also { rootTasks.add(it) }
        } else {
            null
        }

        Tasks.whenAllComplete(rootTasks)
            .addOnSuccessListener {
                val targets = LinkedHashMap<String, Triple<String, String, String>>() // key -> (type, id, title)

                fun collectDocs(task: Task<QuerySnapshot>?, type: String) {
                    if (task == null || !task.isSuccessful) return
                    val docs = task.result?.documents ?: return
                    for (doc in docs) {
                        val status = doc.getString("status") ?: "active"
                        if (status == "archived") continue
                        val title = doc.getString("title")
                            ?: if (type == TARGET_MATERIAL) "Library Material" else "Weekly Assessment"
                        targets["$type:${doc.id}"] = Triple(type, doc.id, title)
                    }
                }

                collectDocs(matTask, TARGET_MATERIAL)
                collectDocs(matTeacherTask, TARGET_MATERIAL)
                collectDocs(assignedMatTask, TARGET_MATERIAL)
                collectDocs(assessTask, TARGET_ASSESSMENT)

                if (targets.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                // Cap subcollection lookups to avoid unbounded reads on very large libraries
                val boundedTargets = targets.values.take(35)
                val subTasks = boundedTargets.map { (type, id, title) ->
                    val task = db.collection(collectionPathFor(type))
                        .document(id)
                        .collection("remarks")
                        .get()
                    Pair(Triple(type, id, title), task)
                }

                Tasks.whenAllComplete(subTasks.map { it.second })
                    .addOnSuccessListener {
                        val adminRemarks = mutableListOf<AdminRemark>()
                        for ((meta, task) in subTasks) {
                            if (!task.isSuccessful) continue
                            val docs = task.result?.documents ?: continue
                            for (rDoc in docs) {
                                val parsed = parseRemarkDoc(rDoc, meta.first, meta.second, meta.third)
                                if (parsed.text.isNotBlank() && parsed.isAdminAuthor) {
                                    adminRemarks.add(parsed)
                                }
                            }
                        }
                        onResult(adminRemarks.sortedByDescending { it.createdAt })
                    }
                    .addOnFailureListener {
                        onResult(emptyList())
                    }
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }
}
