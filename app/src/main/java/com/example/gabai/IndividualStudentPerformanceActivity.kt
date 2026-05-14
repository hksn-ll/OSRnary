package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class IndividualStudentPerformanceActivity : AppCompatActivity() {

    private lateinit var studentId: String
    private lateinit var studentName: String
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_individual_student_performance)

        studentId = intent.getStringExtra("STUDENT_ID") ?: return finish()
        studentName = intent.getStringExtra("STUDENT_NAME") ?: "Student"

        findViewById<TextView>(R.id.tv_student_name).text = studentName

        val header = findViewById<View>(R.id.ind_perf_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadStudentData()
    }

    private fun loadStudentData() {
        GabAIUtils.showGlobalLoading(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Get Base Stats
                val userDoc = db.collection("users").document(studentId).get().await()
                val level = userDoc.getLong("level")?.toInt() ?: 1
                val streak = userDoc.getLong("current_streak")?.toInt() ?: 0

                // 2. Get Quiz Accuracy
                val quizDocs = db.collection("users").document(studentId).collection("quiz_history").get().await()
                var totalScore = 0
                var totalAttempts = 0
                for (quiz in quizDocs) {
                    totalScore += quiz.getLong("finalScore")?.toInt() ?: 0
                    totalAttempts += quiz.getLong("totalAttempts")?.toInt() ?: 0
                }
                val accuracy = if (totalAttempts > 0) ((totalScore.toDouble() / totalAttempts) * 100).toInt() else 0

                // 3. Get Weak Words (Interval == 1 means it's heavily penalized/haunting them)
                val historyDocs = db.collection("users").document(studentId).collection("history")
                    .whereEqualTo("interval", 1)
                    .get().await()

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@IndividualStudentPerformanceActivity)

                    findViewById<TextView>(R.id.tv_ind_level).text = "$level"
                    findViewById<TextView>(R.id.tv_ind_accuracy).text = "$accuracy%"
                    findViewById<TextView>(R.id.tv_ind_streak).text = "$streak"

                    val weakWordsContainer = findViewById<LinearLayout>(R.id.weak_words_container)
                    weakWordsContainer.removeAllViews()

                    if (historyDocs.isEmpty) {
                        weakWordsContainer.addView(TextView(this@IndividualStudentPerformanceActivity).apply {
                            text = "No weak words detected. The student is performing well!"
                            setTextColor(Color.parseColor("#636E72"))
                            setPadding(0, 16, 0, 0)
                        })
                    } else {
                        for (doc in historyDocs) {
                            val word = doc.getString("word") ?: "Unknown"
                            val row = TextView(this@IndividualStudentPerformanceActivity).apply {
                                text = "⚠️ $word"
                                textSize = 16f
                                setTextColor(Color.parseColor("#D63031")) // Red
                                setPadding(0, 16, 0, 16)
                                setTypeface(null, Typeface.BOLD)
                            }
                            weakWordsContainer.addView(row)
                        }
                    }
                    // 🟢 ADD THIS: 4. Populate Quiz History 🟢
                    val quizzesContainer = findViewById<LinearLayout>(R.id.ind_quizzes_container)
                    quizzesContainer.removeAllViews()

                    if (quizDocs.isEmpty) {
                        quizzesContainer.addView(TextView(this@IndividualStudentPerformanceActivity).apply {
                            text = "No quizzes taken yet."
                            setTextColor(Color.parseColor("#636E72"))
                            setPadding(0, 16, 0, 0)
                        })
                    } else {
                        // Sort quizzes by newest first
                        val sortedQuizzes = quizDocs.documents.sortedByDescending { it.getLong("timestamp") ?: 0L }
                        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault())

                        for (quiz in sortedQuizzes) {
                            val timestamp = quiz.getLong("timestamp") ?: 0L
                            val score = quiz.getLong("finalScore")?.toInt() ?: 0
                            val attempts = quiz.getLong("totalAttempts")?.toInt() ?: 0
                            val type = quiz.getString("quizType") ?: "Unknown"
                            val dateString = dateFormat.format(java.util.Date(timestamp))

                            // 🟢 ADD THIS: Extract the Q&A items
                            val items = quiz.get("items") as? List<Map<String, Any>> ?: listOf()

                            val row = LinearLayout(this@IndividualStudentPerformanceActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                setBackgroundResource(R.drawable.bg_card_quiz)
                                setPadding(40, 30, 40, 30)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(0, 0, 0, 16) }

                                // 🟢 ADD THIS: Make the row clickable
                                isClickable = true
                                isFocusable = true
                                setOnClickListener {
                                    showReviewDialog(score, attempts, items)
                                }
                            }

                            val tvTitle = TextView(this@IndividualStudentPerformanceActivity).apply {
                                text = if (type == "material") "Reading Material Quiz" else "Daily Recall Quiz"
                                textSize = 16f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.parseColor("#2D3436"))
                            }

                            val tvDate = TextView(this@IndividualStudentPerformanceActivity).apply {
                                text = dateString
                                textSize = 12f
                                setTextColor(Color.parseColor("#B2BEC3"))
                                setPadding(0, 4, 0, 8)
                            }

                            val tvScore = TextView(this@IndividualStudentPerformanceActivity).apply {
                                text = "Score: $score / $attempts"
                                textSize = 14f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.parseColor("#6C5CE7"))
                            }

                            // 🟢 ADD THIS: Visual hint that it's clickable
                            val tvAction = TextView(this@IndividualStudentPerformanceActivity).apply {
                                text = "Tap to review answers ->"
                                textSize = 14f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.parseColor("#0984E3"))
                                setPadding(0, 8, 0, 0)
                            }

                            row.addView(tvTitle)
                            row.addView(tvDate)
                            row.addView(tvScore)
                            row.addView(tvAction) // 🟢 ADD THIS
                            quizzesContainer.addView(row)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@IndividualStudentPerformanceActivity)
                    GabAIUtils.showSnackbar(this@IndividualStudentPerformanceActivity, "Failed to load data: ${e.message}")
                }
            }
        }
    }
    private fun showReviewDialog(score: Int, attempts: Int, items: List<Map<String, Any>>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        for ((index, item) in items.withIndex()) {
            val question = item["question"] as? String ?: "Unknown Question"
            val targetWord = item["targetWord"] as? String ?: ""
            val userAnswer = item["userAnswer"] as? String ?: ""
            val isCorrect = item["isCorrect"] as? Boolean ?: false

            val qLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
            }

            val tvQ = TextView(this).apply {
                text = "Q${index + 1}: $question"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 10)
            }
            qLayout.addView(tvQ)

            val tvAns = TextView(this).apply {
                text = if (isCorrect) "✔ Answer: $userAnswer" else "✘ Answer: $userAnswer"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isCorrect) "#00B894" else "#D63031"))
            }
            qLayout.addView(tvAns)

            if (!isCorrect) {
                val tvCorrection = TextView(this).apply {
                    text = "Correct Answer: $targetWord"
                    textSize = 14f
                    setTextColor(Color.DKGRAY)
                    setPadding(0, 5, 0, 0)
                }
                qLayout.addView(tvCorrection)
            }

            container.addView(qLayout)

            if (index < items.size - 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                        setMargins(0, 10, 0, 10)
                    }
                    setBackgroundColor(Color.parseColor("#DFE6E9"))
                })
            }
        }

        val scrollView = android.widget.ScrollView(this).apply { addView(container) }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Quiz Review\n$score / $attempts")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
    }
}