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

class QuizActivity : AppCompatActivity() {
    private var score = 0
    private var questionCount = 0
    private val maxQuestions = 7 // You can change this to 10 if you want longer quizzes

    private lateinit var correctWord: String
    // Use the same model setup as your OverviewActivity
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = "AIzaSyB8_5UwKb9B8T0HriQj87as0q-1Z1eIPjA"
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
        // Update preference names from "OSRnary_History" to "GabAI_History"
        val historyPrefs = getSharedPreferences("GabAI_History", MODE_PRIVATE)
        val keys = historyPrefs.all.keys.map { it.substringBefore("_") }.distinct()

        if (keys.isEmpty()) {
            findViewById<TextView>(R.id.question_text).text = "No history found! Scan some words first."
            findViewById<View>(R.id.options_container).visibility = View.GONE
            return
        }

        // Pick a random word from your history to test
        val randomKey = keys.random()
        val wordToTest = historyPrefs.getString("${randomKey}_word", "") ?: ""
        val savedDef = historyPrefs.getString("${randomKey}_content", "") ?: ""

        generateAiQuiz(wordToTest, savedDef)
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
            // Inside checkAnswer
            val leveledUp = XPManager.addXP(this, 5)
            if (leveledUp) {
                Toast.makeText(this, "LEVEL UP! 🎊", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Correct! +5 XP", Toast.LENGTH_SHORT).show()
            }
        } else {
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
}