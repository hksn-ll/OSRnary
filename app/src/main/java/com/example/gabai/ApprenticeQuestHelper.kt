package com.example.gabai

import android.app.Activity
import android.content.Context
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object ApprenticeQuestHelper {
    const val QUEST_BUBBLE = "bubble"
    const val QUEST_BUBBLE_SCAN = "bubble_scan"
    const val QUEST_TEST = "test"
    const val QUEST_SCAN = "scan"
    const val QUEST_SAVE = "save"
    const val QUEST_HISTORY = "history"
    const val QUEST_DETAIL = "detail"
    const val QUEST_LIBRARY = "library"

    private val CANONICAL_QUESTS = listOf(
        QUEST_BUBBLE,
        QUEST_BUBBLE_SCAN,
        QUEST_TEST,
        QUEST_SCAN,
        QUEST_SAVE,
        QUEST_HISTORY,
        QUEST_DETAIL,
        QUEST_LIBRARY
    )

    data class QuestMeta(
        val key: String,
        val title: String,
        val icon: String
    )

    private val QUEST_INFO_MAP = mapOf(
        QUEST_BUBBLE to QuestMeta(QUEST_BUBBLE, "Activate the Floating Bubble", "🔮"),
        QUEST_BUBBLE_SCAN to QuestMeta(QUEST_BUBBLE_SCAN, "Scan Text with Floating Bubble", "🔍"),
        QUEST_SCAN to QuestMeta(QUEST_SCAN, "Scan Text with the Camera", "📷"),
        QUEST_TEST to QuestMeta(QUEST_TEST, "Pass the Initiation Trials", "📜"),
        "read" to QuestMeta(QUEST_TEST, "Pass the Initiation Trials", "📜"),
        QUEST_SAVE to QuestMeta(QUEST_SAVE, "Save a Word to Favorites", "⭐"),
        QUEST_HISTORY to QuestMeta(QUEST_HISTORY, "Check your Scan History", "🕒"),
        QUEST_DETAIL to QuestMeta(QUEST_DETAIL, "View Saved Content (Favourites)", "💚"),
        QUEST_LIBRARY to QuestMeta(QUEST_LIBRARY, "Open the Digital Library", "📚")
    )

    fun isTeacher(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val cachedRole = prefs.getString("cached_user_role", null)
        if (cachedRole != null) {
            return cachedRole.equals("teacher", ignoreCase = true)
        }
        if (context is Activity) {
            val rootTag = context.window.decorView.rootView.tag as? String
            if (rootTag != null && rootTag.equals("teacher", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun completeQuest(
        activity: Activity,
        rawQuestKey: String,
        onDismiss: (() -> Unit)? = null
    ) {
        val normalizedKey = if (rawQuestKey == "read") QUEST_TEST else rawQuestKey
        if (isTeacher(activity)) {
            onDismiss?.invoke()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onDismiss?.invoke()
            return
        }

        val prefs = activity.getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val cacheKey = "cached_completed_quests_$uid"
        val cachedCompleted = prefs.getStringSet(cacheKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        val isAlreadyDone = if (normalizedKey == QUEST_TEST) {
            cachedCompleted.contains(QUEST_TEST) || cachedCompleted.contains("read")
        } else {
            cachedCompleted.contains(normalizedKey)
        }
        if (isAlreadyDone) {
            onDismiss?.invoke()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onDismiss?.invoke()
                    return@addOnSuccessListener
                }
                val role = doc.getString("role") ?: prefs.getString("cached_user_role", "student")
                if (role != null && role.equals("teacher", ignoreCase = true)) {
                    prefs.edit().putString("cached_user_role", "teacher").apply()
                    onDismiss?.invoke()
                    return@addOnSuccessListener
                }

                val remoteCompleted = (doc.get("quests_completed") as? List<*>)
                    ?.mapNotNull { it?.toString() }
                    ?.toMutableSet() ?: mutableSetOf()

                val remoteAlreadyDone = if (normalizedKey == QUEST_TEST) {
                    remoteCompleted.contains(QUEST_TEST) || remoteCompleted.contains("read")
                } else {
                    remoteCompleted.contains(normalizedKey)
                }

                if (remoteAlreadyDone) {
                    cachedCompleted.addAll(remoteCompleted)
                    prefs.edit().putStringSet(cacheKey, cachedCompleted).apply()
                    onDismiss?.invoke()
                    return@addOnSuccessListener
                }

                val keysToAdd = if (normalizedKey == QUEST_TEST) {
                    listOf(QUEST_TEST, "read")
                } else {
                    listOf(normalizedKey)
                }

                remoteCompleted.addAll(keysToAdd)
                cachedCompleted.addAll(keysToAdd)
                prefs.edit().putStringSet(cacheKey, cachedCompleted).apply()

                val keysArray = keysToAdd.toTypedArray()
                userRef.update("quests_completed", FieldValue.arrayUnion(*keysArray))

                val completedCount = calculateCompletedCount(remoteCompleted)
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        showQuestCompletionBanner(activity, normalizedKey, completedCount, onDismiss)
                    }
                } else {
                    onDismiss?.invoke()
                }
            }
            .addOnFailureListener {
                onDismiss?.invoke()
            }
    }

    fun calculateCompletedCount(completed: Set<String>): Int {
        var count = 0
        if (completed.contains(QUEST_BUBBLE)) count++
        if (completed.contains(QUEST_BUBBLE_SCAN)) count++
        if (completed.contains(QUEST_TEST) || completed.contains("read")) count++
        if (completed.contains(QUEST_SCAN)) count++
        if (completed.contains(QUEST_SAVE)) count++
        if (completed.contains(QUEST_HISTORY)) count++
        if (completed.contains(QUEST_DETAIL)) count++
        if (completed.contains(QUEST_LIBRARY)) count++
        return count
    }

    private fun showQuestCompletionBanner(
        activity: Activity,
        questKey: String,
        completedCount: Int,
        onDismiss: (() -> Unit)? = null
    ) {
        val rootGroup = activity.window.decorView as? ViewGroup
        if (rootGroup == null) {
            onDismiss?.invoke()
            return
        }

        val existingBanner = rootGroup.findViewWithTag<View>("quest_completion_banner")
        if (existingBanner != null) {
            rootGroup.removeView(existingBanner)
        }

        val meta = QUEST_INFO_MAP[questKey] ?: QuestMeta(questKey, "Apprentice Quest", "🏆")
        val density = activity.resources.displayMetrics.density
        GabAIUtils.performHaptic(activity.window.decorView, HapticFeedbackConstants.CONFIRM)

        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val statusBarHeight = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top?.takeIf { it > 0 }
            ?: rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top?.takeIf { it > 0 }
            ?: run {
                val identifier = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (identifier > 0) activity.resources.getDimensionPixelSize(identifier) else (36f * density).toInt()
            }

        val topBufferPx = (24f * density).toInt()
        val calculatedTopMargin = statusBarHeight + topBufferPx

        val bannerContainer = FrameLayout(activity).apply {
            tag = "quest_completion_banner"
            val sideMargin = (16f * density).toInt()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(sideMargin, calculatedTopMargin, sideMargin, 0)
            }
            elevation = 0f
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
            elevation = 0f
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
            text = "QUEST COMPLETE!"
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

        val countPill = TextView(activity).apply {
            text = "$completedCount of 8 Unlocked"
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
        badgeRow.addView(countPill)
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
            text = if (completedCount >= 8) {
                "All 8 Complete! Full ranking & leveling unlocked! 🎉"
            } else {
                "Complete all 8 to unlock full ranking & leveling"
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
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    try {
                        rootGroup.removeView(bannerContainer)
                    } catch (_: Exception) {
                    }
                    onDismiss?.invoke()
                }
                .start()
        }

        bannerContainer.setOnClickListener {
            handler.removeCallbacks(dismissAction)
            dismissAction.run()
        }
        handler.postDelayed(dismissAction, 3500L)
    }
}
