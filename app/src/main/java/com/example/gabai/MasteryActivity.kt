package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MasteryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mastery)

        val header = findViewById<View>(R.id.mastery_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadWords()
    }

    private fun loadWords() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        val masteredContainer = findViewById<LinearLayout>(R.id.mastered_words_container)
        val weakContainer = findViewById<LinearLayout>(R.id.weak_words_container)

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).collection("history")
            .get()
            .addOnSuccessListener { docs ->
                GabAIUtils.hideGlobalLoading(this)

                masteredContainer.removeAllViews()
                weakContainer.removeAllViews()

                // Interval >= 4 means Mastered. Interval <= 1 means Weak.
                val masteredWords = docs.documents.filter { (it.getLong("interval") ?: 1) >= 4 }
                val weakWords = docs.documents.filter { (it.getLong("interval") ?: 1) <= 1L }

                if (masteredWords.isEmpty()) {
                    masteredContainer.addView(TextView(this).apply {
                        text = "No mastered words yet. Keep taking AI Quizzes!"
                        setTextColor(Color.parseColor("#636E72"))
                    })
                } else {
                    for (doc in masteredWords) {
                        val word = doc.getString("word") ?: ""
                        masteredContainer.addView(createWordRow(word, true))
                    }
                }

                if (weakWords.isEmpty()) {
                    weakContainer.addView(TextView(this).apply {
                        text = "No weak words detected. Great job!"
                        setTextColor(Color.parseColor("#636E72"))
                    })
                } else {
                    for (doc in weakWords) {
                        val word = doc.getString("word") ?: ""
                        weakContainer.addView(createWordRow(word, false))
                    }
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to load words: ${e.message}")
            }
    }

    private fun createWordRow(word: String, isMastered: Boolean): View {
        return TextView(this).apply {
            text = if (isMastered) "⭐ $word" else "⚠️ $word"
            textSize = 16f
            setTextColor(Color.parseColor(if (isMastered) "#00B894" else "#D63031"))
            setPadding(0, 16, 0, 16)
            setTypeface(null, Typeface.BOLD)
        }
    }
}