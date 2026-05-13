package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AchievementsActivity : AppCompatActivity() {

    // Data class to define our Badges
    data class Badge(
        val title: String,
        val description: String,
        val isUnlocked: Boolean,
        val iconResId: Int,
        val colorHex: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_achievements)

        // Fix Status Bar Overlap
        val header = findViewById<View>(R.id.achievements_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadBadges()
    }

    private fun loadBadges() {
        val container = findViewById<LinearLayout>(R.id.badges_container)
        container.removeAllViews()

        // 1. Fetch Student Stats Locally (Lightning Fast!)
        val currentLevel = XPManager.getLevel(this)
        val currentStreak = QuestManager.getStreak(this)

        // 2. Define the Badge Rules
        val badges = listOf(
            Badge(
                "Apprentice Initiate",
                "Complete your initiation and reach Level 2.",
                currentLevel >= 2,
                android.R.drawable.ic_dialog_info,
                "#0984E3" // Blue
            ),
            Badge(
                "Rising Star",
                "Maintain a 3-Day Learning Streak.",
                currentStreak >= 3,
                android.R.drawable.ic_menu_today,
                "#D35400" // Orange/Fire
            ),
            Badge(
                "The Scholar",
                "Reach Level 5 by earning XP.",
                currentLevel >= 5,
                android.R.drawable.btn_star_big_on,
                "#F1C40F" // Gold
            ),
            Badge(
                "Unstoppable",
                "Maintain a massive 7-Day Learning Streak.",
                currentStreak >= 7,
                android.R.drawable.ic_menu_sort_by_size,
                "#6C5CE7" // Purple
            )
        )

        // 3. Build the UI for each badge
        for (badge in badges) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_card_quiz)
                setPadding(40, 40, 40, 40)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 24) }

                // Slight elevation for unlocked, flat for locked
                elevation = if (badge.isUnlocked) 6f else 1f
                alpha = if (badge.isUnlocked) 1.0f else 0.6f // Dim if locked
            }

            // Badge Icon
            val icon = ImageView(this).apply {
                setImageResource(badge.iconResId)
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(0, 0, 40, 0) }
                // Color it if unlocked, gray if locked
                setColorFilter(Color.parseColor(if (badge.isUnlocked) badge.colorHex else "#B2BEC3"))
            }

            // Text Container
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTitle = TextView(this).apply {
                text = badge.title
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (badge.isUnlocked) "#2D3436" else "#636E72"))
            }

            val tvDesc = TextView(this).apply {
                text = badge.description
                textSize = 14f
                setTextColor(Color.parseColor("#636E72"))
                setPadding(0, 8, 0, 0)
            }

            // Lock/Unlock Indicator Status
            val tvStatus = TextView(this).apply {
                text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (badge.isUnlocked) "#00B894" else "#B2BEC3"))
                setPadding(0, 16, 0, 0)
            }

            textLayout.addView(tvTitle)
            textLayout.addView(tvDesc)
            textLayout.addView(tvStatus)

            card.addView(icon)
            card.addView(textLayout)

            container.addView(card)
        }
    }
}