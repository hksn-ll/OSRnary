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

    }
    private fun showCreateClassDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val gradeLabel = TextView(this).apply { text = "Select Grade:" }
        val gradeSpinner = Spinner(this).apply {
            // 🟢 ADDED GRADES 7 TO 10
            val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)
            setSelection(3) // Default to Grade 10
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
                    GabAIUtils.showSnackbar(this, "Section name cannot be empty")
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
                        text = "No classes yet. Create one or give your Join Code to students!"
                        setTextColor(Color.GRAY)
                        setPadding(0, 20, 0, 0)
                    }
                    container.addView(emptyText)
                    return@addSnapshotListener
                }

                // 1. Separate the lists in memory
                val advisoryCards = mutableListOf<View>()
                val subjectCards = mutableListOf<View>()

                for (doc in snapshots) {
                    val className = doc.getString("className") ?: "Unknown Class"
                    val sectionName = doc.getString("section") ?: ""
                    val gradeName = doc.getString("grade") ?: ""
                    val classId = doc.id
                    val schoolId = doc.getString("schoolId") ?: ""
                    val isAdviser = doc.getBoolean("isAdviser") ?: true

                    // 🟢 FIX: Force the grade into the title if it's missing from old data
                    val displayTitle = if (gradeName.isNotEmpty() && !className.contains(gradeName)) {
                        "$gradeName - $className"
                    } else {
                        className
                    }

                    // Build the card
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

                    val textLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val titleText = TextView(this).apply {
                        text = displayTitle // 🟢 Uses the new forced-grade title
                        textSize = 20f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.BLACK)
                    }

                    val actionText = TextView(this).apply {
                        val baseAction = if (isAdviser) "Tap to manage accounts ->" else "Tap to view roster ->"
                        // 🟢 FIX: Explicitly print the Grade in the subtitle text as well!
                        text = if (gradeName.isNotEmpty()) "$gradeName | $baseAction" else baseAction
                        textSize = 14f
                        setTextColor(Color.parseColor(if (isAdviser) "#6C5CE7" else "#636E72"))
                        setPadding(0, 10, 0, 0)
                    }

                    textLayout.addView(titleText)
                    textLayout.addView(actionText)

                    // ... (Keep the rest of the buttons and click listeners the same below this)

                    // Extract the limits (defaulting to 3 and 10 if missing)
                    val maxSessions = doc.getLong("maxSessionsPerDay")?.toInt() ?: 3
                    val maxItems = doc.getLong("maxItemsPerSession")?.toInt() ?: 10

                    val btnEdit = ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_menu_edit)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#0984E3"))
                        setPadding(20, 20, 20, 20)
                        // Pass the limits into the dialog!
                        setOnClickListener { showEditClassDialog(classId, gradeName, sectionName, maxSessions, maxItems) }
                    }
                    if (!isAdviser) btnEdit.visibility = View.GONE // Hide edit for non-advisers

                    val btnDelete = ImageButton(this).apply {
                        setImageResource(android.R.drawable.ic_menu_delete)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#D63031"))
                        setPadding(20, 20, 20, 20)
                        setOnClickListener { confirmDeleteClass(classId, className, sectionName, gradeName, schoolId, isAdviser) }
                    }

                    card.addView(textLayout)
                    card.addView(btnEdit)
                    card.addView(btnDelete)

                    card.setOnClickListener {
                        val intent = android.content.Intent(this, ClassDetailActivity::class.java).apply {
                            putExtra("CLASS_ID", classId)
                            putExtra("CLASS_NAME", className)
                            putExtra("SECTION_NAME", sectionName)
                            putExtra("SCHOOL_ID", schoolId)
                            putExtra("IS_ADVISER", isAdviser)
                            putExtra("GRADE", gradeName)
                        }
                        startActivity(intent)
                    }

                    // Sort into the correct list
                    if (isAdviser) advisoryCards.add(card) else subjectCards.add(card)
                }

                // 2. BUILD THE UI: Advisory Section (Student Account Management)
                val headerAdvisory = TextView(this).apply {
                    text = "🛡️ Advisory Classes (Full Control)"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#2D3436"))
                    setPadding(0, 20, 0, 20)
                }
                container.addView(headerAdvisory)

                if (advisoryCards.isEmpty()) {
                    container.addView(TextView(this).apply { text = "No advisory classes created yet."; setPadding(0, 0, 0, 40) })
                } else {
                    advisoryCards.forEach { container.addView(it) }
                }

                // Add a divider
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3).apply { setMargins(0, 20, 0, 40) }
                    setBackgroundColor(Color.parseColor("#DFE6E9"))
                })

                // 3. BUILD THE UI: Subject Section (Class Section List)
                val headerSubject = TextView(this).apply {
                    text = "📚 Subject Classes (Joined via Code)"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#2D3436"))
                    setPadding(0, 0, 0, 20)
                }
                container.addView(headerSubject)

                if (subjectCards.isEmpty()) {
                    container.addView(TextView(this).apply { text = "Students haven't joined using your code yet." })
                } else {
                    subjectCards.forEach { container.addView(it) }
                }
                val schoolLookup = mapOf(
                    "305445" to "Caruhatan NHS", "305446" to "Sitero Francisco Memorial NHS",
                    "305565" to "Punturin Senior High School", "305566" to "Justice Eliezer R. De Los Santos HS",
                    "305567" to "Lingunan NHS", "305568" to "Paso De Blas NHS",
                    "305576" to "Ugong Senior High School", "305705" to "Disiplina Village-Bignay NHS",
                    "305706" to "Malanday NHS", "305707" to "Veinte Reales NHS",
                    "305708" to "Lingunan Senior High School", "320401" to "Valenzuela City School of Math and Science",
                    "320402" to "Vicente Trinidad NHS", "320403" to "Mapulang Lupa NHS",
                    "320404" to "Bignay NHS", "320405" to "Arkong Bato NHS",
                    "320406" to "Canumay East NHS", "320407" to "Wawang Pulo NHS",
                    "320408" to "Bagbaguin NHS", "340729" to "Paso de Blas SHS",
                    "305436" to "Polo NHS", "305437" to "Dalandanan NHS",
                    "305438" to "Malinta NHS", "305439" to "Canumay West NHS",
                    "305440" to "Lawang Bato NHS", "305441" to "Valenzuela NHS",
                    "305442" to "Parada NHS", "305443" to "Gen. Tiburcio de Leon NHS",
                    "305444" to "Maysan NHS"
                )

                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance().collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            val sId = doc.getString("schoolId") ?: ""
                            val schoolName = schoolLookup[sId] ?: "EDUCATOR PORTAL"
                            findViewById<TextView>(R.id.tv_school_name)?.text = schoolName.uppercase()
                        }
                }
            }


    }
    // UPDATE Class
    private fun showEditClassDialog(classId: String, currentGrade: String, currentSection: String, currentMaxSessions: Int, currentMaxItems: Int) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 40, 60, 40) }

        val gradeLabel = TextView(this).apply { text = "Select Grade:" }
        val gradeSpinner = Spinner(this).apply {
            // 🟢 ADDED GRADES 7 TO 10
            val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)

            // Set the spinner to the current grade
            val gradeIndex = grades.indexOf(currentGrade)
            if (gradeIndex >= 0) setSelection(gradeIndex)
        }

        val sectionLabel = TextView(this).apply { text = "Enter Section Name:"; setPadding(0, 40, 0, 0) }
        val sectionInput = EditText(this).apply { setText(currentSection) }

        val sessionsLabel = TextView(this).apply { text = "Max Quiz Sessions per Day:"; setPadding(0, 40, 0, 0) }
        val sessionsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMaxSessions.toString())
        }

        val itemsLabel = TextView(this).apply { text = "Max Items per Quiz:"; setPadding(0, 40, 0, 0) }
        val itemsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMaxItems.toString())
        }

        layout.addView(gradeLabel); layout.addView(gradeSpinner)
        layout.addView(sectionLabel); layout.addView(sectionInput)
        layout.addView(sessionsLabel); layout.addView(sessionsInput)
        layout.addView(itemsLabel); layout.addView(itemsInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Class Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newGrade = gradeSpinner.selectedItem.toString()
                val newSection = sectionInput.text.toString().trim()
                val newFullName = "$newGrade - $newSection"

                val newSessions = sessionsInput.text.toString().toIntOrNull() ?: 3
                val newItems = itemsInput.text.toString().toIntOrNull() ?: 10

                if (newSection.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton

                    // 🟢 STRICT CHECK: Prevent editing into a grade they already own (unless it's the same grade)
                    if (newGrade != currentGrade) {
                        db.collection("classes")
                            .whereEqualTo("teacherId", uid)
                            .whereEqualTo("grade", newGrade)
                            .get()
                            .addOnSuccessListener { checkSnaps ->
                                if (!checkSnaps.isEmpty) {
                                    GabAIUtils.showSnackbar(this, "Error: You already manage a section for $newGrade.")
                                } else {
                                    // Safe to update
                                    db.collection("classes").document(classId).update(
                                        "grade", newGrade, "section", newSection, "className", newFullName,
                                        "maxSessionsPerDay", newSessions, "maxItemsPerSession", newItems
                                    ).addOnSuccessListener { GabAIUtils.showSnackbar(this, "Class updated!") }
                                }
                            }
                    } else {
                        // Grade didn't change, just update the rest
                        db.collection("classes").document(classId).update(
                            "section", newSection, "className", newFullName,
                            "maxSessionsPerDay", newSessions, "maxItemsPerSession", newItems
                        ).addOnSuccessListener { GabAIUtils.showSnackbar(this, "Class updated!") }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // DELETE Class
    // DELETE Class & Cascade Delete Students
    // DELETE Class & Cascade Delete Students
    // DELETE Class & Cascade Delete Students (Only if Adviser)
    // 🟢 FIX: Added 'grade' to the function signature
    private fun confirmDeleteClass(classId: String, className: String, sectionName: String, grade: String, schoolId: String, isAdviser: Boolean) {
        val title = if (isAdviser) "Delete Class & Students?" else "Remove Class?"
        val message = if (isAdviser) {
            "Are you sure you want to permanently delete $className? This WILL permanently delete ALL student accounts (both active and pending) associated with this section."
        } else {
            "Remove $className from your list? Since you are not the adviser, the student accounts will NOT be deleted."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (isAdviser) "Delete All" else "Remove") { _, _ ->
                GabAIUtils.showGlobalLoading(this)
                val db = FirebaseFirestore.getInstance()

                if (isAdviser) {
                    // 🟢 STRICT CHECK: Wipe out students ONLY in this specific School, Section, AND Grade!
                    db.collection("pending_students")
                        .whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("section", sectionName)
                        .whereEqualTo("grade", grade) // 🟢 FILTER APPLIED
                        .get().addOnSuccessListener { snaps ->
                            for (doc in snaps.documents) doc.reference.delete()
                        }

                    db.collection("users")
                        .whereEqualTo("role", "student")
                        .whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("section", sectionName)
                        .whereEqualTo("grade", grade) // 🟢 FILTER APPLIED
                        .get().addOnSuccessListener { snaps ->
                            for (doc in snaps.documents) doc.reference.delete()
                        }
                }

                // Delete the class link from the dashboard
                db.collection("classes").document(classId).delete()
                    .addOnSuccessListener {
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Class removed successfully!")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun saveClassToFirestore(grade: String, sectionName: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val schoolId = doc.getString("schoolId") ?: ""
            val fullClassName = "$grade - $sectionName"

            // ==========================================
            // 🟢 STRICT CHECK: ONE SECTION PER GRADE
            // ==========================================
            db.collection("classes")
                .whereEqualTo("teacherId", uid)
                .whereEqualTo("grade", grade)
                .get()
                .addOnSuccessListener { gradeCheck ->
                    if (!gradeCheck.isEmpty) {
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Error: You can only create ONE section for $grade.")
                        return@addOnSuccessListener
                    }

                    // Original logic: Check if name already exists in the school
                    db.collection("classes")
                        .whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("className", fullClassName)
                        .get()
                        .addOnSuccessListener { snapshots ->
                            if (!snapshots.isEmpty) {
                                GabAIUtils.hideGlobalLoading(this)
                                val existingClassDoc = snapshots.documents[0]
                                val originalTeacherId = existingClassDoc.getString("teacherId") ?: ""

                                if (originalTeacherId == uid) {
                                    GabAIUtils.showSnackbar(this, "You already own this section!")
                                } else {
                                    showJoinCodeInstructionDialog(fullClassName)
                                }
                            } else {
                                val classData = hashMapOf(
                                    "className" to fullClassName,
                                    "grade" to grade,
                                    "section" to sectionName,
                                    "teacherId" to uid,
                                    "teacherIds" to listOf(uid),
                                    "schoolId" to schoolId,
                                    "isAdviser" to true,
                                    "joinedStudents" to listOf<String>(),
                                    "maxSessionsPerDay" to 3,
                                    "maxItemsPerSession" to 10,
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("classes").add(classData)
                                    .addOnSuccessListener {
                                        GabAIUtils.hideGlobalLoading(this)
                                        GabAIUtils.showSnackbar(this, "Class '$fullClassName' created!")
                                    }
                            }
                        }
                }
        }
    }

//    private fun askPermissionToJoinSection(className: String, originalTeacherId: String, classId: String) {
//        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        val db = FirebaseFirestore.getInstance()
//
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Section Already Exists")
//            .setMessage("Another teacher at your school has already created '$className'. Would you like to request co-teacher access?")
//            .setPositiveButton("Request Access") { _, _ ->
//                val requestData = hashMapOf(
//                    "classId" to classId,
//                    "className" to className,
//                    "requesterId" to uid,
//                    "ownerId" to originalTeacherId,
//                    "status" to "pending",
//                    "timestamp" to System.currentTimeMillis()
//                )
//                db.collection("section_requests").add(requestData)
//                    .addOnSuccessListener { com.example.gabai.GabAIUtils.showSnackbar(this, "Request sent!") }
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }

    private fun showClassRosterDialog(className: String, targetSection: String, classId: String, schoolId: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val loadingText = TextView(this).apply { text = "Loading student roster..." }
        container.addView(loadingText)

        val scrollView = android.widget.ScrollView(this).apply { addView(container) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Roster: $className")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()

        // 1. Fetch the class document to check privileges and see who joined
        db.collection("classes").document(classId).get().addOnSuccessListener { classDoc ->
            val isAdviser = classDoc.getBoolean("isAdviser") ?: false
            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

            // Only show the Generate button if they are the Adviser
            if (isAdviser) {
                val btnGenerate = Button(this).apply {
                    text = "Generate Student Accounts"
                    setOnClickListener { showGenerateStudentsDialog(className, targetSection, classId, schoolId) }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
                }
                container.addView(btnGenerate, 0)
            }

            db.collection("users").document(uid).get().addOnSuccessListener { teacherDoc ->
                val teacherSchoolId = teacherDoc.getString("schoolId") ?: ""
                container.removeView(loadingText)

                // 2. Fetch Active/Claimed Students
                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", teacherSchoolId)
                    .whereEqualTo("section", targetSection)
                    .get()
                    .addOnSuccessListener { claimedSnaps ->

                        // GATEKEEPER: Filter students based on privileges!
                        // If you aren't the adviser, it ONLY shows students who used the Join Code.
                        val activeStudents = claimedSnaps.documents.filter { doc ->
                            isAdviser || joinedStudents.contains(doc.id)
                        }

                        if (activeStudents.isNotEmpty()) {
                            val claimedHeader = TextView(this).apply {
                                text = "Active Students:"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                setPadding(0, 30, 0, 10)
                            }
                            container.addView(claimedHeader)

                            for (doc in activeStudents) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val level = doc.getLong("level")?.toInt() ?: 1
                                val xp = doc.getLong("current_xp")?.toInt() ?: 0

                                container.addView(TextView(this).apply {
                                    text = "  $fName $lName\n  Level $level ($xp XP)"
                                    textSize = 16f
                                    setTextColor(Color.BLACK)
                                    setPadding(0, 10, 0, 20)
                                })
                                container.addView(View(this).apply {
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
                                    setBackgroundColor(Color.LTGRAY)
                                })
                            }
                        } else if (!isAdviser) {
                            container.addView(TextView(this).apply { text = "No students have joined this class using your code yet." })
                        }

                        // 3. Fetch Pending Students (ONLY FOR ADVISERS)
                        if (isAdviser) {
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
                                                text = "  $fName $lName\n  User: $username | Pass: $password"
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

                                    if (activeStudents.isEmpty() && pendingSnaps.isEmpty) {
                                        container.addView(TextView(this).apply { text = "No students in this section." })
                                    }
                                }
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

        // TURN ON SPINNER
        GabAIUtils.showGlobalLoading(this)

        for (fullName in names) {
            val parts = fullName.split(" ")
            val firstName = parts.firstOrNull() ?: "Student"
            val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

            // FIX: Create a highly unique username by injecting a piece of the School ID and a larger random number
            val randomNum = (10000..99999).random()
            val schoolCode = if (schoolId.length >= 2) schoolId.takeLast(2) else "00"
            val username = "${firstName.replace(" ", "")}${lastName.replace(" ", "")}_${schoolCode}${randomNum}".lowercase()
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
                // When the loop finishes the last name...
                if (completedCount == names.size) {
                    // TURN OFF SPINNER
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "$completedCount accounts generated!")
                }
            }
        }
    }
    private fun showJoinCodeInstructionDialog(className: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Section Already Exists")
            .setMessage("The section '$className' has already been created by its adviser.\n\nYou do not need to create it again. To add these students to your subject, simply give them your unique Teacher Join Code (which we will add to your Profile next) so they can join your class!")
            .setPositiveButton("Understood", null)
            .show()
    }
//    private fun checkForCoTeacherRequests() {
//        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        val db = FirebaseFirestore.getInstance()
//        val reqContainer = findViewById<LinearLayout>(R.id.requests_container)
//
//        db.collection("section_requests")
//            .whereEqualTo("ownerId", uid)
//            .whereEqualTo("status", "pending")
//            .addSnapshotListener { snapshots, error ->
//                if (error != null || snapshots == null || snapshots.isEmpty) {
//                    reqContainer.visibility = View.GONE
//                    return@addSnapshotListener
//                }
//
//                reqContainer.visibility = View.VISIBLE
//                reqContainer.removeAllViews()
//
//                val reqTitle = TextView(this).apply {
//                    text = "Pending Co-Teacher Requests"
//                    setTextColor(Color.parseColor("#D63031"))
//                    setTypeface(null, android.graphics.Typeface.BOLD)
//                    setPadding(0, 0, 0, 16)
//                }
//                reqContainer.addView(reqTitle)
//
//                for (requestDoc in snapshots) {
//                    val className = requestDoc.getString("className") ?: "Unknown Class"
//                    val requesterId = requestDoc.getString("requesterId") ?: ""
//                    val classId = requestDoc.getString("classId") ?: return@addSnapshotListener
//                    val requestId = requestDoc.id
//
//                    db.collection("users").document(requesterId).get().addOnSuccessListener { userDoc ->
//                        val requesterName = "${userDoc.getString("firstName")} ${userDoc.getString("lastName")}"
//
//                        val reqCard = LinearLayout(this).apply {
//                            orientation = LinearLayout.VERTICAL
//                            setBackgroundResource(R.drawable.bg_card_history)
//                            setPadding(30, 30, 30, 30)
//                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
//                        }
//                        reqCard.addView(TextView(this).apply { text = "$requesterName wants to co-teach $className" })
//                        reqCard.setOnClickListener { showApprovalDialog(className, requesterName, requesterId, classId, requestId) }
//
//                        reqContainer.addView(reqCard)
//                    }
//                }
//            }
//    }

//    private fun showApprovalDialog(className: String, requesterName: String, requesterId: String, classId: String, requestId: String) {
//        val db = FirebaseFirestore.getInstance()
//
//        MaterialAlertDialogBuilder(this)
//            .setTitle("Co-Teacher Request")
//            .setMessage("Approve $requesterName to co-teach '$className'?")
//            .setPositiveButton("Approve") { _, _ ->
//                db.collection("classes").document(classId)
//                    .update("teacherIds", com.google.firebase.firestore.FieldValue.arrayUnion(requesterId))
//                    .addOnSuccessListener {
//                        db.collection("section_requests").document(requestId).update("status", "approved")
//                        com.example.gabai.GabAIUtils.showSnackbar(this, "Approved!")
//                    }
//            }
//            .setNegativeButton("Deny") { _, _ ->
//                db.collection("section_requests").document(requestId).update("status", "denied")
//            }
//            .show()
//    }
}