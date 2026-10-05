package com.example.gabai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        findViewById<Button>(R.id.btn_exit).visibility = View.GONE
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
            text = "Return to Library"
            setOnClickListener { finish() }
        }
    }

    private fun loadQuizFromFirestore() {
        findViewById<TextView>(R.id.question_text).text = "Loading quiz pool..."
        db.collection("library_materials").document(materialId).get()
            .addOnSuccessListener { doc ->
                val jsonStr = doc.getString("quiz_pool_json") ?: "[]"
                val maxItems = doc.getLong("quiz_max_items")?.toInt() ?: 5

                try {
                    val jsonArray = JSONArray(jsonStr)
                    val allQuestions = mutableListOf<GeneratedQuestion>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val q = obj.getString("q")
                        val opts = obj.getJSONArray("options")
                        val optList = listOf(opts.getString(0), opts.getString(1), opts.getString(2), opts.getString(3))
                        val ans = obj.getInt("ans")

                        val correctText = optList.getOrElse(ans) { optList[0] }
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
        findViewById<TextView>(R.id.tv_bento_quiz_type)?.text = "READING COMPREHENSION"
        findViewById<TextView>(R.id.tv_bento_quiz_question_num)?.text = "QUESTION ${currentQuizIndex + 1}"
        findViewById<TextView>(R.id.tv_progress_counter)?.text = "Question ${currentQuizIndex + 1} of ${quizList.size}"
        val pct = ((currentQuizIndex + 1) * 100) / quizList.size.coerceAtLeast(1)
        findViewById<TextView>(R.id.tv_progress_percent)?.text = "$pct%"
        findViewById<android.widget.ProgressBar>(R.id.progress_quiz)?.apply {
            max = quizList.size
            progress = currentQuizIndex + 1
        }

        val buttons = listOf(
            findViewById<Button>(R.id.btn_choice1),
            findViewById<Button>(R.id.btn_choice2),
            findViewById<Button>(R.id.btn_choice3),
            findViewById<Button>(R.id.btn_choice4)
        )

        for (i in buttons.indices) {
            val letter = ('A' + i)
            buttons[i].text = "$letter)  ${qData.options[i]}"
            buttons[i].setOnClickListener { checkAnswer(i, qData) }
        }
    }

    private fun checkAnswer(selectedIndex: Int, qData: GeneratedQuestion) {
        totalAttempts++
        val isCorrect = (selectedIndex == qData.correctIndex)

        if (isCorrect) {
            currentScore++
            GabAIUtils.showSnackbar(this, "Correct! ✅")
        } else {
            GabAIUtils.showSnackbar(this, "Wrong! Answer: ${qData.options[qData.correctIndex]} ❌")
        }

        sessionResults.add(hashMapOf(
            "question" to qData.question,
            "targetWord" to qData.options[qData.correctIndex],
            "userAnswer" to qData.options[selectedIndex],
            "isCorrect" to isCorrect
        ))

        currentQuizIndex++
        saveCurrentSession()
        showCurrentQuestion()
    }

    private fun showFinalResults() {
        clearSavedSession()
        if (XPManager.canEarnXP(this)) {
            XPManager.addXP(this, 30)
        }
        QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)

        val isPassed = currentScore >= (quizList.size / 2.0)

        val userId = auth.currentUser?.uid
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "quizType" to "material",
                "materialId" to materialId,
                "isPassed" to isPassed,
                "timestamp" to System.currentTimeMillis(),
                "finalScore" to currentScore,
                "totalAttempts" to totalAttempts,
                "items" to sessionResults
            )
            db.collection("users").document(userId).collection("quiz_history").add(historyData)
        }

        findViewById<View>(R.id.options_container).visibility = View.GONE
        val resultView = findViewById<View>(R.id.result_view)

        findViewById<TextView>(R.id.final_score_text).apply {
            visibility = View.VISIBLE
            if (isPassed) {
                text = "Quiz Passed! 🎉\nYou scored $currentScore / $totalAttempts"
                setTextColor(Color.parseColor("#059669"))
            } else {
                text = "Quiz Failed. ❌\nYou scored $currentScore / $totalAttempts.\nYou must try again."
                setTextColor(Color.parseColor("#EF4444"))
            }
        }

        findViewById<Button>(R.id.btn_restart).apply {
            text = if (isPassed) "Return to Library" else "Retry Quiz"
            setOnClickListener {
                if (isPassed) {
                    finish()
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