package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray

class MaterialQuizActivity : AppCompatActivity() {

    private var currentQuizIndex = 0
    private var currentScore = 0
    private var totalAttempts = 0
    private var quizList = mutableListOf<GeneratedQuestion>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val sessionResults = mutableListOf<Map<String, Any>>()
    private var materialId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()
        val materialTitle = intent.getStringExtra("MATERIAL_TITLE") ?: "Reading Quiz"

        findViewById<TextView>(R.id.quiz_header).text = materialTitle
        findViewById<TextView>(R.id.question_text).text = "Checking your records..."
        findViewById<View>(R.id.options_container).visibility = View.GONE

        // 🟢 BUG FIX: Remove the unneeded extra exit button
        findViewById<Button>(R.id.btn_exit).visibility = View.GONE

        // 🟢 BUG FIX: Make the History Button open the history screen!
        findViewById<Button>(R.id.btn_quiz_history).setOnClickListener {
            startActivity(Intent(this, QuizHistoryActivity::class.java))
            finish()
        }

        checkIfAlreadyPassed()
    }

    // 🟢 NEW: PASS/FAIL LOCKOUT CHECK 🟢
    private fun checkIfAlreadyPassed() {
        val userId = auth.currentUser?.uid ?: return finish()

        // Fetch their history for this specific material
        db.collection("users").document(userId).collection("quiz_history")
            .whereEqualTo("materialId", materialId)
            .get()
            .addOnSuccessListener { docs ->
                // Check in memory to avoid needing to build a complex Firestore index
                val passedDoc = docs.documents.find { it.getBoolean("isPassed") == true }

                if (passedDoc != null) {
                    // THEY ALREADY PASSED IT! Lock them out.
                    val score = passedDoc.getLong("finalScore")?.toInt() ?: 0
                    val attempts = passedDoc.getLong("totalAttempts")?.toInt() ?: 0
                    showAlreadyPassedScreen(score, attempts)
                } else {
                    // Haven't passed yet (or haven't taken it). Load the quiz!
                    loadQuizFromFirestore()
                }
            }
            .addOnFailureListener {
                loadQuizFromFirestore() // Fallback
            }
    }

    private fun showAlreadyPassedScreen(score: Int, attempts: Int) {
        findViewById<View>(R.id.options_container).visibility = View.GONE
        val resultView = findViewById<View>(R.id.result_view)
        resultView.visibility = View.VISIBLE

        findViewById<TextView>(R.id.final_score_text).apply {
            visibility = View.VISIBLE
            text = "You already passed this quiz!\nPrevious Score: $score / $attempts"
            setTextColor(android.graphics.Color.parseColor("#00B894")) // Success Green
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
                        allQuestions.add(GeneratedQuestion(q, optList, ans))
                    }

                    quizList = allQuestions.shuffled().take(maxItems).toMutableList()

                    if (quizList.isNotEmpty()) {
                        findViewById<View>(R.id.options_container).visibility = View.VISIBLE
                        findViewById<Button>(R.id.btn_restart).setOnClickListener { finish() }
                        showCurrentQuestion()
                    } else throw java.lang.Exception("Empty Quiz Pool")

                } catch (e: Exception) {
                    findViewById<TextView>(R.id.question_text).text = "Error loading quiz data."
                }
            }
    }

    private fun showCurrentQuestion() {
        if (currentQuizIndex >= quizList.size) {
            showFinalResults()
            return
        }

        val qData = quizList[currentQuizIndex]
        findViewById<TextView>(R.id.question_text).text = "Q${currentQuizIndex + 1}/${quizList.size}: ${qData.question}"

        val buttons = listOf(
            findViewById<Button>(R.id.btn_choice1),
            findViewById<Button>(R.id.btn_choice2),
            findViewById<Button>(R.id.btn_choice3),
            findViewById<Button>(R.id.btn_choice4)
        )

        for (i in buttons.indices) {
            buttons[i].text = qData.options[i]
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
        showCurrentQuestion()
    }

    private fun showFinalResults() {
        if (XPManager.canEarnXP(this)) {
            XPManager.addXP(this, 30)
        }
        QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)

        // 🟢 DETERMINE IF THEY PASSED (Requires 50% or higher)
        val isPassed = currentScore >= (quizList.size / 2.0)

        val userId = auth.currentUser?.uid
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "quizType" to "material", // Explicitly separate from daily recall quizzes
                "materialId" to materialId, // Save the ID to check later
                "isPassed" to isPassed, // Save pass/fail status
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
                setTextColor(android.graphics.Color.parseColor("#00B894"))
            } else {
                text = "Quiz Failed. ❌\nYou scored $currentScore / $totalAttempts.\nYou must try again."
                setTextColor(android.graphics.Color.parseColor("#D63031"))
            }
        }

        findViewById<Button>(R.id.btn_restart).apply {
            text = if (isPassed) "Return to Library" else "Retry Quiz"
            setOnClickListener {
                if (isPassed) {
                    finish() // Close if passed
                } else {
                    // Retry: Relaunch the exact same intent to cleanly reset everything
                    val retryIntent = intent
                    finish()
                    startActivity(retryIntent)
                }
            }
        }

        resultView.visibility = View.VISIBLE
    }
}