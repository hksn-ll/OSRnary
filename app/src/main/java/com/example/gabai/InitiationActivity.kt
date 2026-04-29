package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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

data class GeneratedQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

class InitiationActivity : AppCompatActivity() {

    private var currentStep = 0
    private var currentQuizIndex = 0
    private var currentGeneratedQuestions: List<GeneratedQuestion> = emptyList()

    // --- NEW: Scoring System ---
    private val scores = intArrayOf(0, 0, 0, 0)
    private var currentScore = 0

    // Setup Gemini
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val pdfFiles = listOf(
        "material1.pdf",
        "material2.pdf",
        "material3.pdf",
        "material4.pdf"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initiation)

        PDFBoxResourceLoader.init(applicationContext)

        loadStep(currentStep)

        findViewById<Button>(R.id.btn_take_quiz).setOnClickListener {
            generateQuizFromPdf(pdfFiles[currentStep])
        }
    }


    override fun onPause() {
        super.onPause()
        // Bring it back when they leave (if it was enabled)
        val isEnabled = getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE).getBoolean("bubble_enabled", false)
        if (isEnabled) {
            val intent = android.content.Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }
    private fun loadStep(stepIndex: Int) {
        currentStep = stepIndex
        // If we processed all 4 PDFs, show the Final Grade Summary!
        if (stepIndex >= pdfFiles.size) {
            showFinalResults()
            return
        }

        // QoL FEATURE: If they already passed this PDF in a previous try, skip it!
        if (scores[stepIndex] >= 3) {
            loadStep(stepIndex + 1)
            return
        }

        // Update UI Progress
        findViewById<TextView>(R.id.tv_progress_tracker).text = "Apprentice Material ${stepIndex + 1} of 4"
        findViewById<ProgressBar>(R.id.progress_bar).progress = ((stepIndex + 1) * 25)

        // Show Reading, Hide Everything Else
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.result_container).visibility = View.GONE

        // LOAD THE PDF FROM ASSETS
        val pdfView = findViewById<PDFView>(R.id.pdf_viewer)
        pdfView.fromAsset(pdfFiles[stepIndex])
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .load()

        startService(android.content.Intent(this, FloatingControlService::class.java).apply { action = "ACTION_SHOW" })
    }

    private fun generateQuizFromPdf(fileName: String) {
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tv_loading_text).text = "Checking database for quiz..."

        startService(android.content.Intent(this, FloatingControlService::class.java).apply { action = "ACTION_HIDE" })
        val db = FirebaseFirestore.getInstance()
        val docId = fileName.replace(".", "_")

        db.collection("initiation_quizzes").document(docId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.contains("quiz_json")) {
                    findViewById<TextView>(R.id.tv_loading_text).text = "Quiz found! Starting..."
                    parseAndStartQuiz(doc.getString("quiz_json") ?: "[]")
                } else {
                    findViewById<TextView>(R.id.tv_loading_text).text = "First time setup:\nAI is reading the PDF..."
                    generateAndCacheQuiz(fileName, docId, db)
                }
            }
            .addOnFailureListener {
                generateAndCacheQuiz(fileName, docId, db)
            }
    }

    private fun generateAndCacheQuiz(fileName: String, docId: String, db: FirebaseFirestore) {
        lifecycleScope.launch {
            try {
                val pdfText = withContext(Dispatchers.IO) {
                    extractTextFromAsset(fileName)
                }

                if (pdfText.isEmpty()) {
                    Toast.makeText(this@InitiationActivity, "Could not read PDF.", Toast.LENGTH_SHORT).show()
                    loadStep(currentStep)
                    return@launch
                }

                val prompt = """
                    You are an AI teacher. Read the following text from a student's reading material:
                    
                    TEXT:
                    ${pdfText.take(8000)}
                    
                    Create a 15-question multiple choice quiz based ONLY on the text above.
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
                Toast.makeText(this@InitiationActivity, "AI Error: ${e.message}", Toast.LENGTH_LONG).show()
                loadStep(currentStep)
            }
        }
    }

    private fun extractTextFromAsset(fileName: String): String {
        return try {
            val inputStream = assets.open(fileName)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            inputStream.close()
            text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
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
                currentGeneratedQuestions = questions.shuffled().take(5)
                currentQuizIndex = 0
                currentScore = 0 // Reset score for this new 5-question set
                showCurrentQuestion()
            } else {
                throw Exception("No questions generated")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse quiz data.", Toast.LENGTH_SHORT).show()
            loadStep(currentStep)
        }
    }

    private fun showCurrentQuestion() {
        val questionData = currentGeneratedQuestions[currentQuizIndex]

        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_quiz_question).text = "Q${currentQuizIndex + 1}/5: ${questionData.question}"

        val buttons = listOf(
            findViewById<Button>(R.id.btn_option_0),
            findViewById<Button>(R.id.btn_option_1),
            findViewById<Button>(R.id.btn_option_2),
            findViewById<Button>(R.id.btn_option_3)
        )

        for (i in buttons.indices) {
            buttons[i].text = questionData.options[i]
            buttons[i].setOnClickListener {
                // Determine if right or wrong
                if (i == questionData.correctIndex) {
                    Toast.makeText(this, "Correct! ✅", Toast.LENGTH_SHORT).show()
                    currentScore++
                } else {
                    val correctText = questionData.options[questionData.correctIndex]
                    Toast.makeText(this, "Wrong! Answer: $correctText ❌", Toast.LENGTH_LONG).show()
                }

                // Proceed to next question automatically
                currentQuizIndex++

                if (currentQuizIndex < currentGeneratedQuestions.size) {
                    showCurrentQuestion()
                } else {
                    // Quiz finished! Save the score and process the next PDF.
                    scores[currentStep] = currentScore
                    currentStep++
                    loadStep(currentStep)
                }
            }
        }
    }

    // --- NEW: Final Results Screen Logic ---
    private fun showFinalResults() {
        // Hide standard UI
        findViewById<LinearLayout>(R.id.header_container).visibility = View.GONE
        findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
        findViewById<LinearLayout>(R.id.reading_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.quiz_container).visibility = View.GONE
        findViewById<LinearLayout>(R.id.ai_loading_container).visibility = View.GONE

        // Show Results UI
        findViewById<LinearLayout>(R.id.result_container).visibility = View.VISIBLE

        val scoresList = findViewById<LinearLayout>(R.id.scores_list)
        scoresList.removeAllViews()

        var allPassed = true
        var totalScore = 0

        // Build the Visual Report Card
        for (i in 0 until pdfFiles.size) {
            val score = scores[i]
            totalScore += score
            val passed = score >= 3
            if (!passed) allPassed = false

            // Container for this specific material
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
            }

            // Chapter Title
            val title = TextView(this).apply {
                text = "CHAPTER ${i + 1}"
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#636E72"))
                letterSpacing = 0.05f
            }
            row.addView(title)

            // Score and Mastered/Failed Text
            val scoreText = TextView(this).apply {
                text = if (passed) "⭐ $score / 5  —  MASTERED" else "⚠️ $score / 5  —  NEEDS REVIEW"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor(if (passed) "#2D3436" else "#D63031"))
                setPadding(0, 8, 0, 12)
            }
            row.addView(scoreText)

            // Visual Progress Bar
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 5
                progress = score
                progressTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(if (passed) "#00B894" else "#D63031")
                )
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20)
            }
            row.addView(bar)

            scoresList.addView(row)

            // Add a visual divider between grades
            if (i < pdfFiles.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3).apply {
                        setMargins(0, 10, 0, 10)
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#DFE6E9"))
                }
                scoresList.addView(divider)
            }
        }

        // Final Gamified Feedback & Button Logic
        val btnAction = findViewById<Button>(R.id.btn_result_action)
        val titleText = findViewById<TextView>(R.id.tv_result_title)
        val subtitleText = findViewById<TextView>(R.id.tv_result_subtitle)
        val rewardsBox = findViewById<LinearLayout>(R.id.rewards_container)

        if (allPassed) {
            rewardsBox.visibility = View.VISIBLE

            if (totalScore == 20) {
                titleText.text = "Flawless Theory! 👑"
                subtitleText.text = "A perfect score! You have mastered the written material."
            } else {
                titleText.text = "Theory Mastered! 🌟"
                subtitleText.text = "You have proven your knowledge of the scrolls."
            }

            btnAction.text = "Return to Quest Board"
            btnAction.setOnClickListener { finishInitiation() }
        } else {
            rewardsBox.visibility = View.GONE // Hide loot if they failed

            titleText.text = "Training Incomplete 🛡️"
            subtitleText.text = "A true master learns from failure. Review the highlighted scrolls and challenge the trials again."

            btnAction.text = "Return to Studies (Retry)"
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D63031"))
            btnAction.setOnClickListener {
                // Reset step back to 0, but loadStep() automatically skips passed ones!
                currentStep = 0

                // Restore standard UI
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

        Toast.makeText(this, "Initiation Complete! Quests Unlocked.", Toast.LENGTH_LONG).show()

        db.collection("users").document(uid).update(
            "quests_completed", FieldValue.arrayUnion("read", "test")
        ).addOnSuccessListener {
            finish()
        }
    }
}