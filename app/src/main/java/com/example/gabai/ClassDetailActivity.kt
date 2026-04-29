package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

class ClassDetailActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var sectionName: String
    private lateinit var schoolId: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_detail)

        // Get Data from Intent
        classId = intent.getStringExtra("CLASS_ID") ?: return finish()
        className = intent.getStringExtra("CLASS_NAME") ?: "Class"
        sectionName = intent.getStringExtra("SECTION_NAME") ?: ""
        schoolId = intent.getStringExtra("SCHOOL_ID") ?: ""

        // Setup Header
        findViewById<TextView>(R.id.tv_header_title).text = className
        val header = findViewById<View>(R.id.detail_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_generate_students).setOnClickListener { showGenerateStudentsDialog() }

        loadStudents()
    }

    private fun loadStudents() {
        val pendingContainer = findViewById<LinearLayout>(R.id.pending_students_container)
        val activeContainer = findViewById<LinearLayout>(R.id.active_students_container)

        // 1. READ Pending Students
        db.collection("pending_students")
            .whereEqualTo("schoolId", schoolId)
            .whereEqualTo("section", sectionName)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                pendingContainer.removeAllViews()

                if (snapshots.isEmpty) {
                    pendingContainer.addView(TextView(this).apply { text = "No pending accounts." })
                } else {
                    for (doc in snapshots) {
                        val fName = doc.getString("firstName") ?: ""
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = "$fName $lName".trim() // Combine them for the UI
                        val username = doc.getString("username") ?: ""
                        val password = doc.getString("password") ?: ""

                        val view = layoutInflater.inflate(R.layout.item_student_manage, pendingContainer, false)
                        view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                        view.findViewById<TextView>(R.id.tv_student_details).text = "User: $username | Pass: $password"

                        // UPDATE
                        view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener {
                            showEditStudentDialog(doc.id, true, fullName, password)
                        }
                        // DELETE
                        view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener {
                            confirmDelete(doc.id, true, fullName)
                        }
                        pendingContainer.addView(view)
                    }
                }
            }

        // 2. READ Active Students
        db.collection("users")
            .whereEqualTo("role", "student")
            .whereEqualTo("schoolId", schoolId)
            .whereEqualTo("section", sectionName)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                activeContainer.removeAllViews()

                if (snapshots.isEmpty) {
                    activeContainer.addView(TextView(this).apply { text = "No active students." })
                } else {
                    for (doc in snapshots) {
                        val fName = doc.getString("firstName") ?: ""
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = "$fName $lName".trim() // Combine them for the UI
                        val level = doc.getLong("level")?.toInt() ?: 1

                        val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                        view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                        view.findViewById<TextView>(R.id.tv_student_details).text = "Level $level Explorer"

                        // For active users, we only allow editing name
                        view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener {
                            showEditStudentDialog(doc.id, false, fullName, "")
                        }
                        view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener {
                            confirmDelete(doc.id, false, fullName)
                        }
                        activeContainer.addView(view)
                    }
                }
            }
    }

    // CREATE
    private fun showGenerateStudentsDialog() {
        val input = EditText(this).apply {
            hint = "Enter student names, one per line\n(e.g.\nJuan Cruz\nMaria Clara)"
            minLines = 4
            gravity = android.view.Gravity.TOP
            setPadding(40, 40, 40, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Students to $className")
            .setMessage("List the students (one name per line). The system will auto-generate secure Usernames and Passwords for them.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val namesText = input.text.toString()
                if (namesText.isNotEmpty()) {
                    // CHANGED: Now splits by New Line instead of Commas
                    val namesList = namesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    generateStudentAccounts(namesList)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateStudentAccounts(names: List<String>) {
        var completedCount = 0
        Toast.makeText(this, "Generating ${names.size} accounts...", Toast.LENGTH_SHORT).show()

        for (fullName in names) {
            // Split back to maintain DB schema seamlessly
            val parts = fullName.split(" ", limit = 2)
            val firstName = parts.firstOrNull() ?: "Student"
            val lastName = if (parts.size > 1) parts[1] else ""

            val randomNum = (1000..9999).random()
            val username = "${fullName.replace(" ", "")}_$randomNum".lowercase()
            val password = java.util.UUID.randomUUID().toString().substring(0, 6)

            val studentData = hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "username" to username,
                "password" to password,
                "role" to "student",
                "section" to sectionName,
                "schoolId" to schoolId,
                "current_xp" to 0,
                "level" to 1,
                "isApproved" to true,
                "is_onboarded" to false,
                "quests_completed" to listOf<String>(),
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("pending_students").document(username).set(studentData).addOnSuccessListener {
                completedCount++
                if (completedCount == names.size) {
                    Toast.makeText(this, "$completedCount accounts generated!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // UPDATE
    private fun showEditStudentDialog(docId: String, isPending: Boolean, currentFullName: String, pass: String) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 40) }

        // CHANGED: Now shows as a single Full Name text field
        val etName = EditText(this).apply { setText(currentFullName); hint = "Full Name" }
        layout.addView(etName)

        var etPass: EditText? = null
        if (isPending) {
            etPass = EditText(this).apply { setText(pass); hint = "Password" }
            layout.addView(etPass)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Student")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newFullName = etName.text.toString().trim()

                // Intelligently split back to maintain DB schema
                val parts = newFullName.split(" ", limit = 2)
                val fName = parts.firstOrNull() ?: "Student"
                val lName = if (parts.size > 1) parts[1] else ""

                val updates = mutableMapOf<String, Any>(
                    "firstName" to fName,
                    "lastName" to lName
                )

                if (isPending && etPass != null) {
                    updates["password"] = etPass.text.toString().trim()
                }

                val collection = if (isPending) "pending_students" else "users"
                db.collection(collection).document(docId).update(updates)
                    .addOnSuccessListener { Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // DELETE
    private fun confirmDelete(docId: String, isPending: Boolean, studentName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Student?")
            .setMessage("Are you sure you want to remove $studentName from this section?")
            .setPositiveButton("Delete") { _, _ ->
                val collection = if (isPending) "pending_students" else "users"
                db.collection(collection).document(docId).delete()
                    .addOnSuccessListener { Toast.makeText(this, "Student removed", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}