package com.example.gabai

import android.content.res.ColorStateList
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_achievements)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_achievements)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val headerInner = findViewById<View>(R.id.achievements_header)
        val scrollContent = findViewById<View>(R.id.scroll_achievements)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner, scrollContent, extraBufferDp = 16)

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
                "#6366F1",
                "Requires Level 2"
            ),
            Badge(
                "Rising Star",
                "Maintain a 3-Day active learning streak.",
                currentStreak >= 3,
                R.drawable.ic_star_filled,
                "#E11D48",
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
                radius = 22 * density
                cardElevation = 0f
                setStrokeColor(Color.parseColor(if (badge.isUnlocked) "#E2E8F0" else "#F1F5F9"))
                strokeWidth = (1.5 * density).toInt()
                setCardBackgroundColor(Color.WHITE)

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                }
                layoutParams = params

                alpha = if (badge.isUnlocked) 1.0f else 0.55f
            }

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt())
            }

            // Squircle Bento Icon Badge Frame (48dp x 48dp)
            val iconFrame = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(
                    if (badge.isUnlocked) {
                        when (badge.colorHex) {
                            "#10B981" -> R.drawable.bg_bento_mint
                            "#E11D48" -> R.drawable.bg_bento_rose
                            "#6366F1" -> R.drawable.bg_bento_lavender
                            "#8B5CF6" -> R.drawable.bg_bento_purple
                            else -> R.drawable.bg_bento_purple
                        }
                    } else {
                        R.drawable.bg_pill_translucent
                    }
                )
                val sizePx = (48 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            }

            val icon = ImageView(this).apply {
                setImageResource(badge.iconResId)
                val padPx = (11 * density).toInt()
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
                setPadding(0, (12 * density).toInt(), 0, 0)
                maxLines = 2
            }

            val tvStatusPill = TextView(this).apply {
                text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED"
                textSize = 9.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                letterSpacing = 0.06f
                setTextColor(Color.parseColor(if (badge.isUnlocked) "#059669" else "#94A3B8"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                if (badge.isUnlocked) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                } else {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                }
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                val pillParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (10 * density).toInt(), 0, 0)
                }
                layoutParams = pillParams
            }

            content.addView(iconFrame)
            content.addView(tvTitle)
            content.addView(tvStatusPill)
            card.addView(content)

            // Spring press feedback on card
            GabAIUtils.addSpringPressEffect(card) {
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