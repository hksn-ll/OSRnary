package com.example.gabai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

object GabAIUtils {

    // 1. THE MULTI-LINE SNACKBAR
    fun showSnackbar(context: Context?, message: String) {
        if (context == null) return

        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootView = activity?.findViewById<View>(android.R.id.content) ?: return

        val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)

        // Force the text to allow up to 20 lines
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 20
        textView.textSize = 14f

        snackbar.show()
    }

    // 2. THE INVISIBLE MINI PROGRESS BAR WITH STATUS TEXT
    fun showGlobalLoading(context: Context?, message: String = "Loading...") {
        if (context == null) return

        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Prevent adding multiple loading bars if tapped twice, but update the text!
        if (rootLayout.findViewWithTag<View>("gabai_global_loader") != null) {
            val container = rootLayout.findViewWithTag<FrameLayout>("gabai_global_loader")
            val tv = container?.findViewWithTag<TextView>("gabai_global_loader_text")
            tv?.text = message
            return
        }

        // Create a transparent background that blocks touches while loading
        val container = FrameLayout(activity).apply {
            tag = "gabai_global_loader"
            setBackgroundColor(Color.parseColor("#99000000")) // Darker overlay for text readability
            isClickable = true
            isFocusable = true
        }

        val innerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Create the purple spinner
        val progressBar = ProgressBar(activity).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#6C5CE7"))
        }

        // Create the status text
        val statusText = TextView(activity).apply {
            tag = "gabai_global_loader_text"
            text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER
        }

        innerLayout.addView(progressBar)
        innerLayout.addView(statusText)

        container.addView(innerLayout)

        // Inject it over everything!
        rootLayout.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun hideGlobalLoading(context: Context?) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        val loader = rootLayout.findViewWithTag<View>("gabai_global_loader")
        if (loader != null) {
            rootLayout.removeView(loader)
        }
    }

    // =========================================================================
    // 🟢 SLEEK FROSTED GLASS & MOTION HELPERS
    // =========================================================================

    /**
     * Connects BlurView overlay with a BlurTarget scrolling content container
     * for real-time hardware-accelerated backdrop blur.
     */
    fun setupBlurView(
        blurView: FastBlurView?,
        blurTarget: FastBlurTarget?,
        downsampleFactor: Float = 12f,
        radius: Float = 2.5f,
        overlayColor: Int = Color.parseColor("#BFFFFFFF")
    ) {
        if (blurView == null || blurTarget == null) return
        try {
            blurView.setupWith(blurTarget, downsampleFactor, radius, overlayColor)
        } catch (_: Exception) {}
    }

    /**
     * Preserves sharp foreground text and icons (avoids destructive RenderEffect blur on text).
     */
    fun applyFrostedGlass(view: View?, blurRadius: Float = 28f) {
        // RenderEffect on a container blurs all child text and icons into smudges.
        // True backdrop glass is handled by BlurView or specular glass drawables.
    }


    /**
     * Bouncy tactile touch feedback (Spring compression & overshoot rebound).
     */
    fun addSpringPressEffect(view: View?, onClick: (() -> Unit)? = null) {
        if (view == null) return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .alpha(0.92f)
                        .setDuration(100)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                        .withEndAction {
                            onClick?.invoke() ?: v.performClick()
                        }
                        .start()
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1.0f)
                        .setDuration(140)
                        .start()
                }
            }
            true
        }
    }

    /**
     * Staggered cascade entrance animation for cards/items.
     */
    fun animateCascade(views: List<View>, baseDelay: Long = 35L, startDelayOffset: Long = 300L) {
        views.forEachIndexed { index, view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = 56f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelayOffset + (index * baseDelay))
                .setDuration(380)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
                .start()
        }
    }

    /**
     * Smoothly interpolates progress bar fill without snapping.
     */
    fun animateProgress(progressBar: ProgressBar?, targetProgress: Int, duration: Long = 750L) {
        if (progressBar == null) return
        val anim = android.animation.ObjectAnimator.ofInt(
            progressBar,
            "progress",
            progressBar.progress,
            targetProgress
        )
        anim.duration = duration
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.start()
    }

    /**
     * Tactile haptic feedback for user interactions.
     */
    fun performHaptic(view: View?, feedbackConstant: Int = android.view.HapticFeedbackConstants.VIRTUAL_KEY) {
        view?.performHapticFeedback(feedbackConstant, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    /**
     * Safe Undo SnackBar with 4-second delay before committing destructive actions.
     */
    fun showUndoSnackbar(view: View?, message: String, actionText: String = "Undo", onUndo: () -> Unit) {
        if (view == null) return
        val snackbar = Snackbar.make(view, message, 4000)
            .setAction(actionText) { onUndo() }
            .setActionTextColor(Color.parseColor("#5341CD"))
        snackbar.show()
    }

    /**
     * Uncaps display refresh rate to hardware maximum (90Hz / 120Hz).
     */
    fun applyHardwareMaxRefreshRate(activity: Activity?) {
        if (activity == null) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val disp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay
            val maxMode = disp?.supportedModes?.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val params = activity.window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                activity.window.attributes = params
            }
        }
    }

    enum class TextSpanType {
        WORD,
        PHRASE,
        SENTENCE
    }

    data class TextSpanClassification(
        val type: TextSpanType,
        val wordCount: Int,
        val cleanText: String,
        val badgeLabel: String,
        val targetSpeakLabel: String,
        val actionButtonLabel: String
    )

    /**
     * Accurately categorizes selected text as a single Word, a multi-word Phrase (e.g. "Daily Quests"),
     * or a full Sentence. Strips trailing punctuation to prevent OCR noise from converting titles/phrases
     * into sentences.
     */
    fun classifyTextSpan(text: String): TextSpanClassification {
        val trimmed = text.trim()
        val clean = trimmed.trim('.', '!', '?', ':', ';', ',', '"', '\'', '-', '—', '“', '”', '`', ' ')
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val count = words.size
        val hasTerminalPunctuation = trimmed.let { it.endsWith(".") || it.endsWith("?") || it.endsWith("!") }

        val type = when {
            count <= 1 -> TextSpanType.WORD
            count in 2..7 && (!hasTerminalPunctuation || count <= 5) -> TextSpanType.PHRASE
            else -> TextSpanType.SENTENCE
        }

        val badge = when (type) {
            TextSpanType.WORD -> "TARGET WORD"
            TextSpanType.PHRASE -> "KEY PHRASE ($count WORDS)"
            TextSpanType.SENTENCE -> "FULL SENTENCE ($count WORDS)"
        }

        val speakLabel = when (type) {
            TextSpanType.WORD -> "Word"
            TextSpanType.PHRASE -> "Phrase"
            TextSpanType.SENTENCE -> "Sentence"
        }

        val actionLabel = when (type) {
            TextSpanType.WORD -> "Explain Word  →"
            TextSpanType.PHRASE -> "Explain Phrase  →"
            TextSpanType.SENTENCE -> "Explain Sentence  →"
        }

        return TextSpanClassification(
            type = type,
            wordCount = count,
            cleanText = clean,
            badgeLabel = badge,
            targetSpeakLabel = speakLabel,
            actionButtonLabel = actionLabel
        )
    }

    /**
     * Determines whether context surrounding a selection is a phrase, sentence, or passage.
     */
    fun classifyContextLabel(contextText: String): String {
        val clean = contextText.trim().trim('.', '!', '?', ':', ';', ',', '"', '\'', '-', '—')
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.size > 25 -> "Passage"
            words.size in 1..7 -> "Phrase"
            else -> "Sentence"
        }
    }
}