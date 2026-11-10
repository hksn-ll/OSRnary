package com.example.gabai

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ClassEnrollmentHelper
 *
 * Provides transactional, automated linking of learners to their class adviser's
 * homeroom section and subject enrollments upon account claim and startup sync.
 */
object ClassEnrollmentHelper {

    private const val TAG = "ClassEnrollmentHelper"

    /**
     * Automatically enrolls a student into their advisory section and links them
     * to their class adviser in Firestore upon account claim or sign in.
     */
    fun autoJoinStudentToAdvisoryClass(
        studentUid: String,
        schoolId: String,
        grade: String,
        section: String,
        onComplete: ((adviserName: String, className: String) -> Unit)? = null
    ) {
        if (studentUid.isBlank() || section.isBlank()) {
            val fallbackTitle = if (grade.isNotBlank() && section.isNotBlank()) "$grade - $section" else "your class"
            onComplete?.invoke("your adviser", fallbackTitle)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val query = if (schoolId.isNotBlank()) {
            db.collection("classes")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("section", section)
        } else {
            db.collection("classes")
                .whereEqualTo("section", section)
        }

        query.get().addOnSuccessListener { classSnaps ->
            if (classSnaps == null || classSnaps.isEmpty) {
                Log.d(TAG, "No matching class section found for section=$section, grade=$grade")
                val fallbackTitle = if (grade.isNotBlank()) "$grade - $section" else section
                onComplete?.invoke("your adviser", fallbackTitle)
                return@addOnSuccessListener
            }

            // Prefer the class with matching grade or isAdviser == true
            val targetClassDoc = classSnaps.documents.firstOrNull { doc ->
                val docGrade = doc.getString("grade")?.trim() ?: ""
                val isAdv = doc.getBoolean("isAdviser") ?: false
                (docGrade.isEmpty() || grade.isEmpty() || docGrade.equals(grade, ignoreCase = true)) && isAdv
            } ?: classSnaps.documents.firstOrNull { doc ->
                val docGrade = doc.getString("grade")?.trim() ?: ""
                docGrade.isEmpty() || grade.isEmpty() || docGrade.equals(grade, ignoreCase = true)
            } ?: classSnaps.documents.first()

            val classDocId = targetClassDoc.id
            val fullClassName = targetClassDoc.getString("className") ?: (if (grade.isNotBlank()) "$grade - $section" else section)
            val adviserUid = targetClassDoc.getString("teacherId") ?: ""

            // 1. Update class document in Firestore
            val classUpdates = hashMapOf<String, Any>(
                "joinedStudents" to FieldValue.arrayUnion(studentUid)
            )
            if (adviserUid.isNotBlank()) {
                classUpdates["teacherIds"] = FieldValue.arrayUnion(adviserUid)
                classUpdates["subjectEnrollments.$adviserUid"] = FieldValue.arrayUnion(studentUid)
            }

            targetClassDoc.reference.update(classUpdates).addOnFailureListener { e ->
                Log.w(TAG, "Could not update class joinedStudents: ${e.message}")
            }

            // 2. Update student user document in Firestore
            val userUpdates = mutableMapOf<String, Any>(
                "enrolledClasses" to FieldValue.arrayUnion(classDocId)
            )
            if (adviserUid.isNotBlank()) {
                userUpdates["enrolledTeacherIds"] = FieldValue.arrayUnion(adviserUid)
            }
            db.collection("users").document(studentUid).update(userUpdates).addOnFailureListener { e ->
                Log.w(TAG, "Could not update student enrolledClasses: ${e.message}")
            }

            // 3. Resolve Adviser Teacher display name
            if (adviserUid.isNotBlank()) {
                db.collection("users").document(adviserUid).get().addOnSuccessListener { teacherDoc ->
                    val teacherName = teacherDoc.getString("name")
                        ?: "${teacherDoc.getString("firstName") ?: ""} ${teacherDoc.getString("lastName") ?: ""}".trim()
                    val cleanAdviserName = if (teacherName.isNotBlank()) {
                        if (teacherName.startsWith("Teacher", ignoreCase = true)) teacherName else "Teacher $teacherName"
                    } else {
                        "your adviser"
                    }
                    onComplete?.invoke(cleanAdviserName, fullClassName)
                }.addOnFailureListener {
                    onComplete?.invoke("your adviser", fullClassName)
                }
            } else {
                onComplete?.invoke("your adviser", fullClassName)
            }
        }.addOnFailureListener { e ->
            Log.w(TAG, "Failed to query class for auto-join: ${e.message}")
            val fallbackTitle = if (grade.isNotBlank()) "$grade - $section" else section
            onComplete?.invoke("your adviser", fallbackTitle)
        }
    }

    /**
     * Background self-healing for students who already claimed their accounts
     * but lack enrolledClasses or joinedStudents association.
     */
    fun syncStudentEnrollmentIfMissing(studentUid: String, userDoc: DocumentSnapshot) {
        val role = userDoc.getString("role") ?: "student"
        if (role != "student") return

        val enrolledClasses = userDoc.get("enrolledClasses") as? List<*>
        val schoolId = userDoc.getString("schoolId") ?: ""
        val grade = userDoc.getString("grade") ?: ""
        val section = userDoc.getString("section") ?: ""

        if ((enrolledClasses.isNullOrEmpty() || enrolledClasses.isEmpty()) && section.isNotBlank()) {
            autoJoinStudentToAdvisoryClass(studentUid, schoolId, grade, section, null)
        }
    }

    /**
     * Ensures all active students in a section are in joinedStudents of the class document.
     */
    fun syncSectionRosterToClass(classDocId: String, schoolId: String, grade: String, section: String, adviserUid: String) {
        if (classDocId.isBlank() || section.isBlank()) return
        val db = FirebaseFirestore.getInstance()

        val query = if (schoolId.isNotBlank()) {
            db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("section", section)
        } else {
            db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("section", section)
        }

        query.get().addOnSuccessListener { snaps ->
            if (snaps == null || snaps.isEmpty) return@addOnSuccessListener
            val studentIds = snaps.documents.filter { doc ->
                val docGrd = doc.getString("grade")?.trim() ?: ""
                grade.isBlank() || docGrd.isBlank() || docGrd.equals(grade, ignoreCase = true)
            }.map { it.id }

            if (studentIds.isNotEmpty()) {
                val classRef = db.collection("classes").document(classDocId)
                val updates = hashMapOf<String, Any>(
                    "joinedStudents" to FieldValue.arrayUnion(*studentIds.toTypedArray())
                )
                if (adviserUid.isNotBlank()) {
                    updates["teacherIds"] = FieldValue.arrayUnion(adviserUid)
                    updates["subjectEnrollments.$adviserUid"] = FieldValue.arrayUnion(*studentIds.toTypedArray())
                }
                classRef.update(updates).addOnFailureListener { e ->
                    Log.w(TAG, "Batch sync section roster failed: ${e.message}")
                }
            }
        }
    }
}
