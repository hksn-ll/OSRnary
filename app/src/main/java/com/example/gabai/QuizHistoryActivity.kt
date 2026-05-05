package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuizHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_history)

        // Fix Status Bar
        val header = findViewById<View>(R.id.history_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadQuizHistory()
    }

    private fun loadQuizHistory() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val container = findViewById<LinearLayout>(R.id.quiz_history_container)

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).collection("quiz_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                GabAIUtils.hideGlobalLoading(this)
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    val emptyMsg = TextView(this).apply {
                        text = "You haven't taken any quizzes yet.\nStart a quiz session to test your knowledge!"
                        textSize = 16f
                        setTextColor(Color.GRAY)
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                    }
                    container.addView(emptyMsg)
                    return@addOnSuccessListener
                }

                val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())

                for (doc in snapshots.documents) {
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val score = doc.getLong("finalScore")?.toInt() ?: 0
                    val attempts = doc.getLong("totalAttempts")?.toInt() ?: 0
                    val dateString = dateFormat.format(Date(timestamp))

                    // Safely extract the items list
                    val items = doc.get("items") as? List<Map<String, Any>> ?: listOf()

                    // Build the Card
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_card_quiz) // Reusing your existing card background
                        setPadding(50, 40, 50, 40)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 24) }
                    }

                    val dateTitle = TextView(this).apply {
                        text = dateString
                        textSize = 16f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#2D3436"))
                    }

                    val scoreText = TextView(this).apply {
                        text = "Score: $score / $attempts"
                        textSize = 14f
                        setTextColor(Color.parseColor("#636E72"))
                        setPadding(0, 8, 0, 16)
                    }

                    val actionText = TextView(this).apply {
                        text = "Tap to review answers ->"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#6C5CE7"))
                    }

                    card.addView(dateTitle)
                    card.addView(scoreText)
                    card.addView(actionText)

                    // Open Detailed Review Dialog when clicked
                    card.setOnClickListener {
                        showReviewDialog(dateString, score, attempts, items)
                    }

                    container.addView(card)
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to load history: ${e.message}")
            }
    }

    private fun showReviewDialog(dateStr: String, score: Int, attempts: Int, items: List<Map<String, Any>>) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        // Build the Q&A list dynamically
        for ((index, item) in items.withIndex()) {
            val question = item["question"] as? String ?: "Unknown Question"
            val targetWord = item["targetWord"] as? String ?: ""
            val userAnswer = item["userAnswer"] as? String ?: ""
            val isCorrect = item["isCorrect"] as? Boolean ?: false

            val qLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 20)
            }

            // Question Text
            val tvQ = TextView(this).apply {
                text = "Q${index + 1}: $question"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                setPadding(0, 0, 0, 10)
            }
            qLayout.addView(tvQ)

            // User Answer Text
            val tvAns = TextView(this).apply {
                text = if (isCorrect) "✔ Your Answer: $userAnswer" else "✘ Your Answer: $userAnswer"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isCorrect) "#00B894" else "#D63031")) // Green if right, Red if wrong
            }
            qLayout.addView(tvAns)

            // Show Correct Answer ONLY if they got it wrong
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

            // Add Divider Line between questions
            if (index < items.size - 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                        setMargins(0, 10, 0, 10)
                    }
                    setBackgroundColor(Color.parseColor("#DFE6E9"))
                })
            }
        }

        val scrollView = ScrollView(this).apply { addView(container) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Session Review\n$score / $attempts")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
    }
}