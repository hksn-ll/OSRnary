package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProgressDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress_dashboard)

        // Fix Status Bar
        val header = findViewById<View>(R.id.progress_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadXPData()
        calculateAnalytics()
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
}