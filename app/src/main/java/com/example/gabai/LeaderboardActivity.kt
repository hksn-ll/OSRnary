package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        // Fix Status Bar
        val header = findViewById<View>(R.id.leaderboard_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val container = findViewById<LinearLayout>(R.id.leaderboard_container)

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val mySchoolId = userDoc.getString("schoolId") ?: ""
            val mySection = userDoc.getString("section") ?: ""
            val myGrade = userDoc.getString("grade") ?: ""
            val myName = "${userDoc.getString("firstName")} ${userDoc.getString("lastName")}".trim()

            val titleText = if (myGrade.isNotEmpty()) "$myGrade $mySection Leaderboard" else "$mySection Leaderboard"
            findViewById<TextView>(R.id.tv_leaderboard_title).text = titleText

            // 🟢 THE FIX: Start with a basic query
            var query = db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("schoolId", mySchoolId)
                .whereEqualTo("section", mySection)

            // 🟢 ONLY add the grade filter if the student actually has a grade saved!
            if (myGrade.isNotEmpty()) {
                query = query.whereEqualTo("grade", myGrade)
            }

            query.get()
                .addOnSuccessListener { snapshots ->
                    GabAIUtils.hideGlobalLoading(this)
                    container.removeAllViews()

                    if (snapshots.isEmpty) {
                        container.addView(TextView(this).apply { text = "No students found." })
                        return@addOnSuccessListener
                    }

                    // SORT LOCALLY
                    val sortedStudents = snapshots.documents.sortedWith(
                        compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                            it.getLong("level") ?: 1L
                        }.thenByDescending {
                            it.getLong("current_xp") ?: 0L
                        }
                    )

                    for ((index, doc) in sortedStudents.withIndex()) {
                        val rank = index + 1
                        val fName = doc.getString("firstName") ?: "Unknown"
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = "$fName $lName".trim()
                        val level = doc.getLong("level")?.toInt() ?: 1
                        val xp = doc.getLong("current_xp")?.toInt() ?: 0

                        val isMe = fullName == myName

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding(40, 40, 40, 40)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 16) }

                            setBackgroundResource(R.drawable.bg_card_quiz)
                            elevation = if (isMe || rank <= 3) 8f else 2f
                        }

                        val tvRank = TextView(this).apply {
                            text = "#$rank"
                            textSize = 20f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT)
                            setTextColor(when (rank) {
                                1 -> android.graphics.Color.parseColor("#F1C40F") // Gold
                                2 -> android.graphics.Color.parseColor("#95A5A6") // Silver
                                3 -> android.graphics.Color.parseColor("#D35400") // Bronze
                                else -> android.graphics.Color.parseColor("#B2BEC3")
                            })
                        }

                        val tvName = TextView(this).apply {
                            text = if (isMe) "$fullName (You)" else fullName
                            textSize = 16f
                            setTypeface(null, if (isMe) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                            setTextColor(android.graphics.Color.parseColor(if (isMe) "#0984E3" else "#2D3436"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val tvLevel = TextView(this).apply {
                            text = "Lvl $level\n($xp XP)"
                            textSize = 14f
                            gravity = android.view.Gravity.END
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor("#6C5CE7"))
                        }

                        row.addView(tvRank)
                        row.addView(tvName)
                        row.addView(tvLevel)

                        container.addView(row)
                    }
                }
                .addOnFailureListener { e ->
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Error loading leaderboard: ${e.message}")
                }
        }
    }
}