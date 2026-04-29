package com.example.gabai

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageClassesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_classes)

        // Fix Status Bar
        val header = findViewById<View>(R.id.manage_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_create_class).setOnClickListener {
            showCreateClassDialog()
        }

        fetchAndDisplayClasses()
        checkForCoTeacherRequests()
    }
    private fun showCreateClassDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val gradeLabel = TextView(this).apply { text = "Select Grade:" }
        val gradeSpinner = Spinner(this).apply {
            val grades = arrayOf("Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)
        }

        val sectionLabel = TextView(this).apply {
            text = "Enter Section Name:"
            setPadding(0, 40, 0, 0)
        }
        val sectionInput = EditText(this).apply {
            hint = "e.g., Rizal"
        }

        layout.addView(gradeLabel)
        layout.addView(gradeSpinner)
        layout.addView(sectionLabel)
        layout.addView(sectionInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Class Section")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val selectedGrade = gradeSpinner.selectedItem.toString()
                val sectionName = sectionInput.text.toString().trim()

                if (sectionName.isNotEmpty()) {
                    saveClassToFirestore(selectedGrade, sectionName)
                } else {
                    Toast.makeText(this, "Section name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchAndDisplayClasses() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val container = findViewById<LinearLayout>(R.id.classes_container)

        db.collection("classes")
            .whereArrayContains("teacherIds", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews()

                if (snapshots.isEmpty) {
                    val emptyText = TextView(this).apply {
                        text = "No classes created yet. Tap the button below to add one."
                        setTextColor(Color.GRAY)
                        setPadding(0, 20, 0, 0)
                    }
                    container.addView(emptyText)
                    return@addSnapshotListener
                }

                for (doc in snapshots) {
                    val className = doc.getString("className") ?: "Unknown Class"
                    val sectionName = doc.getString("section") ?: ""
                    val gradeName = doc.getString("grade") ?: ""
                    val classId = doc.id
                    val schoolId = doc.getString("schoolId") ?: ""

                    // Build a beautiful card for each class
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setBackgroundResource(R.drawable.bg_card_quiz)
                        setPadding(40, 40, 40, 40)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 24) }
                    }

                    // Left Side: Text
                    val textLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val titleText = TextView(this).apply {
                        text = className
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.BLACK)
                    }

                    val actionText = TextView(this).apply {
                        text = "Tap to manage roster ->"
                        textSize = 14f
                        setTextColor(Color.parseColor("#636E72"))
                        setPadding(0, 10, 0, 0)
                    }
                    textLayout.addView(titleText)
                    textLayout.addView(actionText)

                    // Right Side: Action Buttons
                    val btnEdit = ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_menu_edit)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#0984E3"))
                        setPadding(20, 20, 20, 20)
                        setOnClickListener { showEditClassDialog(classId, gradeName, sectionName) }
                    }

                    val btnDelete = ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_menu_delete)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#D63031"))
                        setPadding(20, 20, 20, 20)
                        // UPDATED: Now passes sectionName and schoolId for the cascade delete
                        setOnClickListener { confirmDeleteClass(classId, className, sectionName, schoolId) }
                    }

                    card.addView(textLayout)
                    card.addView(btnEdit)
                    card.addView(btnDelete)

                    // Read: Open the Detail Activity
                    card.setOnClickListener {
                        val intent = android.content.Intent(this, ClassDetailActivity::class.java).apply {
                            putExtra("CLASS_ID", classId)
                            putExtra("CLASS_NAME", className)
                            putExtra("SECTION_NAME", sectionName)
                            putExtra("SCHOOL_ID", schoolId)
                        }
                        startActivity(intent)
                    }
                    container.addView(card)
                }
            }
    }

    // UPDATE Class
    private fun showEditClassDialog(classId: String, currentGrade: String, currentSection: String) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 40) }

        val gradeLabel = TextView(this).apply { text = "Select Grade:" }
        val gradeSpinner = Spinner(this).apply {
            val grades = arrayOf("Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)
        }

        val sectionLabel = TextView(this).apply { text = "Enter Section Name:"; setPadding(0, 40, 0, 0) }
        val sectionInput = EditText(this).apply { setText(currentSection) }

        layout.addView(gradeLabel); layout.addView(gradeSpinner)
        layout.addView(sectionLabel); layout.addView(sectionInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Class Section")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newGrade = gradeSpinner.selectedItem.toString()
                val newSection = sectionInput.text.toString().trim()
                val newFullName = "$newGrade - $newSection"

                if (newSection.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("classes").document(classId).update(
                        "grade", newGrade,
                        "section", newSection,
                        "className", newFullName
                    ).addOnSuccessListener {
                        Toast.makeText(this, "Class updated!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // DELETE Class
    // DELETE Class & Cascade Delete Students
    private fun confirmDeleteClass(classId: String, className: String, sectionName: String, schoolId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Class?")
            .setMessage("Are you sure you want to permanently delete $className? This WILL permanently delete ALL student accounts (both active and pending) associated with this section.")
            .setPositiveButton("Delete") { _, _ ->
                Toast.makeText(this, "Deleting class and students...", Toast.LENGTH_LONG).show()
                val db = FirebaseFirestore.getInstance()

                // 1. Erase all Pending Students in this section
                db.collection("pending_students")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .get()
                    .addOnSuccessListener { snaps ->
                        for (doc in snaps.documents) {
                            doc.reference.delete()
                        }
                    }

                // 2. Erase all Active Students in this section
                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .get()
                    .addOnSuccessListener { snaps ->
                        for (doc in snaps.documents) {
                            doc.reference.delete()
                        }
                    }

                // 3. Finally, delete the Class container itself
                db.collection("classes").document(classId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Class and students deleted successfully!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveClassToFirestore(grade: String, sectionName: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val schoolId = doc.getString("schoolId") ?: ""
            val fullClassName = "$grade - $sectionName"

            db.collection("classes")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("className", fullClassName)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (!snapshots.isEmpty) {
                        val existingClassDoc = snapshots.documents[0]
                        val originalTeacherId = existingClassDoc.getString("teacherId") ?: ""
                        val teacherIds = existingClassDoc.get("teacherIds") as? List<String> ?: listOf(originalTeacherId)

                        if (teacherIds.contains(uid)) {
                            Toast.makeText(this, "You already have access to this section!", Toast.LENGTH_SHORT).show()
                        } else {
                            askPermissionToJoinSection(fullClassName, originalTeacherId, existingClassDoc.id)
                        }
                    } else {
                        val classData = hashMapOf(
                            "className" to fullClassName,
                            "grade" to grade,
                            "section" to sectionName,
                            "teacherId" to uid,
                            "teacherIds" to listOf(uid),
                            "schoolId" to schoolId,
                            "createdAt" to System.currentTimeMillis()
                        )

                        db.collection("classes").add(classData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Class '$fullClassName' created!", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
        }
    }

    private fun askPermissionToJoinSection(className: String, originalTeacherId: String, classId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        MaterialAlertDialogBuilder(this)
            .setTitle("Section Already Exists")
            .setMessage("Another teacher at your school has already created '$className'. Would you like to request co-teacher access?")
            .setPositiveButton("Request Access") { _, _ ->
                val requestData = hashMapOf(
                    "classId" to classId,
                    "className" to className,
                    "requesterId" to uid,
                    "ownerId" to originalTeacherId,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("section_requests").add(requestData)
                    .addOnSuccessListener { Toast.makeText(this, "Request sent!", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClassRosterDialog(className: String, targetSection: String, classId: String, schoolId: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val btnGenerate = Button(this).apply {
            text = "Generate Student Accounts"
            setOnClickListener { showGenerateStudentsDialog(className, targetSection, classId, schoolId) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
        }
        container.addView(btnGenerate)

        val loadingText = TextView(this).apply { text = "Loading student roster..." }
        container.addView(loadingText)

        val scrollView = android.widget.ScrollView(this).apply { addView(container) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Roster: $className")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()

        db.collection("users").document(uid).get().addOnSuccessListener { teacherDoc ->
            val teacherSchoolId = teacherDoc.getString("schoolId") ?: ""
            container.removeView(loadingText)

            db.collection("pending_students")
                .whereEqualTo("schoolId", teacherSchoolId)
                .whereEqualTo("section", targetSection)
                .get()
                .addOnSuccessListener { pendingSnaps ->
                    if (!pendingSnaps.isEmpty) {
                        val pendingHeader = TextView(this).apply {
                            text = "Unclaimed Accounts:"
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.DKGRAY)
                            setPadding(0, 30, 0, 10)
                        }
                        container.addView(pendingHeader)

                        for (doc in pendingSnaps) {
                            val fName = doc.getString("firstName") ?: ""
                            val lName = doc.getString("lastName") ?: ""
                            val username = doc.getString("username") ?: "N/A"
                            val password = doc.getString("password") ?: "N/A"

                            container.addView(TextView(this).apply {
                                text = "👤 $fName $lName\n🔑 User: $username | Pass: $password"
                                textSize = 16f
                                setTextColor(Color.BLACK)
                                setPadding(0, 10, 0, 20)
                            })
                            container.addView(View(this).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
                                setBackgroundColor(Color.LTGRAY)
                            })
                        }
                    }

                    db.collection("users")
                        .whereEqualTo("role", "student")
                        .whereEqualTo("schoolId", teacherSchoolId)
                        .whereEqualTo("section", targetSection)
                        .get()
                        .addOnSuccessListener { claimedSnaps ->
                            if (!claimedSnaps.isEmpty) {
                                val claimedHeader = TextView(this).apply {
                                    text = "Active Students:"
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    setTextColor(Color.DKGRAY)
                                    setPadding(0, 30, 0, 10)
                                }
                                container.addView(claimedHeader)

                                for (doc in claimedSnaps) {
                                    val fName = doc.getString("firstName") ?: ""
                                    val lName = doc.getString("lastName") ?: ""
                                    val level = doc.getLong("level")?.toInt() ?: 1
                                    val xp = doc.getLong("current_xp")?.toInt() ?: 0

                                    container.addView(TextView(this).apply {
                                        text = "✅ $fName $lName\n⭐ Level $level ($xp XP)"
                                        textSize = 16f
                                        setTextColor(Color.BLACK)
                                        setPadding(0, 10, 0, 20)
                                    })
                                    container.addView(View(this).apply {
                                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
                                        setBackgroundColor(Color.LTGRAY)
                                    })
                                }
                            }
                            if (pendingSnaps.isEmpty && claimedSnaps.isEmpty) {
                                container.addView(TextView(this).apply { text = "No students in this section." })
                            }
                        }
                }
        }
    }

    private fun showGenerateStudentsDialog(className: String, sectionName: String, classId: String, schoolId: String) {
        val input = EditText(this).apply {
            hint = "Enter student names, separated by commas (e.g. Juan Cruz, Maria Clara)"
            minLines = 3
            gravity = android.view.Gravity.TOP
            setPadding(40, 40, 40, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Students to $className")
            .setMessage("Paste a comma-separated list of students. The system will auto-generate secure Usernames and Passwords for them.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val namesText = input.text.toString()
                if (namesText.isNotEmpty()) {
                    val namesList = namesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    generateStudentAccounts(namesList, sectionName, classId, schoolId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateStudentAccounts(names: List<String>, sectionName: String, classId: String, schoolId: String) {
        val db = FirebaseFirestore.getInstance()
        var completedCount = 0

        Toast.makeText(this, "Generating ${names.size} accounts...", Toast.LENGTH_SHORT).show()

        for (fullName in names) {
            val parts = fullName.split(" ")
            val firstName = parts.firstOrNull() ?: "Student"
            val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

            val randomNum = (1000..9999).random()
            val username = "${firstName.replace(" ", "")}${lastName.replace(" ", "")}_$randomNum".lowercase()
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

    private fun checkForCoTeacherRequests() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val reqContainer = findViewById<LinearLayout>(R.id.requests_container)

        db.collection("section_requests")
            .whereEqualTo("ownerId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || snapshots.isEmpty) {
                    reqContainer.visibility = View.GONE
                    return@addSnapshotListener
                }

                reqContainer.visibility = View.VISIBLE
                reqContainer.removeAllViews()

                val reqTitle = TextView(this).apply {
                    text = "Pending Co-Teacher Requests"
                    setTextColor(Color.parseColor("#D63031"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 16)
                }
                reqContainer.addView(reqTitle)

                for (requestDoc in snapshots) {
                    val className = requestDoc.getString("className") ?: "Unknown Class"
                    val requesterId = requestDoc.getString("requesterId") ?: ""
                    val classId = requestDoc.getString("classId") ?: return@addSnapshotListener
                    val requestId = requestDoc.id

                    db.collection("users").document(requesterId).get().addOnSuccessListener { userDoc ->
                        val requesterName = "${userDoc.getString("firstName")} ${userDoc.getString("lastName")}"

                        val reqCard = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_card_history)
                            setPadding(30, 30, 30, 30)
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                        }
                        reqCard.addView(TextView(this).apply { text = "$requesterName wants to co-teach $className" })
                        reqCard.setOnClickListener { showApprovalDialog(className, requesterName, requesterId, classId, requestId) }

                        reqContainer.addView(reqCard)
                    }
                }
            }
    }

    private fun showApprovalDialog(className: String, requesterName: String, requesterId: String, classId: String, requestId: String) {
        val db = FirebaseFirestore.getInstance()

        MaterialAlertDialogBuilder(this)
            .setTitle("Co-Teacher Request")
            .setMessage("Approve $requesterName to co-teach '$className'?")
            .setPositiveButton("Approve") { _, _ ->
                db.collection("classes").document(classId)
                    .update("teacherIds", com.google.firebase.firestore.FieldValue.arrayUnion(requesterId))
                    .addOnSuccessListener {
                        db.collection("section_requests").document(requestId).update("status", "approved")
                        Toast.makeText(this, "Approved!", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Deny") { _, _ ->
                db.collection("section_requests").document(requestId).update("status", "denied")
            }
            .show()
    }
}