package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

data class GeneratedQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val competencyCode: String = "EN-LIT-01",
    val competencyName: String = "Literal Comprehension"
)

// Helper class to manage Custom URLs vs Local Assets
data class InitiationMaterial(
    val isCustom: Boolean,
    val pathOrUrl: String,
    val title: String
)

class InitiationActivity : AppCompatActivity() {

    private var currentStep = 0
    private var currentQuizIndex = 0
    private var currentGeneratedQuestions: List<GeneratedQuestion> = emptyList()

    private val scores = IntArray(16) { 0 }
    private var currentScore = 0
    private var isPassageVisibleInQuiz = false

    // 🟢 NEW: Teacher Customizations 🟢
    private var targetQuizItems = 5
    private val downloadedCustomFiles = mutableMapOf<Int, File>()

    private var materials = mutableListOf(
        InitiationMaterial(false, "deped_phil_iri_diagnostic.pdf", "Phil-IRI Diagnostic Reading")
    )

    // Setup Gemini
    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_initiation)

        PDFBoxResourceLoader.init(applicationContext)

        val header = findViewById<View>(R.id.header_container)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        val handleBack = {
            val quizContainer = findViewById<View>(R.id.quiz_container)
            if (quizContainer?.visibility == View.VISIBLE) {
                GabAIDialogs.showConfirmDialog(
                    context = this,
                    title = "Quit Assessment?",
                    message = "Your quiz progress for this module will be lost. Are you sure you want to exit?",
                    confirmText = "Exit Quiz",
                    cancelText = "Continue Quiz",
                    isDestructive = true,
                    badgeIcon = "⚠️"
                ) {
                    finish()
                }
            } else {
                finish()
            }
        }

        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
            handleBack()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        findViewById<Button>(R.id.btn_take_quiz).setOnClickListener {
            generateQuizFromPdf(currentStep)
        }

        findViewById<MaterialButton>(R.id.btn_toggle_passage)?.setOnClickListener {
            toggleReadingPassageInQuiz()
        }

        fetchTeacherConfigAndStart()
    }

    override fun onPause() {
        super.onPause()
        // Defensive: Do not force-show floating bubble when navigating away
    }

    // ========================================================================
    // 🟢 FETCH TEACHER SETTINGS 🟢
    // ========================================================================
    private fun fetchTeacherConfigAndStart() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            loadStep(0)
            return
        }
        val db = FirebaseFirestore.getInstance()

        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_loading_text).text = "Fetching your initiation trials..."
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE

        // 🟢 Network Watchdog: Prevent hanging forever on slow/dropped connections
        var hasLoaded = false
        val watchdog = Runnable {
            if (!hasLoaded && !isDestroyed && !isFinishing) {
                hasLoaded = true
                loadStep(0)
            }
        }
        window.decorView.postDelayed(watchdog, 4000)

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val schoolId = userDoc.getString("schoolId") ?: ""
            val section = userDoc.getString("section") ?: ""
            val grade = userDoc.getString("grade") ?: ""

            // Find the student's advisory class
            db.collection("classes")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("section", section)
                .whereEqualTo("grade", grade)
                .whereEqualTo("isAdviser", true)
                .get()
                .addOnSuccessListener { classSnaps ->
                    if (!hasLoaded) {
                        hasLoaded = true
                        window.decorView.removeCallbacks(watchdog)

                        if (!classSnaps.isEmpty) {
                            val classDoc = classSnaps.documents[0]

                            // 1. Override the Quiz Items
                            targetQuizItems = classDoc.getLong("initiation_items")?.toInt() ?: 5

                            // 2. Override the PDFs with Custom Ones
                            val customPdfs = classDoc.get("initiation_pdfs") as? Map<String, Map<String, String>>
                            if (customPdfs != null && customPdfs.isNotEmpty()) {
                                val loadedCustoms = mutableListOf<InitiationMaterial>()
                                for (slotIdx in 1..4) {
                                    val slot = customPdfs[slotIdx.toString()]
                                    if (slot != null) {
                                        val url = slot["url"] ?: ""
                                        val title = slot["title"] ?: "Custom Material $slotIdx"
                                        if (url.isNotEmpty()) {
                                            loadedCustoms.add(InitiationMaterial(true, url, title))
                                        }
                                    }
                                }
                                if (loadedCustoms.isNotEmpty()) {
                                    materials.clear()
                                    materials.addAll(loadedCustoms)
                                }
                            }
                        }
                        loadStep(0)
                    }
                }
                .addOnFailureListener {
                    if (!hasLoaded) {
                        hasLoaded = true
                        window.decorView.removeCallbacks(watchdog)
                        loadStep(0)
                    }
                }
        }.addOnFailureListener {
            if (!hasLoaded) {
                hasLoaded = true
                window.decorView.removeCallbacks(watchdog)
                loadStep(0)
            }
        }
    }

    private fun loadStep(stepIndex: Int) {
        currentStep = stepIndex
        if (stepIndex >= materials.size) {
            showFinalResults()
            return
        }

        // Target to pass is > 50% of the targetQuizItems
        val passingScore = Math.ceil(targetQuizItems / 2.0).toInt()
        if (scores[stepIndex] >= passingScore) {
            loadStep(stepIndex + 1)
            return
        }

        isPassageVisibleInQuiz = false
        findViewById<MaterialButton>(R.id.btn_toggle_passage)?.visibility = View.GONE
        findViewById<MaterialButton>(R.id.btn_toggle_passage)?.text = "📖 View Passage"
        findViewById<View>(R.id.btn_take_quiz)?.visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_progress_tracker).text = "Apprentice Material ${stepIndex + 1} of ${materials.size}"
        findViewById<ProgressBar>(R.id.progress_bar).progress = ((stepIndex + 1) * (100 / materials.size))

        findViewById<LinearLayout>(R.id.reading_container).visibility = View.VISIBLE
        findViewById<View>(R.id.quiz_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.result_container).visibility = View.GONE

        val pdfView = findViewById<PDFView>(R.id.pdf_viewer)
        val mat = materials[stepIndex]

        // 🟢 LOAD DYNAMICALLY: URL vs Local Asset
        if (mat.isCustom) {
            if (downloadedCustomFiles.containsKey(stepIndex)) {
                pdfView.fromFile(downloadedCustomFiles[stepIndex]).enableSwipe(true).load()
            } else {
                findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
                findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
                findViewById<TextView>(R.id.tv_loading_text).text = "Downloading Teacher's Material..."

                thread {
                    try {
                        val input = URL(mat.pathOrUrl).openStream()
                        val tempFile = File.createTempFile("custom_init_$stepIndex", ".pdf", cacheDir)
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                        downloadedCustomFiles[stepIndex] = tempFile

                        runOnUiThread {
                            findViewById<LinearLayout>(R.id.reading_container).visibility = View.VISIBLE
                            findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
                            pdfView.fromFile(tempFile).enableSwipe(true).load()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { GabAIUtils.showSnackbar(this, "Failed to download material.") }
                    }
                }
            }
        } else {
            pdfView.fromAsset(mat.pathOrUrl).enableSwipe(true).load()
        }

        startService(android.content.Intent(this, FloatingControlService::class.java).apply { action = "ACTION_SHOW" })
    }

    private fun getPhilIriDiagnosticQuestions(): List<GeneratedQuestion> {
        return listOf(
            GeneratedQuestion(
                question = "What is the scientific name of the Philippine Eagle highlighted in the reading selection?",
                options = listOf("Pithecophaga jefferyi", "Haliaeetus leucocephalus", "Aquila chrysaetos", "Spizaetus philippensis"),
                correctIndex = 0,
                competencyCode = "EN-LIT-01",
                competencyName = "Noting Explicit Details"
            ),
            GeneratedQuestion(
                question = "The passage describes the Philippine Eagle as 'endemic' to the archipelago. What does 'endemic' mean?",
                options = listOf("Migrating seasonally across continents", "Exclusively native and found only in a specific geographic region", "Introduced by human settlers", "Bred exclusively in domestic facilities"),
                correctIndex = 1,
                competencyCode = "EN-VOC-02",
                competencyName = "Vocabulary & Context Clues"
            ),
            GeneratedQuestion(
                question = "Why do biologists consider the Philippine Eagle a vital 'ecological indicator' and apex predator?",
                options = listOf("It feeds exclusively on agricultural grain crops", "Its presence signifies a healthy, balanced, and thriving rainforest ecosystem", "It displaces indigenous plants and shrubs", "It clears brush along lowland rivers"),
                correctIndex = 1,
                competencyCode = "EN-INF-03",
                competencyName = "Inferring Ecological Cause & Effect"
            ),
            GeneratedQuestion(
                question = "According to the passage, why is primary rainforest destruction especially devastating for eagle breeding?",
                options = listOf("They only nest in high-altitude volcanic craters", "A single breeding pair requires up to 50 square miles of undisturbed virgin forest", "They cannot fly through stormy weather", "They nest exclusively in lowland grasslands"),
                correctIndex = 1,
                competencyCode = "EN-SYN-04",
                competencyName = "Analyzing Text Relationships & Supporting Details"
            ),
            GeneratedQuestion(
                question = "What primary message is conveyed regarding the conservation of the national bird?",
                options = listOf("Encouraging commercial wildlife trophy hunting", "Defending our national bird is essential to protecting the rainforests that sustain future Filipino generations", "Eagles should be relocated exclusively to foreign zoos", "Forest protection should be left to international tourists"),
                correctIndex = 1,
                competencyCode = "EN-CRI-05",
                competencyName = "Identifying Main Idea & Critical Theme"
            )
        )
    }

    private fun extractEmbeddedQuestionsLocally(text: String): List<GeneratedQuestion> {
        val questions = mutableListOf<GeneratedQuestion>()
        if (text.isBlank()) return questions

        // 1. Search for an Answer Key block or table in the document
        // Matches forms like: "1 B", "1. B", "1-B", "Item No. ... 1 B"
        val answerKeyMap = mutableMapOf<Int, Int>()
        val keyRegex = Regex("""(?im)(?:^|\n)\s*(\d+)[\.\s:-]+([A-Da-d])\b""")
        for (km in keyRegex.findAll(text)) {
            val qNum = km.groupValues[1].toIntOrNull() ?: continue
            val letter = km.groupValues[2].uppercase()
            val ansIdx = when (letter) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> 0
            }
            answerKeyMap[qNum] = ansIdx
        }

        // 2. Parse Multiple-Choice Questions (Question text followed by A, B, C, D choices)
        val qBlockRegex = Regex(
            """(?m)(?:^|\n)\s*(\d+)[\.\)]\s*([^\n]+(?:\n(?!\s*[A-Da-d][\.\)]|\s*\d+[\.\)]).*)*)\s*""" +
            """\n\s*A[\.\)]\s*([^\n]+(?:\n(?!\s*[B-Db-d][\.\)]|\s*\d+[\.\)]).*)*)\s*""" +
            """\n\s*B[\.\)]\s*([^\n]+(?:\n(?!\s*[C-Dc-d][\.\)]|\s*\d+[\.\)]).*)*)\s*""" +
            """\n\s*C[\.\)]\s*([^\n]+(?:\n(?!\s*[D-Dd-d][\.\)]|\s*\d+[\.\)]).*)*)\s*""" +
            """\n\s*D[\.\)]\s*([^\n]+(?:\n(?!\s*[A-Da-d][\.\)]|\s*\d+[\.\)]).*)*)"""
        )

        for (m in qBlockRegex.findAll(text)) {
            val qNum = m.groupValues[1].toIntOrNull() ?: (questions.size + 1)
            val qText = m.groupValues[2].trim().replace(Regex("""\s+"""), " ")
            val optA = m.groupValues[3].trim().replace(Regex("""\s+"""), " ")
            val optB = m.groupValues[4].trim().replace(Regex("""\s+"""), " ")
            val optC = m.groupValues[5].trim().replace(Regex("""\s+"""), " ")
            val optD = m.groupValues[6].trim().replace(Regex("""\s+"""), " ")

            if (qText.isNotEmpty() && optA.isNotEmpty() && optB.isNotEmpty() && optC.isNotEmpty() && optD.isNotEmpty()) {
                val correctIdx = answerKeyMap[qNum] ?: 0
                questions.add(
                    GeneratedQuestion(
                        question = qText,
                        options = listOf(optA, optB, optC, optD),
                        correctIndex = correctIdx
                    )
                )
            }
        }

        return questions
    }

    private fun generateQuizFromPdf(stepIndex: Int) {
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_loading_text).text = "Loading initiation quiz..."

        startService(android.content.Intent(this, FloatingControlService::class.java).apply { action = "ACTION_HIDE" })
        val db = FirebaseFirestore.getInstance()
        val mat = materials[stepIndex]

        if (!mat.isCustom) {
            // Standard Phil-IRI Diagnostic Assessment: 100% deterministic, zero network flakiness
            val questions = getPhilIriDiagnosticQuestions().take(targetQuizItems)
            currentGeneratedQuestions = questions
            currentQuizIndex = 0
            currentScore = 0
            showCurrentQuestion()
            return
        }

        // Custom Teacher Material
        val docId = mat.pathOrUrl.hashCode().toString()
        db.collection("initiation_quizzes").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.contains("quiz_json") && doc.getLong("schema_version") == 2L) {
                    val savedJson = doc.getString("quiz_json") ?: "[]"
                    val jsonArray = JSONArray(savedJson)

                    if (jsonArray.length() >= targetQuizItems) {
                        findViewById<TextView>(R.id.tv_loading_text).text = "Quiz found! Starting..."
                        parseAndStartQuiz(savedJson, isCustom = true)
                    } else {
                        generateAndCacheQuiz(stepIndex, docId, db)
                    }
                } else {
                    findViewById<TextView>(R.id.tv_loading_text).text = "AI is preparing custom quiz from material..."
                    generateAndCacheQuiz(stepIndex, docId, db)
                }
            }
            .addOnFailureListener {
                generateAndCacheQuiz(stepIndex, docId, db)
            }
    }

    private fun generateAndCacheQuiz(stepIndex: Int, docId: String, db: FirebaseFirestore) {
        val mat = materials[stepIndex]
        findViewById<TextView>(R.id.tv_loading_text).text = "AI is reading the material..."

        lifecycleScope.launch {
            var extractedPdfText = ""
            try {
                extractedPdfText = withContext(Dispatchers.IO) {
                    if (mat.isCustom) {
                        val file = downloadedCustomFiles[stepIndex] ?: throw Exception("File not downloaded")
                        val document = PDDocument.load(file)
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document) ?: ""
                        document.close()
                        text
                    } else {
                        val inputStream = assets.open(mat.pathOrUrl)
                        val document = PDDocument.load(inputStream)
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document) ?: ""
                        document.close()
                        inputStream.close()
                        text
                    }
                }

                if (extractedPdfText.isBlank()) {
                    if (!mat.isCustom) {
                        currentGeneratedQuestions = getPhilIriDiagnosticQuestions().take(targetQuizItems)
                        currentQuizIndex = 0
                        currentScore = 0
                        showCurrentQuestion()
                    } else {
                        GabAIUtils.showSnackbar(this@InitiationActivity, "Could not extract text from document.")
                        loadStep(stepIndex)
                    }
                    return@launch
                }

                // First, check if deterministic local regex finds embedded questions immediately
                val localExtracted = extractEmbeddedQuestionsLocally(extractedPdfText)
                if (localExtracted.size >= targetQuizItems) {
                    // Document contains formatted questions and choices — cache and start!
                    val jsonArray = JSONArray()
                    for (q in localExtracted) {
                        val obj = org.json.JSONObject()
                        obj.put("q", q.question)
                        val opts = JSONArray()
                        q.options.forEach { opts.put(it) }
                        obj.put("options", opts)
                        obj.put("ans", q.correctIndex)
                        jsonArray.put(obj)
                    }
                    val jsonStr = jsonArray.toString()
                    db.collection("initiation_quizzes").document(docId).set(
                        hashMapOf(
                            "quiz_json" to jsonStr,
                            "schema_version" to 2L
                        )
                    )
                    parseAndStartQuiz(jsonStr, isCustom = true)
                    return@launch
                }

                val sampleText = extractedPdfText.take(60000)

                val prompt = """
                    You are an expert reading comprehension assessment parser for GabAI.
                    Read the following text extracted from a student's reading material:
                    
                    DOCUMENT TEXT:
                    $sampleText
                    
                    CRITICAL TASK INSTRUCTIONS:
                    1. FIRST, check if this document ALREADY CONTAINS multiple-choice questions (e.g., questions with options A, B, C, D).
                    2. IF multiple-choice questions ALREADY EXIST in the document:
                       - YOU MUST EXTRACT THOSE EXACT QUESTIONS AND CHOICES directly from the text.
                       - DO NOT invent, rewrite, or replace them.
                       - Determine the correct answer index (0 for A, 1 for B, 2 for C, 3 for D) from the answer key if present in the document, or by carefully evaluating the passage.
                       - Keep the exact question numbering and order as printed in the document.
                    3. ONLY IF the document contains NO pre-existing questions (it is a pure reading story or passage with no questions):
                       - Generate a $targetQuizItems-question multiple choice reading comprehension quiz based strictly on the reading passage.
                    
                    Return ONLY a valid JSON array of objects. Do not use markdown code blocks or explanatory text.
                    Format:
                    [
                      {
                        "q": "Exact question text here?",
                        "options": ["Choice A", "Choice B", "Choice C", "Choice D"],
                        "ans": 0
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

                db.collection("initiation_quizzes").document(docId).set(
                    hashMapOf(
                        "quiz_json" to jsonStr,
                        "schema_version" to 2L
                    )
                )
                parseAndStartQuiz(jsonStr, isCustom = true)

            } catch (e: Exception) {
                // Defensive Fallback: Try local regex parser if AI is unavailable/offline
                val localFallback = extractEmbeddedQuestionsLocally(extractedPdfText)
                if (localFallback.isNotEmpty()) {
                    val jsonArray = JSONArray()
                    for (q in localFallback) {
                        val obj = org.json.JSONObject()
                        obj.put("q", q.question)
                        val opts = JSONArray()
                        q.options.forEach { opts.put(it) }
                        obj.put("options", opts)
                        obj.put("ans", q.correctIndex)
                        jsonArray.put(obj)
                    }
                    parseAndStartQuiz(jsonArray.toString(), isCustom = true)
                } else if (!mat.isCustom) {
                    GabAIUtils.showSnackbar(this@InitiationActivity, "Loading standardized diagnostic questions.")
                    currentGeneratedQuestions = getPhilIriDiagnosticQuestions().take(targetQuizItems)
                    currentQuizIndex = 0
                    currentScore = 0
                    showCurrentQuestion()
                } else {
                    GabAIUtils.showSnackbar(this@InitiationActivity, "Could not load quiz for this material: ${e.localizedMessage ?: "Network error"}")
                    loadStep(stepIndex)
                }
            }
        }
    }

    private fun parseAndStartQuiz(jsonStr: String, isCustom: Boolean = false) {
        try {
            val jsonArray = JSONArray(jsonStr)
            val questions = mutableListOf<GeneratedQuestion>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.optString("q", obj.optString("question", "")).trim()
                val opts = obj.optJSONArray("options") ?: obj.optJSONArray("choices")
                if (q.isBlank() || opts == null || opts.length() < 4) continue

                val optList = listOf(
                    opts.getString(0).trim(),
                    opts.getString(1).trim(),
                    opts.getString(2).trim(),
                    opts.getString(3).trim()
                )

                val rawAns = obj.opt("ans") ?: obj.opt("correctIndex") ?: obj.opt("correctAnswer") ?: 0
                val ansIndex = when (rawAns) {
                    is Number -> rawAns.toInt().coerceIn(0, 3)
                    is String -> when (rawAns.trim().uppercase()) {
                        "A", "0" -> 0
                        "B", "1" -> 1
                        "C", "2" -> 2
                        "D", "3" -> 3
                        else -> 0
                    }
                    else -> 0
                }
                questions.add(GeneratedQuestion(q, optList, ansIndex))
            }

            if (questions.isNotEmpty()) {
                if (isCustom) {
                    // For custom materials, adapt quiz item count to available questions and preserve document order
                    if (questions.size >= targetQuizItems) {
                        targetQuizItems = questions.size
                    }
                    currentGeneratedQuestions = questions.take(targetQuizItems)
                } else {
                    currentGeneratedQuestions = questions.take(targetQuizItems)
                }
                currentQuizIndex = 0
                currentScore = 0 // Reset score
                showCurrentQuestion()
            } else {
                throw Exception("No valid questions generated")
            }
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(this, "Failed to parse quiz data.")
            loadStep(currentStep)
        }
    }

    private fun showCurrentQuestion() {
        val questionData = currentGeneratedQuestions[currentQuizIndex]
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<View>(R.id.quiz_container).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        isPassageVisibleInQuiz = false
        findViewById<MaterialButton>(R.id.btn_toggle_passage)?.apply {
            visibility = View.VISIBLE
            text = "📖 View Passage"
        }
        findViewById<View>(R.id.btn_take_quiz)?.visibility = View.GONE

        findViewById<TextView>(R.id.tv_quiz_question).text = "Q${currentQuizIndex + 1}/${targetQuizItems}: ${questionData.question}"

        val buttons = listOf(
            findViewById<Button>(R.id.btn_option_0),
            findViewById<Button>(R.id.btn_option_1),
            findViewById<Button>(R.id.btn_option_2),
            findViewById<Button>(R.id.btn_option_3)
        )

        for (i in buttons.indices) {
            buttons[i].isEnabled = true
            buttons[i].text = questionData.options[i]
            buttons[i].setOnClickListener {
                // Immediately disable all options to prevent multi-tap glitches
                buttons.forEach { b -> b.isEnabled = false }

                if (i == questionData.correctIndex) {
                    GabAIUtils.showSnackbar(this, "Correct! ✅")
                    currentScore++
                } else {
                    val correctText = questionData.options[questionData.correctIndex]
                    GabAIUtils.showSnackbar(this, "Wrong! Answer: $correctText ❌")
                }

                it.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    currentQuizIndex++
                    if (currentQuizIndex < currentGeneratedQuestions.size) {
                        showCurrentQuestion()
                    } else {
                        scores[currentStep] = currentScore
                        currentStep++
                        loadStep(currentStep)
                    }
                }, 400L)
            }
        }
    }

    private fun toggleReadingPassageInQuiz() {
        val readingContainer = findViewById<LinearLayout>(R.id.reading_container) ?: return
        val quizContainer = findViewById<View>(R.id.quiz_container) ?: return
        val toggleBtn = findViewById<MaterialButton>(R.id.btn_toggle_passage) ?: return

        isPassageVisibleInQuiz = !isPassageVisibleInQuiz
        if (isPassageVisibleInQuiz) {
            readingContainer.visibility = View.VISIBLE
            quizContainer.visibility = View.GONE
            toggleBtn.text = "✏️ Back to Quiz"
            findViewById<View>(R.id.btn_take_quiz)?.visibility = View.GONE
        } else {
            readingContainer.visibility = View.GONE
            quizContainer.visibility = View.VISIBLE
            toggleBtn.text = "📖 View Passage"
        }
    }

    private fun showFinalResults() {
        findViewById<MaterialButton>(R.id.btn_toggle_passage)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.header_container).visibility = View.GONE
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<View>(R.id.quiz_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.result_container).visibility = View.VISIBLE

        val scoresList = findViewById<LinearLayout>(R.id.scores_list)
        scoresList.removeAllViews()

        var allPassed = true
        var totalScore = 0

        val passingScore = Math.ceil(targetQuizItems / 2.0).toInt()

        for (i in 0 until materials.size) {
            val score = scores[i]
            totalScore += score
            val passed = score >= passingScore
            if (!passed) allPassed = false

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
            }

            val title = TextView(this).apply {
                val materialTitle = materials.getOrNull(i)?.title
                text = if (!materialTitle.isNullOrBlank()) materialTitle.uppercase() else "MATERIAL ${i + 1}"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#636E72"))
                letterSpacing = 0.05f
            }
            row.addView(title)

            val scoreText = TextView(this).apply {
                text = if (passed) "⭐ $score / $targetQuizItems  —  MASTERED" else "📚 $score / $targetQuizItems  —  PRACTICE MORE"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(if (passed) "#2D3436" else "#D97706"))
                setPadding(0, 8, 0, 12)
            }
            row.addView(scoreText)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = targetQuizItems
                progress = score
                progressTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (passed) "#00B894" else "#F59E0B")
                )
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20)
            }
            row.addView(bar)
            scoresList.addView(row)

            if (i < materials.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3).apply {
                        setMargins(0, 10, 0, 10)
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#DFE6E9"))
                }
                scoresList.addView(divider)
            }
        }

        val btnAction = findViewById<Button>(R.id.btn_result_action)
        val titleText = findViewById<TextView>(R.id.tv_result_title)
        val subtitleText = findViewById<TextView>(R.id.tv_result_subtitle)
        val rewardsBox = findViewById<LinearLayout>(R.id.rewards_container)

        if (allPassed) {
            lastTotalScore = totalScore
            lastTotalPossible = targetQuizItems * materials.size
            rewardsBox.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_quests_completed_summary)?.text = "✅ Read the ${materials.size} Required Material${if (materials.size > 1) "s" else ""}\n✅ Pass the Initiation Test"

            if (totalScore == (targetQuizItems * materials.size)) {
                titleText.text = "Flawless Theory! 👑"
                subtitleText.text = "A perfect score! You have mastered the written material."
            } else {
                titleText.text = "Theory Mastered! 🌟"
                subtitleText.text = "You have proven your knowledge of the scrolls."
            }

            btnAction.text = "Return to Quest Board"
            btnAction.setOnClickListener { finishInitiation() }
        } else {
            rewardsBox.visibility = View.GONE
            titleText.text = "Training Incomplete 🛡️"
            subtitleText.setText("A true master learns from failure. Review the highlighted scrolls and challenge the trials again.")

            btnAction.text = "Return to Studies (Retry)"
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D63031"))
            btnAction.setOnClickListener {
                currentStep = 0
                findViewById<LinearLayout>(R.id.result_container).visibility = View.GONE
                findViewById<LinearLayout>(R.id.header_container).visibility = View.VISIBLE
                findViewById<ProgressBar>(R.id.progress_bar).visibility = View.VISIBLE
                loadStep(currentStep)
            }
        }
    }

    private var lastTotalScore = 0
    private var lastTotalPossible = 0

    private fun finishInitiation() {
        GabAIUtils.performHaptic(window.decorView, android.view.HapticFeedbackConstants.CONFIRM)

        val totalPossible = if (lastTotalPossible > 0) lastTotalPossible else (targetQuizItems * materials.size)
        val percentage = if (totalPossible > 0) ((lastTotalScore.toDouble() / totalPossible) * 100).toInt() else 0
        val philIriTier = when {
            percentage >= 80 -> "Independent"
            percentage >= 59 -> "Instructional"
            else -> "Frustration"
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = FirebaseFirestore.getInstance()
            val competencyScores = hashMapOf(
                "EN-LIT-01" to (if ((scores.getOrNull(0) ?: 0) >= 1) 100 else 0),
                "EN-VOC-02" to (if ((scores.getOrNull(1) ?: 0) >= 1) 100 else 0),
                "EN-INF-03" to (if ((scores.getOrNull(2) ?: 0) >= 1) 100 else 0),
                "EN-SYN-04" to (if ((scores.getOrNull(3) ?: 0) >= 1) 100 else 0),
                "EN-CRI-05" to (if ((scores.getOrNull(4) ?: 0) >= 1) 100 else 0)
            )
            val diagData = hashMapOf(
                "phil_iri_tier" to philIriTier,
                "diagnostic_score" to lastTotalScore,
                "diagnostic_total" to totalPossible,
                "diagnostic_pct" to percentage,
                "competency_scores" to competencyScores,
                "diagnostic_completed_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(diagData as Map<String, Any>)
            db.collection("users").document(uid).collection("diagnostic_results").add(diagData)
        }

        ApprenticeQuestHelper.completeQuest(this, ApprenticeQuestHelper.QUEST_TEST) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            findViewById<PDFView>(R.id.pdf_viewer)?.recycle()
        } catch (_: Exception) {}
    }
}