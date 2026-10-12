package com.example.gabai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object QuestManager {
    private const val BASE_PREFS_NAME = "GabAI_Daily_Quests"
    private const val KEY_LAST_DATE = "last_active_date"

    // Streak Tracking Keys
    private const val KEY_STREAK = "current_streak"
    private const val KEY_LAST_STREAK_DATE = "last_streak_date"

    const val QUEST_SCAN = "quest_scan"       // Target: 1 Lens Scan
    const val QUEST_QUIZ = "quest_quiz"       // Target: 1 Quiz Session
    const val QUEST_READ = "quest_read"       // Target: Open 1 PDF
    const val QUEST_SAVE = "quest_save"       // Legacy / Unused in daily

    data class DailyQuestMeta(
        val key: String,
        val title: String,
        val subtitle: String,
        val icon: String
    )

    val DAILY_QUEST_METAS = mapOf(
        QUEST_SCAN to DailyQuestMeta(QUEST_SCAN, "Scan Words", "Lens scan completed", "🔍"),
        QUEST_QUIZ to DailyQuestMeta(QUEST_QUIZ, "Daily Quiz", "Vocabulary quiz finished", "⚡"),
        QUEST_READ to DailyQuestMeta(QUEST_READ, "Reading Time", "Material study session finished", "📖")
    )

    val questTargets = mapOf(
        QUEST_SCAN to 1,
        QUEST_QUIZ to 1,
        QUEST_READ to 1
    )

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val prefsName = if (!uid.isNullOrEmpty()) "${BASE_PREFS_NAME}_$uid" else BASE_PREFS_NAME
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // Seamless migration: copy legacy data if user-scoped is fresh
        if (!uid.isNullOrEmpty() && !prefs.contains(KEY_LAST_DATE) && !prefs.contains(KEY_STREAK)) {
            val legacyPrefs = context.getSharedPreferences(BASE_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.contains(KEY_STREAK) || legacyPrefs.contains(KEY_LAST_DATE)) {
                prefs.edit()
                    .putString(KEY_LAST_DATE, legacyPrefs.getString(KEY_LAST_DATE, ""))
                    .putInt(KEY_STREAK, legacyPrefs.getInt(KEY_STREAK, 0))
                    .putString(KEY_LAST_STREAK_DATE, legacyPrefs.getString(KEY_LAST_STREAK_DATE, ""))
                    .putInt(QUEST_SCAN, legacyPrefs.getInt(QUEST_SCAN, 0))
                    .putInt(QUEST_QUIZ, legacyPrefs.getInt(QUEST_QUIZ, 0))
                    .putInt(QUEST_READ, legacyPrefs.getInt(QUEST_READ, 0))
                    .apply()
            }
        }
        return prefs
    }

    // Helper to calculate days between two dates
    private fun getDaysDifference(date1: String, date2: String): Long {
        if (date1.isEmpty() || date2.isEmpty()) return 0
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val d1 = format.parse(date1)
            val d2 = format.parse(date2)
            if (d1 != null && d2 != null) {
                TimeUnit.MILLISECONDS.toDays(d1.time - d2.time)
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    fun syncStreakFromFirestore(context: Context, cloudStreak: Int) {
        val prefs = getPrefs(context)
        val localStreak = prefs.getInt(KEY_STREAK, 0)
        if (cloudStreak > localStreak) {
            prefs.edit().putInt(KEY_STREAK, cloudStreak).apply()
        }
    }

    private const val KEY_LAST_SYSTEM_TIME = "last_system_time"

    private fun checkAndResetDaily(context: Context) {
        val prefs = getPrefs(context)
        val nowMs = System.currentTimeMillis()
        val lastSavedTime = prefs.getLong(KEY_LAST_SYSTEM_TIME, 0L)
        if (nowMs < lastSavedTime) {
            // System clock was rewound backwards; prevent tampering
            return
        }
        prefs.edit().putLong(KEY_LAST_SYSTEM_TIME, nowMs).apply()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (today != lastDate) {
            // It's a new day: reset daily tasks to 0
            val currentStreak = prefs.getInt(KEY_STREAK, 0)
            val lastStreakDate = prefs.getString(KEY_LAST_STREAK_DATE, "")

            // STREAK PENALTY LOGIC: Reset to 0 if gap > 1 day
            val newStreak = if (!lastStreakDate.isNullOrEmpty()) {
                val daysMissed = getDaysDifference(today, lastStreakDate)
                if (daysMissed > 1) 0 else currentStreak
            } else {
                currentStreak
            }

            prefs.edit()
                .putInt(QUEST_SCAN, 0)
                .putInt(QUEST_QUIZ, 0)
                .putInt(QUEST_READ, 0)
                .putInt(QUEST_SAVE, 0)
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_STREAK, newStreak)
                .putString(KEY_LAST_STREAK_DATE, lastStreakDate ?: "")
                .apply()
        }
    }

    fun addProgress(context: Context, questKey: String) {
        if (!XPManager.canEarnXP(context)) return // Locked until initiation is done

        checkAndResetDaily(context)

        val prefs = getPrefs(context)
        val target = questTargets[questKey] ?: return
        val currentProgress = prefs.getInt(questKey, 0)

        if (currentProgress < target) {
            val newProgress = currentProgress + 1
            prefs.edit().putInt(questKey, newProgress).apply()

            // 1. Give XP for completing the specific task
            if (newProgress == target) {
                val leveledUp = XPManager.addXP(context, 50)
                val activity = findActivity(context)

                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        showDailyQuestCompletionBanner(activity, questKey, 50, leveledUp)
                    }
                } else {
                    if (leveledUp) {
                        GabAIUtils.showSnackbar(context, "Quest Complete! 🎉 +50 XP and you LEVELED UP!")
                    } else {
                        GabAIUtils.showSnackbar(context, "Quest Complete! 🎉 +50 XP")
                    }
                }
            }

            // 2. STREAK REWARD LOGIC: Did they extend their streak today?
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastStreakDate = prefs.getString(KEY_LAST_STREAK_DATE, "")

            val daysDiff = if (lastStreakDate.isNullOrEmpty()) 1 else getDaysDifference(today, lastStreakDate)
            if (today != lastStreakDate && daysDiff >= 1) {
                // First task of the day extends streak
                val currentStreak = prefs.getInt(KEY_STREAK, 0)
                val newStreak = currentStreak + 1

                prefs.edit()
                    .putInt(KEY_STREAK, newStreak)
                    .putString(KEY_LAST_STREAK_DATE, today)
                    .apply()

                // Sync streak to Firestore
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid).update("current_streak", newStreak)
                }

                val act = findActivity(context)
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!act.isFinishing && !act.isDestroyed) {
                            act.runOnUiThread {
                                showStreakBanner(act, newStreak)
                            }
                        }
                    }, 4000L)
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({
                        GabAIUtils.showSnackbar(context, "🔥 Streak extended! You're on a $newStreak-day streak!")
                    }, 2000L)
                }
            }
        }
    }

    private fun findActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun showDailyQuestCompletionBanner(
        activity: Activity,
        questKey: String,
        xpEarned: Int = 50,
        leveledUp: Boolean = false,
        onDismiss: (() -> Unit)? = null
    ) {
        val rootGroup = activity.window.decorView as? ViewGroup ?: run {
            onDismiss?.invoke()
            return
        }

        val existingBanner = rootGroup.findViewWithTag<View>("daily_quest_completion_banner")
        if (existingBanner != null) {
            rootGroup.removeView(existingBanner)
        }

        val meta = DAILY_QUEST_METAS[questKey] ?: DailyQuestMeta(questKey, "Daily Quest", "Daily challenge completed", "🎯")
        val density = activity.resources.displayMetrics.density
        GabAIUtils.performHaptic(activity.window.decorView, HapticFeedbackConstants.CONFIRM)

        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val statusBarHeight = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?.takeIf { it > 0 }
            ?: rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top?.takeIf { it > 0 }
            ?: run {
                val identifier = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (identifier > 0) activity.resources.getDimensionPixelSize(identifier) else (36f * density).toInt()
            }

        val calculatedTopMargin = statusBarHeight + (24f * density).toInt()

        val bannerContainer = FrameLayout(activity).apply {
            tag = "daily_quest_completion_banner"
            val sideMargin = (16f * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(sideMargin, calculatedTopMargin, sideMargin, 0)
            }
            elevation = 12f * density
        }

        ViewCompat.setOnApplyWindowInsetsListener(bannerContainer) { _, insets ->
            val sbHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.takeIf { it > 0 }
                ?: insets.getInsets(WindowInsetsCompat.Type.systemBars()).top.takeIf { it > 0 }
                ?: statusBarHeight
            val params = bannerContainer.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.topMargin = (24f * density).toInt() + sbHeight
                bannerContainer.layoutParams = params
            }
            insets
        }

        val fontJakarta = try {
            ResourcesCompat.getFont(activity, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }
        val fontNunito = try {
            ResourcesCompat.getFont(activity, R.font.font_nunito_sans)
        } catch (_: Exception) {
            null
        }

        val cardRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14f * density).toInt(),
                (13f * density).toInt(),
                (14f * density).toInt(),
                (13f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 22f * density
                setStroke((1.2f * density).toInt(), Color.parseColor("#E2E8F0"))
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            elevation = 6f * density
        }

        val iconSize = (46f * density).toInt()
        val iconView = TextView(activity).apply {
            text = meta.icon
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = (12f * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF3C7"))
                cornerRadius = 14f * density
                setStroke((1f * density).toInt(), Color.parseColor("#FDE68A"))
            }
            elevation = 0f
        }
        cardRow.addView(iconView)

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (3f * density).toInt()
            }
        }

        val completePill = TextView(activity).apply {
            text = "DAILY QUEST COMPLETE!"
            textSize = 9.5f
            setTextColor(Color.parseColor("#15803D"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            setPadding(
                (7f * density).toInt(),
                (2.5f * density).toInt(),
                (7f * density).toInt(),
                (2.5f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0FDF4"))
                cornerRadius = 8f * density
                setStroke((1f * density).toInt(), Color.parseColor("#BBF7D0"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (6f * density).toInt()
            }
        }
        badgeRow.addView(completePill)

        val xpPill = TextView(activity).apply {
            text = if (leveledUp) "🎉 LEVEL UP!" else "+$xpEarned XP CLAIMED"
            textSize = 10.5f
            setTextColor(Color.parseColor("#4338CA"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            setPadding(
                (7f * density).toInt(),
                (2.5f * density).toInt(),
                (7f * density).toInt(),
                (2.5f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EEF2FF"))
                cornerRadius = 8f * density
                setStroke((1f * density).toInt(), Color.parseColor("#C7D2FE"))
            }
        }
        badgeRow.addView(xpPill)
        textCol.addView(badgeRow)

        val titleView = TextView(activity).apply {
            text = meta.title
            textSize = 14.5f
            setTextColor(Color.parseColor("#0F172A"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(titleView)

        val subtitleView = TextView(activity).apply {
            text = if (leveledUp) {
                "Congratulations! You leveled up! 🚀 Tap to dismiss"
            } else {
                "${meta.subtitle} • +$xpEarned XP added"
            }
            textSize = 11.5f
            setTextColor(Color.parseColor("#64748B"))
            typeface = fontNunito ?: Typeface.DEFAULT
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(subtitleView)
        cardRow.addView(textCol)

        val closeBtn = TextView(activity).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            val pad = (6f * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6f * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        cardRow.addView(closeBtn)

        bannerContainer.addView(cardRow)

        val offscreenOffsetY = -(((80f * density).toInt() + calculatedTopMargin).toFloat())
        bannerContainer.translationY = offscreenOffsetY
        bannerContainer.alpha = 0f
        rootGroup.addView(bannerContainer)

        bannerContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()

        val handler = Handler(Looper.getMainLooper())
        var isDismissed = false
        val dismissAction = Runnable {
            if (isDismissed) return@Runnable
            isDismissed = true
            bannerContainer.animate()
                .translationY(offscreenOffsetY)
                .alpha(0f)
                .setDuration(240L)
                .withEndAction {
                    rootGroup.removeView(bannerContainer)
                    onDismiss?.invoke()
                }
                .start()
        }

        bannerContainer.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            handler.removeCallbacks(dismissAction)
            dismissAction.run()
        }
        closeBtn.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            handler.removeCallbacks(dismissAction)
            dismissAction.run()
        }

        handler.postDelayed(dismissAction, 3800L)
    }

    fun showStreakBanner(
        activity: Activity,
        streakDays: Int,
        onDismiss: (() -> Unit)? = null
    ) {
        val rootGroup = activity.window.decorView as? ViewGroup ?: run {
            onDismiss?.invoke()
            return
        }

        val existingBanner = rootGroup.findViewWithTag<View>("streak_extended_banner")
        if (existingBanner != null) {
            rootGroup.removeView(existingBanner)
        }

        val density = activity.resources.displayMetrics.density
        GabAIUtils.performHaptic(activity.window.decorView, HapticFeedbackConstants.CONFIRM)

        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val statusBarHeight = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?.takeIf { it > 0 }
            ?: rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top?.takeIf { it > 0 }
            ?: run {
                val identifier = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (identifier > 0) activity.resources.getDimensionPixelSize(identifier) else (36f * density).toInt()
            }

        val calculatedTopMargin = statusBarHeight + (24f * density).toInt()

        val bannerContainer = FrameLayout(activity).apply {
            tag = "streak_extended_banner"
            val sideMargin = (16f * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(sideMargin, calculatedTopMargin, sideMargin, 0)
            }
            elevation = 12f * density
        }

        ViewCompat.setOnApplyWindowInsetsListener(bannerContainer) { _, insets ->
            val sbHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top.takeIf { it > 0 }
                ?: insets.getInsets(WindowInsetsCompat.Type.systemBars()).top.takeIf { it > 0 }
                ?: statusBarHeight
            val params = bannerContainer.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.topMargin = (24f * density).toInt() + sbHeight
                bannerContainer.layoutParams = params
            }
            insets
        }

        val fontJakarta = try {
            ResourcesCompat.getFont(activity, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }
        val fontNunito = try {
            ResourcesCompat.getFont(activity, R.font.font_nunito_sans)
        } catch (_: Exception) {
            null
        }

        val cardRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14f * density).toInt(),
                (13f * density).toInt(),
                (14f * density).toInt(),
                (13f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 22f * density
                setStroke((1.2f * density).toInt(), Color.parseColor("#FED7AA"))
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            elevation = 6f * density
        }

        val iconSize = (46f * density).toInt()
        val iconView = TextView(activity).apply {
            text = "🔥"
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = (12f * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFEDD5"))
                cornerRadius = 14f * density
                setStroke((1f * density).toInt(), Color.parseColor("#FED7AA"))
            }
            elevation = 0f
        }
        cardRow.addView(iconView)

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (3f * density).toInt()
            }
        }

        val streakPill = TextView(activity).apply {
            text = "STREAK EXTENDED!"
            textSize = 9.5f
            setTextColor(Color.parseColor("#C2410C"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            setPadding(
                (7f * density).toInt(),
                (2.5f * density).toInt(),
                (7f * density).toInt(),
                (2.5f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF7ED"))
                cornerRadius = 8f * density
                setStroke((1f * density).toInt(), Color.parseColor("#FED7AA"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (6f * density).toInt()
            }
        }
        badgeRow.addView(streakPill)

        val daysPill = TextView(activity).apply {
            text = "$streakDays Days On Fire"
            textSize = 10.5f
            setTextColor(Color.parseColor("#EA580C"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            setPadding(
                (7f * density).toInt(),
                (2.5f * density).toInt(),
                (7f * density).toInt(),
                (2.5f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFEDD5"))
                cornerRadius = 8f * density
                setStroke((1f * density).toInt(), Color.parseColor("#FDBA74"))
            }
        }
        badgeRow.addView(daysPill)
        textCol.addView(badgeRow)

        val titleView = TextView(activity).apply {
            text = "$streakDays-Day Study Streak Active"
            textSize = 14.5f
            setTextColor(Color.parseColor("#0F172A"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(titleView)

        val subtitleView = TextView(activity).apply {
            text = "Keep studying daily to maintain your multiplier! • Tap to dismiss"
            textSize = 11.5f
            setTextColor(Color.parseColor("#64748B"))
            typeface = fontNunito ?: Typeface.DEFAULT
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textCol.addView(subtitleView)
        cardRow.addView(textCol)

        val closeBtn = TextView(activity).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            val pad = (6f * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6f * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        cardRow.addView(closeBtn)

        bannerContainer.addView(cardRow)

        val offscreenOffsetY = -(((80f * density).toInt() + calculatedTopMargin).toFloat())
        bannerContainer.translationY = offscreenOffsetY
        bannerContainer.alpha = 0f
        rootGroup.addView(bannerContainer)

        bannerContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(OvershootInterpolator(0.85f))
            .start()

        val handler = Handler(Looper.getMainLooper())
        var isDismissed = false
        val dismissAction = Runnable {
            if (isDismissed) return@Runnable
            isDismissed = true
            bannerContainer.animate()
                .translationY(offscreenOffsetY)
                .alpha(0f)
                .setDuration(240L)
                .withEndAction {
                    rootGroup.removeView(bannerContainer)
                    onDismiss?.invoke()
                }
                .start()
        }

        bannerContainer.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            handler.removeCallbacks(dismissAction)
            dismissAction.run()
        }
        closeBtn.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            handler.removeCallbacks(dismissAction)
            dismissAction.run()
        }

        handler.postDelayed(dismissAction, 3800L)
    }

    fun getProgress(context: Context, questKey: String): Int {
        checkAndResetDaily(context)
        return getPrefs(context).getInt(questKey, 0)
    }

    fun getStreak(context: Context): Int {
        checkAndResetDaily(context)
        return getPrefs(context).getInt(KEY_STREAK, 0)
    }
}