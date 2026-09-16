package com.example.gabai

import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

data class School(
    val schoolId: String = "",
    val schoolName: String = "",
    val division: String = "Valenzuela City",
    val adminUid: String = "",
    val adminEmail: String = "",
    val adminName: String = "",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = if (schoolName.isNotEmpty() && schoolId.isNotEmpty()) "$schoolName - $schoolId" else schoolName
}

data class SchoolAdminInvite(
    val inviteCode: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val claimed: Boolean = false,
    val claimedByUid: String = "",
    val issuedAt: Long = System.currentTimeMillis()
)

object SchoolRepository {

    private val db = FirebaseFirestore.getInstance()

    val defaultSchools = listOf(
        School("305445", "Caruhatan National High School", "Valenzuela City"),
        School("305446", "Sitero Francisco Memorial National High School", "Valenzuela City"),
        School("305565", "Punturin Senior High School", "Valenzuela City"),
        School("305566", "Justice Eliezer R. De Los Santos High School", "Valenzuela City"),
        School("305567", "Lingunan National High School", "Valenzuela City"),
        School("305568", "Paso De Blas National High School", "Valenzuela City"),
        School("305576", "Ugong Senior High School", "Valenzuela City"),
        School("305705", "Disiplina Village-Bignay National High School", "Valenzuela City"),
        School("305706", "Malanday National High School", "Valenzuela City"),
        School("305707", "Veinte Reales National High School", "Valenzuela City"),
        School("305708", "Lingunan Senior High School", "Valenzuela City"),
        School("320401", "Valenzuela City School of Mathematics and Science", "Valenzuela City"),
        School("320402", "Vicente Trinidad National High School (Punturin NHS)", "Valenzuela City"),
        School("320403", "Mapulang Lupa National High School", "Valenzuela City"),
        School("320404", "Bignay National High School", "Valenzuela City"),
        School("320405", "Arkong Bato National High School", "Valenzuela City"),
        School("320406", "Canumay East National High School", "Valenzuela City"),
        School("320407", "Wawang Pulo National High School", "Valenzuela City"),
        School("320408", "Bagbaguin National High School", "Valenzuela City"),
        School("340729", "Paso de Blas Senior High School", "Valenzuela City"),
        School("305436", "Polo National High School", "Valenzuela City"),
        School("305437", "Dalandanan National High School", "Valenzuela City"),
        School("305438", "Malinta National High School", "Valenzuela City"),
        School("305439", "Canumay West National High School", "Valenzuela City"),
        School("305440", "Lawang Bato National High School", "Valenzuela City"),
        School("305441", "Valenzuela National High School", "Valenzuela City"),
        School("305442", "Parada National High School", "Valenzuela City"),
        School("305443", "Gen. Tiburcio de Leon National High School", "Valenzuela City"),
        School("305444", "Maysan National High School", "Valenzuela City")
    )

    fun fetchSchools(onResult: (List<School>) -> Unit) {
        db.collection("schools").get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    // Seed fallback
                    onResult(defaultSchools)
                    seedDefaultSchools()
                } else {
                    val list = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(School::class.java)
                    }.sortedBy { it.schoolName }
                    if (list.isEmpty()) {
                        onResult(defaultSchools)
                    } else {
                        onResult(list)
                    }
                }
            }
            .addOnFailureListener {
                onResult(defaultSchools)
            }
    }

    private fun seedDefaultSchools() {
        val batch = db.batch()
        for (s in defaultSchools) {
            val docRef = db.collection("schools").document(s.schoolId)
            batch.set(docRef, s)
        }
        batch.commit()
    }

    fun addSchool(school: School, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (school.schoolId.isEmpty()) {
            onFailure(IllegalArgumentException("School ID cannot be empty."))
            return
        }
        db.collection("schools").document(school.schoolId).set(school)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun generateSchoolAdminInvite(
        schoolId: String,
        schoolName: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val code = "SDO-${schoolId}-${UUID.randomUUID().toString().substring(0, 4).uppercase()}"
        val invite = SchoolAdminInvite(
            inviteCode = code,
            schoolId = schoolId,
            schoolName = schoolName,
            claimed = false,
            issuedAt = System.currentTimeMillis()
        )
        db.collection("school_admin_invites").document(code).set(invite)
            .addOnSuccessListener { onSuccess(code) }
            .addOnFailureListener { onFailure(it) }
    }

    fun claimInvite(
        inviteCode: String,
        adminUid: String,
        adminEmail: String,
        adminName: String,
        onSuccess: (SchoolAdminInvite) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val docRef = db.collection("school_admin_invites").document(inviteCode.trim())
        docRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                onFailure("Invalid Invite Code. Please verify with SDO.")
                return@addOnSuccessListener
            }
            val invite = doc.toObject(SchoolAdminInvite::class.java)
            if (invite == null || invite.claimed) {
                onFailure("This Invite Code has already been claimed.")
                return@addOnSuccessListener
            }

            // Mark invite claimed
            docRef.update("claimed", true, "claimedByUid", adminUid).addOnSuccessListener {
                // Also update school document with admin details
                db.collection("schools").document(invite.schoolId).update(
                    "adminUid", adminUid,
                    "adminEmail", adminEmail,
                    "adminName", adminName
                )
                onSuccess(invite)
            }.addOnFailureListener {
                onFailure("Failed to claim invite code: ${it.localizedMessage}")
            }
        }.addOnFailureListener {
            onFailure("Network error checking invite: ${it.localizedMessage}")
        }
    }
}
