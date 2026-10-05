package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class LeaderboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_leaderboard)

        val blurHeader = findViewById<io.alterac.blurkit.FastBlurView>(R.id.blur_header_leaderboard)
        val blurTarget = findViewById<io.alterac.blurkit.FastBlurTarget>(R.id.blur_target_leaderboard)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val headerInner = findViewById<View>(R.id.leaderboard_header)
        val initialPaddingTop = headerInner.paddingTop
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerInner) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + initialPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val container = findViewById<LinearLayout>(R.id.leaderboard_container)
        val podiumContainer = findViewById<View>(R.id.podium_container)
        val podiumFirst = findViewById<View>(R.id.podium_first)
        val podiumSecond = findViewById<View>(R.id.podium_second)
        val podiumThird = findViewById<View>(R.id.podium_third)
        val cardStickyUser = findViewById<View>(R.id.card_sticky_user)
        val tvStickyRank = findViewById<TextView>(R.id.tv_sticky_rank)
        val tvStickyName = findViewById<TextView>(R.id.tv_sticky_name)
        val tvStickyXp = findViewById<TextView>(R.id.tv_sticky_xp)

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val mySchoolId = userDoc.getString("schoolId") ?: ""
            val mySection = userDoc.getString("section") ?: ""
            val myGrade = userDoc.getString("grade") ?: ""
            val myName = "${userDoc.getString("firstName")} ${userDoc.getString("lastName")}".trim()

            val titleText = if (myGrade.isNotEmpty()) "$myGrade - $mySection" else "$mySection Leaderboard"
            findViewById<TextView>(R.id.tv_leaderboard_title).text = titleText

            var query = db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("schoolId", mySchoolId)
                .whereEqualTo("section", mySection)

            if (myGrade.isNotEmpty()) {
                query = query.whereEqualTo("grade", myGrade)
            }

            query.get()
                .addOnSuccessListener { snapshots ->
                    GabAIUtils.hideGlobalLoading(this)
                    container.removeAllViews()

                    if (snapshots.isEmpty) {
                        podiumContainer.visibility = View.GONE
                        cardStickyUser.visibility = View.GONE
                        val emptyText = TextView(this).apply {
                            text = "No students found in this roster."
                            textSize = 15f
                            setTextColor(Color.parseColor("#64748B"))
                            gravity = Gravity.CENTER
                            setPadding(0, 60, 0, 0)
                        }
                        container.addView(emptyText)
                        return@addOnSuccessListener
                    }

                    val fontJakarta = try {
                        ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
                    } catch (_: Exception) {
                        null
                    }

                    // SORT LOCALLY: Level DESC, then XP DESC
                    val sortedStudents = snapshots.documents.sortedWith(
                        compareByDescending<DocumentSnapshot> {
                            it.getLong("level") ?: 1L
                        }.thenByDescending {
                            it.getLong("current_xp") ?: 0L
                        }
                    )

                    // 1. Olympic Podium Population (Top 3)
                    podiumContainer.visibility = View.VISIBLE

                    // 1st Place
                    val firstDoc = sortedStudents[0]
                    val firstFName = firstDoc.getString("firstName") ?: "Scholar"
                    val firstLName = firstDoc.getString("lastName") ?: ""
                    val firstInitial = (firstFName.firstOrNull() ?: '1').uppercaseChar().toString()
                    val firstXp = firstDoc.getLong("current_xp")?.toInt() ?: 0
                    findViewById<TextView>(R.id.tv_podium_first_avatar).text = firstInitial
                    findViewById<TextView>(R.id.tv_podium_first_name).text = "$firstFName $firstLName".trim()
                    findViewById<TextView>(R.id.tv_podium_first_xp).text = "$firstXp XP"
                    podiumFirst.visibility = View.VISIBLE

                    // 2nd Place
                    if (sortedStudents.size > 1) {
                        val secondDoc = sortedStudents[1]
                        val secondFName = secondDoc.getString("firstName") ?: "Scholar"
                        val secondLName = secondDoc.getString("lastName") ?: ""
                        val secondInitial = (secondFName.firstOrNull() ?: '2').uppercaseChar().toString()
                        val secondXp = secondDoc.getLong("current_xp")?.toInt() ?: 0
                        findViewById<TextView>(R.id.tv_podium_second_avatar).text = secondInitial
                        findViewById<TextView>(R.id.tv_podium_second_name).text = "$secondFName $secondLName".trim()
                        findViewById<TextView>(R.id.tv_podium_second_xp).text = "$secondXp XP"
                        podiumSecond.visibility = View.VISIBLE
                    } else {
                        podiumSecond.visibility = View.INVISIBLE
                    }

                    // 3rd Place
                    if (sortedStudents.size > 2) {
                        val thirdDoc = sortedStudents[2]
                        val thirdFName = thirdDoc.getString("firstName") ?: "Scholar"
                        val thirdLName = thirdDoc.getString("lastName") ?: ""
                        val thirdInitial = (thirdFName.firstOrNull() ?: '3').uppercaseChar().toString()
                        val thirdXp = thirdDoc.getLong("current_xp")?.toInt() ?: 0
                        findViewById<TextView>(R.id.tv_podium_third_avatar).text = thirdInitial
                        findViewById<TextView>(R.id.tv_podium_third_name).text = "$thirdFName $thirdLName".trim()
                        findViewById<TextView>(R.id.tv_podium_third_xp).text = "$thirdXp XP"
                        podiumThird.visibility = View.VISIBLE
                    } else {
                        podiumThird.visibility = View.INVISIBLE
                    }

                    // 2. Ranks 4+ List
                    val rowViews = mutableListOf<View>()
                    var myRank = -1
                    var myUserDoc: DocumentSnapshot? = null

                    for ((index, doc) in sortedStudents.withIndex()) {
                        val rank = index + 1
                        val fName = doc.getString("firstName") ?: "Unknown"
                        val lName = doc.getString("lastName") ?: ""
                        val fullName = "$fName $lName".trim()
                        val level = doc.getLong("level")?.toInt() ?: 1
                        val xp = doc.getLong("current_xp")?.toInt() ?: 0

                        val isMe = (doc.id == uid) || (fullName == myName)
                        if (isMe) {
                            myRank = rank
                            myUserDoc = doc
                        }

                        // Top 3 are honored on podium, but show rank 4+ in the list
                        if (rank > 3) {
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(36, 32, 36, 32)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(0, 0, 0, 16) }

                                setBackgroundResource(R.drawable.bg_glass_card_bento)
                            }

                            val tvRank = TextView(this).apply {
                                text = "  #$rank"
                                textSize = 14f
                                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                                layoutParams = LinearLayout.LayoutParams(110, LinearLayout.LayoutParams.WRAP_CONTENT)
                                setTextColor(Color.parseColor("#94A3B8"))
                            }

                            val tvName = TextView(this).apply {
                                text = if (isMe) "$fullName (You)" else fullName
                                textSize = 14f
                                setTypeface(fontJakarta ?: typeface, if (isMe) Typeface.BOLD else Typeface.NORMAL)
                                setTextColor(Color.parseColor(if (isMe) "#5341CD" else "#161D1F"))
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            }

                            val tvLevel = TextView(this).apply {
                                text = "Lvl $level • $xp XP"
                                textSize = 13f
                                gravity = Gravity.END
                                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                                setTextColor(Color.parseColor("#5341CD"))
                            }

                            row.addView(tvRank)
                            row.addView(tvName)
                            row.addView(tvLevel)

                            GabAIUtils.addSpringPressEffect(row) {
                                GabAIUtils.showSnackbar(this, "$fullName • Rank #$rank ($xp Total XP)")
                            }

                            container.addView(row)
                            rowViews.add(row)
                        }
                    }

                    // 3. Sticky Bottom "You" Bar
                    if (myUserDoc != null) {
                        cardStickyUser.visibility = View.VISIBLE
                        val rankLabel = if (myRank in 1..3) {
                            when (myRank) {
                                1 -> "🥇 #1"
                                2 -> "🥈 #2"
                                else -> "🥉 #3"
                            }
                        } else "#$myRank"
                        tvStickyRank.text = rankLabel
                        val myLvl = myUserDoc.getLong("level")?.toInt() ?: 1
                        val myXp = myUserDoc.getLong("current_xp")?.toInt() ?: 0
                        tvStickyName.text = "You • Rank $rankLabel"
                        tvStickyXp.text = "Lvl $myLvl • $myXp XP"

                        cardStickyUser.setOnClickListener {
                            val scroll = findViewById<ScrollView>(R.id.scroll_leaderboard)
                            if (myRank <= 3) {
                                scroll?.smoothScrollTo(0, 0)
                            } else {
                                scroll?.smoothScrollTo(0, 240 + (myRank - 4) * 60)
                            }
                            GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } else {
                        cardStickyUser.visibility = View.GONE
                    }

                    if (rowViews.isNotEmpty()) {
                        GabAIUtils.animateCascade(rowViews, 25L)
                    }
                }
                .addOnFailureListener { e ->
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Error loading leaderboard: ${e.message}")
                }
        }
    }
}