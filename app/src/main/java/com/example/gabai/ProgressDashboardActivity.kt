package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Button
import android.widget.LinearLayout

class ProgressDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress_dashboard)

        val header = findViewById<View>(R.id.progress_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
// 🟢 MAKE THE WORDS MASTERED CARD CLICKABLE
        findViewById<TextView>(R.id.tv_words_mastered).parent.let { parentView ->
            (parentView as View).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(android.content.Intent(this@ProgressDashboardActivity, MasteryActivity::class.java))
                }
            }
        }
        // 🟢 NEW BUTTON CLICKS 🟢
        findViewById<View>(R.id.btn_view_all_badges).setOnClickListener {
            startActivity(android.content.Intent(this, AchievementsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_prog_history).setOnClickListener {
            startActivity(android.content.Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btn_prog_quiz_history).setOnClickListener {
            startActivity(android.content.Intent(this, QuizHistoryActivity::class.java))
        }

        loadXPData()
        calculateAnalytics()
        loadUnlockedBadges() // 🟢 Trigger the new badge loader
    }

    private fun loadXPData() {
        val level = XPManager.getLevel(this)
        val xp = XPManager.getXP(this)

        // 🟢 FETCH THE NEW MAX
        val maxXP = XPManager.getMaxXPForLevel(level)

        findViewById<TextView>(R.id.tv_level).text = "Level $level"
        findViewById<TextView>(R.id.tv_xp).text = "$xp / $maxXP XP to next level"

        // 🟢 SET THE PROGRESS BAR MAX
        val progressBar = findViewById<ProgressBar>(R.id.progress_xp)
        progressBar.max = maxXP
        progressBar.progress = xp
    }

    private fun calculateAnalytics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showGlobalLoading(this)
        // 🟢 Fetch Total Quests Done
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val quests = userDoc.get("quests_completed") as? List<String> ?: listOf()
            findViewById<TextView>(R.id.tv_quests_done)?.text = quests.size.toString()
        }

        // 1. Calculate Words Mastered (Interval >= 4 means they got it right multiple times)
        db.collection("users").document(uid).collection("history")
            .whereGreaterThanOrEqualTo("interval", 4)
            .get()
            .addOnSuccessListener { masteredDocs ->
                val masteredCount = masteredDocs.size()
                findViewById<TextView>(R.id.tv_words_mastered).text = masteredCount.toString()

                // 2. Calculate Overall Quiz Accuracy
                db.collection("users").document(uid).collection("quiz_history")
                    .get()
                    .addOnSuccessListener { quizDocs ->
                        var totalScore = 0
                        var totalAttempts = 0

                        for (doc in quizDocs.documents) {
                            totalScore += doc.getLong("finalScore")?.toInt() ?: 0
                            totalAttempts += doc.getLong("totalAttempts")?.toInt() ?: 0
                        }

                        val accuracy = if (totalAttempts > 0) {
                            ((totalScore.toDouble() / totalAttempts.toDouble()) * 100).toInt()
                        } else {
                            0
                        }

                        findViewById<TextView>(R.id.tv_accuracy).text = "$accuracy%"

                        GabAIUtils.hideGlobalLoading(this)
                    }
                    .addOnFailureListener {
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Failed to load accuracy.")
                    }
            }
            .addOnFailureListener {
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to load mastered words.")
            }
    }
    // 🟢 NEW FUNCTION: Render Mini Badges 🟢
    private fun loadUnlockedBadges() {
        val container = findViewById<LinearLayout>(R.id.recent_badges_container)
        container.removeAllViews()

        val currentLevel = XPManager.getLevel(this)
        val currentStreak = QuestManager.getStreak(this)

        // Evaluate rules locally
        val badges = listOf(
            Triple("Apprentice", currentLevel >= 2, Triple(android.R.drawable.ic_dialog_info, "#0984E3", false)),
            Triple("Rising Star", currentStreak >= 3, Triple(android.R.drawable.ic_menu_today, "#D35400", false)),
            Triple("The Scholar", currentLevel >= 5, Triple(android.R.drawable.btn_star_big_on, "#F1C40F", false)),
            Triple("Unstoppable", currentStreak >= 7, Triple(android.R.drawable.ic_menu_sort_by_size, "#6C5CE7", false))
        ).filter { it.second } // ONLY keep the unlocked ones!

        if (badges.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Keep completing quests to unlock badges!"
                setTextColor(android.graphics.Color.GRAY)
                setPadding(0, 16, 0, 0)
            })
            return
        }

        for (badge in badges) {
            val titleText = badge.first
            val iconResId = badge.third.first
            val colorHex = badge.third.second

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_card_quiz)
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(250, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 24, 0)
                }
                elevation = 4f
            }

            val icon = android.widget.ImageView(this).apply {
                setImageResource(iconResId as Int)
                layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0, 0, 0, 16) }
                setColorFilter(android.graphics.Color.parseColor(colorHex as String))
            }

            val title = TextView(this).apply {
                text = titleText
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#2D3436"))
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            }

            card.addView(icon)
            card.addView(title)
            container.addView(card)
        }
    }

}