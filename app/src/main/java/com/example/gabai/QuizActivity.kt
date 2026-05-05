package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    // --- Dynamic Limits (Fetched from Teacher's Settings) ---
    // --- Dynamic Limits (Fetched from Teacher's Settings) ---
    private var maxItemsPerSession = 1   // <--- CHANGE THIS TO 1 FOR TESTING
    private var maxSessionsPerDay = 3

    // --- Session Trackers ---
    private var sessionWords = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
    private var currentActiveDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    private val sessionResults = mutableListOf<Map<String, Any>>()

    private var score = 0
    private var totalAttempts = 0
    private lateinit var correctWord: String
    private var startTime: Long = 0

    private var currentDocId: String? = null
    private var currentInterval: Int = 1

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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

        initializeQuizSession()

        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            finish() // Return to dashboard
        }

        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            finish()
        }
        val btnHistory = findViewById<Button>(R.id.btn_quiz_history)
        // Check if the button exists in the layout to prevent crashes
        btnHistory?.setOnClickListener {
            startActivity(android.content.Intent(this, QuizHistoryActivity::class.java))
            finish() // Optional: close the current quiz screen so pressing "back" doesn't return here
        }
    }

    // ========================================================================
    // STEP 1: FETCH TEACHER'S LIMITS
    // ========================================================================
    private fun initializeQuizSession() {
        val userId = auth.currentUser?.uid ?: return

        findViewById<TextView>(R.id.question_text).text = "Fetching teacher settings..."
        findViewById<View>(R.id.options_container).visibility = View.GONE

        db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
            val schoolId = userDoc.getString("schoolId") ?: ""
            val section = userDoc.getString("section") ?: ""

            db.collection("classes")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("section", section)
                .get()
                .addOnSuccessListener { classDocs ->
                    if (!classDocs.isEmpty) {
                        val classDoc = classDocs.documents[0]
                        maxSessionsPerDay = classDoc.getLong("maxSessionsPerDay")?.toInt() ?: 3
                        maxItemsPerSession = classDoc.getLong("maxItemsPerSession")?.toInt() ?: 10
                    }
                    checkDailyLimits(userId)
                }
        }
    }

    // ========================================================================
    // STEP 2: CHECK IF THEY ALREADY DID THEIR MAX QUIZZES TODAY
    // ========================================================================
    // ========================================================================
    // STEP 2: CHECK IF THEY ALREADY DID THEIR MAX QUIZZES TODAY
    // ========================================================================
    private fun checkDailyLimits(userId: String) {
        findViewById<TextView>(R.id.question_text).text = "Checking daily limits..."

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        db.collection("users").document(userId).collection("quiz_history")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .get()
            .addOnSuccessListener { historyDocs ->
                val sessionsToday = historyDocs.size()

                if (sessionsToday >= maxSessionsPerDay) {
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)

                    // FIX: Write the warning to the result screen!
                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Brain Rest Required!\n\nYou've completed your $maxSessionsPerDay daily sessions. Come back tomorrow to let your memory consolidate!"

                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Dashboard"
                } else {
                    loadWordsForSession(userId)
                }
            }
    }

    // ========================================================================
    // STEP 3: LOAD THE WORDS DUE FOR REVIEW
    // ========================================================================
    private fun loadWordsForSession(userId: String) {
        val currentTime = System.currentTimeMillis()
        findViewById<TextView>(R.id.question_text).text = "Loading your study session..."

        db.collection("users").document(userId).collection("history")
            .whereLessThanOrEqualTo("nextReview", currentTime)
            .limit(maxItemsPerSession.toLong())
            .get()
            .addOnSuccessListener { documents ->
                val readyCount = documents.size()

                // STRICT MODE: Student MUST have enough words to meet the teacher's exact requirement
                if (readyCount < maxItemsPerSession) {
                    val needed = maxItemsPerSession - readyCount

                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)

                    // FIX: Make the text visible and write the warning directly to the result screen!
                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Session Locked!\n\n" +
                            "Your teacher requires a fixed $maxItemsPerSession-item quiz.\n" +
                            "You only have $readyCount word(s) ready for review right now.\n\n" +
                            "You need $needed more word(s) to unlock this session. Keep reading and saving words!"

                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Dashboard"
                } else {
                    // They hit the exact requirement! Let them play.
                    sessionWords.addAll(documents.documents)
                    startNewQuestion()
                }
            }
            .addOnFailureListener { e ->
                findViewById<TextView>(R.id.question_text).text = "Failed to load session: ${e.message}"
            }
    }

    // ========================================================================
    // POP THE NEXT WORD FROM THE QUEUE
    // ========================================================================
    private fun startNewQuestion() {
        if (sessionWords.isEmpty()) {
            showFinalResults()
            return
        }

        currentActiveDoc = sessionWords.removeAt(0)

        currentDocId = currentActiveDoc?.id
        currentInterval = currentActiveDoc?.getLong("interval")?.toInt() ?: 1

        val wordToTest = currentActiveDoc?.getString("word") ?: ""
        val savedDef = currentActiveDoc?.getString("explanation") ?: ""
        val savedContext = currentActiveDoc?.getString("originalContext") ?: ""

        generateAiQuiz(wordToTest, savedDef, savedContext)
    }

    // ========================================================================
    // GENERATE THE CONTEXTUAL QUIZ
    // ========================================================================
    private fun generateAiQuiz(word: String, definition: String, contextText: String) {
        val questionTextView = findViewById<TextView>(R.id.question_text)
        questionTextView.text = "AI is thinking of a challenge..."
        findViewById<View>(R.id.options_container).visibility = View.INVISIBLE

        lifecycleScope.launch {
            try {
                val prompt = """
                    You are an expert linguistics engine generating cloze (fill-in-the-blank) tests for Grade 10 students.
                    Target Word: "$word"
                    Simplified Definition: "$definition"
                    Original Context: "$contextText"

                    **STEP 1: SENTENCE GENERATION**
                    - Read the "Original Context".
                    - Identify the exact morphological form of the "Target Word" needed (e.g., base verb, past tense, plural noun).
                    - Create a clear, realistic high-school-level sentence using this context. Expand it logically if it is shorter than 6 words.
                    - Replace the target word in the sentence with exactly 7 underscores: "_______".

                    **STEP 2: OPTION GENERATION (CRITICAL RULES)**
                    - Generate exactly 4 options labeled A, B, C, and D.
                    - Exactly ONE option MUST be the correct Target Word (in the correct grammatical form to fit the blank).
                    - Generate THREE distractors.
                    - **ABSOLUTE GRAMMAR RULE:** All 4 options MUST share the EXACT same part of speech and grammatical form. 
                        - Example: If the blank follows "to" (infinitive), ALL 4 options MUST be base-form verbs (e.g., come, stay, leave, go). Never mix tenses (e.g., do not mix "arrived" with "come").
                        - Example: If the correct word is a plural noun, ALL distractors must be plural nouns.
                    - Distractors must fit grammatically perfectly into the blank, but be logically incorrect contextually.
                    - The Question sentence and all 4 Options MUST be written in the exact same language as the "Original Context".

                    **STEP 3: OUTPUT FORMAT**
                    - Output ONLY the text requested below. 
                    - NO conversational filler (do not say "Here is the quiz"). 
                    - NO markdown formatting (no bolding or italics) on the options.

                    Question: [The generated sentence with _______]
                    A) [Option 1]
                    B) [Option 2]
                    C) [Option 3]
                    D) [Option 4]
                    Correct: [The exact text of the correct option, matching one of the A-D strings precisely]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                parseAndDisplayQuiz(response.text ?: "")

            } catch (e: Exception) {
                questionTextView.text = "Error: Could not reach AI."
            }
        }
    }

//    private fun parseAndDisplayQuiz(rawResult: String) {
//        try {
//            val lines = rawResult.lines().map { it.trim() }
//            val question = lines.find { it.startsWith("Question:") }?.removePrefix("Question:")?.trim() ?: ""
//
//            val options = lines.filter { it.matches(Regex("^[A-D]\\).*")) }
//                .map { it.substringAfter(")").trim().replace("*", "") }
//
//            val correctLine = lines.find { it.startsWith("Correct:") } ?: ""
//            var cleanCorrect = correctLine.removePrefix("Correct:").trim()
//            cleanCorrect = cleanCorrect.replace(Regex("^[A-D]\\)"), "").replace("*", "").trim().removeSuffix(".")
//
//            correctWord = cleanCorrect
//            findViewById<TextView>(R.id.question_text).text = question
//
//            val buttons = listOf(
//                findViewById<Button>(R.id.btn_choice1),
//                findViewById<Button>(R.id.btn_choice2),
//                findViewById<Button>(R.id.btn_choice3),
//                findViewById<Button>(R.id.btn_choice4)
//            )
//
//            for (i in buttons.indices) {
//                if (i < options.size) {
//                    buttons[i].text = options[i]
//                    buttons[i].setOnClickListener { checkAnswer(options[i]) }
//                }
//            }
//
//            findViewById<View>(R.id.options_container).visibility = View.VISIBLE
//            startTime = System.currentTimeMillis() // Start timing for SRS!
//
//        } catch (e: Exception) {
//            startNewQuestion() // If AI hallucinated the format, skip to next word
//        }
//    }
private fun parseAndDisplayQuiz(rawResult: String) {
    try {
        val lines = rawResult.lines().map { it.trim() }

        // FIX 1: Flexible regex to ignore bolding (**) and handle variations in formatting
        val questionLine = lines.find { it.contains("Question:", ignoreCase = true) }
            ?: throw Exception("Missing Question")
        val question = questionLine.substringAfter("Question:").replace("**", "").trim()

        // FIX 2: Catch A), A., **A)**, etc.
        val options = lines.filter { it.matches(Regex(".*[A-D][\\)\\.].*")) }
            .map { it.substringAfter(")").substringAfter(".").replace("**", "").trim() }

        if (options.isEmpty()) throw Exception("Missing Options")

        // FIX 3: Flexible Correct answer parsing
        val correctLine = lines.find { it.contains("Correct:", ignoreCase = true) }
            ?: throw Exception("Missing Correct Answer")
        var cleanCorrect = correctLine.substringAfter("Correct:").replace("**", "").trim()
        cleanCorrect = cleanCorrect.replace(Regex("^[A-D][\\)\\.]"), "").trim()
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
                buttons[i].visibility = View.VISIBLE
                buttons[i].text = options[i]
                buttons[i].setOnClickListener { checkAnswer(options[i]) }
            } else {
                buttons[i].visibility = View.GONE // Hide extra buttons if AI generates fewer than 4
            }
        }

        findViewById<View>(R.id.options_container).visibility = View.VISIBLE
        startTime = System.currentTimeMillis() // Start timing for SRS!

    } catch (e: Exception) {
        startNewQuestion() // If AI hallucinated the format heavily, skip to the next word safely
    }
}


    // ========================================================================
    // CHECK ANSWER & HAUNT FAILED WORDS
    // ========================================================================
    // ========================================================================
    // CHECK ANSWER & HAUNT FAILED WORDS
    // ========================================================================
    private fun checkAnswer(selected: String) {
        val responseTime = System.currentTimeMillis() - startTime
        totalAttempts++

        val userChoice = selected.replace("*", "").trim().removeSuffix(".")
        val rightAnswer = correctWord.replace("*", "").trim().removeSuffix(".")
        val isCorrect = userChoice.equals(rightAnswer, ignoreCase = true)

        // 1. Audit Trail: Save this answer to our history block
        val currentQuestionText = findViewById<TextView>(R.id.question_text).text.toString()
        val resultItem = hashMapOf(
            "question" to currentQuestionText,
            "targetWord" to rightAnswer,
            "userAnswer" to userChoice,
            "isCorrect" to isCorrect
        )
        sessionResults.add(resultItem)

        // 2. SRS & Haunting Logic
        if (isCorrect) {
            score++
            updateSRSMetadata(true, responseTime)
            com.example.gabai.GabAIUtils.showSnackbar(this, "Correct! (${responseTime / 1000}s)")
        } else {
            updateSRSMetadata(false, responseTime)
            com.example.gabai.GabAIUtils.showSnackbar(this, "Wrong! Answer: $rightAnswer")

            // --- THE HAUNTING FIXED ---
            // We NO LONGER add the word back into the current session queue here.
            // updateSRSMetadata() already penalized this word and set it to reappear in 30 seconds.
            // It will haunt them in their NEXT quiz session instead of dragging this one out!
        }

        // 3. Move to the next word in the queue
        startNewQuestion()
    }

    // ========================================================================
    // SPACED REPETITION MATH
    // ========================================================================
    // ========================================================================
    // SPACED REPETITION MATH
    // ========================================================================
    private fun updateSRSMetadata(isCorrect: Boolean, latency: Long) {
        val docId = currentDocId ?: return
        val userId = auth.currentUser?.uid ?: return
        val newInterval: Int
        val nextReviewDate: Long

        if (!isCorrect) {
            newInterval = 1
            // HAUNTING: See this word again in exactly 30 seconds!
            nextReviewDate = System.currentTimeMillis() + (30 * 1000)
        } else {
            // SPEED BOOST: 2.0x for fast answers, 1.5x for slow answers
            val boost: Double = if (latency < 5000) 2.0 else 1.5
            newInterval = (currentInterval.toDouble() * boost).toInt().coerceAtLeast(1)

            // FASTER ALGORITHM: Base unit is now 4 HOURS instead of 24 HOURS
            // 4L * 60 mins * 60 secs * 1000 ms = 4 Hours
            nextReviewDate = System.currentTimeMillis() + (newInterval * 4L * 60 * 60 * 1000)
        }

        val updates = hashMapOf(
            "interval" to newInterval,
            "nextReview" to nextReviewDate
        )

        db.collection("users").document(userId)
            .collection("history").document(docId)
            .update(updates as Map<String, Any>)
    }

    // ========================================================================
    // FINISH AND SAVE SESSION
    // ========================================================================
    private fun showFinalResults() {
        if (XPManager.canEarnXP(this)) {
            XPManager.addXP(this, 20)
        }

        // Save the entire session to a new 'quiz_history' collection
        val userId = auth.currentUser?.uid
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "timestamp" to System.currentTimeMillis(),
                "finalScore" to score,
                "totalAttempts" to totalAttempts,
                "items" to sessionResults
            )
            db.collection("users").document(userId).collection("quiz_history").add(historyData)
        }

        findViewById<View>(R.id.options_container).visibility = View.GONE
        val resultView = findViewById<View>(R.id.result_view)
        val scoreText = findViewById<TextView>(R.id.final_score_text)

        // --- TURN THE REAL SCORE BACK ON ---
        scoreText.visibility = View.VISIBLE
        scoreText.text = "Session Complete!\nYou scored $score / $totalAttempts"

        val btnRestart = findViewById<Button>(R.id.btn_restart)
        btnRestart.text = "Return to Dashboard"
        btnRestart.setOnClickListener { finish() }

        resultView.visibility = View.VISIBLE
    }
}