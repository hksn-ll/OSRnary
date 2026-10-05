package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar

class QuizActivity : AppCompatActivity() {

    // --- Dynamic Limits (Fetched from Teacher's Settings) ---
    private var maxItemsPerSession = 10
    private var maxSessionsPerDay = 3

    // --- Data Model for Pre-Generated Questions ---
    data class QuizQuestionItem(
        val docId: String = "",
        val interval: Int = 1,
        val word: String,
        val question: String,
        val options: List<String>,
        val correct: String,
        val explanation: String
    )

    // --- Session Trackers ---
    private val quizQueue = mutableListOf<QuizQuestionItem>()
    private var currentActiveItem: QuizQuestionItem? = null
    private val sessionResults = mutableListOf<Map<String, Any>>()

    private var totalQuestionsInSession = 0
    private var currentQuestionIndex = 0

    private var score = 0
    private var totalAttempts = 0
    private var startTime: Long = 0

    // Combo streak tracking
    private var comboStreak = 0
    private var maxComboStreak = 0

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // View references
    private lateinit var quizHeader: TextView
    private lateinit var tvComboStreak: TextView
    private lateinit var tvProgressCounter: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressQuiz: ProgressBar
    private lateinit var questionCard: View
    private lateinit var questionText: TextView
    private lateinit var optionsContainer: View
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var cardExplanation: View
    private lateinit var tvExplanation: TextView
    private lateinit var btnNextQuestion: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_quiz)

        // Fix Status Bar
        val root = findViewById<View>(R.id.quiz_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 16, v.paddingRight, v.paddingBottom)
            insets
        }

        bindViews()
        setupListeners()
        setupBackProtection()

        initializeQuizSession()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentSession()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentSession()
    }

    private fun bindViews() {
        quizHeader = findViewById(R.id.quiz_header)
        tvComboStreak = findViewById(R.id.tv_combo_streak)
        tvProgressCounter = findViewById(R.id.tv_progress_counter)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
        progressQuiz = findViewById(R.id.progress_quiz)
        questionCard = findViewById(R.id.question_card)
        questionText = findViewById(R.id.question_text)
        optionsContainer = findViewById(R.id.options_container)

        optionButtons = listOf(
            findViewById(R.id.btn_choice1),
            findViewById(R.id.btn_choice2),
            findViewById(R.id.btn_choice3),
            findViewById(R.id.btn_choice4)
        )

        cardExplanation = findViewById(R.id.card_explanation)
        tvExplanation = findViewById(R.id.tv_explanation)
        btnNextQuestion = findViewById(R.id.btn_next_question)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            confirmExitQuiz()
        }

        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_quiz_history)?.setOnClickListener {
            startActivity(Intent(this, QuizHistoryActivity::class.java))
            finish()
        }

        btnNextQuestion.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            cardExplanation.visibility = View.GONE
            btnNextQuestion.visibility = View.GONE
            displayNextQuestion()
        }
    }

    private fun setupBackProtection() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExitQuiz()
            }
        })
    }

    private fun confirmExitQuiz() {
        val resultView = findViewById<View>(R.id.result_view)
        if (resultView.visibility == View.VISIBLE || (quizQueue.isEmpty() && currentActiveItem == null)) {
            finish()
            return
        }

        saveCurrentSession()
        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Leave Quiz Session?",
            message = "Your session progress is safely saved. You can leave and continue anytime right where you left off!",
            confirmText = "Leave & Save",
            cancelText = "Keep Playing",
            isDestructive = false,
            badgeIcon = "💾",
            onConfirm = {
                saveCurrentSession()
                finish()
            }
        )
    }

    // ========================================================================
    // PERSISTENCE ENGINE: SAVE, RESTORE, AND CLEAR QUIZ SESSIONS
    // ========================================================================
    private fun saveCurrentSession() {
        val userId = auth.currentUser?.uid ?: return
        val resultView = findViewById<View>(R.id.result_view)
        if (resultView.visibility == View.VISIBLE || (quizQueue.isEmpty() && currentActiveItem == null)) {
            clearSavedSession(userId)
            return
        }

        try {
            val prefs = getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
            val sessionJson = org.json.JSONObject()
            sessionJson.put("userId", userId)
            sessionJson.put("timestamp", System.currentTimeMillis())
            sessionJson.put("totalQuestionsInSession", totalQuestionsInSession)
            sessionJson.put("currentQuestionIndex", currentQuestionIndex)
            sessionJson.put("score", score)
            sessionJson.put("totalAttempts", totalAttempts)
            sessionJson.put("comboStreak", comboStreak)
            sessionJson.put("maxComboStreak", maxComboStreak)

            currentActiveItem?.let { item ->
                val activeObj = org.json.JSONObject().apply {
                    put("docId", item.docId)
                    put("interval", item.interval)
                    put("word", item.word)
                    put("question", item.question)
                    put("correct", item.correct)
                    put("explanation", item.explanation)
                    val optsArr = org.json.JSONArray()
                    item.options.forEach { optsArr.put(it) }
                    put("options", optsArr)
                }
                sessionJson.put("currentActiveItem", activeObj)
            }

            val queueArr = org.json.JSONArray()
            for (item in quizQueue) {
                val qObj = org.json.JSONObject().apply {
                    put("docId", item.docId)
                    put("interval", item.interval)
                    put("word", item.word)
                    put("question", item.question)
                    put("correct", item.correct)
                    put("explanation", item.explanation)
                    val optsArr = org.json.JSONArray()
                    item.options.forEach { optsArr.put(it) }
                    put("options", optsArr)
                }
                queueArr.put(qObj)
            }
            sessionJson.put("quizQueue", queueArr)

            val resultsArr = org.json.JSONArray()
            for (res in sessionResults) {
                val rObj = org.json.JSONObject()
                res.forEach { (k, v) -> rObj.put(k, v) }
                resultsArr.put(rObj)
            }
            sessionJson.put("sessionResults", resultsArr)

            prefs.edit().putString("active_session_$userId", sessionJson.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun restoreSavedSession(userId: String): Boolean {
        try {
            val prefs = getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
            val jsonStr = prefs.getString("active_session_$userId", null) ?: return false
            val sessionJson = org.json.JSONObject(jsonStr)

            val sessionUserId = sessionJson.optString("userId", "")
            if (sessionUserId != userId) return false

            val timestamp = sessionJson.optLong("timestamp", 0L)
            if (System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000L) {
                clearSavedSession(userId)
                return false
            }

            totalQuestionsInSession = sessionJson.optInt("totalQuestionsInSession", 0)
            currentQuestionIndex = sessionJson.optInt("currentQuestionIndex", 0)
            score = sessionJson.optInt("score", 0)
            totalAttempts = sessionJson.optInt("totalAttempts", 0)
            comboStreak = sessionJson.optInt("comboStreak", 0)
            maxComboStreak = sessionJson.optInt("maxComboStreak", 0)

            sessionResults.clear()
            val resArr = sessionJson.optJSONArray("sessionResults")
            if (resArr != null) {
                for (i in 0 until resArr.length()) {
                    val obj = resArr.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = obj.get(key)
                    }
                    sessionResults.add(map)
                }
            }

            quizQueue.clear()
            val queueArr = sessionJson.optJSONArray("quizQueue")
            if (queueArr != null) {
                for (i in 0 until queueArr.length()) {
                    val qObj = queueArr.getJSONObject(i)
                    val optsArr = qObj.getJSONArray("options")
                    val opts = mutableListOf<String>()
                    for (k in 0 until optsArr.length()) {
                        opts.add(optsArr.getString(k))
                    }
                    quizQueue.add(
                        QuizQuestionItem(
                            docId = qObj.optString("docId", ""),
                            interval = qObj.optInt("interval", 1),
                            word = qObj.optString("word", ""),
                            question = qObj.optString("question", ""),
                            options = opts,
                            correct = qObj.optString("correct", ""),
                            explanation = qObj.optString("explanation", "")
                        )
                    )
                }
            }

            val activeObj = sessionJson.optJSONObject("currentActiveItem")
            if (activeObj != null) {
                val optsArr = activeObj.getJSONArray("options")
                val opts = mutableListOf<String>()
                for (k in 0 until optsArr.length()) {
                    opts.add(optsArr.getString(k))
                }
                currentActiveItem = QuizQuestionItem(
                    docId = activeObj.optString("docId", ""),
                    interval = activeObj.optInt("interval", 1),
                    word = activeObj.optString("word", ""),
                    question = activeObj.optString("question", ""),
                    options = opts,
                    correct = activeObj.optString("correct", ""),
                    explanation = activeObj.optString("explanation", "")
                )
            } else if (quizQueue.isNotEmpty()) {
                currentActiveItem = quizQueue.removeAt(0)
            }

            if (currentActiveItem == null && quizQueue.isEmpty()) {
                clearSavedSession(userId)
                return false
            }

            displayActiveRestoredQuestion()
            GabAIUtils.showSnackbar(this, "Resumed saved quiz session (Item $currentQuestionIndex of $totalQuestionsInSession) 🔄")
            return true
        } catch (e: Exception) {
            clearSavedSession(userId)
            return false
        }
    }

    private fun displayActiveRestoredQuestion() {
        val item = currentActiveItem ?: return

        tvProgressCounter.text = "Question $currentQuestionIndex of $totalQuestionsInSession"
        val pct = (currentQuestionIndex * 100) / totalQuestionsInSession.coerceAtLeast(1)
        tvProgressPercent.text = "$pct%"
        progressQuiz.max = totalQuestionsInSession
        progressQuiz.progress = currentQuestionIndex

        if (comboStreak >= 2) {
            tvComboStreak.text = "🔥 ${comboStreak}x Combo"
            tvComboStreak.visibility = View.VISIBLE
        } else {
            tvComboStreak.visibility = View.GONE
        }

        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE
        resetOptionButtonStyles()

        questionText.text = item.question

        for (i in optionButtons.indices) {
            if (i < item.options.size) {
                val opt = item.options[i]
                val letter = ('A' + i)
                optionButtons[i].visibility = View.VISIBLE
                optionButtons[i].isEnabled = true
                optionButtons[i].text = "$letter)  $opt"
                optionButtons[i].setOnClickListener {
                    checkAnswer(opt, optionButtons[i], item)
                }
            } else {
                optionButtons[i].visibility = View.GONE
            }
        }

        optionsContainer.visibility = View.VISIBLE
        startTime = System.currentTimeMillis()
    }

    private fun clearSavedSession(userId: String?) {
        if (userId == null) return
        try {
            getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
                .edit()
                .remove("active_session_$userId")
                .apply()
        } catch (_: Exception) {}
    }

    // ========================================================================
    // STEP 1: FETCH TEACHER'S LIMITS
    // ========================================================================
    private fun initializeQuizSession() {
        val userId = auth.currentUser?.uid ?: return

        if (restoreSavedSession(userId)) {
            return
        }

        questionText.text = "Fetching teacher settings..."
        optionsContainer.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

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
                .addOnFailureListener { showLoadError() }
        }.addOnFailureListener { showLoadError() }
    }

    private fun showLoadError() {
        optionsContainer.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE
        questionText.text = getString(R.string.error_load_settings_failed)
        val resultView = findViewById<View>(R.id.result_view)
        val btnRestart = findViewById<Button>(R.id.btn_restart)
        val scoreText = findViewById<TextView>(R.id.final_score_text)
        scoreText.visibility = View.VISIBLE
        scoreText.text = getString(R.string.error_load_settings_failed)
        btnRestart.text = getString(R.string.action_retry)
        btnRestart.setOnClickListener { finish() }
        resultView.visibility = View.VISIBLE
    }

    // ========================================================================
    // STEP 2: CHECK DAILY LIMITS
    // ========================================================================
    private fun checkDailyLimits(userId: String) {
        questionText.text = "Checking daily limits..."

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        db.collection("users").document(userId).collection("quiz_history")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .get()
            .addOnSuccessListener { historyDocs ->
                val sessionsToday = historyDocs.documents.count { it.getString("quizType") != "material" }

                if (sessionsToday >= maxSessionsPerDay) {
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)

                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Brain Rest Required!\n\nYou've completed your $maxSessionsPerDay daily sessions. Come back tomorrow to let your memory consolidate!"
                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Dashboard"
                } else {
                    loadWordsForSession(userId)
                }
            }
            .addOnFailureListener { showLoadError() }
    }

    // ========================================================================
    // STEP 3: LOAD WORDS & BATCH PRE-GENERATE ALL QUESTIONS
    // ========================================================================
    private fun loadWordsForSession(userId: String) {
        val currentTime = System.currentTimeMillis()
        questionText.text = "Loading your review words..."

        db.collection("users").document(userId).collection("history")
            .whereLessThanOrEqualTo("nextReview", currentTime)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val eligibleDocs = documents.documents.filter { doc ->
                    val w = doc.getString("word")?.trim() ?: ""
                    val wordCount = w.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    wordCount in 1..4
                }
                val readyCount = eligibleDocs.size

                if (readyCount < maxItemsPerSession) {
                    val needed = maxItemsPerSession - readyCount
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)

                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Session Locked!\n\n" +
                            "Your teacher requires a fixed $maxItemsPerSession-item quiz.\n" +
                            "You only have $readyCount word(s) ready for review right now.\n\n" +
                            "You need $needed more word(s) to unlock this session. Keep reading and saving words!"

                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Dashboard"
                } else {
                    val targetDocs = eligibleDocs.take(maxItemsPerSession)
                    batchGenerateQuizQuestions(targetDocs, userId)
                }
            }
            .addOnFailureListener { e ->
                questionText.text = "Failed to load session: ${e.message}"
            }
    }

    // ========================================================================
    // BATCH AI PRE-GENERATION (Single Request for All Session Words)
    // ========================================================================
    private fun batchGenerateQuizQuestions(docs: List<DocumentSnapshot>, userId: String) {
        questionText.text = "AI is preparing your challenge session..."
        optionsContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val itemsSpec = StringBuilder()
                docs.forEachIndexed { i, doc ->
                    val w = doc.getString("word")?.trim() ?: ""
                    val d = doc.getString("explanation")?.trim() ?: ""
                    val c = doc.getString("originalContext")?.trim() ?: ""
                    itemsSpec.append("${i + 1}. Word: \"$w\"\n   Definition: \"$d\"\n   Context: \"$c\"\n\n")
                }

                val prompt = """
                    You are an expert pedagogical assessment designer creating high-school-level vocabulary challenge questions for Grade 10 students.
                    Generate exactly ${docs.size} UNAMBIGUOUS multiple-choice questions, one for each target word provided below:

                    $itemsSpec

                    CRITICAL UNAMBIGUITY & CLUE-LOCKING DIRECTIVES:
                    1. CONTEXT-CLUE CONSTRAINT (NO AMBIGUITY):
                       The question sentence MUST contain a definitive context clue — such as an explicit contrast (e.g., "unlike...", "instead of...", "whereas..."), an explicit cause-and-effect relationship (e.g., "because...", "consequently..."), or an explicit functional definition — that makes the target word the SINGLE, UNIQUELY LOGICAL answer.
                       NEVER generate generic sentences like "She had great _______" or "The results were _______" where multiple choices could fit. The surrounding sentence must strictly lock in the target word.
                       Replace the target word in the sentence with exactly 7 underscores: "_______".

                    2. DISTINCT & PLAUSIBLE DISTRACTORS:
                       Provide exactly 4 options: exactly ONE is the correct target word (in its exact grammatical form), and THREE are distractors.
                       Distractors MUST share the exact same part of speech and grammatical form (tense, plurality, affixes) as the correct answer.
                       However, their meanings must contextually contradict the sentence clues, making them unambiguously wrong to a student who understands the words.

                    3. CLEAR EDUCATIONAL EXPLANATION:
                       In "explanation", explicitly point out the clue in the sentence and explain why the correct word is the only option that logically satisfies the context.

                    CRITICAL: Output ONLY a valid JSON array of objects. No markdown backticks outside the JSON, no preamble, no commentary.
                    JSON Format:
                    [
                      {
                        "itemIndex": 1,
                        "question": "Sentence with contextual clue and _______.",
                        "options": ["choice1", "choice2", "choice3", "choice4"],
                        "correct": "choice1",
                        "explanation": "Clear educational insight referencing the sentence context clue."
                      }
                    ]
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    generativeModel.generateContent(prompt)
                }

                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) {
                    jsonStr = jsonStr.substring(startIndex, endIndex + 1)
                }

                val jsonArray = JSONArray(jsonStr)
                quizQueue.clear()

                for (i in 0 until jsonArray.length().coerceAtMost(docs.size)) {
                    val obj = jsonArray.getJSONObject(i)
                    val q = obj.getString("question").replace("**", "").trim()
                    val optsArr = obj.getJSONArray("options")
                    val optsList = mutableListOf<String>()
                    for (k in 0 until optsArr.length()) {
                        optsList.add(optsArr.getString(k).replace("**", "").trim())
                    }
                    val correct = obj.getString("correct").replace("**", "").trim()
                    val explanation = obj.optString("explanation", "Review the key context and grammatical form of the target word.").trim()

                    // Ensure target word option is present and shuffle choices
                    val finalOpts = if (optsList.contains(correct)) optsList else (optsList.take(3) + correct)
                    val shuffledOpts = finalOpts.shuffled()

                    quizQueue.add(
                        QuizQuestionItem(
                            docId = docs[i].id,
                            interval = docs[i].getLong("interval")?.toInt() ?: 1,
                            word = docs[i].getString("word") ?: correct,
                            question = q,
                            options = shuffledOpts,
                            correct = correct,
                            explanation = explanation
                        )
                    )
                }

                if (quizQueue.isEmpty()) throw Exception("AI did not produce valid questions.")

                totalQuestionsInSession = quizQueue.size
                currentQuestionIndex = 0
                saveCurrentSession()
                displayNextQuestion()

            } catch (e: Exception) {
                val resultView = findViewById<View>(R.id.result_view)
                val btnRestart = findViewById<Button>(R.id.btn_restart)
                val scoreText = findViewById<TextView>(R.id.final_score_text)

                scoreText.visibility = View.VISIBLE
                scoreText.text = "Could not generate AI questions (${e.localizedMessage ?: "network issue"}).\nPlease check your internet connection."
                btnRestart.text = "Retry Session"
                btnRestart.setOnClickListener {
                    resultView.visibility = View.GONE
                    batchGenerateQuizQuestions(docs, userId)
                }
                resultView.visibility = View.VISIBLE
            }
        }
    }

    // ========================================================================
    // DISPLAY NEXT QUESTION FROM PRE-GENERATED QUEUE
    // ========================================================================
    private fun displayNextQuestion() {
        if (quizQueue.isEmpty()) {
            showFinalResults()
            return
        }

        currentActiveItem = quizQueue.removeAt(0)
        currentQuestionIndex++

        val item = currentActiveItem ?: return

        // Update Progress UI
        tvProgressCounter.text = "Question $currentQuestionIndex of $totalQuestionsInSession"
        val pct = (currentQuestionIndex * 100) / totalQuestionsInSession.coerceAtLeast(1)
        tvProgressPercent.text = "$pct%"
        progressQuiz.max = totalQuestionsInSession
        progressQuiz.progress = currentQuestionIndex

        // Reset state & buttons
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

        resetOptionButtonStyles()

        animateCardFlip {
            questionText.text = item.question

            for (i in optionButtons.indices) {
                if (i < item.options.size) {
                    val opt = item.options[i]
                    val letter = ('A' + i)
                    optionButtons[i].visibility = View.VISIBLE
                    optionButtons[i].isEnabled = true
                    optionButtons[i].text = "$letter)  $opt"
                    optionButtons[i].setOnClickListener {
                        checkAnswer(opt, optionButtons[i], item)
                    }
                } else {
                    optionButtons[i].visibility = View.GONE
                }
            }

            optionsContainer.visibility = View.VISIBLE
            startTime = System.currentTimeMillis()
            saveCurrentSession()
        }
    }

    private fun resetOptionButtonStyles() {
        for (btn in optionButtons) {
            btn.isEnabled = true
            btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EDF2F7"))
            btn.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
            btn.setTextColor(Color.parseColor("#161D1F"))
        }
    }

    private fun animateCardFlip(onHalfway: () -> Unit) {
        questionCard.cameraDistance = 8000f * resources.displayMetrics.density
        questionCard.animate()
            .rotationY(90f)
            .setDuration(140)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                onHalfway()
                questionCard.rotationY = -90f
                questionCard.animate()
                    .rotationY(0f)
                    .setDuration(140)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    // ========================================================================
    // TWO-STEP ANSWER FLOW & EDUCATIONAL INSIGHT PILL
    // ========================================================================
    private fun checkAnswer(selectedOption: String, selectedBtn: MaterialButton, item: QuizQuestionItem) {
        val responseTime = System.currentTimeMillis() - startTime
        totalAttempts++

        // Freeze all buttons immediately to prevent duplicate presses
        for (btn in optionButtons) {
            btn.isEnabled = false
        }

        val cleanSelected = selectedOption.replace("*", "").trim().removeSuffix(".")
        val cleanCorrect = item.correct.replace("*", "").trim().removeSuffix(".")
        val isCorrect = cleanSelected.equals(cleanCorrect, ignoreCase = true)

        // 1. Audit Trail: Save with educational explanation
        val resultItem = hashMapOf(
            "question" to item.question,
            "targetWord" to cleanCorrect,
            "userAnswer" to cleanSelected,
            "isCorrect" to isCorrect,
            "explanation" to item.explanation
        )
        sessionResults.add(resultItem)

        // 2. High-Contrast Two-Step Button Feedback
        if (isCorrect) {
            score++
            comboStreak++
            if (comboStreak > maxComboStreak) maxComboStreak = comboStreak
            updateSRSMetadata(item.docId, item.interval, true, responseTime)

            // Mint green success styling
            selectedBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
            selectedBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
            selectedBtn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            selectedBtn.setTextColor(Color.parseColor("#065F46"))

            if (comboStreak >= 2) {
                tvComboStreak.text = "🔥 ${comboStreak}x Combo"
                tvComboStreak.visibility = View.VISIBLE
                tvComboStreak.animate()?.scaleX(1.25f)?.scaleY(1.25f)?.setDuration(100)?.withEndAction {
                    tvComboStreak.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(100)?.start()
                }?.start()
                GabAIUtils.performHaptic(tvComboStreak, HapticFeedbackConstants.CONFIRM)
            } else {
                tvComboStreak.visibility = View.GONE
                GabAIUtils.performHaptic(selectedBtn, HapticFeedbackConstants.CLOCK_TICK)
            }
        } else {
            comboStreak = 0
            tvComboStreak.visibility = View.GONE
            updateSRSMetadata(item.docId, item.interval, false, responseTime)
            GabAIUtils.performHaptic(selectedBtn, HapticFeedbackConstants.REJECT)

            // Rose red error styling on selected button
            selectedBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            selectedBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            selectedBtn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            selectedBtn.setTextColor(Color.parseColor("#991B1B"))

            // Highlight the correct answer in mint so the user learns
            for ((idx, opt) in item.options.withIndex()) {
                val cleanOpt = opt.replace("*", "").trim().removeSuffix(".")
                if (cleanOpt.equals(cleanCorrect, ignoreCase = true) && idx < optionButtons.size) {
                    val correctBtn = optionButtons[idx]
                    correctBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                    correctBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
                    correctBtn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
                    correctBtn.setTextColor(Color.parseColor("#065F46"))
                }
            }
        }

        // Save progress immediately after evaluating answer
        saveCurrentSession()

        // 3. Reveal Educational Insight Pill
        tvExplanation.text = item.explanation
        cardExplanation.alpha = 0f
        cardExplanation.visibility = View.VISIBLE
        cardExplanation.animate().alpha(1f).setDuration(200).start()

        // 4. Reveal "Next Question ➔" button
        if (quizQueue.isEmpty()) {
            btnNextQuestion.text = "Complete Quiz ✓"
            btnNextQuestion.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00B894"))
        } else {
            btnNextQuestion.text = "Next Question ➔"
            btnNextQuestion.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6C5CE7"))
        }
        btnNextQuestion.alpha = 0f
        btnNextQuestion.visibility = View.VISIBLE
        btnNextQuestion.animate().alpha(1f).setDuration(200).start()
    }

    // ========================================================================
    // SPACED REPETITION MATH
    // ========================================================================
    private fun updateSRSMetadata(docId: String, currentInterval: Int, isCorrect: Boolean, latency: Long) {
        val userId = auth.currentUser?.uid ?: return
        if (docId.isBlank()) return
        val newInterval: Int
        val nextReviewDate: Long

        if (!isCorrect) {
            newInterval = 1
            // HAUNTING: Review this word again in 30 seconds
            nextReviewDate = System.currentTimeMillis() + (30 * 1000)
        } else {
            // SPEED BOOST: 2.0x for fast responses, 1.5x for slow
            val boost: Double = if (latency < 5000) 2.0 else 1.5
            newInterval = (currentInterval.toDouble() * boost).toInt().coerceAtLeast(1)
            // Base unit: 4 Hours
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
    // FINISH AND SAVE SESSION WITH EDUCATIONAL INSIGHTS
    // ========================================================================
    private fun showFinalResults() {
        clearSavedSession(auth.currentUser?.uid)
        if (XPManager.canEarnXP(this)) {
            XPManager.addXP(this, 20)
        }
        QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)

        // Save session with explanations to quiz_history
        val userId = auth.currentUser?.uid
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "quizType" to "recall",
                "timestamp" to System.currentTimeMillis(),
                "finalScore" to score,
                "totalAttempts" to totalAttempts,
                "items" to sessionResults
            )
            db.collection("users").document(userId).collection("quiz_history").add(historyData)
        }

        optionsContainer.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

        val resultView = findViewById<View>(R.id.result_view)
        val scoreText = findViewById<TextView>(R.id.final_score_text)

        scoreText.visibility = View.VISIBLE
        val streakMsg = if (maxComboStreak >= 2) "\n🔥 Best Streak: ${maxComboStreak}x Combo!" else ""
        scoreText.text = "Session Complete!\nYou scored $score / $totalAttempts$streakMsg"

        val btnRestart = findViewById<Button>(R.id.btn_restart)
        btnRestart.text = "Return to Dashboard"
        btnRestart.setOnClickListener { finish() }

        resultView.visibility = View.VISIBLE
    }
}