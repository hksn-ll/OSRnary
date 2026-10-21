package com.example.gabai

import com.google.firebase.auth.FirebaseAuth

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.app.Dialog
import android.provider.OpenableColumns
import com.google.ai.client.generativeai.GenerativeModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.DocumentSnapshot

class ClassDetailActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var sectionName: String
    private lateinit var schoolId: String
    private lateinit var grade: String // 🟢 NEW
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var isAdviser: Boolean = true
    // --- INITIATION MANAGEMENT VARIABLES ---
    private var currentPdfSlot = 1
    private var currentInitiationItems = 5
    private var customPdfs = mutableMapOf<String, Map<String, String>>()
    private val driveApiUrl = GabAIApp.DRIVE_API_URL
    private var activeDialog: Dialog? = null

    // Search in student roster
    private var studentSearchQuery = ""
    private val pendingItemViews = mutableListOf<Pair<View, String>>()
    private val activeItemViews = mutableListOf<Pair<View, String>>()
    private var pendingHeaderView: View? = null
    private var activeHeaderView: View? = null

    // --- WEEKLY ASSESSMENT VARIABLES ---
    private var selectedGeminiFocus: String = "Standard Comprehensive (Balanced concepts & applications)"

    private val assessmentPdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            handleAssessmentPdfSelected(uri)
        }
    }

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            activeDialog?.dismiss() // Close settings dialog while we name the file
            promptForInitiationPdfTitle(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_class_detail)

        // Initialize PDFBox for in-memory PDF parsing and text extraction
        PDFBoxResourceLoader.init(applicationContext)

        // 1. Get Data from Intent (FIXED: Removed 'val' so it uses your class variables)
        classId = intent.getStringExtra("CLASS_ID") ?: return finish()
        className = intent.getStringExtra("CLASS_NAME") ?: "Unknown"
        sectionName = intent.getStringExtra("SECTION_NAME") ?: ""
        schoolId = intent.getStringExtra("SCHOOL_ID") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""
        isAdviser = intent.getBooleanExtra("IS_ADVISER", false)

        // 2. Setup Header & Blur
        findViewById<TextView>(R.id.tv_header_title).text = className
        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_class_detail)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_class_detail)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.detail_header)
        val scrollContent = findViewById<View>(R.id.scroll_class_detail)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val btnGenerate = findViewById<View>(R.id.btn_generate_students)
        val btnManageInitiation = findViewById<View>(R.id.btn_manage_initiation)
        val btnViewPerformance = findViewById<View>(R.id.btn_view_performance)
        val btnCreateAssessment = findViewById<View>(R.id.btn_create_weekly_assessment)
        val bottomActionContainer = findViewById<View>(R.id.bottom_action_container)
        val tvLabelUnclaimed = findViewById<View>(R.id.tv_label_unclaimed)

        if (!isAdviser) {
            btnGenerate?.visibility = View.GONE
            bottomActionContainer?.visibility = View.GONE
            btnManageInitiation?.visibility = View.GONE
            tvLabelUnclaimed?.visibility = View.GONE
        } else {
            btnManageInitiation?.let { view ->
                GabAIUtils.addSpringPressEffect(view) { openInitiationSettings() }
            }
        }

        btnViewPerformance?.let { view ->
            GabAIUtils.addSpringPressEffect(view) {
                val perfIntent = android.content.Intent(this, TeacherPerformanceActivity::class.java)
                perfIntent.putExtra("CLASS_ID", classId)
                perfIntent.putExtra("CLASS_NAME", className)
                perfIntent.putExtra("SECTION_NAME", sectionName)
                perfIntent.putExtra("SCHOOL_ID", schoolId)
                perfIntent.putExtra("GRADE", grade)
                startActivity(perfIntent)
            }
        }

        btnCreateAssessment?.let { view ->
            GabAIUtils.addSpringPressEffect(view) { showCreateAssessmentDialog() }
        }

        btnGenerate?.let { view ->
            GabAIUtils.addSpringPressEffect(view) { showGenerateStudentsDialog() }
        }

        val btnRecycleVault = findViewById<ImageButton>(R.id.btn_recycle_vault)
        btnRecycleVault?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) {
                GabAIDialogs.showArchiveVaultDialog(
                    context = this,
                    schoolId = schoolId,
                    initialFilter = "student"
                ) {
                    loadStudents()
                }
            }
        }

        setupStudentSearch()

        // 5. Load the students into the lists
        loadStudents()

        // 6. Section Roster Assessment List
        loadWeeklyAssessments()
    }

    private fun setupStudentSearch() {
        val etSearch = findViewById<EditText>(R.id.et_search_class_students) ?: return
        val btnClear = findViewById<ImageButton>(R.id.btn_clear_search_class_students) ?: return

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                studentSearchQuery = s?.toString()?.trim() ?: ""
                btnClear.visibility = if (studentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyStudentSearchFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        GabAIUtils.addSpringPressEffect(btnClear) {
            etSearch.setText("")
        }
    }

    private fun applyStudentSearchFilter() {
        var visiblePending = 0
        for ((view, name) in pendingItemViews) {
            val matches = studentSearchQuery.isEmpty() || name.contains(studentSearchQuery, ignoreCase = true)
            view.visibility = if (matches) View.VISIBLE else View.GONE
            if (matches) visiblePending++
        }
        pendingHeaderView?.visibility = if (studentSearchQuery.isEmpty() || visiblePending > 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tv_label_unclaimed)?.visibility = View.GONE

        var visibleActive = 0
        for ((view, name) in activeItemViews) {
            val matches = studentSearchQuery.isEmpty() || name.contains(studentSearchQuery, ignoreCase = true)
            view.visibility = if (matches) View.VISIBLE else View.GONE
            if (matches) visibleActive++
        }
        activeHeaderView?.visibility = if (studentSearchQuery.isEmpty() || visibleActive > 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tv_label_active)?.visibility = if (studentSearchQuery.isEmpty() || visibleActive > 0) View.VISIBLE else View.GONE
    }

    private fun loadStudents() {
        val pendingContainer = findViewById<LinearLayout>(R.id.pending_students_container)
        val activeContainer = findViewById<LinearLayout>(R.id.active_students_container)

        // 1. Fetch the class document to get the joinedStudents list
        db.collection("classes").document(classId).get().addOnSuccessListener { classDoc ->
            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()
            @Suppress("UNCHECKED_CAST")
            val subjectEnrollments = classDoc.get("subjectEnrollments") as? Map<String, List<String>>

            // ==========================================
            // PATH A: ADVISER VIEW (FULL PRIVILEGE)
            // ==========================================
            if (isAdviser) {
                pendingContainer.visibility = View.VISIBLE

                // Read Pending Students
                db.collection("pending_students")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) return@addSnapshotListener
                        pendingContainer.removeAllViews()
                        pendingItemViews.clear()

                        if (snapshots.isEmpty) {
                            pendingHeaderView = null
                            pendingContainer.addView(TextView(this).apply { text = "No pending accounts." })
                        } else {
                            val headerLayout = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                setPadding(0, 0, 0, 10)
                                gravity = android.view.Gravity.CENTER_VERTICAL
                            }
                            val headerTitle = TextView(this).apply {
                                text = "Unclaimed Accounts (${snapshots.size()})"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.parseColor("#B45309"))
                                textSize = 14f
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val btnShareAll = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                                text = "Share 📤"
                                textSize = 12f
                                isAllCaps = false
                                setOnClickListener {
                                    val rosterBuilder = StringBuilder()
                                    rosterBuilder.append("Section: $sectionName ($grade)\n")
                                    rosterBuilder.append("Unclaimed Student Accounts (${snapshots.size()}):\n\n")
                                    for (d in snapshots) {
                                        val fn = "${d.getString("firstName") ?: ""} ${d.getString("lastName") ?: ""}".trim()
                                        val u = d.getString("username") ?: ""
                                        val p = d.getString("password") ?: ""
                                        rosterBuilder.append("• $fn\n  Username: @$u\n  Password: $p\n\n")
                                    }
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, rosterBuilder.toString().trim())
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "$sectionName Unclaimed Student Accounts")
                                        type = "text/plain"
                                    }
                                    startActivity(android.content.Intent.createChooser(sendIntent, "Share Student Credentials"))
                                    GabAIUtils.performHaptic(this, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }

                            val btnCopyAll = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                                text = "Copy All 📋"
                                textSize = 12f
                                isAllCaps = false
                                setOnClickListener {
                                    val rosterBuilder = StringBuilder()
                                    rosterBuilder.append("Section: $sectionName ($grade)\n")
                                    rosterBuilder.append("Unclaimed Student Accounts (${snapshots.size()}):\n\n")
                                    for (d in snapshots) {
                                        val fn = "${d.getString("firstName") ?: ""} ${d.getString("lastName") ?: ""}".trim()
                                        val u = d.getString("username") ?: ""
                                        val p = d.getString("password") ?: ""
                                        rosterBuilder.append("• $fn\n  Username: @$u\n  Password: $p\n\n")
                                    }
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Class Roster Credentials", rosterBuilder.toString().trim())
                                    clipboard.setPrimaryClip(clip)
                                    GabAIUtils.performHaptic(this, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    GabAIUtils.showSnackbar(this@ClassDetailActivity, "Copied all credentials to clipboard! 📋")
                                }
                            }

                            headerLayout.addView(headerTitle)
                            headerLayout.addView(btnShareAll)
                            headerLayout.addView(btnCopyAll)
                            pendingHeaderView = headerLayout
                            pendingContainer.addView(headerLayout)

                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val username = doc.getString("username") ?: ""
                                val password = doc.getString("password") ?: ""

                                val view = layoutInflater.inflate(R.layout.item_student_manage, pendingContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                val tvMonogram = view.findViewById<TextView>(R.id.tv_student_avatar_monogram)
                                val initial = fullName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
                                tvMonogram?.text = initial

                                val tvStatusBadge = view.findViewById<TextView>(R.id.tv_student_status_badge)
                                tvStatusBadge?.visibility = View.VISIBLE
                                tvStatusBadge?.text = "Unclaimed"

                                val detailsView = view.findViewById<TextView>(R.id.tv_student_details)
                                val btnTogglePass = view.findViewById<ImageButton>(R.id.btn_toggle_pass)
                                val btnCopyStudent = view.findViewById<ImageButton>(R.id.btn_copy_student)

                                var isPassVisible = true
                                fun updatePassDisplay() {
                                    val passText = if (isPassVisible) password else "•".repeat(password.length.coerceIn(6, 10))
                                    detailsView.text = "User: @$username | Pass: $passText"
                                    btnTogglePass?.setImageResource(if (isPassVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
                                }
                                updatePassDisplay()

                                btnTogglePass?.visibility = View.VISIBLE
                                btnTogglePass?.setOnClickListener {
                                    isPassVisible = !isPassVisible
                                    updatePassDisplay()
                                    GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                }

                                val copyCredentials = {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Student Credentials", "Username: @$username\nPassword: $password")
                                    clipboard.setPrimaryClip(clip)
                                    GabAIUtils.performHaptic(view, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    GabAIUtils.showSnackbar(this, "Copied credentials for $fullName! 📋")
                                }

                                btnCopyStudent?.visibility = View.VISIBLE
                                btnCopyStudent?.setOnClickListener { copyCredentials() }
                                detailsView.setOnClickListener { copyCredentials() }

                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, true, fullName, password) }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, true, fullName) }

                                pendingItemViews.add(view to fullName)
                                pendingContainer.addView(view)
                            }
                            applyStudentSearchFilter()
                        }
                    }

                // Read Active Students
                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) return@addSnapshotListener
                        activeContainer.removeAllViews()
                        activeItemViews.clear()

                        if (snapshots.isEmpty) {
                            activeHeaderView = null
                            activeContainer.addView(TextView(this).apply { text = "No active students." })
                        } else {
                            val pendingPhotos = snapshots.documents.filter { it.getString("photoStatus") == "pending" }
                            if (pendingPhotos.isNotEmpty()) {
                                activeContainer.addView(TextView(this).apply {
                                    text = "📷 Photo Approvals (${pendingPhotos.size} Pending)"
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    setTextColor(Color.parseColor("#5341CD"))
                                    setPadding(0, 10, 0, 10)
                                })
                                for (pDoc in pendingPhotos) {
                                    val pName = "${pDoc.getString("firstName") ?: ""} ${pDoc.getString("lastName") ?: ""}".trim()
                                    val pUrl = pDoc.getString("pendingPhotoUrl") ?: ""
                                    activeContainer.addView(createPhotoModerationCard(pDoc.id, pName, pUrl))
                                }
                            }

                            val activeHeaderLabel = TextView(this).apply {
                                text = "Active Students"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                setPadding(0, 20, 0, 10)
                            }
                            activeHeaderView = activeHeaderLabel
                            activeContainer.addView(activeHeaderLabel)

                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val username = doc.getString("username") ?: ""
                                val level = doc.getLong("level")?.toInt() ?: 1
                                val photoUrl = doc.getString("photoUrl")

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                val userHandle = if (username.isNotBlank()) "@$username • " else ""
                                view.findViewById<TextView>(R.id.tv_student_details).text = "${userHandle}Level $level Explorer"

                                val ivPhoto = view.findViewById<ImageView>(R.id.iv_student_avatar_photo)
                                val tvMonogram = view.findViewById<TextView>(R.id.tv_student_avatar_monogram)
                                val initial = fullName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
                                tvMonogram?.text = initial
                                GabAIUtils.loadAvatarImage(photoUrl, ivPhoto, tvMonogram)

                                view.setOnClickListener { openStudentProfile(doc) }
                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, false, fullName, "") }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, false, fullName) }

                                activeItemViews.add(view to fullName)
                                activeContainer.addView(view)
                            }
                            applyStudentSearchFilter()
                        }
                    }

            }
            // ==========================================
            // PATH B: SUBJECT TEACHER VIEW (LEAST PRIVILEGE)
            // ==========================================
            else {
                // Completely hide the Pending Accounts container
                pendingContainer.visibility = View.GONE
                pendingItemViews.clear()

                val currentTeacherUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val teacherJoined = subjectEnrollments?.get(currentTeacherUid)

                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) return@addSnapshotListener
                        activeContainer.removeAllViews()
                        activeItemViews.clear()

                        // GATEKEEPER: Scoped to students enrolled with this teacher, falling back to joinedStudents if legacy
                        val activeDocs = snapshots.documents.filter { doc ->
                            if (teacherJoined != null) {
                                teacherJoined.contains(doc.id)
                            } else {
                                joinedStudents.contains(doc.id)
                            }
                        }

                        if (activeDocs.isEmpty()) {
                            activeHeaderView = null
                            activeContainer.addView(TextView(this).apply { text = "No students have joined using your code yet." })
                        } else {
                            val pendingPhotos = activeDocs.filter { it.getString("photoStatus") == "pending" }
                            if (pendingPhotos.isNotEmpty()) {
                                activeContainer.addView(TextView(this).apply {
                                    text = "📷 Photo Approvals (${pendingPhotos.size} Pending)"
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    setTextColor(Color.parseColor("#5341CD"))
                                    setPadding(0, 10, 0, 10)
                                })
                                for (pDoc in pendingPhotos) {
                                    val pName = "${pDoc.getString("firstName") ?: ""} ${pDoc.getString("lastName") ?: ""}".trim()
                                    val pUrl = pDoc.getString("pendingPhotoUrl") ?: ""
                                    activeContainer.addView(createPhotoModerationCard(pDoc.id, pName, pUrl))
                                }
                            }

                            val activeHeaderLabel = TextView(this).apply {
                                text = "Students Joined"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                setPadding(0, 0, 0, 10)
                            }
                            activeHeaderView = activeHeaderLabel
                            activeContainer.addView(activeHeaderLabel)

                            for (doc in activeDocs) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val username = doc.getString("username") ?: ""
                                val level = doc.getLong("level")?.toInt() ?: 1
                                val photoUrl = doc.getString("photoUrl")

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                val userHandle = if (username.isNotBlank()) "@$username • " else ""
                                view.findViewById<TextView>(R.id.tv_student_details).text = "${userHandle}Level $level Explorer"

                                val ivPhoto = view.findViewById<ImageView>(R.id.iv_student_avatar_photo)
                                val tvMonogram = view.findViewById<TextView>(R.id.tv_student_avatar_monogram)
                                val initial = fullName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
                                tvMonogram?.text = initial
                                GabAIUtils.loadAvatarImage(photoUrl, ivPhoto, tvMonogram)

                                view.setOnClickListener { openStudentProfile(doc) }

                                // Remove editing controls so they can't mess with the adviser's students
                                view.findViewById<ImageButton>(R.id.btn_edit_student).visibility = View.GONE
                                view.findViewById<ImageButton>(R.id.btn_delete_student).visibility = View.GONE

                                activeItemViews.add(view to fullName)
                                activeContainer.addView(view)
                            }
                            applyStudentSearchFilter()
                        }
                    }
            }
        }
    }
    // CREATE
    private fun showGenerateStudentsDialog() {
        GabAIDialogs.showInputDialog(
            context = this,
            title = "Add Students to $className",
            message = "List the students (one name per line). The system will auto-generate secure Usernames and Passwords for them.",
            hint = "Enter student names, one per line\n(e.g.\nJuan Cruz\nMaria Clara)",
            confirmText = "Generate",
            cancelText = "Cancel",
            minLines = 4,
            badgeIcon = "👥",
            onConfirm = { namesText ->
                if (namesText.isNotEmpty()) {
                    val namesList = namesText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    generateStudentAccounts(namesList)
                }
            }
        )
    }

    private fun generateStudentAccounts(names: List<String>) {
        val validNames = names.map { it.trim() }.filter { it.isNotBlank() }
        if (validNames.isEmpty()) return

        var completedCount = 0
        val usedInBatch = mutableSetOf<String>()
        com.example.gabai.GabAIUtils.showSnackbar(this, "Generating ${validNames.size} accounts...")

        for (fullName in validNames) {
            val parts = fullName.split("\\s+".toRegex())
            val firstName = parts.firstOrNull() ?: "Student"
            val lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

            val cleanBase = "${firstName}${lastName}".replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
            val baseName = if (cleanBase.isNotBlank()) cleanBase else "student"

            var username: String
            var attempts = 0
            do {
                val randomNum = (10000..99999).random()
                username = "${baseName}_$randomNum"
                attempts++
            } while (usedInBatch.contains(username) && attempts < 100)
            usedInBatch.add(username)

            val password = java.util.UUID.randomUUID().toString().substring(0, 6)

            val studentData = hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "username" to username,
                "password" to password,
                "mustChangePassword" to true,
                "role" to "student",
                "section" to sectionName,
                "grade" to grade,
                "schoolId" to schoolId,
                "current_xp" to 0,
                "level" to 1,
                "isApproved" to true,
                "is_onboarded" to false,
                "quests_completed" to listOf<String>(),
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("pending_students").document(username).set(studentData)
                .addOnSuccessListener {
                    completedCount++
                    if (completedCount == validNames.size) {
                        com.example.gabai.GabAIUtils.showSnackbar(this, "$completedCount accounts generated!")
                    }
                }
                .addOnFailureListener {
                    completedCount++
                }
        }
    }

    // UPDATE
    private fun showEditStudentDialog(docId: String, isPending: Boolean, currentFullName: String, pass: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val etName = EditText(this).apply {
            setText(currentFullName)
            hint = "Full Name"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            textSize = 14f
        }
        layout.addView(etName)

        var etPass: EditText? = null
        if (isPending) {
            etPass = EditText(this).apply {
                setText(pass)
                hint = "Password"
                setBackgroundResource(R.drawable.bg_modern_input)
                setPadding(32, 24, 32, 24)
                setTextColor(Color.parseColor("#2D3436"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }
            }
            layout.addView(etPass)
        }

        val btnResetPass = com.google.android.material.button.MaterialButton(this).apply {
            text = "Reset to Temporary Password 🔑"
            textSize = 13f
            setTextColor(Color.parseColor("#5341CD"))
            setBackgroundColor(Color.parseColor("#EEF2FF"))
            cornerRadius = (14 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        layout.addView(btnResetPass)

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Edit Student",
            subtitle = "Update student profile details",
            customView = layout,
            confirmText = "Save",
            cancelText = "Cancel",
            badgeIcon = "✏️",
            onConfirm = { dialog ->
                val newFullName = etName.text.toString().trim()
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

                dialog.dismiss()
                val collection = if (isPending) "pending_students" else "users"
                db.collection(collection).document(docId).update(updates)
                    .addOnSuccessListener { GabAIUtils.showSnackbar(this, "Updated successfully") }
            }
        ).also { activeDialog ->
            btnResetPass.setOnClickListener {
                activeDialog.dismiss()
                resetStudentTemporaryPassword(docId, isPending, currentFullName)
            }
        }
    }

    private fun resetStudentTemporaryPassword(docId: String, isPending: Boolean, studentName: String) {
        val tempPass = "gab" + (10000..99999).random()
        val collection = if (isPending) "pending_students" else "users"
        val updates = mutableMapOf<String, Any>(
            "password" to tempPass,
            "mustChangePassword" to true
        )
        if (!isPending) {
            updates["tempPassword"] = tempPass
        }

        db.collection(collection).document(docId).update(updates)
            .addOnSuccessListener {
                GabAIDialogs.showNoticeDialog(
                    context = this,
                    title = "Temporary Password",
                    message = "Temporary credentials for $studentName:\n\nPassword: $tempPass\n\nWhen $studentName signs in, they will be required to set their own permanent password.",
                    buttonText = "Copy & Close",
                    badgeIcon = "🔑",
                    onAction = {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Temporary Password", tempPass)
                        clipboard.setPrimaryClip(clip)
                        GabAIUtils.showSnackbar(this, "Copied temporary password: $tempPass 📋")
                    }
                )
            }
            .addOnFailureListener { e ->
                GabAIUtils.showSnackbar(this, "Failed to reset password: ${e.message}")
            }
    }

    // DELETE
    private fun confirmDelete(docId: String, isPending: Boolean, studentName: String) {
        GabAIDialogs.showConfirmDialog(
            this,
            title = "Remove Student?",
            message = "Are you sure you want to remove $studentName from this section? You can restore them anytime from the Recycle Vault.",
            confirmText = "Remove",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "👤"
        ) {
            GabAIUtils.showGlobalLoading(this, "Moving student to Recycle Vault...")
            val currentUser = auth.currentUser
            val operatorUid = currentUser?.uid ?: ""
            val operatorName = currentUser?.displayName ?: "Teacher"

            ArchiveVaultRepository.archiveStudent(
                studentDocId = docId,
                isPending = isPending,
                schoolId = schoolId,
                operatorUid = operatorUid,
                operatorName = operatorName,
                operatorRole = "teacher",
                reason = "Removed from section: $className",
                onSuccess = { archiveId ->
                    GabAIUtils.hideGlobalLoading(this)
                    // Clean up joinedStudents from class document
                    db.collection("classes").document(classId).update(
                        "joinedStudents", com.google.firebase.firestore.FieldValue.arrayRemove(docId)
                    )
                    loadStudents()

                    val rootLayout = findViewById<View>(android.R.id.content)
                    GabAIUtils.showUndoSnackbar(
                        view = rootLayout,
                        message = "$studentName moved to Recycle Vault",
                        actionText = "Undo"
                    ) {
                        GabAIUtils.showGlobalLoading(this, "Restoring student...")
                        ArchiveVaultRepository.restoreEntity(
                            archiveId = archiveId,
                            restoredByUid = operatorUid,
                            onSuccess = {
                                GabAIUtils.hideGlobalLoading(this)
                                db.collection("classes").document(classId).update(
                                    "joinedStudents", com.google.firebase.firestore.FieldValue.arrayUnion(docId)
                                )
                                loadStudents()
                                GabAIUtils.showSnackbar(this, "Restored $studentName successfully!")
                            },
                            onFailure = { e ->
                                GabAIUtils.hideGlobalLoading(this)
                                GabAIUtils.showSnackbar(this, "Failed to restore: ${e.message}")
                            }
                        )
                    }
                },
                onFailure = { e ->
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Failed to remove student: ${e.message}")
                }
            )
        }
    }
    // ==============================================================
    // 🟢 PHASE 1: INITIATION QUEST MANAGEMENT 🟢
    // ==============================================================
    private fun openInitiationSettings() {
        GabAIUtils.showGlobalLoading(this, "Loading settings...")

        db.collection("classes").document(classId).get().addOnSuccessListener { doc ->
            GabAIUtils.hideGlobalLoading(this)
            currentInitiationItems = doc.getLong("initiation_items")?.toInt() ?: 5

            val savedPdfs = doc.get("initiation_pdfs") as? Map<String, Map<String, String>>
            if (savedPdfs != null) {
                customPdfs.clear()
                customPdfs.putAll(savedPdfs)
            }
            showInitiationDialogUI()
        }.addOnFailureListener {
            GabAIUtils.hideGlobalLoading(this)
            GabAIUtils.showSnackbar(this, "Failed to load settings.")
        }
    }

    private fun showInitiationDialogUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val itemsLabel = TextView(this).apply {
            text = "Number of Quiz Items per material:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
            setPadding(0, 0, 0, 8)
        }
        val itemsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentInitiationItems.toString())
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            textSize = 14f
        }
        layout.addView(itemsLabel)
        layout.addView(itemsInput)

        val pdfsLabel = TextView(this).apply {
            text = "Custom Reading Materials (Optional):"
            setPadding(0, 24, 0, 10)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
        }
        layout.addView(pdfsLabel)

        // Generate 4 Upload Slots
        for (i in 1..4) {
            val slotData = customPdfs[i.toString()]
            val btnText = if (slotData != null) "Material $i: ${slotData["title"]}" else "+ Upload Material $i"

            val btn = Button(this).apply {
                text = btnText
                isAllCaps = false
                setTextColor(Color.parseColor("#4338CA"))
                setBackgroundResource(R.drawable.bg_btn_modern_secondary)
                setOnClickListener {
                    currentPdfSlot = i
                    pdfPickerLauncher.launch("application/pdf")
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.density * 46).toInt()
                ).apply { setMargins(0, 0, 0, 12) }
            }
            layout.addView(btn)
        }

        val scrollView = ScrollView(this).apply {
            addView(layout)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 340).toInt()
            )
        }

        activeDialog = GabAIDialogs.showCustomDialog(
            context = this,
            title = "Manage Initiation Quest",
            subtitle = "Configure quest questions and study materials",
            customView = scrollView,
            confirmText = "Save Item Count",
            cancelText = "Cancel",
            badgeIcon = "📜",
            onConfirm = { dialog ->
                val newCount = itemsInput.text.toString().toIntOrNull() ?: 5
                db.collection("classes").document(classId).update("initiation_items", newCount)
                GabAIUtils.showSnackbar(this, "Initiation settings updated!")
                dialog.dismiss()
            }
        )
    }

    private fun promptForInitiationPdfTitle(fileUri: Uri) {
        var originalName = ""
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) originalName = cursor.getString(nameIndex) ?: ""
        }
        originalName = originalName.removeSuffix(".pdf").replace("_", " ")

        GabAIDialogs.showInputDialog(
            context = this,
            title = "Upload Custom Material $currentPdfSlot",
            subtitle = "Enter a title for this initiation reading material:",
            initialText = originalName,
            hint = "Enter Material Title",
            confirmText = "Upload",
            cancelText = "Cancel",
            badgeIcon = "📄",
            onConfirm = { title ->
                if (title.isNotEmpty()) uploadInitiationPdfToDrive(fileUri, title)
            }
        )
    }

    private fun uploadInitiationPdfToDrive(fileUri: Uri, title: String) {
        GabAIUtils.showGlobalLoading(this, "AI is validating reading content...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pfd = contentResolver.openFileDescriptor(fileUri, "r")
                val fileSize = pfd?.statSize ?: 0L
                pfd?.close()
                if (fileSize > 15 * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                        GabAIUtils.showSnackbar(this@ClassDetailActivity, "PDF exceeds the 15MB upload limit. Please compress or select a smaller PDF.")
                    }
                    return@launch
                }

                // AI CONTENT VALIDATION: Ensure only reading material is uploaded
                val validation = AIContentValidator.validateReadingPdf(this@ClassDetailActivity, fileUri)
                if (!validation.isValid) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                        GabAIDialogs.showNoticeDialog(
                            context = this@ClassDetailActivity,
                            title = "Document Rejected by AI",
                            message = "Detected: ${validation.detectedSubject}\n\n${validation.rejectionReason}\n\nInitiation quest materials must be reading comprehension passages.",
                            buttonText = "Understood",
                            badgeIcon = "🚫"
                        )
                        openInitiationSettings()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    GabAIUtils.showGlobalLoading(this@ClassDetailActivity, "Uploading Material $currentPdfSlot to Google Drive...")
                }

                val inputStream = contentResolver.openInputStream(fileUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Could not read file.")
                val base64File = Base64.encodeToString(bytes, Base64.DEFAULT)
                inputStream.close()

                val cleanTitle = title.replace(Regex("[^A-Za-z0-9]"), "")
                val systematicFilename = "GabAI_Initiation_${classId}_${cleanTitle}.pdf"

                val client = OkHttpClient.Builder().connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                val formBody = FormBody.Builder()
                    .add("action", "upload")
                    .add("fileName", systematicFilename)
                    .add("mimeType", "application/pdf")
                    .add("fileData", base64File)
                    .build()

                val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                if (response.isSuccessful && responseData != null) {
                    val json = JSONObject(responseData)
                    if (json.getString("status") == "success") {
                        val downloadUrl = json.getString("url")
                        val fileId = json.getString("fileId")

                        // Save the custom PDF data to the map
                        customPdfs[currentPdfSlot.toString()] = mapOf("title" to title, "url" to downloadUrl, "fileId" to fileId)

                        // Push the map to Firestore
                        db.collection("classes").document(classId).update("initiation_pdfs", customPdfs).await()

                        withContext(Dispatchers.Main) {
                            GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                            GabAIUtils.showSnackbar(this@ClassDetailActivity, "Material $currentPdfSlot uploaded!")
                            openInitiationSettings() // Reopen the menu to show updated slots
                        }
                    } else throw Exception(json.getString("message"))
                } else throw Exception("Google Error Code: ${response.code}")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                    GabAIUtils.showSnackbar(this@ClassDetailActivity, "Upload Error: ${e.message}")
                    openInitiationSettings()
                }
            }
        }
    }

    // =========================================================
    // WEEKLY ASSESSMENT GENERATION & SECTION ROSTER METHODS
    // =========================================================

    private fun showCreateAssessmentDialog() {
        val focusOptions = listOf(
            "Standard Comprehensive (Balanced concepts & applications)",
            "Higher-Order Thinking & Problem Solving (Scenario & analytical)",
            "Core Vocabulary & Key Terms (Definitions & concept recall)",
            "Quick Diagnostic (Core knowledge & fast check)"
        )

        GabAIDialogs.showSelectionDialog(
            context = this,
            title = "Assessment Focus",
            subtitle = "Select how you want Gemini to generate the assessment",
            items = focusOptions,
            badgeIcon = "📝",
            onSelected = { _, selectedText ->
                selectedGeminiFocus = selectedText
                promptUploadAssessmentPdf()
            }
        )
    }

    private fun promptUploadAssessmentPdf() {
        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Upload Assessment PDF",
            message = "Please upload the lesson PDF or reviewer document for Section $sectionName ($className).\n\nGemini will analyze the material and create 10 multiple-choice questions focusing on:\n• $selectedGeminiFocus.",
            confirmText = "Choose PDF",
            cancelText = "Cancel",
            badgeIcon = "📄",
            onConfirm = {
                assessmentPdfPickerLauncher.launch("application/pdf")
            }
        )
    }

    private fun handleAssessmentPdfSelected(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        GabAIUtils.showGlobalLoading(this, "AI is validating reading content...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val extractedText = extractTextFromUri(uri)
                if (extractedText.isBlank()) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                        GabAIUtils.showSnackbar(this@ClassDetailActivity, "Could not extract text from this PDF. Please select a readable document.")
                    }
                    return@launch
                }

                // AI CONTENT VALIDATION: Ensure only reading comprehension materials are processed
                val validation = AIContentValidator.validateReadingDocument(extractedText)
                if (!validation.isValid) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                        GabAIDialogs.showNoticeDialog(
                            context = this@ClassDetailActivity,
                            title = "Document Rejected by AI",
                            message = "Detected: ${validation.detectedSubject}\n\n${validation.rejectionReason}\n\nWeekly assessments must be based on reading comprehension materials.",
                            buttonText = "Understood",
                            badgeIcon = "🚫"
                        )
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    GabAIUtils.showGlobalLoading(this@ClassDetailActivity, "Gemini is generating 10 weekly assessment questions for $sectionName...")
                }

                val generativeModel = GenerativeModel(
                    modelName = "gemini-3.5-flash-lite",
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val prompt = """
                    You are an expert reading curriculum designer and literacy assessment specialist.
                    Section: $sectionName
                    Class/Subject: $className
                    Grade Level: $grade
                    Generation Style / Assessment Focus: $selectedGeminiFocus

                    Analyze the following lesson material and generate exactly 10 high-quality multiple choice assessment questions following the specified focus style.

                    Source Text:
                    ${extractedText.take(12000)}

                    Requirements:
                    1. Generate exactly 10 questions.
                    2. 4 plausible multiple-choice options per question.
                    3. "ans" must be the 0-indexed integer of the correct option (0, 1, 2, or 3).
                    4. "explanation" must be a clear educational explanation of why the correct choice is right.
                    5. Return ONLY a valid JSON array. No markdown fences or commentary.

                    JSON Array format:
                    [
                      {
                        "q": "Question text here?",
                        "options": ["Choice A", "Choice B", "Choice C", "Choice D"],
                        "ans": 0,
                        "explanation": "Clear educational explanation."
                      }
                    ]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) {
                    jsonStr = jsonStr.substring(startIndex, endIndex + 1)
                }

                val array = JSONArray(jsonStr)
                if (array.length() == 0) throw Exception("No questions generated by AI.")

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                    val intent = Intent(this@ClassDetailActivity, QuizEditorActivity::class.java).apply {
                        putExtra("IS_WEEKLY_ASSESSMENT", true)
                        putExtra("TARGET_CLASS_ID", classId)
                        putExtra("TARGET_CLASS_NAME", if (grade.isNotEmpty() && !className.contains(grade)) "$grade - $className" else className)
                        putExtra("SECTION_NAME", sectionName)
                        putExtra("GRADE", grade)
                        putExtra("SCHOOL_ID", schoolId)
                        putExtra("SUBJECT_NAME", className)
                        val cleanTitle = fileName.removeSuffix(".pdf").replace('_', ' ')
                        putExtra("ASSESSMENT_TITLE", "Weekly Assessment: $cleanTitle")
                        putExtra("TARGET_ITEMS", 10)
                        putExtra("SOURCE_TYPE", "pdf")
                        putExtra("SOURCE_REF", fileName)
                        putExtra("QUIZ_JSON", jsonStr)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@ClassDetailActivity)
                    GabAIUtils.showSnackbar(this@ClassDetailActivity, "Generation error: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    private fun extractTextFromUri(uri: Uri): String {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            inputStream?.close()
            text
        } catch (e: Exception) {
            ""
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "assessment_document.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun loadWeeklyAssessments() {
        val container = findViewById<LinearLayout>(R.id.section_weekly_assessments_container) ?: return

        db.collection("weekly_assessments")
            .whereEqualTo("classId", classId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    val emptyView = TextView(this).apply {
                        text = "No weekly assessments created for this section yet.\nTap '✨ Create Weekly Assessment' below to generate one with Gemini."
                        setTextColor(Color.parseColor("#8898AA"))
                        textSize = 13f
                        setPadding(16, 20, 16, 20)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(emptyView)
                    return@addSnapshotListener
                }

                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

                for (doc in snapshots) {
                    val assessmentId = doc.id
                    val title = doc.getString("title") ?: "Weekly Assessment"
                    val subject = doc.getString("subjectName") ?: className
                    val teacher = doc.getString("teacherName") ?: "Teacher"
                    val count = doc.getLong("questionCount")?.toInt() ?: 10
                    val dueMillis = doc.getLong("dueDate") ?: 0L

                    val card = layoutInflater.inflate(R.layout.item_weekly_assessment_card, container, false)
                    card.findViewById<TextView>(R.id.tv_assessment_subject_badge)?.text = subject
                    card.findViewById<TextView>(R.id.tv_assessment_item_count)?.text = "$count Items"
                    card.findViewById<TextView>(R.id.tv_assessment_title)?.text = title
                    card.findViewById<TextView>(R.id.tv_assessment_meta)?.text = "Assigned by $teacher"

                    val tvDueDate = card.findViewById<TextView>(R.id.tv_assessment_due_date)
                    if (dueMillis > 0) {
                        tvDueDate?.visibility = View.VISIBLE
                        tvDueDate?.text = "Due: ${sdf.format(Date(dueMillis))}"
                    } else {
                        tvDueDate?.visibility = View.GONE
                    }

                    val btnAction = card.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_action_assessment)
                    
                    // 🟢 Query submissions for this weekly assessment
                    db.collection("assessment_submissions")
                        .whereEqualTo("assessmentId", assessmentId)
                        .get()
                        .addOnSuccessListener { subSnaps ->
                            val subCount = subSnaps.size()
                            if (subCount > 0) {
                                btnAction?.text = "Review ($subCount) ➔"
                                btnAction?.setOnClickListener {
                                    showSubmissionsReviewDialog(assessmentId, title, subSnaps.documents)
                                }
                            } else {
                                btnAction?.text = "Preview ➔"
                                btnAction?.setOnClickListener {
                                    val intent = Intent(this, WeeklyAssessmentActivity::class.java).apply {
                                        putExtra("ASSESSMENT_ID", assessmentId)
                                        putExtra("CLASS_ID", classId)
                                        putExtra("CLASS_NAME", className)
                                        putExtra("GRADE", grade)
                                        putExtra("SCHOOL_ID", schoolId)
                                    }
                                    startActivity(intent)
                                }
                            }
                        }
                        .addOnFailureListener {
                            btnAction?.text = "Preview ➔"
                            btnAction?.setOnClickListener {
                                val intent = Intent(this, WeeklyAssessmentActivity::class.java).apply {
                                    putExtra("ASSESSMENT_ID", assessmentId)
                                    putExtra("CLASS_ID", classId)
                                    putExtra("CLASS_NAME", className)
                                    putExtra("GRADE", grade)
                                    putExtra("SCHOOL_ID", schoolId)
                                }
                                startActivity(intent)
                            }
                        }

                    card.setOnClickListener {
                        val intent = Intent(this, WeeklyAssessmentActivity::class.java).apply {
                            putExtra("ASSESSMENT_ID", assessmentId)
                            putExtra("CLASS_ID", classId)
                            putExtra("CLASS_NAME", className)
                            putExtra("GRADE", grade)
                            putExtra("SCHOOL_ID", schoolId)
                        }
                        startActivity(intent)
                    }

                    container.addView(card)
                }
            }
    }

    private fun showSubmissionsReviewDialog(
        assessmentId: String,
        assessmentTitle: String,
        submissions: List<DocumentSnapshot>
    ) {
        val density = resources.displayMetrics.density
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

        for (doc in submissions) {
            val studentName = doc.getString("studentName") ?: "Student"
            val score = doc.getLong("score")?.toInt() ?: 0
            val total = doc.getLong("totalQuestions")?.toInt() ?: 10
            val pct = doc.getLong("percentage")?.toInt() ?: if (total > 0) (score * 100) / total else 0
            val subTime = doc.getLong("submittedAt") ?: 0L
            val currentRemark = doc.getString("teacherRemark") ?: ""

            val itemCard = MaterialCardView(this).apply {
                radius = 16 * density
                cardElevation = 0f
                strokeWidth = (1 * density).toInt()
                setStrokeColor(Color.parseColor("#E2E8F0"))
                setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (10 * density).toInt())
                }
                layoutParams = params
            }

            val itemContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tvName = TextView(this).apply {
                text = studentName
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeColor = if (pct >= 75) "#10B981" else if (pct >= 50) "#F59E0B" else "#EF4444"
            val tvScore = TextView(this).apply {
                text = "$score / $total ($pct%)"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor(badgeColor))
            }

            headerRow.addView(tvName)
            headerRow.addView(tvScore)
            itemContent.addView(headerRow)

            val tvMeta = TextView(this).apply {
                text = if (subTime > 0) "Submitted: ${sdf.format(Date(subTime))}" else "Submitted"
                textSize = 11f
                setTextColor(Color.parseColor("#94A3B8"))
                setPadding(0, (4 * density).toInt(), 0, 0)
            }
            itemContent.addView(tvMeta)

            if (currentRemark.isNotBlank()) {
                val tvRemark = TextView(this).apply {
                    text = "💬 Commendation: $currentRemark"
                    textSize = 12f
                    setTextColor(Color.parseColor("#4F46E5"))
                    setPadding(0, (6 * density).toInt(), 0, 0)
                }
                itemContent.addView(tvRemark)
            }

            itemCard.addView(itemContent)

            // Click to add / edit teacher remark
            itemCard.setOnClickListener {
                showAddTeacherRemarkDialog(doc.id, studentName, currentRemark)
            }

            listLayout.addView(itemCard)
        }

        val scroll = ScrollView(this).apply {
            addView(listLayout)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (360 * density).toInt()
            )
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Submissions Review",
            subtitle = "$assessmentTitle • ${submissions.size} Submission(s)",
            customView = scroll,
            confirmText = null,
            cancelText = "Done",
            badgeIcon = "📝"
        )
    }

    private fun showAddTeacherRemarkDialog(submissionDocId: String, studentName: String, existingRemark: String) {
        val input = EditText(this).apply {
            setText(existingRemark)
            hint = "e.g., Great mastery of vocabulary! Keep reading."
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 14f
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Teacher Commendation",
            subtitle = "Feedback for $studentName",
            customView = input,
            confirmText = "Save Remark",
            cancelText = "Cancel",
            badgeIcon = "💬",
            onConfirm = { dialog ->
                val newRemark = input.text.toString().trim()
                db.collection("assessment_submissions").document(submissionDocId)
                    .update("teacherRemark", newRemark)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        com.example.gabai.GabAIUtils.showSnackbar(this, "Commendation saved for $studentName! ⭐")
                        loadWeeklyAssessments()
                    }
            }
        )
    }

    private fun openStudentProfile(doc: DocumentSnapshot) {
        val fName = doc.getString("firstName") ?: ""
        val lName = doc.getString("lastName") ?: ""
        val fullName = "$fName $lName".trim()
        val level = doc.getLong("level")?.toInt() ?: 1
        val xp = doc.getLong("current_xp")?.toInt() ?: 0
        val sheet = StudentProfileBottomSheet.newInstance(
            studentId = doc.id,
            studentName = fullName,
            grade = grade,
            section = sectionName,
            level = level,
            xp = xp,
            isTeacherView = true
        )
        sheet.show(supportFragmentManager, "StudentProfileSheet")
    }

    private fun createPhotoModerationCard(studentId: String, studentName: String, photoUrl: String): View {
        val density = resources.displayMetrics.density
        val card = MaterialCardView(this).apply {
            radius = 16f * density
            strokeWidth = (1f * density).toInt()
            strokeColor = Color.parseColor("#E2E8F0")
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (12f * density).toInt())
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16f * density).toInt(),
                (14f * density).toInt(),
                (16f * density).toInt(),
                (14f * density).toInt()
            )
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val avatarFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (54f * density).toInt(),
                (54f * density).toInt()
            ).apply {
                marginEnd = (12f * density).toInt()
            }
            setBackgroundResource(R.drawable.bg_bento_purple)
            clipToOutline = true
        }

        val avatarMonogram = TextView(this).apply {
            text = studentName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
            textSize = 20f
            setTextColor(Color.parseColor("#5341CD"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }

        val avatarImageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        avatarFrame.addView(avatarMonogram)
        avatarFrame.addView(avatarImageView)

        // Visually render student's submitted photo from Drive
        GabAIUtils.loadAvatarImage(photoUrl, avatarImageView, avatarMonogram)

        avatarFrame.setOnClickListener {
            GabAIDialogs.showConfirmDialog(
                this,
                title = "$studentName's Photo",
                message = "Student submitted a profile photo for verification.\n\nApprove or decline this photo for the student profile?",
                confirmText = "Approve",
                cancelText = "Decline",
                badgeIcon = "📷"
            ) {
                db.collection("users").document(studentId)
                    .update("photoUrl", photoUrl, "photoStatus", "approved", "pendingPhotoUrl", null)
                    .addOnSuccessListener {
                        GabAIUtils.showSnackbar(this@ClassDetailActivity, "Approved photo for $studentName! ✅")
                    }
            }
        }

        val nameView = TextView(this).apply {
            text = "$studentName\nSubmitted a photo for review"
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleRow.addView(avatarFrame)
        titleRow.addView(nameView)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10f * density).toInt()
            }
        }

        val btnApprove = MaterialButton(this).apply {
            text = "✅ Approve"
            textSize = 12f
            cornerRadius = (10f * density).toInt()
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, (42f * density).toInt(), 1f).apply {
                marginEnd = (6f * density).toInt()
            }
            setOnClickListener {
                db.collection("users").document(studentId)
                    .update("photoUrl", photoUrl, "photoStatus", "approved", "pendingPhotoUrl", null)
                    .addOnSuccessListener {
                        GabAIUtils.showSnackbar(this@ClassDetailActivity, "Approved photo for $studentName! ✅")
                    }
            }
        }

        val btnDecline = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "❌ Decline"
            textSize = 12f
            cornerRadius = (10f * density).toInt()
            strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            strokeWidth = (1f * density).toInt()
            setTextColor(Color.parseColor("#EF4444"))
            layoutParams = LinearLayout.LayoutParams(0, (42f * density).toInt(), 1f).apply {
                marginStart = (6f * density).toInt()
            }
            setOnClickListener {
                db.collection("users").document(studentId)
                    .update("photoStatus", "declined", "pendingPhotoUrl", null)
                    .addOnSuccessListener {
                        GabAIUtils.showSnackbar(this@ClassDetailActivity, "Declined photo for $studentName.")
                    }
            }
        }

        actionRow.addView(btnApprove)
        actionRow.addView(btnDecline)
        layout.addView(titleRow)
        layout.addView(actionRow)
        card.addView(layout)
        return card
    }
}