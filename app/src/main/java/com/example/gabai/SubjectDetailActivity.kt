package com.example.gabai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import com.google.ai.client.generativeai.GenerativeModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class SubjectDetailActivity : AppCompatActivity() {

    private lateinit var subjectId: String
    private lateinit var subjectName: String
    private lateinit var uploadProgress: ProgressBar
    private lateinit var tvUploadStatus: TextView
    private lateinit var uploadStatusContainer: LinearLayout
    private lateinit var btnUploadPdf: Button
    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private var teacherFullName: String = "Teacher"

    // --- PASTE YOUR GOOGLE APPS SCRIPT WEB APP URL HERE ---
    private val driveApiUrl = "https://script.google.com/macros/s/AKfycbxmlWtZXkpYqbgQU8wZ6Qdga9ImIHhlP5kMUSdujH8y2Db9SdP_DLswqoTO1-FDcf9CaQ/exec"

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) promptForPdfTitle(uri)
    }

    private val assessmentPdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) handlePdfAssessmentSource(uri)
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContentView(R.layout.activity_subject_detail)

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: return finish()
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""

        findViewById<TextView>(R.id.tv_subject_title).text = subjectName
        val header = findViewById<View>(R.id.subject_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        uploadProgress = findViewById(R.id.upload_progress)
        tvUploadStatus = findViewById(R.id.tv_upload_status)
        uploadStatusContainer = findViewById(R.id.upload_status_container)
        btnUploadPdf = findViewById(R.id.btn_upload_pdf)

        btnUploadPdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }

        findViewById<View>(R.id.btn_create_assessment)?.setOnClickListener {
            showAssessmentSourcingDialog()
        }

        loadPdfs()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                teacherFullName = "${doc.getString("firstName")} ${doc.getString("lastName")}"
            }
        }
    }

    // 1. AUTO-FILL PDF TITLE LOGIC
    private fun promptForPdfTitle(fileUri: Uri) {
        var originalName = ""
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) originalName = cursor.getString(nameIndex) ?: ""
        }
        originalName = originalName.removeSuffix(".pdf").replace("_", " ")

        val input = EditText(this).apply {
            setText(originalName)
            hint = "Enter Document Title"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Upload PDF")
            .setView(input)
            .setPositiveButton("Upload") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) uploadPdfToDrive(fileUri, title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadPdfToDrive(fileUri: Uri, title: String) {
        btnUploadPdf.isEnabled = false
        btnUploadPdf.setBackgroundColor(Color.LTGRAY)
        btnUploadPdf.text = "Uploading..."
        uploadStatusContainer.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true
        tvUploadStatus.text = "Uploading to Google Drive..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val thumbnailBase64 = generateThumbnail(fileUri)
                val inputStream = contentResolver.openInputStream(fileUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Could not read file.")
                val base64File = Base64.encodeToString(bytes, Base64.DEFAULT)
                inputStream.close()

                val cleanTitle = title.replace(Regex("[^A-Za-z0-9]"), "")
                val systematicFilename = "GabAI_${subjectName}_${cleanTitle}_${System.currentTimeMillis()}.pdf"

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
                    val json = org.json.JSONObject(responseData)
                    if (json.getString("status") == "success") {
                        val downloadUrl = json.getString("url")
                        val fileId = json.getString("fileId")

                        withContext(Dispatchers.Main) {
                            tvUploadStatus.text = "Saving to Database..."
                            // Save with EMPTY quiz arrays initially
                            saveToFirestore(title, downloadUrl, thumbnailBase64, fileId, "[]", 0)
                        }
                    } else throw Exception(json.getString("message"))
                } else throw Exception("Google Error Code: ${response.code}")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resetUploadUI()
                    com.example.gabai.GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Error: ${e.message}")
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
        } catch (e: Exception) { "" }
    }

    private suspend fun generateQuizPool(pdfText: String, totalQuestions: Int): String {
        if (pdfText.isEmpty()) return "[]"
        val prompt = """
        You are an AI teacher. Read this text:
        ${pdfText.take(10000)}
        
        Create exactly $totalQuestions multiple choice questions based on the text.
        Return ONLY a valid JSON array. Format:
        [ { "q": "Question?", "options": ["A", "B", "C", "D"], "ans": 0 } ]
    """.trimIndent()

        val response = generativeModel.generateContent(prompt)
        var jsonStr = response.text ?: "[]"
        val startIndex = jsonStr.indexOf("[")
        val endIndex = jsonStr.lastIndexOf("]")
        if (startIndex != -1 && endIndex != -1) {
            jsonStr = jsonStr.substring(startIndex, endIndex + 1)
        }
        return jsonStr
    }

    private fun saveToFirestore(title: String, pdfUrl: String, thumbBase64: String, fileId: String, quizJson: String, maxItems: Int) {
        val materialData = hashMapOf(
            "title" to title,
            "subjectId" to subjectId,
            "pdfUrl" to pdfUrl,
            "driveFileId" to fileId,
            "thumbnail" to thumbBase64,
            "teacherId" to uid,
            "uploaderName" to teacherFullName,
            "assignedSections" to listOf<String>(),
            "quiz_pool_json" to quizJson, // SAVED!
            "quiz_max_items" to maxItems, // SAVED!
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("library_materials").add(materialData).addOnSuccessListener {
            uploadProgress.progress = 100
            tvUploadStatus.text = "100% - Complete!"
            com.example.gabai.GabAIUtils.showSnackbar(this, "Uploaded successfully!")

            // Wait 1 second so they can read "100% - Complete!" before hiding it
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                resetUploadUI()
            }, 1000)
        }.addOnFailureListener { e ->
            resetUploadUI()
            com.example.gabai.GabAIUtils.showSnackbar(this, "Database Error: ${e.message}")
        }
    }

    // HELPER FUNCTION: Un-grays the button and hides the progress bar
    private fun resetUploadUI() {
        uploadStatusContainer.visibility = View.GONE
        btnUploadPdf.isEnabled = true
        btnUploadPdf.setBackgroundColor(Color.parseColor("#6C5CE7"))
        btnUploadPdf.text = "+ Upload PDF Here"
    }

    private fun loadPdfs() {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container)
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    container.addView(TextView(this).apply { text = "No PDFs uploaded yet." })
                    return@addSnapshotListener
                }

                for (doc in snapshots) {
                    val title = doc.getString("title") ?: "Document"
                    val pdfUrl = doc.getString("pdfUrl") ?: ""
                    val fileId = doc.getString("driveFileId") ?: ""
                    val thumbStr = doc.getString("thumbnail") ?: ""
                    val assignedSections = doc.get("assignedSections") as? List<String> ?: listOf()

                    val row = layoutInflater.inflate(R.layout.item_pdf_document, container, false)
                    row.findViewById<TextView>(R.id.tv_pdf_title).text = title
                    row.findViewById<TextView>(R.id.tv_uploader_name)?.text = "Uploaded by: ${doc.getString("uploaderName") ?: "Unknown"}"

                    // THUMBNAIL
                    val imgThumb = row.findViewById<ImageView>(R.id.img_pdf_thumb)
                    if (thumbStr.isNotEmpty()) {
                        try {
                            val decodedBytes = android.util.Base64.decode(thumbStr, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            imgThumb.setImageBitmap(bitmap)
                        } catch (e: Exception) { imgThumb.setImageResource(android.R.drawable.ic_menu_report_image) }
                    } else imgThumb.setImageResource(android.R.drawable.ic_menu_report_image)

                    // 🟢 THE NEW LAUNCH LOGIC 🟢
                    row.setOnClickListener {
                        val intent = Intent(this@SubjectDetailActivity, PdfViewerActivity::class.java).apply {
                            putExtra("PDF_URL", pdfUrl)
                            putExtra("PDF_TITLE", title)
                            putExtra("MATERIAL_ID", doc.id)
                            putExtra("DRIVE_FILE_ID", fileId)
                            putExtra("IS_TEACHER", true)
                            putStringArrayListExtra("ASSIGNED_SECTIONS", ArrayList(assignedSections))
                        }
                        startActivity(intent)
                    }

                    container.addView(row)
                }
            }
    }

    private fun deleteFileFromDriveAndDatabase(docId: String, fileId: String) {
        uploadProgress.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (fileId.isNotEmpty()) {
                    val client = OkHttpClient()
                    val formBody = FormBody.Builder()
                        .add("action", "delete") // Tell script to trash the file
                        .add("fileId", fileId)
                        .build()
                    val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                    client.newCall(request).execute()
                }

                // Delete from database after Drive deletion succeeds (or if there was no Drive ID)
                withContext(Dispatchers.Main) {
                    db.collection("library_materials").document(docId).delete()
                    uploadProgress.visibility = View.GONE
                    com.example.gabai.GabAIUtils.showSnackbar(this@SubjectDetailActivity, "PDF Deleted")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadProgress.visibility = View.GONE
                    com.example.gabai.GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Failed to delete from Drive: ${e.message}")
                }
            }
        }
    }

    private fun generateThumbnail(uri: Uri): String {
        try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return ""
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)

            val width = 300
            val height = (300f * page.height / page.width).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val base64Thumb = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

            page.close()
            renderer.close()
            fd.close()

            return base64Thumb
        } catch (e: Exception) {
            return ""
        }

    }
    private fun renamePdfInDriveAndDatabase(docId: String, fileId: String, newTitle: String) {
        uploadProgress.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Rename physically in Google Drive
                if (fileId.isNotEmpty()) {
                    val client = OkHttpClient()
                    val formBody = FormBody.Builder()
                        .add("action", "rename") // Tell script to rename the file
                        .add("fileId", fileId)
                        .add("newName", newTitle) // Send the new name
                        .build()
                    val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                    client.newCall(request).execute()
                }

                // 2. Rename in the Database so students see it instantly
                withContext(Dispatchers.Main) {
                    db.collection("library_materials").document(docId).update("title", newTitle)
                    uploadProgress.visibility = View.GONE
                    com.example.gabai.GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Renamed in Drive & App!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadProgress.visibility = View.GONE
                    com.example.gabai.GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Failed to rename: ${e.message}")
                }
            }
        }
    }
    private fun managePdfAccess(materialId: String, title: String, currentlyAssigned: List<String>) {
        if (uid == null) return
        // TURN ON SPINNER
        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).get().addOnSuccessListener { teacherDoc ->
            val teacherSchoolId = teacherDoc.getString("schoolId") ?: ""

            // 1. FIRST get the sections this teacher actually teaches/owns
            db.collection("classes").whereArrayContains("teacherIds", uid).get()
                .addOnSuccessListener { classSnaps ->
                    if (classSnaps.isEmpty) {
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "You need to create or join a class section first.")
                        return@addOnSuccessListener
                    }

                    // 2. NOW fetch the students in the school
                    db.collection("users")
                        .whereEqualTo("role", "student")
                        .whereEqualTo("schoolId", teacherSchoolId)
                        .get()
                        .addOnSuccessListener { studentSnaps ->
                            val studentsByClass = mutableMapOf<String, MutableList<Map<String, String>>>()

                            // 3. FLIPPED LOGIC: Match students ONLY if they used the Join Code
                            for (classDoc in classSnaps.documents) {
                                val className = classDoc.getString("className") ?: continue
                                val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

                                // If nobody joined this specific class yet, skip it
                                if (joinedStudents.isEmpty()) continue

                                for (studentId in joinedStudents) {
                                    val studentDoc = studentSnaps.documents.find { it.id == studentId }
                                    if (studentDoc != null) {
                                        val fName = studentDoc.getString("firstName") ?: ""
                                        val lName = studentDoc.getString("lastName") ?: ""
                                        val studentData = mapOf("id" to studentId, "name" to "$fName $lName".trim())

                                        if (!studentsByClass.containsKey(className)) {
                                            studentsByClass[className] = mutableListOf()
                                        }

                                        // Prevent accidental duplicates
                                        if (studentsByClass[className]?.none { it["id"] == studentId } == true) {
                                            studentsByClass[className]?.add(studentData)
                                        }
                                    }
                                }
                            }

                            // If no students were found in any joinedStudents array
                            if (studentsByClass.isEmpty()) {
                                GabAIUtils.hideGlobalLoading(this)
                                GabAIUtils.showSnackbar(this, "No students have joined your classes yet.")
                                return@addOnSuccessListener
                            }

                            // 4. Build the UI
                            val selectedStudentIds = currentlyAssigned.toMutableList()
                            val mainContainer = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(40, 20, 40, 20)
                            }

                            for ((className, students) in studentsByClass) {
                                val sectionLayout = LinearLayout(this).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = android.view.Gravity.CENTER_VERTICAL
                                    setPadding(0, 10, 0, 10)
                                }

                                val sectionCheckbox = CheckBox(this)
                                sectionCheckbox.isChecked = students.all { selectedStudentIds.contains(it["id"]) }

                                val sectionTitle = TextView(this).apply {
                                    text = className // Shows "Grade 10 - Rizal", etc.
                                    textSize = 18f
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    setTextColor(Color.parseColor("#2D3436"))
                                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                }

                                val expandIcon = TextView(this).apply {
                                    text = "▼"
                                    textSize = 16f
                                    setPadding(20, 20, 20, 20)
                                }

                                sectionLayout.addView(sectionCheckbox)
                                sectionLayout.addView(sectionTitle)
                                sectionLayout.addView(expandIcon)
                                mainContainer.addView(sectionLayout)

                                val studentListContainer = LinearLayout(this).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(60, 0, 0, 20)
                                    visibility = View.GONE
                                }

                                val studentCheckboxes = mutableListOf<CheckBox>()
                                for (student in students) {
                                    // Skip malformed records instead of crashing on a missing id.
                                    val stId = student["id"] ?: continue
                                    val stCb = CheckBox(this).apply {
                                        text = student["name"]
                                        textSize = 16f
                                        isChecked = selectedStudentIds.contains(stId)
                                    }
                                    studentCheckboxes.add(stCb)
                                    studentListContainer.addView(stCb)

                                    stCb.setOnCheckedChangeListener { _, isChecked ->
                                        if (isChecked) {
                                            if (!selectedStudentIds.contains(stId)) selectedStudentIds.add(stId)
                                        } else {
                                            selectedStudentIds.remove(stId)
                                        }
                                        sectionCheckbox.setOnCheckedChangeListener(null)
                                        sectionCheckbox.isChecked = studentCheckboxes.all { it.isChecked }
                                        sectionCheckbox.setOnCheckedChangeListener { _, parentChecked ->
                                            studentCheckboxes.forEach { it.isChecked = parentChecked }
                                        }
                                    }
                                }

                                sectionCheckbox.setOnCheckedChangeListener { _, isChecked ->
                                    studentCheckboxes.forEach { it.isChecked = isChecked }
                                }

                                val toggleExpand = View.OnClickListener {
                                    if (studentListContainer.visibility == View.VISIBLE) {
                                        studentListContainer.visibility = View.GONE
                                        expandIcon.text = "▼"
                                    } else {
                                        studentListContainer.visibility = View.VISIBLE
                                        expandIcon.text = "▲"
                                    }
                                }

                                sectionTitle.setOnClickListener(toggleExpand)
                                expandIcon.setOnClickListener(toggleExpand)

                                mainContainer.addView(studentListContainer)
                                mainContainer.addView(View(this).apply {
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 10, 0, 10) }
                                    setBackgroundColor(Color.parseColor("#DFE6E9"))
                                })
                            }

                            GabAIUtils.hideGlobalLoading(this)

                            val scrollView = ScrollView(this).apply { addView(mainContainer) }

                            MaterialAlertDialogBuilder(this)
                                .setTitle("Manage Access: '$title'")
                                .setView(scrollView)
                                .setPositiveButton("Save Access") { _, _ ->
                                    uploadProgress.visibility = View.VISIBLE
                                    db.collection("library_materials").document(materialId)
                                        .update("assignedSections", selectedStudentIds)
                                        .addOnSuccessListener {
                                            uploadProgress.visibility = View.GONE
                                            GabAIUtils.showSnackbar(this, "Access updated successfully!")
                                        }
                                        .addOnFailureListener {
                                            uploadProgress.visibility = View.GONE
                                            GabAIUtils.showSnackbar(this, "Failed to update access.")
                                        }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                }
        }
    }

    // =========================================================================
    // 🟢 WEEKLY ASSESSMENT: 3 MATERIAL SOURCING FLOWS
    // =========================================================================

    private fun showAssessmentSourcingDialog() {
        val options = arrayOf(
            "📄 Upload New PDF (Extract Text)",
            "📚 Select from Existing Library Materials",
            "💡 Enter Topic Keyword & Grade"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("✨ Create Weekly Assessment")
            .setMessage("Select how you want Gemini to generate the 10-question multiple-choice assessment:")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> assessmentPdfPickerLauncher.launch("application/pdf")
                    1 -> handleLibraryMaterialSource()
                    2 -> handleTopicAssessmentSource()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 1. Source A: PDF Upload & Text Extraction
    private fun handlePdfAssessmentSource(uri: Uri) {
        GabAIUtils.showGlobalLoading(this, "Reading PDF & Generating 10 Questions...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = extractTextFromUri(uri)
                var originalName = "PDF Material"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        originalName = cursor.getString(nameIndex) ?: "PDF Material"
                    }
                }
                if (originalName.endsWith(".pdf", ignoreCase = true)) {
                    originalName = originalName.substring(0, originalName.length - 4)
                }

                val prompt = """
                    You are an expert curriculum developer. Based on this lesson text:
                    ${text.take(12000)}

                    Create exactly 10 high-quality multiple choice assessment questions for students on this subject ($subjectName).
                    Each question must have:
                    - "q": clear, concise question stem
                    - "options": an array of exactly 4 plausible choices
                    - "ans": the zero-indexed index of the correct answer (0, 1, 2, or 3)
                    - "explanation": a concise, educational explanation clarifying why the correct answer is right.

                    Return ONLY a valid JSON array. Format:
                    [
                      {
                        "q": "Question?",
                        "options": ["Option A", "Option B", "Option C", "Option D"],
                        "ans": 0,
                        "explanation": "Educational explanation."
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

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    val intent = Intent(this@SubjectDetailActivity, QuizEditorActivity::class.java).apply {
                        putExtra("IS_WEEKLY_ASSESSMENT", true)
                        putExtra("SUBJECT_ID", subjectId)
                        putExtra("SUBJECT_NAME", subjectName)
                        putExtra("SOURCE_TYPE", "pdf_upload")
                        putExtra("SOURCE_REF", originalName)
                        putExtra("ASSESSMENT_TITLE", "$originalName Assessment")
                        putExtra("QUIZ_JSON", jsonStr)
                        putExtra("TARGET_ITEMS", 10)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Generation error: ${e.message}")
                }
            }
        }
    }

    // 2. Source B: Select from library_materials
    private fun handleLibraryMaterialSource() {
        GabAIUtils.showGlobalLoading(this, "Loading materials...")
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .get()
            .addOnSuccessListener { snapshots ->
                GabAIUtils.hideGlobalLoading(this)
                if (snapshots.isEmpty) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("No Library Materials Found")
                        .setMessage("No lesson materials have been uploaded to $subjectName yet. Would you like to create an assessment by Topic keyword instead?")
                        .setPositiveButton("Enter Topic") { _, _ -> handleTopicAssessmentSource() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return@addOnSuccessListener
                }

                val titles = snapshots.documents.map { it.getString("title") ?: "Untitled Document" }.toTypedArray()
                val existingPools = snapshots.documents.map { it.getString("quiz_pool_json") ?: "" }.toTypedArray()

                MaterialAlertDialogBuilder(this)
                    .setTitle("Select Lesson Document")
                    .setItems(titles) { _, which ->
                        val selectedTitle = titles[which]
                        val existingPool = existingPools[which]
                        generateAssessmentFromMaterial(selectedTitle, existingPool)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Error: ${e.message}")
            }
    }

    private fun generateAssessmentFromMaterial(materialTitle: String, existingPoolJson: String) {
        GabAIUtils.showGlobalLoading(this, "AI is creating 10 questions for '$materialTitle'...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    You are an expert DepEd teacher.
                    Lesson Title: $materialTitle
                    Subject: $subjectName
                    ${if (existingPoolJson.length > 20) "Reference Questions/Concepts: $existingPoolJson" else ""}

                    Create exactly 10 comprehensive, curriculum-aligned multiple choice assessment questions for students on "$materialTitle".
                    Each question must have:
                    - "q": clearly stated question
                    - "options": 4 plausible options
                    - "ans": 0-indexed correct answer (0, 1, 2, or 3)
                    - "explanation": educational explanation clarifying the correct answer.

                    Return ONLY a valid JSON array. Format:
                    [
                      {
                        "q": "Question?",
                        "options": ["A", "B", "C", "D"],
                        "ans": 0,
                        "explanation": "Detailed explanation."
                      }
                    ]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) jsonStr = jsonStr.substring(startIndex, endIndex + 1)

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    val intent = Intent(this@SubjectDetailActivity, QuizEditorActivity::class.java).apply {
                        putExtra("IS_WEEKLY_ASSESSMENT", true)
                        putExtra("SUBJECT_ID", subjectId)
                        putExtra("SUBJECT_NAME", subjectName)
                        putExtra("SOURCE_TYPE", "library_material")
                        putExtra("SOURCE_REF", materialTitle)
                        putExtra("ASSESSMENT_TITLE", "$materialTitle Assessment")
                        putExtra("QUIZ_JSON", jsonStr)
                        putExtra("TARGET_ITEMS", 10)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Generation error: ${e.message}")
                }
            }
        }
    }

    // 3. Source C: Topic Keyword & Grade
    private fun handleTopicAssessmentSource() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 20)
        }

        val etTopic = EditText(this).apply {
            hint = "Topic Keyword (e.g. Photosynthesis, Cellular Division)"
            textSize = 14f
        }

        val tvGradeLabel = TextView(this).apply {
            text = "Grade Level:"
            setPadding(0, 24, 0, 8)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val gradeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SubjectDetailActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12")
            )
        }

        val etExtra = EditText(this).apply {
            hint = "Learning Focus / Context (Optional)"
            textSize = 13f
            setPadding(0, 20, 0, 10)
        }

        layout.addView(etTopic)
        layout.addView(tvGradeLabel)
        layout.addView(gradeSpinner)
        layout.addView(etExtra)

        MaterialAlertDialogBuilder(this)
            .setTitle("Generate from Topic")
            .setMessage("Enter the topic keyword and grade level. Gemini will generate 10 curriculum-aligned assessment questions with explanations.")
            .setView(layout)
            .setPositiveButton("Generate ✨") { _, _ ->
                val topic = etTopic.text.toString().trim()
                if (topic.isEmpty()) {
                    GabAIUtils.showSnackbar(this, "Please enter a topic keyword.")
                    return@setPositiveButton
                }
                val grade = gradeSpinner.selectedItem.toString()
                val extraFocus = etExtra.text.toString().trim()
                generateAssessmentFromTopic(topic, grade, extraFocus)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateAssessmentFromTopic(topic: String, grade: String, extraFocus: String) {
        GabAIUtils.showGlobalLoading(this, "Gemini is generating 10 questions for $topic ($grade)...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    You are an expert DepEd curriculum designer.
                    Topic: $topic
                    Grade Level: $grade
                    Subject: $subjectName
                    ${if (extraFocus.isNotBlank()) "Learning Focus: $extraFocus" else ""}

                    Create exactly 10 rigorous, grade-appropriate multiple choice assessment questions for $grade students on "$topic".
                    Each question must have:
                    - "q": clear question stem
                    - "options": exactly 4 distinct plausible options
                    - "ans": zero-indexed correct answer (0, 1, 2, or 3)
                    - "explanation": clear educational explanation explaining why this answer is correct.

                    Return ONLY a valid JSON array. Format:
                    [
                      {
                        "q": "Question?",
                        "options": ["A", "B", "C", "D"],
                        "ans": 0,
                        "explanation": "Educational explanation."
                      }
                    ]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) jsonStr = jsonStr.substring(startIndex, endIndex + 1)

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    val intent = Intent(this@SubjectDetailActivity, QuizEditorActivity::class.java).apply {
                        putExtra("IS_WEEKLY_ASSESSMENT", true)
                        putExtra("SUBJECT_ID", subjectId)
                        putExtra("SUBJECT_NAME", subjectName)
                        putExtra("GRADE", grade)
                        putExtra("SOURCE_TYPE", "topic_keyword")
                        putExtra("SOURCE_REF", topic)
                        putExtra("ASSESSMENT_TITLE", "$topic Assessment")
                        putExtra("QUIZ_JSON", jsonStr)
                        putExtra("TARGET_ITEMS", 10)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@SubjectDetailActivity)
                    GabAIUtils.showSnackbar(this@SubjectDetailActivity, "Generation error: ${e.message}")
                }
            }
        }
    }
}