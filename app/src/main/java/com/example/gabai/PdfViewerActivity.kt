package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

class PdfViewerActivity : AppCompatActivity() {

    private var downloadedPdfFile: File? = null
    private val db = FirebaseFirestore.getInstance()
    private val driveApiUrl = GabAIApp.DRIVE_API_URL
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private var totalPdfPages = 1
    private var currentPdfPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        QuestManager.addProgress(this, QuestManager.QUEST_READ)
        PDFBoxResourceLoader.init(applicationContext) // Required for local AI Text Extraction
        setContentView(R.layout.activity_pdf_viewer)

        val pdfUrl = intent.getStringExtra("PDF_URL") ?: return finish()
        var title = intent.getStringExtra("PDF_TITLE") ?: "Document"
        val isTeacher = intent.getBooleanExtra("IS_TEACHER", false)
        val materialId = intent.getStringExtra("MATERIAL_ID")

        val titleView = findViewById<TextView>(R.id.tv_pdf_title)
        titleView.text = title

        val btnClose = findViewById<ImageButton>(R.id.btn_close_pdf)
        GabAIUtils.addSpringPressEffect(btnClose) { finish() }

        val headerInner = findViewById<View>(R.id.pdf_header)
        val initialPaddingTop = headerInner.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerInner) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + initialPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        val bottomDock = findViewById<View>(R.id.bottom_reading_dock)
        val initialDockPaddingBottom = bottomDock.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomDock) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + initialDockPaddingBottom)
            insets
        }

        // ==========================================
        // 🟢 BOTTOM QUIZ ACTION BUTTON 🟢
        // ==========================================
        val btnBottomQuiz = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_bottom_quiz_action)
        val hasQuiz = intent.getBooleanExtra("HAS_QUIZ", false)
        if (isTeacher) {
            btnBottomQuiz.visibility = View.VISIBLE
            btnBottomQuiz.text = "✏️ Manage Quiz"
            GabAIUtils.addSpringPressEffect(btnBottomQuiz) { checkExistingQuizAndProceed() }
        } else if (hasQuiz && materialId != null) {
            btnBottomQuiz.visibility = View.VISIBLE
            btnBottomQuiz.text = "📝 Take Quiz"
            GabAIUtils.addSpringPressEffect(btnBottomQuiz) {
                val quizIntent = Intent(this, MaterialQuizActivity::class.java).apply {
                    putExtra("MATERIAL_ID", materialId)
                    putExtra("MATERIAL_TITLE", titleView.text.toString())
                }
                startActivity(quizIntent)
            }
        }

        // ==========================================
        // 🟢 THE NEW MEATBALLS MENU 🟢
        // ==========================================
        val btnKebab = findViewById<ImageButton>(R.id.btn_kebab_menu)
        GabAIUtils.addSpringPressEffect(btnKebab) {
            val popup = PopupMenu(this, btnKebab)

            if (isTeacher) {
                popup.menu.add(0, 1, 0, "Assign to Sections")
                popup.menu.add(0, 2, 0, "Add/Edit AI Quiz")
                popup.menu.add(0, 3, 0, "Rename Document")
                popup.menu.add(0, 4, 0, "Delete Document")
            } else {
                if (hasQuiz) {
                    popup.menu.add(0, 5, 0, "Take AI Quiz")
                } else {
                    popup.menu.add(0, 6, 0, "No Quiz Available").isEnabled = false
                }
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> managePdfAccess()
                    2 -> checkExistingQuizAndProceed()
                    3 -> promptRename(titleView)
                    4 -> confirmDelete()
                    5 -> {
                        val quizIntent = Intent(this, MaterialQuizActivity::class.java)
                        quizIntent.putExtra("MATERIAL_ID", materialId)
                        quizIntent.putExtra("MATERIAL_TITLE", titleView.text.toString())
                        startActivity(quizIntent)
                    }
                }
                true
            }
            popup.show()
        }

        val progressBar = findViewById<View>(R.id.pdf_loading_bar)
        val pdfView = findViewById<PDFView>(R.id.online_pdf_viewer)
        val pageIndicator = findViewById<TextView>(R.id.tv_page_indicator)

        val pdfPrefKey = "pdf_last_page_" + (materialId ?: pdfUrl.hashCode().toString())
        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val savedPage = prefs.getInt(pdfPrefKey, 0)
        currentPdfPage = savedPage

        pageIndicator.setOnClickListener {
            GabAIDialogs.showInputDialog(
                this,
                title = "Jump to Page",
                subtitle = "Enter a page number between 1 and $totalPdfPages:",
                hint = "1 - $totalPdfPages",
                confirmText = "Jump",
                isNumeric = true,
                badgeIcon = "📖"
            ) { text ->
                val pageNum = text.toIntOrNull()
                if (pageNum != null && pageNum in 1..totalPdfPages) {
                    pdfView.jumpTo(pageNum - 1)
                    GabAIUtils.performHaptic(pageIndicator, android.view.HapticFeedbackConstants.CLOCK_TICK)
                } else {
                    GabAIUtils.showSnackbar(this, "Please enter a valid page number")
                }
            }
        }

        loadPdfDocument(pdfUrl, savedPage, pdfPrefKey, prefs, progressBar, pdfView, pageIndicator)
    }

    private fun loadPdfDocument(
        pdfUrl: String,
        savedPage: Int,
        pdfPrefKey: String,
        prefs: android.content.SharedPreferences,
        progressBar: View,
        pdfView: PDFView,
        pageIndicator: TextView
    ) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val safeHash = pdfUrl.hashCode().toString().replace("-", "n")
                val cachedFile = File(cacheDir, "cached_pdf_${safeHash}.pdf")

                val finalFile = if (cachedFile.exists() && cachedFile.length() > 0) {
                    cachedFile
                } else {
                    val input = URL(pdfUrl).openStream()
                    val tempFile = File.createTempFile("temp_pdf", ".pdf", cacheDir)
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                    if (tempFile.renameTo(cachedFile)) {
                        cachedFile
                    } else {
                        tempFile
                    }
                }

                downloadedPdfFile = finalFile

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    pdfView.visibility = View.VISIBLE
                    pdfView.fromFile(finalFile)
                        .defaultPage(savedPage)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .onPageChange { page, pageCount ->
                            currentPdfPage = page
                            totalPdfPages = pageCount
                            prefs.edit().putInt(pdfPrefKey, page).apply()
                            pageIndicator.text = "${page + 1} / $pageCount"
                            pageIndicator.visibility = View.VISIBLE
                        }
                        .onLoad { nbPages ->
                            totalPdfPages = nbPages
                            pageIndicator.text = "${currentPdfPage + 1} / $nbPages"
                            pageIndicator.visibility = View.VISIBLE
                        }
                        .load()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    GabAIDialogs.showConfirmDialog(
                        this@PdfViewerActivity,
                        title = "Failed to Load Document",
                        message = "Could not download or open the PDF material. Please check your internet connection and try again.",
                        confirmText = "Retry",
                        cancelText = "Close",
                        badgeIcon = "⚠️"
                    ) {
                        loadPdfDocument(pdfUrl, savedPage, pdfPrefKey, prefs, progressBar, pdfView, pageIndicator)
                    }
                }
            }
        }
    }

    // ==============================================================
    // TEACHER ACTION: ADD AI QUIZ
    // ==============================================================
    private fun checkExistingQuizAndProceed() {
        val materialId = intent.getStringExtra("MATERIAL_ID") ?: return
        GabAIUtils.showGlobalLoading(this, "Checking quiz status...")

        db.collection("library_materials").document(materialId).get()
            .addOnSuccessListener { doc ->
                GabAIUtils.hideGlobalLoading(this)
                val quizJson = doc.getString("quiz_pool_json") ?: "[]"
                val maxItems = doc.getLong("quiz_max_items")?.toInt() ?: 5

                if (quizJson.length > 5 && quizJson != "[]") {
                    // 🟢 Quiz exists! Open editor directly to edit it.
                    openQuizEditor(quizJson, maxItems)
                } else {
                    // 🟢 No quiz exists. Prompt for initial creation.
                    promptForInitialQuizGeneration()
                }
            }
            .addOnFailureListener {
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to check database.")
            }
    }

    private fun promptForInitialQuizGeneration() {
        GabAIDialogs.showInputDialog(
            context = this,
            title = "Create New AI Quiz",
            message = "No quiz exists for this material yet. How many questions should GabAI generate to start? (Max 15 at a time)",
            hint = "Number of Quiz Items (Max 15)",
            initialText = "5",
            confirmText = "Generate",
            cancelText = "Cancel",
            isNumeric = true,
            badgeIcon = "✨",
            onConfirm = { text ->
                var maxItems = text.toIntOrNull() ?: 5
                if (maxItems > 15) {
                    maxItems = 15
                    GabAIUtils.showSnackbar(this, "AI generation limited to 15 questions at a time.")
                }
                generateInitialQuizLocally(maxItems)
            }
        )
    }

    private fun generateInitialQuizLocally(maxItems: Int) {
        if (downloadedPdfFile == null) {
            GabAIUtils.showSnackbar(this, "Please wait for PDF to finish loading first.")
            return
        }

        findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_action_status).text = "AI is reading the material..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val document = PDDocument.load(downloadedPdfFile)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()

                withContext(Dispatchers.Main) { findViewById<TextView>(R.id.tv_action_status).text = "Generating $maxItems questions..." }

                val prompt = """
                    You are an AI teacher. Read this text:
                    ${text.take(10000)}
                    Create exactly $maxItems multiple choice questions based on the text.
                    Return ONLY a valid JSON array. Format:
                    [ { "q": "Question?", "options": ["A", "B", "C", "D"], "ans": 0 } ]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) jsonStr = jsonStr.substring(startIndex, endIndex + 1)

                withContext(Dispatchers.Main) {
                    findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                    // 🟢 DO NOT SAVE TO DB BLINDLY. Pass to Editor!
                    openQuizEditor(jsonStr, maxItems)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                    GabAIUtils.showSnackbar(this@PdfViewerActivity, "Error: ${e.message}")
                }
            }
        }
    }

    private fun openQuizEditor(quizJson: String, targetItems: Int) {
        val editorIntent = Intent(this, QuizEditorActivity::class.java).apply {
            putExtra("MATERIAL_ID", intent.getStringExtra("MATERIAL_ID"))
            putExtra("TARGET_ITEMS", targetItems)
            putExtra("QUIZ_JSON", quizJson)
            // Send the file path so the editor can read the PDF if they click "AI Add More"
            putExtra("PDF_FILE_PATH", downloadedPdfFile?.absolutePath)
        }
        startActivity(editorIntent)
    }

    // ==============================================================
    // TEACHER ACTION: ASSIGN
    // ==============================================================
    private fun managePdfAccess() {
        val materialId = intent.getStringExtra("MATERIAL_ID") ?: return
        val currentlyAssigned = intent.getStringArrayListExtra("ASSIGNED_SECTIONS")?.toList() ?: listOf()
        val currentUid = uid ?: return

        findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_action_status).text = "Loading students..."

        db.collection("users").document(currentUid).get().addOnSuccessListener { teacherDoc ->
            val teacherSchoolId = teacherDoc.getString("schoolId") ?: ""
            db.collection("classes").whereArrayContains("teacherIds", currentUid).get().addOnSuccessListener { classSnaps ->
                if (classSnaps.isEmpty) {
                    findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                    GabAIUtils.showSnackbar(this, "You need to create or join a class section first.")
                    return@addOnSuccessListener
                }

                db.collection("users").whereEqualTo("role", "student").whereEqualTo("schoolId", teacherSchoolId).get()
                    .addOnSuccessListener { studentSnaps ->
                        val studentsByClass = mutableMapOf<String, MutableList<Map<String, String>>>()

                        for (classDoc in classSnaps.documents) {
                            val className = classDoc.getString("className") ?: continue
                            val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()
                            if (joinedStudents.isEmpty()) continue

                            for (studentId in joinedStudents) {
                                val studentDoc = studentSnaps.documents.find { it.id == studentId }
                                if (studentDoc != null) {
                                    val fName = studentDoc.getString("firstName") ?: ""
                                    val lName = studentDoc.getString("lastName") ?: ""
                                    val studentData = mapOf("id" to studentId, "name" to "$fName $lName".trim())
                                    if (!studentsByClass.containsKey(className)) studentsByClass[className] = mutableListOf()
                                    if (studentsByClass[className]?.none { it["id"] == studentId } == true) studentsByClass[className]?.add(studentData)
                                }
                            }
                        }

                        if (studentsByClass.isEmpty()) {
                            findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                            GabAIUtils.showSnackbar(this, "No students have joined your classes yet.")
                            return@addOnSuccessListener
                        }

                        val selectedStudentIds = currentlyAssigned.toMutableList()
                        val mainContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }

                        for ((className, students) in studentsByClass) {
                            val sectionLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, 10, 0, 10) }
                            val sectionCheckbox = CheckBox(this).apply { isChecked = students.all { selectedStudentIds.contains(it["id"]) } }
                            val sectionTitle = TextView(this).apply { text = className; textSize = 18f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(Color.parseColor("#2D3436")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                            val expandIcon = TextView(this).apply { text = "▼"; textSize = 16f; setPadding(20, 20, 20, 20) }

                            sectionLayout.addView(sectionCheckbox); sectionLayout.addView(sectionTitle); sectionLayout.addView(expandIcon)
                            mainContainer.addView(sectionLayout)

                            val studentListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(60, 0, 0, 20); visibility = View.GONE }
                            val studentCheckboxes = mutableListOf<CheckBox>()

                            for (student in students) {
                                // Skip malformed records instead of crashing on a missing id.
                                val stId = student["id"] ?: continue
                                val stCb = CheckBox(this).apply { text = student["name"]; textSize = 16f; isChecked = selectedStudentIds.contains(stId) }
                                studentCheckboxes.add(stCb)
                                studentListContainer.addView(stCb)

                                stCb.setOnCheckedChangeListener { _, isChecked ->
                                    if (isChecked && !selectedStudentIds.contains(stId)) selectedStudentIds.add(stId)
                                    else if (!isChecked) selectedStudentIds.remove(stId)

                                    sectionCheckbox.setOnCheckedChangeListener(null)
                                    sectionCheckbox.isChecked = studentCheckboxes.all { it.isChecked }
                                    sectionCheckbox.setOnCheckedChangeListener { _, parentChecked -> studentCheckboxes.forEach { it.isChecked = parentChecked } }
                                }
                            }

                            sectionCheckbox.setOnCheckedChangeListener { _, isChecked -> studentCheckboxes.forEach { it.isChecked = isChecked } }

                            val toggleExpand = View.OnClickListener {
                                if (studentListContainer.visibility == View.VISIBLE) { studentListContainer.visibility = View.GONE; expandIcon.text = "▼" }
                                else { studentListContainer.visibility = View.VISIBLE; expandIcon.text = "▲" }
                            }
                            sectionTitle.setOnClickListener(toggleExpand); expandIcon.setOnClickListener(toggleExpand)

                            mainContainer.addView(studentListContainer)
                        }

                        findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
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
                            subtitle = "Assign sections permitted to access this document",
                            customView = scrollView,
                            confirmText = "Save Access",
                            cancelText = "Cancel",
                            badgeIcon = "👥",
                            onConfirm = { dialog ->
                                dialog.dismiss()
                                findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.VISIBLE
                                db.collection("library_materials").document(materialId).update("assignedSections", selectedStudentIds).addOnSuccessListener {
                                    findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                                    intent.putStringArrayListExtra("ASSIGNED_SECTIONS", ArrayList(selectedStudentIds)) // Update intent so it remembers!
                                    GabAIUtils.showSnackbar(this, "Access updated successfully!")
                                }
                            }
                        )
                    }
            }
        }
    }

    // ==============================================================
    // TEACHER ACTION: RENAME
    // ==============================================================
    private fun promptRename(titleView: TextView) {
        val materialId = intent.getStringExtra("MATERIAL_ID") ?: return
        val fileId = intent.getStringExtra("DRIVE_FILE_ID") ?: return
        val currentTitle = titleView.text.toString()

        GabAIDialogs.showInputDialog(
            this,
            title = "Rename Document",
            subtitle = "Enter a new title for this PDF:",
            initialText = currentTitle,
            confirmText = "Save",
            badgeIcon = "✏️"
        ) { newTitle ->
            if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tv_action_status).text = "Renaming in Drive..."

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        if (fileId.isNotEmpty()) {
                            val client = OkHttpClient()
                            val formBody = FormBody.Builder().add("action", "rename").add("fileId", fileId).add("newName", newTitle).build()
                            client.newCall(Request.Builder().url(driveApiUrl).post(formBody).build()).execute()
                        }
                        withContext(Dispatchers.Main) {
                            db.collection("library_materials").document(materialId).update("title", newTitle)
                            titleView.text = newTitle
                            findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                            GabAIUtils.showSnackbar(this@PdfViewerActivity, "Renamed Successfully")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                            GabAIUtils.showSnackbar(this@PdfViewerActivity, "Failed to rename: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // ==============================================================
    // TEACHER ACTION: DELETE
    // ==============================================================
    private fun confirmDelete() {
        val materialId = intent.getStringExtra("MATERIAL_ID") ?: return
        val fileId = intent.getStringExtra("DRIVE_FILE_ID") ?: return

        GabAIDialogs.showConfirmDialog(
            this,
            title = "Delete PDF Document?",
            message = "This will permanently delete the file from Google Drive and remove it from the library.",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "🗑️"
        ) {
            findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_action_status).text = "Deleting from Drive..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (fileId.isNotEmpty()) {
                        val client = OkHttpClient()
                        val formBody = FormBody.Builder().add("action", "delete").add("fileId", fileId).build()
                        client.newCall(Request.Builder().url(driveApiUrl).post(formBody).build()).execute()
                    }
                    withContext(Dispatchers.Main) {
                        db.collection("library_materials").document(materialId).delete()
                        finish()
                        GabAIUtils.showSnackbar(this@PdfViewerActivity, "PDF Deleted")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        findViewById<LinearLayout>(R.id.pdf_action_loader).visibility = View.GONE
                        GabAIUtils.showSnackbar(this@PdfViewerActivity, "Failed to delete: ${e.message}")
                    }
                }
            }
        }
    }
}