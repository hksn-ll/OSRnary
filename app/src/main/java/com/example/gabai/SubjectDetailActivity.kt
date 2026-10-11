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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
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
    private var allMaterials = listOf<DocumentSnapshot>()
    private var currentSearchQuery = ""

    private val driveApiUrl = GabAIApp.DRIVE_API_URL

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) promptForPdfTitle(uri)
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        PDFBoxResourceLoader.init(applicationContext)
        setContentView(R.layout.activity_subject_detail)

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: return finish()
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""

        findViewById<TextView>(R.id.tv_subject_title).text = subjectName

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_subject_detail)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_subject_detail)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.subject_header)
        val scrollContent = findViewById<View>(R.id.scroll_subject_detail)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        uploadProgress = findViewById(R.id.upload_progress)
        tvUploadStatus = findViewById(R.id.tv_upload_status)
        uploadStatusContainer = findViewById(R.id.upload_status_container)
        btnUploadPdf = findViewById(R.id.btn_upload_pdf)

        GabAIUtils.addSpringPressEffect(btnUploadPdf) {
            pdfPickerLauncher.launch("application/pdf")
        }

        setupSearch()
        loadPdfs()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                teacherFullName = "${doc.getString("firstName")} ${doc.getString("lastName")}"
            }
        }
    }

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.et_search_materials) ?: return
        val btnClear = findViewById<ImageButton>(R.id.btn_clear_search_materials) ?: return

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClear.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                renderPdfList()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        GabAIUtils.addSpringPressEffect(btnClear) {
            etSearch.setText("")
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

        GabAIDialogs.showInputDialog(
            context = this,
            title = "Upload PDF",
            subtitle = "Enter a title for this study material:",
            initialText = originalName,
            hint = "Enter Document Title",
            confirmText = "Upload",
            cancelText = "Cancel",
            badgeIcon = "📄",
            onConfirm = { title ->
                if (title.isNotEmpty()) uploadPdfToDrive(fileUri, title)
            }
        )
    }

    private fun uploadPdfToDrive(fileUri: Uri, title: String) {
        btnUploadPdf.isEnabled = false
        btnUploadPdf.alpha = 0.5f
        btnUploadPdf.text = "Uploading..."
        uploadStatusContainer.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true
        tvUploadStatus.text = "Uploading to Google Drive..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pfd = contentResolver.openFileDescriptor(fileUri, "r")
                val fileSize = pfd?.statSize ?: 0L
                pfd?.close()
                if (fileSize > 15 * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        resetUploadUI()
                        GabAIUtils.showSnackbar(this@SubjectDetailActivity, "PDF exceeds the 15MB upload limit. Please compress or select a smaller PDF.")
                    }
                    return@launch
                }

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
        btnUploadPdf.alpha = 1.0f
        btnUploadPdf.text = "+ Upload Lesson PDF"
    }

    private fun loadPdfs() {
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                allMaterials = snapshots.documents
                renderPdfList()
            }
    }

    private fun renderPdfList() {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container) ?: return
        val emptyCard = findViewById<View>(R.id.card_empty_pdfs)
        val noResultsCard = findViewById<View>(R.id.card_no_search_materials)
        val searchBox = findViewById<View>(R.id.card_search_materials)

        container.removeAllViews()

        if (allMaterials.isEmpty()) {
            emptyCard?.visibility = View.VISIBLE
            noResultsCard?.visibility = View.GONE
            searchBox?.visibility = View.GONE
            return
        }

        emptyCard?.visibility = View.GONE
        searchBox?.visibility = View.VISIBLE

        val filtered = if (currentSearchQuery.isEmpty()) {
            allMaterials
        } else {
            allMaterials.filter { doc ->
                val title = doc.getString("title") ?: ""
                val uploader = doc.getString("uploaderName") ?: ""
                title.contains(currentSearchQuery, ignoreCase = true) || uploader.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            noResultsCard?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_no_search_materials_subtitle)?.text = "No materials matching \"$currentSearchQuery\""
            return
        }

        noResultsCard?.visibility = View.GONE
        val rowViews = mutableListOf<View>()

        for (doc in filtered) {
            val title = doc.getString("title") ?: "Document"
            val pdfUrl = doc.getString("pdfUrl") ?: ""
            val fileId = doc.getString("driveFileId") ?: ""
            val thumbStr = doc.getString("thumbnail") ?: ""
            val assignedSections = doc.get("assignedSections") as? List<String> ?: listOf()

            val row = layoutInflater.inflate(R.layout.item_pdf_document, container, false)
            row.findViewById<TextView>(R.id.tv_pdf_title).text = title
            row.findViewById<TextView>(R.id.tv_uploader_name)?.text = "Uploaded by: ${doc.getString("uploaderName") ?: "Unknown"}"
            val quizJson = doc.getString("quiz_pool_json") ?: "[]"
            val hasQuiz = quizJson.length > 5
            row.findViewById<TextView>(R.id.tv_quiz_badge)?.apply {
                visibility = if (hasQuiz) View.VISIBLE else View.GONE
                text = "QUIZ READY ⚡"
            }

            // THUMBNAIL
            val imgThumb = row.findViewById<ImageView>(R.id.img_pdf_thumb)
            if (thumbStr.isNotEmpty()) {
                try {
                    val decodedBytes = android.util.Base64.decode(thumbStr, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    imgThumb.imageTintList = null
                    imgThumb.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    imgThumb.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6366F1"))
                    imgThumb.setImageResource(R.drawable.ic_menu_book)
                }
            } else {
                imgThumb.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6366F1"))
                imgThumb.setImageResource(R.drawable.ic_menu_book)
            }

            // 🟢 THE NEW LAUNCH LOGIC 🟢
            GabAIUtils.addSpringPressEffect(row) {
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
            rowViews.add(row)
        }

        GabAIUtils.animateCascade(rowViews, 25L)
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

                            // 3. Match students: Advisers get their homeroom section automatically; Subject Teachers get QR-joined students
                            for (classDoc in classSnaps.documents) {
                                val className = classDoc.getString("className") ?: continue
                                val classGrade = classDoc.getString("grade")?.trim() ?: ""
                                val classSection = classDoc.getString("section")?.trim() ?: ""
                                val ownerTeacherId = classDoc.getString("teacherId") ?: ""
                                val isAdviser = (classDoc.getBoolean("isAdviser") == true && ownerTeacherId == uid)
                                val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

                                // If ADVISER: Automatically match all students in the school with matching section & grade,
                                // PLUS any students in joinedStudents!
                                val matchedDocs = if (isAdviser) {
                                    studentSnaps.documents.filter { doc ->
                                        val sSec = doc.getString("section")?.trim() ?: ""
                                        val sGrd = doc.getString("grade")?.trim() ?: ""
                                        (sSec.equals(classSection, ignoreCase = true) && (classGrade.isEmpty() || sGrd.equals(classGrade, ignoreCase = true))) ||
                                            joinedStudents.contains(doc.id)
                                    }
                                } else {
                                    // If SUBJECT TEACHER: Only students who joined via QR/Join Code
                                    studentSnaps.documents.filter { doc ->
                                        joinedStudents.contains(doc.id)
                                    }
                                }

                                if (matchedDocs.isEmpty()) continue

                                val sectionHeaderTitle = if (isAdviser) "$className (Advisory Section)" else "$className (Subject Section)"
                                if (!studentsByClass.containsKey(sectionHeaderTitle)) {
                                    studentsByClass[sectionHeaderTitle] = mutableListOf()
                                }

                                for (studentDoc in matchedDocs) {
                                    val studentId = studentDoc.id
                                    val fName = studentDoc.getString("firstName") ?: ""
                                    val lName = studentDoc.getString("lastName") ?: ""
                                    val username = studentDoc.getString("username") ?: ""
                                    val rawName = "$fName $lName".trim().ifEmpty { username.ifEmpty { "Student" } }
                                    val displayName = if (username.isNotBlank()) "$rawName (@$username)" else rawName
                                    val studentData = mapOf("id" to studentId, "name" to displayName)

                                    if (studentsByClass[sectionHeaderTitle]?.none { it["id"] == studentId } == true) {
                                        studentsByClass[sectionHeaderTitle]?.add(studentData)
                                    }
                                }
                            }

                            // If no students were found in any class
                            if (studentsByClass.isEmpty()) {
                                GabAIUtils.hideGlobalLoading(this)
                                GabAIUtils.showSnackbar(this, "No students found in your advisory or subject sections.")
                                return@addOnSuccessListener
                            }

                            // 4. Build the UI
                            val density = resources.displayMetrics.density
                            val fontJakarta = try { androidx.core.content.res.ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans) } catch (_: Exception) { null }

                            val selectedStudentIds = currentlyAssigned.toMutableList()
                            val mainContainer = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(0, 0, 0, 0)
                            }

                            // 1. Search Box for Students
                            val searchBox = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                                setBackgroundResource(R.drawable.bg_modern_input)
                                setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, (12 * density).toInt())
                                }
                            }

                            val ivSearch = ImageView(this).apply {
                                setImageResource(R.drawable.ic_search_modern)
                                layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt()).apply {
                                    marginEnd = (10 * density).toInt()
                                }
                                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B"))
                            }

                            val etSearchStudent = EditText(this).apply {
                                hint = "Search student name..."
                                setTextColor(Color.parseColor("#0F172A"))
                                setHintTextColor(Color.parseColor("#94A3B8"))
                                textSize = 13.5f
                                setBackgroundResource(android.R.color.transparent)
                                maxLines = 1
                                inputType = android.text.InputType.TYPE_CLASS_TEXT
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val btnClearSearch = ImageButton(this).apply {
                                setImageResource(R.drawable.ic_clear_circle)
                                setBackgroundResource(android.R.color.transparent)
                                imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                                visibility = View.GONE
                                layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
                            }
                            btnClearSearch.setOnClickListener { etSearchStudent.setText("") }

                            searchBox.addView(ivSearch)
                            searchBox.addView(etSearchStudent)
                            searchBox.addView(btnClearSearch)
                            mainContainer.addView(searchBox)

                            // 2. Select All / Deselect All Action Bar
                            val actionBar = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = android.view.Gravity.CENTER_VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, (14 * density).toInt())
                                }
                            }

                            val allStudentCheckboxes = mutableListOf<Pair<String, CheckBox>>() // (studentId, cb)
                            val allSectionCheckboxes = mutableListOf<Pair<CheckBox, List<CheckBox>>>() // (sectionCb, childCbs)

                            val btnSelectAll = TextView(this).apply {
                                text = "✓ Select All"
                                textSize = 12f
                                setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                                setTextColor(Color.parseColor("#6366F1"))
                                setBackgroundResource(R.drawable.bg_pill_translucent)
                                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
                                setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                            }

                            val btnDeselectAll = TextView(this).apply {
                                text = "✕ Clear All"
                                textSize = 12f
                                setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                                setTextColor(Color.parseColor("#64748B"))
                                setBackgroundResource(R.drawable.bg_pill_translucent)
                                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                                setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                    marginStart = (8 * density).toInt()
                                }
                                layoutParams = lp
                            }

                            actionBar.addView(btnSelectAll)
                            actionBar.addView(btnDeselectAll)
                            mainContainer.addView(actionBar)

                            GabAIUtils.addSpringPressEffect(btnSelectAll) {
                                allStudentCheckboxes.forEach { (stId, cb) ->
                                    cb.isChecked = true
                                    if (!selectedStudentIds.contains(stId)) selectedStudentIds.add(stId)
                                }
                                allSectionCheckboxes.forEach { (secCb, _) -> secCb.isChecked = true }
                            }

                            GabAIUtils.addSpringPressEffect(btnDeselectAll) {
                                allStudentCheckboxes.forEach { (_, cb) ->
                                    cb.isChecked = false
                                }
                                selectedStudentIds.clear()
                                allSectionCheckboxes.forEach { (secCb, _) -> secCb.isChecked = false }
                            }

                            val sectionContainers = mutableListOf<Triple<View, View, List<Pair<View, String>>>>() // (sectionHeader, listContainer, studentViews)

                            for ((className, students) in studentsByClass) {
                                val sectionLayout = LinearLayout(this).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = android.view.Gravity.CENTER_VERTICAL
                                    setPadding(0, 10, 0, 10)
                                }

                                val sectionCheckbox = CheckBox(this)
                                sectionCheckbox.isChecked = students.all { selectedStudentIds.contains(it["id"]) }

                                val sectionTitle = TextView(this).apply {
                                    text = className
                                    textSize = 15.5f
                                    setTypeface(fontJakarta ?: typeface, android.graphics.Typeface.BOLD)
                                    setTextColor(Color.parseColor("#0F172A"))
                                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                }

                                val expandIcon = TextView(this).apply {
                                    text = "▼"
                                    textSize = 14f
                                    setTextColor(Color.parseColor("#64748B"))
                                    setPadding(20, 20, 20, 20)
                                }

                                sectionLayout.addView(sectionCheckbox)
                                sectionLayout.addView(sectionTitle)
                                sectionLayout.addView(expandIcon)
                                mainContainer.addView(sectionLayout)

                                val studentListContainer = LinearLayout(this).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding((32 * density).toInt(), 0, 0, (12 * density).toInt())
                                    visibility = View.GONE
                                }

                                val studentCheckboxes = mutableListOf<CheckBox>()
                                val studentItems = mutableListOf<Pair<View, String>>()

                                for (student in students) {
                                    val stId = student["id"] ?: continue
                                    val stName = student["name"] ?: "Student"
                                    val stCb = CheckBox(this).apply {
                                        text = stName
                                        textSize = 14f
                                        setTextColor(Color.parseColor("#334155"))
                                        isChecked = selectedStudentIds.contains(stId)
                                    }
                                    studentCheckboxes.add(stCb)
                                    allStudentCheckboxes.add(stId to stCb)
                                    studentItems.add(stCb to stName)
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

                                allSectionCheckboxes.add(sectionCheckbox to studentCheckboxes)

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
                                sectionContainers.add(Triple(sectionLayout, studentListContainer, studentItems))

                                mainContainer.addView(View(this).apply {
                                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { setMargins(0, 8, 0, 8) }
                                    setBackgroundColor(Color.parseColor("#EDF2F7"))
                                })
                            }

                            // Wire search listener to live-filter students across sections
                            etSearchStudent.addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    val query = s?.toString()?.trim() ?: ""
                                    btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                                    for ((secHeader, listContainer, studentItems) in sectionContainers) {
                                        var hasVisibleStudents = false
                                        for ((stView, stName) in studentItems) {
                                            val matches = query.isEmpty() || stName.contains(query, ignoreCase = true)
                                            stView.visibility = if (matches) View.VISIBLE else View.GONE
                                            if (matches) hasVisibleStudents = true
                                        }

                                        if (query.isNotEmpty()) {
                                            secHeader.visibility = if (hasVisibleStudents) View.VISIBLE else View.GONE
                                            listContainer.visibility = if (hasVisibleStudents) View.VISIBLE else View.GONE
                                        } else {
                                            secHeader.visibility = View.VISIBLE
                                            listContainer.visibility = View.GONE
                                        }
                                    }
                                }
                                override fun afterTextChanged(s: android.text.Editable?) {}
                            })

                            GabAIUtils.hideGlobalLoading(this)

                            val scrollView = ScrollView(this).apply {
                                addView(mainContainer)
                                isFillViewport = true
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    (resources.displayMetrics.density * 340).toInt()
                                )
                            }

                            GabAIDialogs.showCustomDialog(
                                context = this,
                                title = "Manage Access",
                                subtitle = "File: $title",
                                customView = scrollView,
                                confirmText = "Save Access",
                                cancelText = "Cancel",
                                badgeIcon = "👥",
                                onConfirm = { dialog ->
                                    dialog.dismiss()
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
                            )
                        }
                }
        }
    }
}