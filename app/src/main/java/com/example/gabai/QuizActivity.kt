package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
// Add these below your questionCount variable
private var currentDocId: String? = null
private var currentInterval: Int = 1
private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()


class QuizActivity : AppCompatActivity() {
    private var score = 0
    private var questionCount = 0
    private val maxQuestions = 7 // You can change this to 10 if you want longer quizzes

    private lateinit var correctWord: String
    // Use the same model setup as your OverviewActivity
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        // Fix Status Bar
        val root = findViewById<View>(R.id.quiz_root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        startNewQuestion()
        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            score = 0
            questionCount = 0
            findViewById<View>(R.id.result_view).visibility = View.GONE
            startNewQuestion()
        }

        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            finish()
        }
    }

    private fun startNewQuestion() {
        val userId = auth.currentUser?.uid ?: return
        val currentTime = System.currentTimeMillis()

        // Query for one word that is due for review
        db.collection("users").document(userId).collection("history")
            .whereLessThanOrEqualTo("nextReview", currentTime)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    findViewById<TextView>(R.id.question_text).text = "All caught up! Check back later for new reviews."
                    findViewById<View>(R.id.options_container).visibility = View.GONE
                } else {
                    val doc = documents.documents[0]
                    currentDocId = doc.id
                    currentInterval = doc.getLong("interval")?.toInt() ?: 1

                    val wordToTest = doc.getString("word") ?: ""
                    val savedDef = doc.getString("explanation") ?: "" // Matches OverviewActivity key

                    generateAiQuiz(wordToTest, savedDef)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load SRS data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateAiQuiz(word: String, definition: String) {
        val questionTextView = findViewById<TextView>(R.id.question_text)
        questionTextView.text = "AI is thinking of a challenge..."
        findViewById<View>(R.id.options_container).visibility = View.INVISIBLE

        lifecycleScope.launch {
            try {
                // The prompt that forces the AI to make a smart quiz
                val prompt = """
                    Create a multiple choice question for a student.
                    Target Word: "$word"
                    Definition: "$definition"
                    
                    RULES:
                    1. DO NOT use the word "$word" in the question text.
                    2. Create 3 realistic but WRONG distractors.
                    3. Format the response EXACTLY like this:
                    Question: [Challenge description here]
                    A) [Option 1]
                    B) [Option 2]
                    C) [Option 3]
                    D) [Option 4]
                    Correct: [Exact text of the correct option]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val result = response.text ?: ""

                // Parse the AI's response
                parseAndDisplayQuiz(result)

            } catch (e: Exception) {
                questionTextView.text = "Error: Could not reach AI."
            }
        }
    }

    private fun parseAndDisplayQuiz(rawResult: String) {
        try {
            val lines = rawResult.lines().map { it.trim() }

            val question = lines.find { it.startsWith("Question:") }?.removePrefix("Question:")?.trim() ?: ""

            // 1. Clean the options (remove A), B), etc. and stars)
            val options = lines.filter { it.matches(Regex("^[A-D]\\).*")) }
                .map { it.substringAfter(")").trim().replace("*", "") }

            val correctLine = lines.find { it.startsWith("Correct:") } ?: ""

            // 2. Clean the master "Correct Word" key
            var cleanCorrect = correctLine.removePrefix("Correct:").trim()
            cleanCorrect = cleanCorrect.replace(Regex("^[A-D]\\)"), "") // Remove prefix if AI added it
            cleanCorrect = cleanCorrect.replace("*", "").trim().removeSuffix(".")

            correctWord = cleanCorrect

            findViewById<TextView>(R.id.question_text).text = question

            val buttons = listOf(
                findViewById<Button>(R.id.btn_choice1),
                findViewById<Button>(R.id.btn_choice2),
                findViewById<Button>(R.id.btn_choice3),
                findViewById<Button>(R.id.btn_choice4)
            )

            for (i in buttons.indices) {
                if (i < options.size) {
                    buttons[i].text = options[i]
                    buttons[i].setOnClickListener { checkAnswer(options[i]) }
                }
            }

            findViewById<View>(R.id.options_container).visibility = View.VISIBLE

        } catch (e: Exception) {
            startNewQuestion()
        }
    }

    private fun checkAnswer(selected: String) {
        questionCount++

        // Normalize both for a "fair" comparison
        val userChoice = selected.replace("*", "").trim().removeSuffix(".")
        val rightAnswer = correctWord.replace("*", "").trim().removeSuffix(".")

        if (userChoice.equals(rightAnswer, ignoreCase = true)) {
            score++
            updateSRSMetadata(true)

            // Inside checkAnswer
            val leveledUp = XPManager.addXP(this, 5)
            if (leveledUp) {
                Toast.makeText(this, "LEVEL UP! 🎊", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Correct! +5 XP", Toast.LENGTH_SHORT).show()
            }
        } else {
            updateSRSMetadata(false)
            // If it was actually right but failed the check, the toast will now show exactly
            // what the computer was looking for vs what you clicked.
            Toast.makeText(this, "Wrong! Answer: $rightAnswer", Toast.LENGTH_SHORT).show()
        }

        if (questionCount >= maxQuestions) {
            showFinalResults()
        } else {
            startNewQuestion()
        }
    }
    private fun showFinalResults() {
        XPManager.addXP(this, 20)
        val resultView = findViewById<View>(R.id.result_view)
        val scoreText = findViewById<TextView>(R.id.final_score_text)

        scoreText.text = "You got $score out of $maxQuestions correct!"
        resultView.visibility = View.VISIBLE
    }
    private fun updateSRSMetadata(isCorrect: Boolean) {
        val docId = currentDocId ?: return
        val userId = auth.currentUser?.uid ?: return

        // Algorithm logic: Double interval if correct, reset to 1 if wrong
        val newInterval = if (isCorrect) currentInterval * 2 else 1
        val nextReviewDate = System.currentTimeMillis() + (newInterval * 24L * 60 * 60 * 1000)

        val updates = hashMapOf(
            "interval" to newInterval,
            "nextReview" to nextReviewDate
        )

        db.collection("users").document(userId)
            .collection("history").document(docId)
            .update(updates as Map<String, Any>)
    }
}