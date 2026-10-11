package com.example.gabai

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class MaterialQuizActivity : AppCompatActivity() {

    private var currentQuizIndex = 0
    private var currentScore = 0
    private var totalAttempts = 0
    private var quizList = mutableListOf<GeneratedQuestion>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val sessionResults = mutableListOf<Map<String, Any>>()
    private var materialId = ""

    private val prefName: String
        get() = "material_quiz_session_${materialId}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_quiz)

        val root = findViewById<View>(R.id.quiz_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()
        val materialTitle = intent.getStringExtra("MATERIAL_TITLE") ?: "Reading Quiz"

        findViewById<TextView>(R.id.quiz_header).text = materialTitle
        findViewById<TextView>(R.id.question_text).text = "Checking your records..."
        findViewById<View>(R.id.options_container).visibility = View.GONE

        // Hide vocabulary-quest-specific UI views that do not belong to reading comprehension
        findViewById<View>(R.id.layout_lifelines)?.visibility = View.GONE
        findViewById<View>(R.id.layout_segmented_progress)?.visibility = View.GONE
        findViewById<View>(R.id.tv_fuel_pods)?.visibility = View.GONE
        findViewById<View>(R.id.tv_combo_streak)?.visibility = View.GONE
        findViewById<View>(R.id.layout_impostor_container)?.visibility = View.GONE
        findViewById<View>(R.id.layout_rapid_match)?.visibility = View.GONE
        findViewById<View>(R.id.layout_cloze_slot)?.visibility = View.GONE
        findViewById<View>(R.id.card_explanation)?.visibility = View.GONE
        findViewById<View>(R.id.btn_next_question)?.visibility = View.GONE
        findViewById<View>(R.id.progress_quiz)?.visibility = View.VISIBLE

        // Header back button with safe exit confirmation
        findViewById<View>(R.id.btn_back)?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) {
                if (quizList.isNotEmpty() && currentQuizIndex < quizList.size) {
                    saveCurrentSession()
                    GabAIDialogs.showNoticeDialog(
                        context = this,
                        title = "Progress Saved",
                        message = "Your quiz progress has been safely preserved. You can resume anytime!",
                        buttonText = "Exit Quiz",
                        badgeIcon = "💾",
                        onDismiss = { finish() }
                    )
                } else {
                    finish()
                }
            }
        }

        findViewById<Button>(R.id.btn_quiz_history).setOnClickListener {
            startActivity(Intent(this, QuizHistoryActivity::class.java))
            finish()
        }

        checkIfAlreadyPassed()
    }

    override fun onPause() {
        super.onPause()
        if (quizList.isNotEmpty() && currentQuizIndex < quizList.size) {
            saveCurrentSession()
        }
    }

    override fun onStop() {
        super.onStop()
        if (quizList.isNotEmpty() && currentQuizIndex < quizList.size) {
            saveCurrentSession()
        }
    }

    private fun saveCurrentSession() {
        if (materialId.isEmpty() || quizList.isEmpty()) return
        val prefs = getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (q in quizList) {
            val obj = JSONObject()
            obj.put("q", q.question)
            val optsArray = JSONArray()
            q.options.forEach { optsArray.put(it) }
            obj.put("opts", optsArray)
            obj.put("ans", q.correctIndex)
            jsonArray.put(obj)
        }

        prefs.edit()
            .putBoolean("has_saved_session", true)
            .putString("saved_quiz_data", jsonArray.toString())
            .putInt("saved_current_index", currentQuizIndex)
            .putInt("saved_score", currentScore)
            .putInt("saved_attempts", totalAttempts)
            .apply()
    }

    private fun restoreSavedSession(): Boolean {
        if (materialId.isEmpty()) return false
        val prefs = getSharedPreferences(prefName, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("has_saved_session", false)) return false

        val dataStr = prefs.getString("saved_quiz_data", null) ?: return false
        try {
            val jsonArray = JSONArray(dataStr)
            val restored = mutableListOf<GeneratedQuestion>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.getString("q")
                val optsArray = obj.getJSONArray("opts")
                val opts = mutableListOf<String>()
                for (j in 0 until optsArray.length()) {
                    opts.add(optsArray.getString(j))
                }
                val ans = obj.getInt("ans")
                restored.add(GeneratedQuestion(q, opts, ans))
            }

            if (restored.isNotEmpty()) {
                quizList = restored
                currentQuizIndex = prefs.getInt("saved_current_index", 0)
                currentScore = prefs.getInt("saved_score", 0)
                totalAttempts = prefs.getInt("saved_attempts", 0)

                findViewById<View>(R.id.options_container).visibility = View.VISIBLE
                findViewById<Button>(R.id.btn_restart).setOnClickListener { finish() }
                showCurrentQuestion()
                GabAIUtils.showSnackbar(this, "Resumed saved quiz session! 📖")
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun clearSavedSession() {
        getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun checkIfAlreadyPassed() {
        val userId = auth.currentUser?.uid ?: return finish()

        db.collection("users").document(userId).collection("quiz_history")
            .whereEqualTo("materialId", materialId)
            .get()
            .addOnSuccessListener { docs ->
                val passedDoc = docs.documents.find { it.getBoolean("isPassed") == true }

                if (passedDoc != null) {
                    val score = passedDoc.getLong("finalScore")?.toInt() ?: 0
                    val attempts = passedDoc.getLong("totalAttempts")?.toInt() ?: 0
                    showAlreadyPassedScreen(score, attempts)
                } else {
                    if (!restoreSavedSession()) {
                        loadQuizFromFirestore()
                    }
                }
            }
            .addOnFailureListener {
                if (!restoreSavedSession()) {
                    loadQuizFromFirestore()
                }
            }
    }

    private fun showAlreadyPassedScreen(score: Int, attempts: Int) {
        clearSavedSession()
        findViewById<View>(R.id.options_container).visibility = View.GONE
        val resultView = findViewById<View>(R.id.result_view)
        resultView.visibility = View.VISIBLE

        findViewById<TextView>(R.id.final_score_text).apply {
            visibility = View.VISIBLE
            text = "✨ Quiz Already Mastered! ✨\nPrevious Score: $score / $attempts\nYou have satisfied this lesson's requirements."
            setTextColor(Color.parseColor("#059669"))
        }

        findViewById<Button>(R.id.btn_restart).apply {
            text = "Back to Home"
            setOnClickListener {
                navigateBackToHome()
            }
        }
    }

    private fun navigateBackToHome() {
        try {
            val intentHide = Intent(this, FloatingControlService::class.java).apply {
                action = "ACTION_HIDE"
            }
            startService(intentHide)
        } catch (_: Exception) {}
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun loadQuizFromFirestore() {
        findViewById<TextView>(R.id.question_text).text = "Loading reading quiz..."
        db.collection("library_materials").document(materialId).get()
            .addOnSuccessListener { doc ->
                val jsonStr = doc.getString("quiz_pool_json") ?: "[]"
                val maxItems = doc.getLong("quiz_max_items")?.toInt() ?: 5

                try {
                    val jsonArray = JSONArray(jsonStr)
                    val allQuestions = mutableListOf<GeneratedQuestion>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val q = obj.optString("q", "").trim()
                        if (q.isBlank()) continue
                        val opts = obj.optJSONArray("options") ?: JSONArray()
                        val optList = mutableListOf<String>()
                        for (j in 0 until opts.length()) {
                            val optText = opts.optString(j, "").trim()
                            if (optText.isNotBlank()) {
                                optList.add(optText)
                            }
                        }
                        if (optList.size < 2) continue

                        val ans = obj.optInt("ans", 0).coerceIn(0, optList.size - 1)
                        val correctText = optList[ans]
                        val shuffledOpts = optList.shuffled()
                        val newAns = shuffledOpts.indexOf(correctText).coerceAtLeast(0)

                        allQuestions.add(GeneratedQuestion(q, shuffledOpts, newAns))
                    }

                    quizList = allQuestions.shuffled().take(maxItems).toMutableList()

                    if (quizList.isNotEmpty()) {
                        findViewById<View>(R.id.options_container).visibility = View.VISIBLE
                        findViewById<Button>(R.id.btn_restart).setOnClickListener { finish() }
                        showCurrentQuestion()
                    } else throw Exception("Empty Quiz Pool")

                } catch (e: Exception) {
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)

                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Error loading quiz data (${e.localizedMessage ?: "network issue"})."
                    btnRestart.text = "Retry"
                    btnRestart.setOnClickListener {
                        resultView.visibility = View.GONE
                        loadQuizFromFirestore()
                    }
                    resultView.visibility = View.VISIBLE
                }
            }
    }

    private fun showCurrentQuestion() {
        if (currentQuizIndex >= quizList.size) {
            showFinalResults()
            return
        }

        val qData = quizList[currentQuizIndex]
        findViewById<TextView>(R.id.question_text).text = qData.question
        findViewById<TextView>(R.id.tv_question_badge)?.text = "READING COMPREHENSION 📖"
        findViewById<TextView>(R.id.tv_question_step_hint)?.text = "QUESTION ${currentQuizIndex + 1}"
        findViewById<TextView>(R.id.tv_progress_counter)?.text = "Question ${currentQuizIndex + 1} of ${quizList.size}"
        val pct = ((currentQuizIndex + 1) * 100) / quizList.size.coerceAtLeast(1)
        findViewById<TextView>(R.id.tv_progress_percent)?.text = "$pct%"
        findViewById<android.widget.ProgressBar>(R.id.progress_quiz)?.apply {
            max = quizList.size
            progress = currentQuizIndex + 1
        }

        findViewById<View>(R.id.card_explanation)?.visibility = View.GONE
        findViewById<View>(R.id.btn_next_question)?.visibility = View.GONE

        val buttons = listOf(
            findViewById<MaterialButton>(R.id.btn_choice1),
            findViewById<MaterialButton>(R.id.btn_choice2),
            findViewById<MaterialButton>(R.id.btn_choice3),
            findViewById<MaterialButton>(R.id.btn_choice4)
        )

        for (i in buttons.indices) {
            val btn = buttons[i]
            if (i < qData.options.size) {
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
                val letter = ('A' + i)
                btn.text = "$letter)  ${qData.options[i]}"
                btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EDF2F7"))
                btn.setTextColor(Color.parseColor("#161D1F"))
                btn.setOnClickListener {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    checkAnswer(i, qData)
                }
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    private fun checkAnswer(selectedIndex: Int, qData: GeneratedQuestion) {
        val buttons = listOf(
            findViewById<MaterialButton>(R.id.btn_choice1),
            findViewById<MaterialButton>(R.id.btn_choice2),
            findViewById<MaterialButton>(R.id.btn_choice3),
            findViewById<MaterialButton>(R.id.btn_choice4)
        )

        // Lock all buttons immediately to prevent multi-tapping
        buttons.forEach { it.isEnabled = false }

        totalAttempts++
        val isCorrect = (selectedIndex == qData.correctIndex)
        val selectedText = qData.options.getOrElse(selectedIndex) { "" }
        val correctText = qData.options.getOrElse(qData.correctIndex) { "" }

        if (isCorrect) {
            currentScore++
            GabAIUtils.performHaptic(buttons.getOrNull(selectedIndex) ?: findViewById(R.id.quiz_root), HapticFeedbackConstants.CONFIRM)
            if (selectedIndex in buttons.indices) {
                val chosenBtn = buttons[selectedIndex]
                chosenBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                chosenBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
                chosenBtn.setTextColor(Color.parseColor("#065F46"))
            }
        } else {
            GabAIUtils.performHaptic(buttons.getOrNull(selectedIndex) ?: findViewById(R.id.quiz_root), HapticFeedbackConstants.REJECT)
            if (selectedIndex in buttons.indices) {
                val chosenBtn = buttons[selectedIndex]
                chosenBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                chosenBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                chosenBtn.setTextColor(Color.parseColor("#991B1B"))
            }
            if (qData.correctIndex in buttons.indices) {
                val correctBtn = buttons[qData.correctIndex]
                correctBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                correctBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
                correctBtn.setTextColor(Color.parseColor("#065F46"))
            }
        }

        sessionResults.add(hashMapOf(
            "question" to qData.question,
            "targetWord" to correctText,
            "correctAnswer" to correctText,
            "userAnswer" to selectedText,
            "isCorrect" to isCorrect,
            "options" to qData.options,
            "mechanic" to "READING_COMPREHENSION",
            "explanation" to "Correct answer: $correctText"
        ))

        saveCurrentSession()

        // Reveal Educational Explanation and Next Question button
        val cardExplanation = findViewById<View>(R.id.card_explanation)
        val tvExplanation = findViewById<TextView>(R.id.tv_explanation)
        val btnNextQuestion = findViewById<MaterialButton>(R.id.btn_next_question)

        tvExplanation?.text = if (isCorrect) {
            "Excellent comprehension! \"$correctText\" accurately reflects the reading material."
        } else {
            "Review: The correct answer is \"$correctText\"."
        }
        cardExplanation?.visibility = View.VISIBLE

        btnNextQuestion?.apply {
            visibility = View.VISIBLE
            text = if (currentQuizIndex + 1 >= quizList.size) "Complete Reading Quiz 🏆" else "Next Question ➔"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (currentQuizIndex + 1 >= quizList.size) "#00B894" else "#6C5CE7"))
            setOnClickListener {
                GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                currentQuizIndex++
                saveCurrentSession()
                showCurrentQuestion()
            }
        }
    }

    private fun showFinalResults() {
        clearSavedSession()
        val isPassed = currentScore >= (quizList.size / 2.0)
        val xpEarned = if (isPassed) 30 else 0

        if (isPassed) {
            if (XPManager.canEarnXP(this)) {
                XPManager.addXP(this, xpEarned)
            }
            QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)
        }

        val userId = auth.currentUser?.uid
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "quizType" to "material",
                "materialId" to materialId,
                "isPassed" to isPassed,
                "timestamp" to System.currentTimeMillis(),
                "finalScore" to currentScore,
                "totalAttempts" to totalAttempts,
                "xpEarned" to xpEarned,
                "items" to sessionResults
            )
            db.collection("users").document(userId).collection("quiz_history").add(historyData)
        }

        findViewById<View>(R.id.options_container).visibility = View.GONE
        findViewById<View>(R.id.card_explanation)?.visibility = View.GONE
        findViewById<View>(R.id.btn_next_question)?.visibility = View.GONE

        val resultView = findViewById<View>(R.id.result_view)
        val layoutXpSummary = findViewById<View>(R.id.layout_xp_summary)
        val tvXpTotal = findViewById<TextView>(R.id.tv_xp_total)
        val tvXpDetails = findViewById<TextView>(R.id.tv_xp_details)

        if (isPassed) {
            layoutXpSummary?.visibility = View.VISIBLE
            tvXpTotal?.text = "+$xpEarned XP"
            tvXpDetails?.text = "Reading Comprehension Quest: +$xpEarned XP"
        } else {
            layoutXpSummary?.visibility = View.GONE
        }

        findViewById<TextView>(R.id.final_score_text).apply {
            visibility = View.VISIBLE
            if (isPassed) {
                text = "Quiz Passed! 🎉\nYou scored $currentScore / $totalAttempts"
                setTextColor(Color.parseColor("#059669"))
            } else {
                text = "Quiz Failed. ❌\nYou scored $currentScore / $totalAttempts.\nYou must score at least 50% to pass."
                setTextColor(Color.parseColor("#EF4444"))
            }
        }

        findViewById<Button>(R.id.btn_restart).apply {
            text = if (isPassed) "Back to Home" else "Retry Quiz"
            setOnClickListener {
                if (isPassed) {
                    navigateBackToHome()
                } else {
                    val retryIntent = intent
                    finish()
                    startActivity(retryIntent)
                }
            }
        }

        resultView.visibility = View.VISIBLE
    }
}