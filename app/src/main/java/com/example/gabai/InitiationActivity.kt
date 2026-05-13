package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
    val correctIndex: Int
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

    private val scores = intArrayOf(0, 0, 0, 0)
    private var currentScore = 0

    // 🟢 NEW: Teacher Customizations 🟢
    private var targetQuizItems = 5
    private val downloadedCustomFiles = mutableMapOf<Int, File>()

    private var materials = mutableListOf(
        InitiationMaterial(false, "material1.pdf", "Material 1"),
        InitiationMaterial(false, "material2.pdf", "Material 2"),
        InitiationMaterial(false, "material3.pdf", "Material 3"),
        InitiationMaterial(false, "material4.pdf", "Material 4")
    )

    // Setup Gemini
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initiation)

        PDFBoxResourceLoader.init(applicationContext)

        findViewById<Button>(R.id.btn_take_quiz).setOnClickListener {
            generateQuizFromPdf(currentStep)
        }

        fetchTeacherConfigAndStart()
    }

    override fun onPause() {
        super.onPause()
        val isEnabled = getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE).getBoolean("bubble_enabled", false)
        if (isEnabled) {
            val intent = android.content.Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }

    // ========================================================================
    // 🟢 FETCH TEACHER SETTINGS 🟢
    // ========================================================================
    private fun fetchTeacherConfigAndStart() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_loading_text).text = "Fetching your initiation trials..."
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE

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
                    if (!classSnaps.isEmpty) {
                        val classDoc = classSnaps.documents[0]

                        // 1. Override the Quiz Items
                        targetQuizItems = classDoc.getLong("initiation_items")?.toInt() ?: 5

                        // 2. Override the PDFs with Custom Ones
                        val customPdfs = classDoc.get("initiation_pdfs") as? Map<String, Map<String, String>>
                        if (customPdfs != null) {
                            for (i in 1..4) {
                                val slot = customPdfs[i.toString()]
                                if (slot != null) {
                                    val url = slot["url"] ?: ""
                                    val title = slot["title"] ?: "Custom Material $i"
                                    if (url.isNotEmpty()) {
                                        materials[i-1] = InitiationMaterial(true, url, title)
                                    }
                                }
                            }
                        }
                    }
                    loadStep(0)
                }
                .addOnFailureListener { loadStep(0) }
        }.addOnFailureListener { loadStep(0) }
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

        findViewById<TextView>(R.id.tv_progress_tracker).text = "Apprentice Material ${stepIndex + 1} of 4"
        findViewById<ProgressBar>(R.id.progress_bar).progress = ((stepIndex + 1) * 25)

        findViewById<LinearLayout>(R.id.reading_container).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.GONE
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

    private fun generateQuizFromPdf(stepIndex: Int) {
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_loading_text).text = "Checking database for quiz..."

        startService(android.content.Intent(this, FloatingControlService::class.java).apply { action = "ACTION_HIDE" })
        val db = FirebaseFirestore.getInstance()
        val mat = materials[stepIndex]

        // Unique ID based on the URL or the local file name
        val docId = if (mat.isCustom) mat.pathOrUrl.hashCode().toString() else mat.pathOrUrl.replace(".", "_")

        db.collection("initiation_quizzes").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.contains("quiz_json")) {
                    val savedJson = doc.getString("quiz_json") ?: "[]"
                    val jsonArray = JSONArray(savedJson)

                    // If the teacher increased the quiz size, the cached version might be too small! Regenerate if needed.
                    if (jsonArray.length() >= targetQuizItems) {
                        findViewById<TextView>(R.id.tv_loading_text).text = "Quiz found! Starting..."
                        parseAndStartQuiz(savedJson)
                    } else {
                        generateAndCacheQuiz(stepIndex, docId, db)
                    }
                } else {
                    findViewById<TextView>(R.id.tv_loading_text).text = "First time setup:\nAI is reading the material..."
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
            try {
                val pdfText = withContext(Dispatchers.IO) {
                    if (mat.isCustom) {
                        val file = downloadedCustomFiles[stepIndex] ?: throw Exception("File not downloaded")
                        val document = PDDocument.load(file)
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document)
                        document.close()
                        text
                    } else {
                        val inputStream = assets.open(mat.pathOrUrl)
                        val document = PDDocument.load(inputStream)
                        val stripper = PDFTextStripper()
                        val text = stripper.getText(document)
                        document.close()
                        inputStream.close()
                        text
                    }
                }

                if (pdfText.isEmpty()) {
                    GabAIUtils.showSnackbar(this@InitiationActivity, "Could not read PDF.")
                    loadStep(stepIndex)
                    return@launch
                }

                val prompt = """
                    You are an AI teacher. Read the following text from a student's reading material:
                    
                    TEXT:
                    ${pdfText.take(8000)}
                    
                    Create a ${targetQuizItems + 5}-question multiple choice quiz based ONLY on the text above.
                    Return ONLY a valid JSON array of objects. Do not use markdown.
                    Format exactly like this:
                    [
                      {
                        "q": "Question text goes here?",
                        "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
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

                db.collection("initiation_quizzes").document(docId).set(hashMapOf("quiz_json" to jsonStr))
                parseAndStartQuiz(jsonStr)

            } catch (e: Exception) {
                GabAIUtils.showSnackbar(this@InitiationActivity, "AI Error: ${e.message}")
                loadStep(stepIndex)
            }
        }
    }

    private fun parseAndStartQuiz(jsonStr: String) {
        try {
            val jsonArray = JSONArray(jsonStr)
            val questions = mutableListOf<GeneratedQuestion>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.getString("q")
                val opts = obj.getJSONArray("options")
                val optList = listOf(opts.getString(0), opts.getString(1), opts.getString(2), opts.getString(3))
                val ans = obj.getInt("ans")
                questions.add(GeneratedQuestion(q, optList, ans))
            }

            if (questions.isNotEmpty()) {
                currentGeneratedQuestions = questions.shuffled().take(targetQuizItems)
                currentQuizIndex = 0
                currentScore = 0 // Reset score
                showCurrentQuestion()
            } else {
                throw Exception("No questions generated")
            }
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(this, "Failed to parse quiz data.")
            loadStep(currentStep)
        }
    }

    private fun showCurrentQuestion() {
        val questionData = currentGeneratedQuestions[currentQuizIndex]
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_quiz_question).text = "Q${currentQuizIndex + 1}/${targetQuizItems}: ${questionData.question}"

        val buttons = listOf(
            findViewById<Button>(R.id.btn_option_0),
            findViewById<Button>(R.id.btn_option_1),
            findViewById<Button>(R.id.btn_option_2),
            findViewById<Button>(R.id.btn_option_3)
        )

        for (i in buttons.indices) {
            buttons[i].text = questionData.options[i]
            buttons[i].setOnClickListener {
                if (i == questionData.correctIndex) {
                    GabAIUtils.showSnackbar(this, "Correct! ✅")
                    currentScore++
                } else {
                    val correctText = questionData.options[questionData.correctIndex]
                    GabAIUtils.showSnackbar(this, "Wrong! Answer: $correctText ❌")
                }

                currentQuizIndex++
                if (currentQuizIndex < currentGeneratedQuestions.size) {
                    showCurrentQuestion()
                } else {
                    scores[currentStep] = currentScore
                    currentStep++
                    loadStep(currentStep)
                }
            }
        }
    }

    private fun showFinalResults() {
        findViewById<LinearLayout>(R.id.header_container).visibility = View.GONE
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.GONE
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
                text = "CHAPTER ${i + 1}"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#636E72"))
                letterSpacing = 0.05f
            }
            row.addView(title)

            val scoreText = TextView(this).apply {
                text = if (passed) "⭐ $score / $targetQuizItems  —  MASTERED" else "⚠️ $score / $targetQuizItems  —  NEEDS REVIEW"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(if (passed) "#2D3436" else "#D63031"))
                setPadding(0, 8, 0, 12)
            }
            row.addView(scoreText)

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = targetQuizItems
                progress = score
                progressTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (passed) "#00B894" else "#D63031")
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
            rewardsBox.visibility = View.VISIBLE

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
            subtitleText.text = "A true master learns from failure. Review the highlighted scrolls and challenge the trials again."

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

    private fun finishInitiation() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showSnackbar(this, "Initiation Complete! Quests Unlocked.")

        db.collection("users").document(uid).update(
            "quests_completed", FieldValue.arrayUnion("read", "test")
        ).addOnSuccessListener {
            finish()
        }
    }
}