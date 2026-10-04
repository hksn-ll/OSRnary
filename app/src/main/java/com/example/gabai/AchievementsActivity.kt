package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView

class AchievementsActivity : AppCompatActivity() {

    data class Badge(
        val title: String,
        val description: String,
        val isUnlocked: Boolean,
        val iconResId: Int,
        val colorHex: String,
        val unlockCriteria: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_achievements)

        val header = findViewById<View>(R.id.achievements_header)
        GabAIUtils.applyFrostedGlass(header, 28f)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) {
            finish()
        }

        loadBadges()
    }

    private fun loadBadges() {
        val grid = findViewById<GridLayout>(R.id.trophy_grid_container)
        grid.removeAllViews()

        val currentLevel = XPManager.getLevel(this)
        val currentStreak = QuestManager.getStreak(this)

        val badges = listOf(
            Badge(
                "Apprentice Initiate",
                "Complete your onboarding initiation and reach Level 2.",
                currentLevel >= 2,
                R.drawable.ic_badge_check,
                "#5341CD",
                "Requires Level 2"
            ),
            Badge(
                "Rising Star",
                "Maintain a 3-Day active learning streak.",
                currentStreak >= 3,
                R.drawable.ic_star_filled,
                "#F59E0B",
                "Requires 3-Day Streak"
            ),
            Badge(
                "The Scholar",
                "Ascend to Level 5 through focused study and reading.",
                currentLevel >= 5,
                R.drawable.ic_school,
                "#10B981",
                "Requires Level 5"
            ),
            Badge(
                "Mind Over Matter",
                "Harness your intellect and reach Level 10.",
                currentLevel >= 10,
                R.drawable.ic_psychology,
                "#6366F1",
                "Requires Level 10"
            ),
            Badge(
                "Unstoppable Force",
                "Maintain a legendary 7-Day learning streak.",
                currentStreak >= 7,
                R.drawable.ic_military_tech,
                "#E11D48",
                "Requires 7-Day Streak"
            ),
            Badge(
                "Grandmaster",
                "Achieve true mastery by reaching Level 15.",
                currentLevel >= 15,
                R.drawable.ic_insights,
                "#8B5CF6",
                "Requires Level 15"
            )
        )

        // Summary Progress
        val unlockedCount = badges.count { it.isUnlocked }
        val totalCount = badges.size
        findViewById<TextView>(R.id.tv_trophy_count).text = "$unlockedCount / $totalCount"
        val progressPct = if (totalCount > 0) ((unlockedCount.toFloat() / totalCount) * 100).toInt() else 0
        val progressBar = findViewById<ProgressBar>(R.id.progress_trophy_completion)
        GabAIUtils.animateProgress(progressBar, progressPct)

        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        val cardViews = mutableListOf<View>()
        val density = resources.displayMetrics.density

        for (badge in badges) {
            val card = MaterialCardView(this).apply {
                radius = 18 * density
                cardElevation = 1 * density
                setStrokeColor(Color.parseColor(if (badge.isUnlocked) "#EDF2F7" else "#F1F5F9"))
                strokeWidth = (1 * density).toInt()
                setCardBackgroundColor(Color.WHITE)

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                }
                layoutParams = params

                alpha = if (badge.isUnlocked) 1.0f else 0.6f
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((14 * density).toInt(), (18 * density).toInt(), (14 * density).toInt(), (18 * density).toInt())
            }

            // Circular Icon Badge
            val iconFrame = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(if (badge.isUnlocked) R.drawable.bg_bento_purple else R.drawable.bg_pill_translucent)
                val sizePx = (52 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            }

            val icon = ImageView(this).apply {
                setImageResource(badge.iconResId)
                val padPx = (12 * density).toInt()
                setPadding(padPx, padPx, padPx, padPx)
                setColorFilter(Color.parseColor(if (badge.isUnlocked) badge.colorHex else "#94A3B8"))
            }
            iconFrame.addView(icon)

            val tvTitle = TextView(this).apply {
                text = badge.title
                textSize = 13.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(if (badge.isUnlocked) "#0F172A" else "#64748B"))
                gravity = Gravity.CENTER
                setPadding(0, (10 * density).toInt(), 0, 0)
                maxLines = 2
            }

            val tvStatusPill = TextView(this).apply {
                text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED"
                textSize = 10f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                letterSpacing = 0.05f
                setTextColor(Color.parseColor(if (badge.isUnlocked) "#10B981" else "#94A3B8"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                val pillParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (8 * density).toInt(), 0, 0)
                }
                layoutParams = pillParams
            }

            content.addView(iconFrame)
            content.addView(tvTitle)
            content.addView(tvStatusPill)
            card.addView(content)

            // Click Dialog
            card.setOnClickListener {
                GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
                GabAIDialogs.showNoticeDialog(
                    context = this,
                    title = badge.title,
                    message = "${badge.description}\n\nCriteria: ${badge.unlockCriteria}\nStatus: ${if (badge.isUnlocked) "Completed! 🎉" else "In Progress 🔒"}",
                    buttonText = "Close",
                    badgeIcon = if (badge.isUnlocked) "🏆" else "🔒"
                )
            }

            grid.addView(card)
            cardViews.add(card)
        }

        GabAIUtils.animateCascade(cardViews, 30L)
    }
}