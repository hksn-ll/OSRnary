package com.example.gabai

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

class ClassDetailActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var sectionName: String
    private lateinit var schoolId: String
    private lateinit var grade: String // 🟢 NEW
    private val db = FirebaseFirestore.getInstance()
    private var isAdviser: Boolean = true
    // --- INITIATION MANAGEMENT VARIABLES ---
    private var currentPdfSlot = 1
    private var currentInitiationItems = 5
    private var customPdfs = mutableMapOf<String, Map<String, String>>()
    private val driveApiUrl = GabAIApp.DRIVE_API_URL
    private var activeDialog: Dialog? = null

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
            btnViewPerformance?.visibility = View.GONE
            tvLabelUnclaimed?.visibility = View.GONE
        } else {
            btnManageInitiation?.let { view ->
                GabAIUtils.addSpringPressEffect(view) { openInitiationSettings() }
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
        }

        btnCreateAssessment?.let { view ->
            GabAIUtils.addSpringPressEffect(view) { showCreateAssessmentDialog() }
        }

        btnGenerate?.let { view ->
            GabAIUtils.addSpringPressEffect(view) { showGenerateStudentsDialog() }
        }

        // 5. Load the students into the lists
        loadStudents()

        // 6. Section Roster Assessment List
        loadWeeklyAssessments()
    }

    private fun loadStudents() {
        val pendingContainer = findViewById<LinearLayout>(R.id.pending_students_container)
        val activeContainer = findViewById<LinearLayout>(R.id.active_students_container)

        // 1. Fetch the class document to get the joinedStudents list
        db.collection("classes").document(classId).get().addOnSuccessListener { classDoc ->
            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

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

                        if (snapshots.isEmpty) {
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
                                setTextColor(Color.DKGRAY)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }
                            val btnCopyAll = com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                                text = "Copy All 📋"
                                textSize = 12f
                                isAllCaps = false
                                setOnClickListener {
                                    val rosterBuilder = StringBuilder()
                                    rosterBuilder.append("Section: $sectionName ($grade)\n")
                                    rosterBuilder.append("Unclaimed Student Accounts:\n\n")
                                    for (d in snapshots) {
                                        val fn = "${d.getString("firstName") ?: ""} ${d.getString("lastName") ?: ""}".trim()
                                        val u = d.getString("username") ?: ""
                                        val p = d.getString("password") ?: ""
                                        rosterBuilder.append("• $fn\n  Username: $u\n  Password: $p\n\n")
                                    }
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Class Roster Credentials", rosterBuilder.toString().trim())
                                    clipboard.setPrimaryClip(clip)
                                    GabAIUtils.performHaptic(this, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    GabAIUtils.showSnackbar(this@ClassDetailActivity, "Copied all credentials to clipboard! 📋")
                                }
                            }
                            headerLayout.addView(headerTitle)
                            headerLayout.addView(btnCopyAll)
                            pendingContainer.addView(headerLayout)
                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val username = doc.getString("username") ?: ""
                                val password = doc.getString("password") ?: ""

                                val view = layoutInflater.inflate(R.layout.item_student_manage, pendingContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                val detailsView = view.findViewById<TextView>(R.id.tv_student_details)
                                val maskedPass = "•".repeat(password.length.coerceIn(6, 10))
                                detailsView.text = "User: $username | Pass: $maskedPass  📋 Tap to copy"
                                detailsView.setOnClickListener {
                                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Student Credentials", "Username: $username\nPassword: $password")
                                    clipboard.setPrimaryClip(clip)
                                    GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    GabAIUtils.showSnackbar(this, "Copied credentials for $fullName! 📋")
                                }

                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, true, fullName, password) }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, true, fullName) }
                                pendingContainer.addView(view)
                            }
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

                        if (snapshots.isEmpty) {
                            activeContainer.addView(TextView(this).apply { text = "No active students." })
                        } else {
                            activeContainer.addView(TextView(this).apply {
                                text = "Active Students"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                setPadding(0, 30, 0, 10)
                            })
                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val level = doc.getLong("level")?.toInt() ?: 1

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                view.findViewById<TextView>(R.id.tv_student_details).text = "Level $level Explorer"

                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, false, fullName, "") }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, false, fullName) }
                                activeContainer.addView(view)
                            }
                        }
                    }

            }
            // ==========================================
            // PATH B: SUBJECT TEACHER VIEW (LEAST PRIVILEGE)
            // ==========================================
            else {
                // Completely hide the Pending Accounts container
                pendingContainer.visibility = View.GONE

                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) return@addSnapshotListener
                        activeContainer.removeAllViews()

                        // GATEKEEPER: Only allow students in the joinedStudents array
                        val activeDocs = snapshots.documents.filter { joinedStudents.contains(it.id) }

                        if (activeDocs.isEmpty()) {
                            activeContainer.addView(TextView(this).apply { text = "No students have joined using your code yet." })
                        } else {
                            activeContainer.addView(TextView(this).apply {
                                text = "Students Joined"
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                setPadding(0, 0, 0, 10)
                            })
                            for (doc in activeDocs) {
                                val fName = doc.getString("firstName") ?: ""
                                val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val level = doc.getLong("level")?.toInt() ?: 1

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                view.findViewById<TextView>(R.id.tv_student_details).text = "Level $level Explorer"

                                // Remove editing controls so they can't mess with the adviser's students
                                view.findViewById<ImageButton>(R.id.btn_edit_student).visibility = View.GONE
                                view.findViewById<ImageButton>(R.id.btn_delete_student).visibility = View.GONE

                                activeContainer.addView(view)
                            }
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
        var completedCount = 0
        com.example.gabai.GabAIUtils.showSnackbar(this, "Generating ${names.size} accounts...")

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
                "grade" to grade,
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
                    com.example.gabai.GabAIUtils.showSnackbar(this, "$completedCount accounts generated!")
                }
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
        )
    }

    // DELETE
    private fun confirmDelete(docId: String, isPending: Boolean, studentName: String) {
        GabAIDialogs.showConfirmDialog(
            this,
            title = "Remove Student?",
            message = "Are you sure you want to remove $studentName from this section?",
            confirmText = "Remove",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "👤"
        ) {
            val collection = if (isPending) "pending_students" else "users"
            db.collection(collection).document(docId).delete()
                .addOnSuccessListener { com.example.gabai.GabAIUtils.showSnackbar(this, "Student removed") }
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
        GabAIUtils.showGlobalLoading(this, "Uploading Material $currentPdfSlot to Google Drive...")

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
        GabAIUtils.showGlobalLoading(this, "Gemini is generating 10 weekly assessment questions for $sectionName...")

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

                val generativeModel = GenerativeModel(
                    modelName = "gemini-3.5-flash-lite",
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val prompt = """
                    You are an expert DepEd curriculum designer and assessment specialist.
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
}