package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import androidx.appcompat.app.AlertDialog // Make sure you have this

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
    private val driveApiUrl = "https://script.google.com/macros/s/AKfycbxmlWtZXkpYqbgQU8wZ6Qdga9ImIHhlP5kMUSdujH8y2Db9SdP_DLswqoTO1-FDcf9CaQ/exec"
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            activeDialog?.dismiss() // Close settings dialog while we name the file
            promptForInitiationPdfTitle(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_detail)

        // 1. Get Data from Intent (FIXED: Removed 'val' so it uses your class variables)
        classId = intent.getStringExtra("CLASS_ID") ?: return finish()
        className = intent.getStringExtra("CLASS_NAME") ?: "Unknown"
        sectionName = intent.getStringExtra("SECTION_NAME") ?: ""
        schoolId = intent.getStringExtra("SCHOOL_ID") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""
        isAdviser = intent.getBooleanExtra("IS_ADVISER", false)

        // 2. Setup Header
        findViewById<TextView>(R.id.tv_header_title).text = className
        val header = findViewById<View>(R.id.detail_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val btnGenerate = findViewById<Button>(R.id.btn_generate_students)
        val btnManageInitiation = findViewById<Button>(R.id.btn_manage_initiation)

        if (!isAdviser) {
            btnGenerate.visibility = View.GONE
            btnManageInitiation.visibility = View.GONE
        } else {
            btnManageInitiation.setOnClickListener { openInitiationSettings() }
        }
        btnGenerate.setOnClickListener { showGenerateStudentsDialog() }

        // ---------------------------------------------------------
        // 3. THE PERFORMANCE BUTTON BLOCK
        // ---------------------------------------------------------
        val btnViewPerformance = findViewById<Button>(R.id.btn_view_performance)

        // Hide the button if they are just a subject teacher, not the adviser
        if (!isAdviser) {
            btnViewPerformance?.visibility = View.GONE
        }

        btnViewPerformance?.setOnClickListener {
            // Using full path "android.content.Intent" so you don't need to import it manually!
            val perfIntent = android.content.Intent(this, TeacherPerformanceActivity::class.java)
            perfIntent.putExtra("CLASS_ID", classId)
            perfIntent.putExtra("CLASS_NAME", className)
            perfIntent.putExtra("SECTION_NAME", sectionName)
            perfIntent.putExtra("SCHOOL_ID", schoolId)
            perfIntent.putExtra("GRADE", grade)
            startActivity(perfIntent)
        }
        // ---------------------------------------------------------

        // 4. HIDE GENERATE BUTTON IF NOT ADVISER
        if (!isAdviser) btnGenerate.visibility = View.GONE

        // 5. Load the students into the lists
        loadStudents()
    }

    private fun loadStudents() {
        val pendingContainer = findViewById<LinearLayout>(R.id.pending_students_container)
        val activeContainer = findViewById<LinearLayout>(R.id.active_students_container)
        val tvPendingTitle = findViewById<TextView>(R.id.tv_pending_title)

        // FIX: Use addSnapshotListener instead of get() so it updates instantly when a student joins via code!
        db.collection("classes").document(classId).addSnapshotListener { classDoc, classError ->
            if (classError != null || classDoc == null || !classDoc.exists()) return@addSnapshotListener

            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

            if (isAdviser) {
                // ADVISER VIEW: Show Pending and Active
                tvPendingTitle?.visibility = View.VISIBLE
                pendingContainer.visibility = View.VISIBLE

                // PENDING STUDENTS (Adviser)
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
                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""; val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val username = doc.getString("username") ?: ""; val password = doc.getString("password") ?: ""

                                val view = layoutInflater.inflate(R.layout.item_student_manage, pendingContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                view.findViewById<TextView>(R.id.tv_student_details).text = "User: $username | Pass: $password"

                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, true, fullName, password) }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, true, fullName) }
                                pendingContainer.addView(view)
                            }
                        }
                    }

                // ACTIVE STUDENTS (Adviser)
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
                            for (doc in snapshots) {
                                val fName = doc.getString("firstName") ?: ""; val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val level = doc.getLong("level")?.toInt() ?: 1

                                // FIX: Show User and Pass for active students so the teacher has a backup record
                                val username = doc.getString("username") ?: "N/A"
                                val password = doc.getString("password") ?: "N/A"

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                view.findViewById<TextView>(R.id.tv_student_details).text = "Lvl $level | User: $username | Pass: $password"

                                view.findViewById<ImageButton>(R.id.btn_edit_student).setOnClickListener { showEditStudentDialog(doc.id, false, fullName, "") }
                                view.findViewById<ImageButton>(R.id.btn_delete_student).setOnClickListener { confirmDelete(doc.id, false, fullName) }
                                activeContainer.addView(view)
                            }
                        }
                    }

            } else {
                // SUBJECT TEACHER VIEW: Hide Pending
                tvPendingTitle?.visibility = View.GONE
                pendingContainer.visibility = View.GONE

                if (joinedStudents.isEmpty()) {
                    activeContainer.removeAllViews()
                    activeContainer.addView(TextView(this).apply { text = "No students have joined using your code yet." })
                    return@addSnapshotListener
                }

                db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) return@addSnapshotListener
                        activeContainer.removeAllViews()

                        val activeDocs = snapshots.documents.filter { joinedStudents.contains(it.id) }

                        if (activeDocs.isEmpty()) {
                            activeContainer.addView(TextView(this).apply { text = "No students have joined using your code yet." })
                        } else {
                            for (doc in activeDocs) {
                                val fName = doc.getString("firstName") ?: ""; val lName = doc.getString("lastName") ?: ""
                                val fullName = "$fName $lName".trim()
                                val level = doc.getLong("level")?.toInt() ?: 1

                                val view = layoutInflater.inflate(R.layout.item_student_manage, activeContainer, false)
                                view.findViewById<TextView>(R.id.tv_student_name).text = fullName
                                view.findViewById<TextView>(R.id.tv_student_details).text = "Level $level Explorer"

                                view.findViewById<ImageButton>(R.id.btn_edit_student).visibility = View.GONE
                                view.findViewById<ImageButton>(R.id.btn_delete_student).visibility = View.GONE

                                activeContainer.addView(view)
                            }
                        }
                    }
            }
        }
    }    // CREATE
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
                    .addOnSuccessListener { com.example.gabai.GabAIUtils.showSnackbar(this, "Updated successfully") }
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
                    .addOnSuccessListener { com.example.gabai.GabAIUtils.showSnackbar(this, "Student removed") }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            setPadding(60, 40, 60, 40)
        }

        val itemsLabel = TextView(this).apply { text = "Number of Quiz Items per material:" }
        val itemsInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentInitiationItems.toString())
        }
        layout.addView(itemsLabel)
        layout.addView(itemsInput)

        val pdfsLabel = TextView(this).apply {
            text = "Custom Reading Materials (Optional):"
            setPadding(0, 40, 0, 10)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(pdfsLabel)

        // Generate 4 Upload Slots
        for (i in 1..4) {
            val slotData = customPdfs[i.toString()]
            val btnText = if (slotData != null) "Material $i: ${slotData["title"]}" else "+ Upload Material $i"

            val btn = Button(this).apply {
                text = btnText
                setOnClickListener {
                    currentPdfSlot = i
                    pdfPickerLauncher.launch("application/pdf")
                }
            }
            layout.addView(btn)
        }

        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle("Manage Initiation Quest")
            .setView(layout)
            .setPositiveButton("Save Item Count") { _, _ ->
                val newCount = itemsInput.text.toString().toIntOrNull() ?: 5
                db.collection("classes").document(classId).update("initiation_items", newCount)
                GabAIUtils.showSnackbar(this, "Initiation settings updated!")
            }
            .setNegativeButton("Cancel", null)

        activeDialog = dialogBuilder.show()
    }

    private fun promptForInitiationPdfTitle(fileUri: Uri) {
        var originalName = ""
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) originalName = cursor.getString(nameIndex) ?: ""
        }
        originalName = originalName.removeSuffix(".pdf").replace("_", " ")

        val input = EditText(this).apply {
            setText(originalName)
            hint = "Enter Material Title"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Upload Custom Material $currentPdfSlot")
            .setView(input)
            .setPositiveButton("Upload") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) uploadInitiationPdfToDrive(fileUri, title)
            }
            .setNegativeButton("Cancel") { _, _ -> openInitiationSettings() } // Go back if canceled
            .show()
    }

    private fun uploadInitiationPdfToDrive(fileUri: Uri, title: String) {
        GabAIUtils.showGlobalLoading(this, "Uploading Material $currentPdfSlot to Google Drive...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
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
}