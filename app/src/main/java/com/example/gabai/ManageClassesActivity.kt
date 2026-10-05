package com.example.gabai

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageClassesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_manage_classes)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_manage)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_manage)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val headerInner = findViewById<View>(R.id.manage_header)
        val initialPaddingTop = headerInner.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerInner) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + initialPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        val btnCreate = findViewById<View>(R.id.btn_create_class)
        GabAIUtils.addSpringPressEffect(btnCreate) { showCreateClassDialog() }

        fetchAndDisplayClasses()
    }
    private fun showCreateClassDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val gradeLabel = TextView(this).apply {
            text = "Select Grade:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 0, 0, 8)
        }
        val gradeSpinner = Spinner(this).apply {
            val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)
            setSelection(3) // Default to Grade 10
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(24, 20, 24, 20)
        }

        val sectionLabel = TextView(this).apply {
            text = "Enter Section Name:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 20, 0, 8)
        }
        val sectionInput = EditText(this).apply {
            hint = "e.g., Rizal"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
        }

        layout.addView(gradeLabel)
        layout.addView(gradeSpinner)
        layout.addView(sectionLabel)
        layout.addView(sectionInput)

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Create New Class Section",
            subtitle = "Set up a new grade level and section roster",
            customView = layout,
            confirmText = "Create",
            cancelText = "Cancel",
            badgeIcon = "🏫",
            onConfirm = { dialog ->
                val selectedGrade = gradeSpinner.selectedItem.toString()
                val sectionName = sectionInput.text.toString().trim()

                if (sectionName.isNotEmpty()) {
                    dialog.dismiss()
                    saveClassToFirestore(selectedGrade, sectionName)
                } else {
                    GabAIUtils.showSnackbar(this, "Section name cannot be empty")
                }
            }
        )
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

                    // Build Bento Card
                    val density = resources.displayMetrics.density
                    val fontJakarta = try { androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans) } catch (_: Exception) { null }

                    val card = com.google.android.material.card.MaterialCardView(this).apply {
                        radius = 22 * density
                        cardElevation = 0f
                        strokeWidth = (1.5 * density).toInt()
                        setStrokeColor(Color.parseColor("#E2E8F0"))
                        setCardBackgroundColor(Color.WHITE)
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, (14 * density).toInt()) }
                    }

                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    // Monogram squircle badge
                    val monogram = TextView(this).apply {
                        val gNum = gradeName.filter { it.isDigit() }
                        val sChar = sectionName.firstOrNull()?.uppercaseChar() ?: 'C'
                        text = if (gNum.isNotEmpty()) "$gNum$sChar" else "CL"
                        textSize = 14f
                        setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor(if (isAdviser) "#6366F1" else "#059669"))
                        gravity = android.view.Gravity.CENTER
                        setBackgroundResource(if (isAdviser) R.drawable.bg_bento_purple else R.drawable.bg_bento_mint)
                        val sizePx = (44 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                            setMargins(0, 0, (14 * density).toInt(), 0)
                        }
                    }

                    val textLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val titleText = TextView(this).apply {
                        text = displayTitle
                        textSize = 15.5f
                        setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F172A"))
                    }

                    val pillRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, (4 * density).toInt(), 0, 0)
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val badgeText = TextView(this).apply {
                        text = if (isAdviser) "Advisory Section" else "Subject Section"
                        textSize = 10f
                        setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor(if (isAdviser) "#6366F1" else "#059669"))
                        setBackgroundResource(R.drawable.bg_pill_translucent)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(if (isAdviser) "#EEF2FF" else "#ECFDF5"))
                        setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
                    }
                    pillRow.addView(badgeText)

                    textLayout.addView(titleText)
                    textLayout.addView(pillRow)

                    val maxSessions = doc.getLong("maxSessionsPerDay")?.toInt() ?: 3
                    val maxItems = doc.getLong("maxItemsPerSession")?.toInt() ?: 10

                    val btnEdit = ImageButton(this).apply {
                        setImageResource(R.drawable.ic_edit)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#6366F1"))
                        setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                        setOnClickListener { showEditClassDialog(classId, gradeName, sectionName, maxSessions, maxItems) }
                    }
                    if (!isAdviser) btnEdit.visibility = View.GONE

                    val btnDelete = ImageButton(this).apply {
                        setImageResource(R.drawable.ic_trash)
                        setBackgroundResource(android.R.color.transparent)
                        setColorFilter(Color.parseColor("#EF4444"))
                        setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                        setOnClickListener { confirmDeleteClass(classId, className, sectionName, gradeName, schoolId, isAdviser) }
                    }

                    rowLayout.addView(monogram)
                    rowLayout.addView(textLayout)
                    rowLayout.addView(btnEdit)
                    rowLayout.addView(btnDelete)
                    card.addView(rowLayout)

                    val launchAction = {
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
                    card.setOnClickListener { launchAction() }
                    GabAIUtils.addSpringPressEffect(card) { launchAction() }

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
            }
    }
    // UPDATE Class
    private fun showEditClassDialog(classId: String, currentGrade: String, currentSection: String, currentMaxSessions: Int, currentMaxItems: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val gradeLabel = TextView(this).apply {
            text = "Select Grade:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 0, 0, 8)
        }
        val gradeSpinner = Spinner(this).apply {
            val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
            adapter = ArrayAdapter(this@ManageClassesActivity, android.R.layout.simple_spinner_dropdown_item, grades)
            val gradeIndex = grades.indexOf(currentGrade)
            if (gradeIndex >= 0) setSelection(gradeIndex)
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(24, 20, 24, 20)
        }

        val sectionLabel = TextView(this).apply {
            text = "Enter Section Name:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 20, 0, 8)
        }
        val sectionInput = EditText(this).apply {
            setText(currentSection)
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            textSize = 14f
        }

        val sessionsLabel = TextView(this).apply {
            text = "Max Quiz Sessions per Day:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 20, 0, 8)
        }
        val sessionsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMaxSessions.toString())
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            textSize = 14f
        }

        val itemsLabel = TextView(this).apply {
            text = "Max Items per Quiz:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 20, 0, 8)
        }
        val itemsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMaxItems.toString())
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            textSize = 14f
        }

        layout.addView(gradeLabel); layout.addView(gradeSpinner)
        layout.addView(sectionLabel); layout.addView(sectionInput)
        layout.addView(sessionsLabel); layout.addView(sessionsInput)
        layout.addView(itemsLabel); layout.addView(itemsInput)

        val scrollView = ScrollView(this).apply {
            addView(layout)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 340).toInt()
            )
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Class Settings",
            subtitle = "Configure section details and daily limits",
            customView = scrollView,
            confirmText = "Save",
            cancelText = "Cancel",
            badgeIcon = "⚙️",
            onConfirm = { dialog ->
                val newGrade = gradeSpinner.selectedItem.toString()
                val newSection = sectionInput.text.toString().trim()
                val newFullName = "$newGrade - $newSection"

                val newSessions = sessionsInput.text.toString().toIntOrNull() ?: 3
                val newItems = itemsInput.text.toString().toIntOrNull() ?: 10

                if (newSection.isNotEmpty()) {
                    val db = FirebaseFirestore.getInstance()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@showCustomDialog

                    dialog.dismiss()

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
        )
    }

    // DELETE Class & Cascade Delete Students (Only if Adviser)
    private fun confirmDeleteClass(classId: String, className: String, sectionName: String, grade: String, schoolId: String, isAdviser: Boolean) {
        val title = if (isAdviser) "Delete Class & Students?" else "Remove Class?"
        val message = if (isAdviser) {
            "Are you sure you want to permanently delete $className? This WILL permanently delete ALL student accounts (both active and pending) associated with this section."
        } else {
            "Remove $className from your list? Since you are not the adviser, the student accounts will NOT be deleted."
        }

        GabAIDialogs.showConfirmDialog(
            context = this,
            title = title,
            message = message,
            confirmText = if (isAdviser) "Delete All" else "Remove",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "🗑️",
            iconRes = R.drawable.ic_trash,
            onConfirm = {
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
        )
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



    private fun showClassRosterDialog(className: String, targetSection: String, classId: String, schoolId: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val loadingText = TextView(this).apply {
            text = "Loading student roster..."
            setTextColor(Color.parseColor("#636E72"))
            setPadding(0, 16, 0, 16)
        }
        container.addView(loadingText)

        val scrollView = android.widget.ScrollView(this).apply {
            addView(container)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 340).toInt()
            )
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Roster: $className",
            subtitle = "Active and pending student accounts",
            customView = scrollView,
            confirmText = null,
            cancelText = "Close",
            badgeIcon = "👥"
        )

        // 1. Fetch the class document to check privileges and see who joined
        db.collection("classes").document(classId).get().addOnSuccessListener { classDoc ->
            val isAdviser = classDoc.getBoolean("isAdviser") ?: false
            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

            // Only show the Generate button if they are the Adviser
            if (isAdviser) {
                val btnGenerate = Button(this).apply {
                    text = "Generate Student Accounts"
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_btn_modern_primary)
                    setOnClickListener { showGenerateStudentsDialog(className, targetSection, classId, schoolId) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.density * 48).toInt()
                    ).apply { setMargins(0, 0, 0, 24) }
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
        GabAIDialogs.showInputDialog(
            context = this,
            title = "Add Students to $className",
            message = "Paste a comma-separated list of students. The system will auto-generate secure Usernames and Passwords for them.",
            hint = "Enter student names (e.g. Juan Cruz, Maria Clara)",
            confirmText = "Generate",
            cancelText = "Cancel",
            minLines = 3,
            badgeIcon = "👥",
            onConfirm = { namesText ->
                if (namesText.isNotEmpty()) {
                    val namesList = namesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    generateStudentAccounts(namesList, sectionName, classId, schoolId)
                }
            }
        )
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
        GabAIDialogs.showNoticeDialog(
            context = this,
            title = "Section Already Exists",
            message = "The section '$className' has already been created by its adviser.\n\nYou do not need to create it again. To add these students to your subject, simply share your unique Teacher Join Code located in your Profile so they can join your class.",
            buttonText = "Understood",
            badgeIcon = "ℹ️"
        )
    }
}