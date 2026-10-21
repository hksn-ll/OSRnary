package com.example.gabai

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

data class ArchiveRecord(
    val archiveId: String = "",
    val entityType: String = "", // student, teacher, class, material, subject, announcement
    val originalCollection: String = "",
    val originalDocId: String = "",
    val schoolId: String = "",
    val displayName: String = "",
    val subtitle: String = "",
    val archivedAt: Long = System.currentTimeMillis(),
    val archivedByUid: String = "",
    val archivedByName: String = "",
    val archivedByRole: String = "teacher",
    val deletionReason: String = "",
    val snapshot: Map<String, Any> = emptyMap(),
    val subcollections: Map<String, List<Map<String, Any>>> = emptyMap(),
    val status: String = "archived", // archived, restored, purged
    val retentionUntil: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days
)

object ArchiveVaultRepository {

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION_ARCHIVES = "system_archives"

    /**
     * Archive a student profile (from users or pending_students) along with all subcollections.
     */
    fun archiveStudent(
        studentDocId: String,
        isPending: Boolean,
        schoolId: String,
        operatorUid: String,
        operatorName: String,
        operatorRole: String = "teacher",
        reason: String = "Removed from section roster",
        onSuccess: (archiveId: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val originalCollection = if (isPending) "pending_students" else "users"
        val docRef = db.collection(originalCollection).document(studentDocId)

        docRef.get().addOnSuccessListener { docSnap ->
            if (!docSnap.exists()) {
                onFailure(Exception("Student record $studentDocId does not exist in $originalCollection"))
                return@addOnSuccessListener
            }

            val studentData = docSnap.data ?: emptyMap<String, Any>()
            val fullName = studentData["fullName"] as? String
                ?: "${studentData["firstName"] ?: ""} ${studentData["lastName"] ?: ""}".trim()
                ?: studentDocId
            val handle = (studentData["username"] as? String)?.let { "@$it" } ?: "@$studentDocId"

            val archiveId = UUID.randomUUID().toString()

            if (isPending) {
                // Pending students do not have subcollections
                val record = hashMapOf<String, Any>(
                    "archiveId" to archiveId,
                    "entityType" to "student",
                    "originalCollection" to originalCollection,
                    "originalDocId" to studentDocId,
                    "schoolId" to (studentData["schoolId"] as? String ?: schoolId),
                    "displayName" to fullName,
                    "subtitle" to "$handle • Pending Registration Slip",
                    "archivedAt" to System.currentTimeMillis(),
                    "archivedByUid" to operatorUid,
                    "archivedByName" to operatorName,
                    "archivedByRole" to operatorRole,
                    "deletionReason" to reason,
                    "snapshot" to studentData,
                    "status" to "archived",
                    "retentionUntil" to (System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
                )

                db.collection(COLLECTION_ARCHIVES).document(archiveId).set(record)
                    .addOnSuccessListener {
                        docRef.delete()
                        onSuccess(archiveId)
                    }
                    .addOnFailureListener(onFailure)
            } else {
                // Active students: deep-capture subcollections (quiz_history, diagnostic_results, favorites, history)
                val subcollectionsMap = mutableMapOf<String, List<Map<String, Any>>>()
                val subcollectionNames = listOf("quiz_history", "diagnostic_results", "favorites", "history")
                var pendingFetches = subcollectionNames.size

                val finalizeArchive = {
                    val record = hashMapOf<String, Any>(
                        "archiveId" to archiveId,
                        "entityType" to "student",
                        "originalCollection" to originalCollection,
                        "originalDocId" to studentDocId,
                        "schoolId" to (studentData["schoolId"] as? String ?: schoolId),
                        "displayName" to fullName,
                        "subtitle" to "$handle • Section ${studentData["section"] ?: "General"}",
                        "archivedAt" to System.currentTimeMillis(),
                        "archivedByUid" to operatorUid,
                        "archivedByName" to operatorName,
                        "archivedByRole" to operatorRole,
                        "deletionReason" to reason,
                        "snapshot" to studentData,
                        "subcollections" to subcollectionsMap,
                        "status" to "archived",
                        "retentionUntil" to (System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
                    )

                    db.collection(COLLECTION_ARCHIVES).document(archiveId).set(record)
                        .addOnSuccessListener {
                            // Safely remove or disassociate active user doc
                            docRef.delete().addOnFailureListener { /* ignore if restricted */ }
                            onSuccess(archiveId)
                        }
                        .addOnFailureListener(onFailure)
                }

                for (subName in subcollectionNames) {
                    docRef.collection(subName).get()
                        .addOnSuccessListener { subSnap ->
                            val docsList = subSnap.documents.map { d ->
                                val map = d.data?.toMutableMap() ?: mutableMapOf()
                                map["__subDocId"] = d.id
                                map
                            }
                            if (docsList.isNotEmpty()) {
                                subcollectionsMap[subName] = docsList
                            }
                            pendingFetches--
                            if (pendingFetches <= 0) finalizeArchive()
                        }
                        .addOnFailureListener {
                            pendingFetches--
                            if (pendingFetches <= 0) finalizeArchive()
                        }
                }
            }
        }.addOnFailureListener(onFailure)
    }

    /**
     * Archive a learning material before removal.
     */
    fun archiveMaterial(
        materialId: String,
        schoolId: String,
        operatorUid: String,
        operatorName: String,
        operatorRole: String = "teacher",
        reason: String = "Removed from curriculum subject",
        onSuccess: (archiveId: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = db.collection("library_materials").document(materialId)
        docRef.get().addOnSuccessListener { docSnap ->
            if (!docSnap.exists()) {
                onFailure(Exception("Material $materialId does not exist."))
                return@addOnSuccessListener
            }

            val matData = docSnap.data ?: emptyMap<String, Any>()
            val title = matData["title"] as? String ?: "Learning Material"
            val subjectId = matData["subjectId"] as? String ?: ""
            val archiveId = UUID.randomUUID().toString()

            val record = hashMapOf<String, Any>(
                "archiveId" to archiveId,
                "entityType" to "material",
                "originalCollection" to "library_materials",
                "originalDocId" to materialId,
                "schoolId" to (matData["schoolId"] as? String ?: schoolId),
                "displayName" to title,
                "subtitle" to "Subject: ${matData["subjectName"] ?: subjectId}",
                "archivedAt" to System.currentTimeMillis(),
                "archivedByUid" to operatorUid,
                "archivedByName" to operatorName,
                "archivedByRole" to operatorRole,
                "deletionReason" to reason,
                "snapshot" to matData,
                "status" to "archived",
                "retentionUntil" to (System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
            )

            db.collection(COLLECTION_ARCHIVES).document(archiveId).set(record)
                .addOnSuccessListener {
                    docRef.delete()
                    onSuccess(archiveId)
                }
                .addOnFailureListener(onFailure)
        }.addOnFailureListener(onFailure)
    }

    /**
     * Archive a curriculum subject along with its child materials.
     */
    fun archiveSubject(
        subjectId: String,
        schoolId: String,
        operatorUid: String,
        operatorName: String,
        operatorRole: String = "teacher",
        reason: String = "Subject deleted by faculty",
        onSuccess: (archiveId: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val subjRef = db.collection("library_subjects").document(subjectId)
        subjRef.get().addOnSuccessListener { subjSnap ->
            if (!subjSnap.exists()) {
                onFailure(Exception("Subject $subjectId does not exist."))
                return@addOnSuccessListener
            }

            val subjData = subjSnap.data ?: emptyMap<String, Any>()
            val subjName = subjData["name"] as? String ?: "Subject"

            db.collection("library_materials").whereEqualTo("subjectId", subjectId).get()
                .addOnSuccessListener { matSnaps ->
                    val childMaterials = matSnaps.documents.map { d ->
                        val map = d.data?.toMutableMap() ?: mutableMapOf()
                        map["__matDocId"] = d.id
                        map
                    }

                    val archiveId = UUID.randomUUID().toString()
                    val record = hashMapOf<String, Any>(
                        "archiveId" to archiveId,
                        "entityType" to "subject",
                        "originalCollection" to "library_subjects",
                        "originalDocId" to subjectId,
                        "schoolId" to (subjData["schoolId"] as? String ?: schoolId),
                        "displayName" to subjName,
                        "subtitle" to "${childMaterials.size} nested learning materials",
                        "archivedAt" to System.currentTimeMillis(),
                        "archivedByUid" to operatorUid,
                        "archivedByName" to operatorName,
                        "archivedByRole" to operatorRole,
                        "deletionReason" to reason,
                        "snapshot" to subjData,
                        "subcollections" to mapOf("materials" to childMaterials),
                        "status" to "archived",
                        "retentionUntil" to (System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
                    )

                    db.collection(COLLECTION_ARCHIVES).document(archiveId).set(record)
                        .addOnSuccessListener {
                            // Delete child materials and subject
                            for (m in matSnaps.documents) m.reference.delete()
                            subjRef.delete()
                            onSuccess(archiveId)
                        }
                        .addOnFailureListener(onFailure)
                }
                .addOnFailureListener(onFailure)
        }.addOnFailureListener(onFailure)
    }

    /**
     * Archive a classroom section before removal.
     */
    fun archiveClass(
        classId: String,
        schoolId: String,
        operatorUid: String,
        operatorName: String,
        operatorRole: String = "teacher",
        reason: String = "Class section removed",
        onSuccess: (archiveId: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val classRef = db.collection("classes").document(classId)
        classRef.get().addOnSuccessListener { classSnap ->
            if (!classSnap.exists()) {
                onFailure(Exception("Class $classId does not exist."))
                return@addOnSuccessListener
            }

            val classData = classSnap.data ?: emptyMap<String, Any>()
            val secName = classData["section"] as? String ?: classData["className"] as? String ?: "Section"
            val grade = classData["grade"] as? String ?: "Grade"
            val archiveId = UUID.randomUUID().toString()

            val record = hashMapOf<String, Any>(
                "archiveId" to archiveId,
                "entityType" to "class",
                "originalCollection" to "classes",
                "originalDocId" to classId,
                "schoolId" to (classData["schoolId"] as? String ?: schoolId),
                "displayName" to secName,
                "subtitle" to "$grade • Join Code #${classData["joinCode"] ?: "N/A"}",
                "archivedAt" to System.currentTimeMillis(),
                "archivedByUid" to operatorUid,
                "archivedByName" to operatorName,
                "archivedByRole" to operatorRole,
                "deletionReason" to reason,
                "snapshot" to classData,
                "status" to "archived",
                "retentionUntil" to (System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000))
            )

            db.collection(COLLECTION_ARCHIVES).document(archiveId).set(record)
                .addOnSuccessListener {
                    classRef.delete()
                    onSuccess(archiveId)
                }
                .addOnFailureListener(onFailure)
        }.addOnFailureListener(onFailure)
    }

    /**
     * 1-Click Restoration Engine: restores any archived item back to active collection.
     */
    fun restoreEntity(
        archiveId: String,
        restoredByUid: String,
        onSuccess: (displayName: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val archRef = db.collection(COLLECTION_ARCHIVES).document(archiveId)
        archRef.get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                onFailure(Exception("Archived record $archiveId not found."))
                return@addOnSuccessListener
            }

            val origCol = snap.getString("originalCollection") ?: ""
            val origId = snap.getString("originalDocId") ?: ""
            val entityType = snap.getString("entityType") ?: ""
            val displayName = snap.getString("displayName") ?: "Record"
            val snapshot = snap.get("snapshot") as? Map<String, Any> ?: emptyMap()
            val subcollections = snap.get("subcollections") as? Map<String, List<Map<String, Any>>> ?: emptyMap()

            if (origCol.isBlank() || origId.isBlank()) {
                onFailure(Exception("Invalid archive metadata for restoration."))
                return@addOnSuccessListener
            }

            // 1. Re-insert original document into active collection
            db.collection(origCol).document(origId).set(snapshot).addOnSuccessListener {
                // 2. Restore subcollections if present
                for ((subName, docList) in subcollections) {
                    if (entityType == "subject" && subName == "materials") {
                        // Special nested restore for subject materials
                        for (matMap in docList) {
                            val cleanMap = matMap.toMutableMap()
                            val matId = cleanMap.remove("__matDocId") as? String ?: UUID.randomUUID().toString()
                            db.collection("library_materials").document(matId).set(cleanMap)
                        }
                    } else {
                        // Standard subcollections (e.g. quiz_history, diagnostic_results)
                        for (subMap in docList) {
                            val cleanMap = subMap.toMutableMap()
                            val subDocId = cleanMap.remove("__subDocId") as? String ?: UUID.randomUUID().toString()
                            db.collection(origCol).document(origId).collection(subName).document(subDocId).set(cleanMap)
                        }
                    }
                }

                // 3. If restored entity was a student, re-enroll into class section if present
                val enrolledClasses = snapshot["enrolledClasses"] as? List<*>
                if (entityType == "student" && !enrolledClasses.isNullOrEmpty()) {
                    for (cId in enrolledClasses) {
                        val cStr = cId?.toString() ?: continue
                        db.collection("classes").document(cStr).update("joinedStudents", FieldValue.arrayUnion(origId))
                    }
                }

                // 4. Update archive status to 'restored'
                archRef.update(
                    mapOf(
                        "status" to "restored",
                        "restoredAt" to System.currentTimeMillis(),
                        "restoredByUid" to restoredByUid
                    )
                ).addOnSuccessListener {
                    onSuccess(displayName)
                }.addOnFailureListener {
                    // Restoration succeeded even if status update hit permission issue
                    onSuccess(displayName)
                }

            }.addOnFailureListener(onFailure)
        }.addOnFailureListener(onFailure)
    }

    /**
     * Query active archives for a school campus.
     */
    fun fetchCampusArchives(
        schoolId: String,
        entityTypeFilter: String = "ALL", // ALL, student, material, subject, class
        onSuccess: (List<ArchiveRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        var q: Query = db.collection(COLLECTION_ARCHIVES)
            .whereEqualTo("status", "archived")

        if (schoolId.isNotBlank() && schoolId != "ALL") {
            q = q.whereEqualTo("schoolId", schoolId)
        }

        q.orderBy("archivedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                val records = snaps.documents.mapNotNull { d ->
                    val type = d.getString("entityType") ?: ""
                    if (entityTypeFilter != "ALL" && !type.equals(entityTypeFilter, ignoreCase = true)) {
                        return@mapNotNull null
                    }

                    ArchiveRecord(
                        archiveId = d.id,
                        entityType = type,
                        originalCollection = d.getString("originalCollection") ?: "",
                        originalDocId = d.getString("originalDocId") ?: "",
                        schoolId = d.getString("schoolId") ?: "",
                        displayName = d.getString("displayName") ?: "Record",
                        subtitle = d.getString("subtitle") ?: "",
                        archivedAt = d.getLong("archivedAt") ?: System.currentTimeMillis(),
                        archivedByUid = d.getString("archivedByUid") ?: "",
                        archivedByName = d.getString("archivedByName") ?: "Faculty",
                        archivedByRole = d.getString("archivedByRole") ?: "teacher",
                        deletionReason = d.getString("deletionReason") ?: "",
                        status = d.getString("status") ?: "archived",
                        retentionUntil = d.getLong("retentionUntil") ?: 0L
                    )
                }
                onSuccess(records)
            }
            .addOnFailureListener(onFailure)
    }
}
