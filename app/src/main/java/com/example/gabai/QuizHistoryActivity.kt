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
import androidx.core.content.res.ResourcesCompat
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

        val header = findViewById<View>(R.id.history_header)
        GabAIUtils.applyFrostedGlass(header, 28f)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

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
                        text = "No quiz sessions recorded yet.\nComplete a quiz challenge to track your growth!"
                        textSize = 15f
                        setTextColor(Color.parseColor("#64748B"))
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, 100, 0, 0)
                    }
                    container.addView(emptyMsg)
                    return@addOnSuccessListener
                }

                val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                val fontJakarta = try {
                    ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
                } catch (_: Exception) {
                    null
                }

                val cardViews = mutableListOf<View>()

                for (doc in snapshots.documents) {
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val score = doc.getLong("finalScore")?.toInt() ?: 0
                    val attempts = doc.getLong("totalAttempts")?.toInt() ?: 0
                    val dateString = dateFormat.format(Date(timestamp))

                    val items = doc.get("items") as? List<Map<String, Any>> ?: listOf()

                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_glass_card_bento)
                        setPadding(44, 40, 44, 40)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 28) }
                    }

                    val dateTitle = TextView(this).apply {
                        text = dateString
                        textSize = 15f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#161D1F"))
                    }

                    val scoreText = TextView(this).apply {
                        text = "Score: $score / $attempts"
                        textSize = 14f
                        setTypeface(fontJakarta ?: typeface, Typeface.NORMAL)
                        setTextColor(Color.parseColor("#5A6472"))
                        setPadding(0, 6, 0, 12)
                    }

                    val actionText = TextView(this).apply {
                        text = "Review Answers ➔"
                        textSize = 13f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#5341CD"))
                    }

                    card.addView(dateTitle)
                    card.addView(scoreText)
                    card.addView(actionText)

                    GabAIUtils.addSpringPressEffect(card) {
                        showReviewDialog(dateString, score, attempts, items)
                    }

                    container.addView(card)
                    cardViews.add(card)
                }

                GabAIUtils.animateCascade(cardViews, 35L)
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

        for ((index, item) in items.withIndex()) {
            val question = item["question"] as? String ?: "Question"
            val targetWord = item["targetWord"] as? String ?: ""
            val userAnswer = item["userAnswer"] as? String ?: ""
            val isCorrect = item["isCorrect"] as? Boolean ?: false

            val qLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val tvQ = TextView(this).apply {
                text = "Q${index + 1}: $question"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#161D1F"))
                setPadding(0, 0, 0, 8)
            }
            qLayout.addView(tvQ)

            val tvAns = TextView(this).apply {
                text = if (isCorrect) "✔ Your Answer: $userAnswer" else "✘ Your Answer: $userAnswer"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (isCorrect) "#059669" else "#DC2626"))
            }
            qLayout.addView(tvAns)

            if (!isCorrect) {
                val tvCorrection = TextView(this).apply {
                    text = "Correct Answer: $targetWord"
                    textSize = 13f
                    setTextColor(Color.parseColor("#4B5563"))
                    setPadding(0, 4, 0, 0)
                }
                qLayout.addView(tvCorrection)
            }

            val explanation = item["explanation"] as? String
            if (!explanation.isNullOrBlank()) {
                val tvExp = TextView(this).apply {
                    text = "💡 Insight: $explanation"
                    textSize = 12.5f
                    setTypeface(null, Typeface.ITALIC)
                    setTextColor(Color.parseColor("#4338CA"))
                    setPadding(0, 6, 0, 0)
                }
                qLayout.addView(tvExp)
            }

            container.addView(qLayout)

            if (index < items.size - 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                })
            }
        }

        val scrollView = ScrollView(this).apply {
            addView(container)
            isFillViewport = true
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 340).toInt()
            )
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Session Review",
            subtitle = "Score: $score / $attempts",
            customView = scrollView,
            confirmText = "Done",
            cancelText = null,
            badgeIcon = "📝",
            onConfirm = { dialog ->
                dialog.dismiss()
            }
        )
    }
}