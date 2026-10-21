package com.example.gabai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        formatFloatingPillSnackbar(snackbar, rootView.context)
        snackbar.show()
    }

    // 2. THE BRAND GLOBAL LOADING OVERLAY WITH SPRING ENTRANCE
    fun showGlobalLoading(context: Context?, message: String = "Loading...") {
        if (context == null) return

        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Prevent adding multiple loading bars if tapped twice, but update the text!
        val existingContainer = rootLayout.findViewWithTag<FrameLayout>("gabai_global_loader")
        if (existingContainer != null) {
            val tv = existingContainer.findViewWithTag<TextView>("gabai_global_loader_text")
            tv?.text = message
            return
        }

        val density = activity.resources.displayMetrics.density
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth * 0.82f).toInt().coerceIn((260 * density).toInt(), (340 * density).toInt())

        // 1. Light translucent backdrop scrim with touch blocking
        val container = FrameLayout(activity).apply {
            tag = "gabai_global_loader"
            setBackgroundColor(Color.parseColor("#590F172A")) // 35% translucent dimming
            isClickable = true
            isFocusable = true
            alpha = 0f
        }

        // 2. Elevated Light Mode card container with generous horizontal breathing room (no text clipping)
        val cardLayout = LinearLayout(activity).apply {
            tag = "gabai_global_loader_card"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((28 * density).toInt(), (28 * density).toInt(), (28 * density).toInt(), (24 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFFFF")) // Clean elevated white card
                cornerRadius = 28 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#E2E8F0")) // Modern crisp border
            }
            elevation = 16 * density
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            scaleX = 0.60f
            scaleY = 0.60f
            alpha = 0f
        }

        // 3. Brand animated logo loader VERY BIG (112dp x 112dp)
        val loaderSizePx = (112 * density).toInt()
        val loadingView = GabAiLoadingView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(loaderSizePx, loaderSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // 4. Status text with MATCH_PARENT width, centered, multi-line capable (no word cutting)
        val statusText = TextView(activity).apply {
            tag = "gabai_global_loader_text"
            text = message
            setTextColor(Color.parseColor("#0F172A")) // Deep dark slate high-contrast typography
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding((4 * density).toInt(), (18 * density).toInt(), (4 * density).toInt(), 0)
            maxLines = 4
            isSingleLine = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            translationY = 18 * density
            alpha = 0f
        }

        cardLayout.addView(loadingView)
        cardLayout.addView(statusText)
        container.addView(cardLayout)

        // Inject over root view
        rootLayout.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ENTRANCE ANIMATION:
        // A. Fade in background scrim
        container.animate()
            .alpha(1f)
            .setDuration(220)
            .start()

        // B. Spring entrance for the central loader card
        cardLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        // C. Logo pop entrance
        loadingView.playEntranceAnimation()

        // D. Status text glides up smoothly into place
        statusText.animate()
            .translationY(0f)
            .alpha(1f)
            .setStartDelay(100)
            .setDuration(340)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideGlobalLoading(context: Context?) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }

        val rootLayout = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        val loader = rootLayout.findViewWithTag<View>("gabai_global_loader") ?: return
        val card = loader.findViewWithTag<View>("gabai_global_loader_card")

        // Smooth exit animation
        card?.animate()
            ?.scaleX(0.85f)
            ?.scaleY(0.85f)
            ?.alpha(0f)
            ?.setDuration(180)
            ?.start()

        loader.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                rootLayout.removeView(loader)
            }
            .start()
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
     * Synchronously applies window insets to both fixed frosted header and underlying scroll content.
     * Prevents content (e.g. "MY ACTIVE CLASSES") from hiding behind status-bar padded header.
     */
    fun applyHeaderAndScrollInsets(
        headerView: View?,
        scrollView: View?,
        extraBufferDp: Int = 12
    ) {
        if (headerView == null) return
        val initialHeaderPaddingTop = headerView.paddingTop
        val initialScrollPaddingTop = scrollView?.paddingTop ?: 0
        val density = headerView.resources.displayMetrics.density
        val extraBufferPx = (extraBufferDp * density).toInt()

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(headerView) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + initialHeaderPaddingTop, v.paddingRight, v.paddingBottom)
            scrollView?.let { scroll ->
                scroll.setPadding(
                    scroll.paddingLeft,
                    initialScrollPaddingTop + systemBars.top + extraBufferPx,
                    scroll.paddingRight,
                    scroll.paddingBottom
                )
            }
            insets
        }
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
     * Instantaneous 0ms touch reaction with snappy mechanical pop.
     */
    fun addSpringPressEffect(view: View?, onClick: (() -> Unit)? = null) {
        if (view == null) return
        var startX = 0f
        var startY = 0f
        var isClickAllowed = true

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isClickAllowed = true
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    performHaptic(v, android.view.HapticFeedbackConstants.CLOCK_TICK)
                    v.animate().cancel()
                    v.animate()
                        .setStartDelay(0L)
                        .scaleX(0.94f)
                        .scaleY(0.94f)
                        .setDuration(50)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - startX)
                    val dy = Math.abs(event.rawY - startY)
                    val slop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop
                    if (dx > slop || dy > slop) {
                        isClickAllowed = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.animate().cancel()
                        v.animate()
                            .setStartDelay(0L)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.animate().cancel()
                    v.animate()
                        .setStartDelay(0L)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                        .start()

                    if (isClickAllowed) {
                        performHaptic(v, android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick?.invoke() ?: v.performClick()
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.animate().cancel()
                    v.animate()
                        .setStartDelay(0L)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
            }
            true
        }
    }

    /**
     * Staggered cascade entrance animation for cards/items.
     */
    fun animateCascade(views: List<View>, baseDelay: Long = 30L, startDelayOffset: Long = 0L) {
        val visibleViews = views.filter { it.visibility == View.VISIBLE }
        visibleViews.forEachIndexed { index, view ->
            val density = view.resources.displayMetrics.density
            val liftPx = 32f * density
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = liftPx
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelayOffset + (index * baseDelay))
                .setDuration(340)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .withEndAction {
                    view.animate().setStartDelay(0L)
                }
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
     * Safe Undo SnackBar with 6-second delay before committing destructive actions.
     */
    fun showUndoSnackbar(view: View?, message: String, actionText: String = "Undo", durationMs: Int = 6000, onUndo: () -> Unit) {
        if (view == null) return
        val snackbar = Snackbar.make(view, message, durationMs)
            .setAction(actionText) {
                performHaptic(view)
                onUndo()
            }
            .setActionTextColor(Color.parseColor("#A5B4FC"))
        formatFloatingPillSnackbar(snackbar, view.context)
        snackbar.show()
    }

    fun showUndoSnackbar(context: Context?, message: String, actionText: String = "Undo", durationMs: Int = 6000, onUndo: () -> Unit) {
        if (context == null) return
        var activity: Activity? = context as? Activity
        if (activity == null && context is ContextWrapper) {
            activity = context.baseContext as? Activity
        }
        val rootView = activity?.findViewById<View>(android.R.id.content) ?: return
        showUndoSnackbar(rootView, message, actionText, durationMs, onUndo)
    }

    private fun formatFloatingPillSnackbar(snackbar: Snackbar, ctx: Context) {
        val sView = snackbar.view
        sView.setBackgroundResource(R.drawable.bg_snackbar_floating)

        // Make it float above navigation bar with 16dp horizontal and 24dp bottom margins
        val density = ctx.resources.displayMetrics.density
        val marginH = (16 * density).toInt()
        val marginB = (24 * density).toInt()

        val params = sView.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            params.setMargins(marginH, 0, marginH, marginB)
            sView.layoutParams = params
        }

        sView.elevation = 10f * density

        // Customize the text view to support multi-line text cleanly
        val textView = sView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.maxLines = 20
        textView.textSize = 13.5f
        textView.setTextColor(Color.parseColor("#F8FAFC"))
        textView.setLineSpacing(0f, 1.25f)
        try {
            val typeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.font_plus_jakarta_sans)
            if (typeface != null) textView.typeface = typeface
        } catch (_: Exception) {}

        // Action button styling
        val actionView = sView.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        actionView.textSize = 13.5f
        actionView.typeface = android.graphics.Typeface.DEFAULT_BOLD
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
    fun classifyContextLabel(contextText: String, targetSpanType: TextSpanType = TextSpanType.WORD): String {
        val clean = contextText.trim().trim('.', '!', '?', ':', ';', ',', '"', '\'', '-', '—')
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasTerminalPunct = contextText.trim().let { it.endsWith(".") || it.endsWith("?") || it.endsWith("!") }
        val isTrueSentence = words.size >= 8 || (words.size >= 5 && hasTerminalPunct)

        return when {
            words.size > 25 -> "Passage"
            isTrueSentence -> "Sentence"
            targetSpanType == TextSpanType.PHRASE -> "Context"
            targetSpanType == TextSpanType.WORD -> if (words.size in 2..7) "Context" else "Sentence"
            else -> "Context"
        }
    }

    private val avatarMemoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return (bitmap.byteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    private fun getAvatarDiskCacheFile(context: Context, url: String): File {
        val cacheDir = File(context.cacheDir, "avatar_cache").apply { if (!exists()) mkdirs() }
        val safeHash = url.hashCode().toString().replace("-", "n")
        return File(cacheDir, "avatar_$safeHash.jpg")
    }

    fun invalidateAvatarCache(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        avatarMemoryCache.remove(url)
        try {
            val diskFile = getAvatarDiskCacheFile(context, url)
            if (diskFile.exists()) diskFile.delete()
        } catch (_: Exception) {}
    }

    fun loadAvatarImage(url: String?, imageView: ImageView, fallbackView: View? = null) {
        if (url.isNullOrBlank()) {
            imageView.visibility = View.GONE
            fallbackView?.visibility = View.VISIBLE
            return
        }

        // 🟢 ZERO-LATENCY SYNCHRONOUS MEMORY CACHE HIT (0ms, no flicker)
        val memoryHit = avatarMemoryCache.get(url)
        if (memoryHit != null) {
            imageView.setImageBitmap(memoryHit)
            imageView.visibility = View.VISIBLE
            fallbackView?.visibility = View.GONE
            return
        }

        val context = imageView.context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 🟢 DISK CACHE CHECK
                val diskFile = getAvatarDiskCacheFile(context, url)
                if (diskFile.exists() && diskFile.length() > 0) {
                    val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                    if (diskBitmap != null) {
                        avatarMemoryCache.put(url, diskBitmap)
                        withContext(Dispatchers.Main) {
                            imageView.setImageBitmap(diskBitmap)
                            imageView.visibility = View.VISIBLE
                            fallbackView?.visibility = View.GONE
                        }
                        return@launch
                    }
                }

                if (url.startsWith("content://") || url.startsWith("file://")) {
                    val cr = context.contentResolver
                    val bitmap = try {
                        cr.openInputStream(Uri.parse(url))?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (_: Throwable) {
                        null
                    }
                    if (bitmap != null) {
                        avatarMemoryCache.put(url, bitmap)
                    }
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            imageView.visibility = View.VISIBLE
                            fallbackView?.visibility = View.GONE
                        } else {
                            imageView.visibility = View.GONE
                            fallbackView?.visibility = View.VISIBLE
                        }
                    }
                    return@launch
                }

                val resolvedUrl = if (url.contains("drive.google.com")) {
                    val fileId = when {
                        url.contains("id=") -> url.substringAfter("id=").substringBefore("&")
                        url.contains("/d/") -> url.substringAfter("/d/").substringBefore("/")
                        else -> null
                    }
                    if (!fileId.isNullOrBlank()) "https://lh3.googleusercontent.com/d/$fileId" else url
                } else {
                    url
                }

                val req = Request.Builder().url(resolvedUrl).build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            avatarMemoryCache.put(url, bitmap)
                            try {
                                diskFile.outputStream().use { it.write(bytes) }
                            } catch (_: Exception) {}

                            withContext(Dispatchers.Main) {
                                imageView.setImageBitmap(bitmap)
                                imageView.visibility = View.VISIBLE
                                fallbackView?.visibility = View.GONE
                            }
                            return@launch
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    imageView.visibility = View.GONE
                    fallbackView?.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    imageView.visibility = View.GONE
                    fallbackView?.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Sanitizes raw vocabulary strings extracted from OCR, dictionaries, or reading texts:
     * - Strips quotation marks ("Daily Routine" -> Daily Routine, 'word' -> word)
     * - Strips phonetic brackets (/word/, [word] -> word)
     * - Strips syllabification dots and bullets (dai·ly rou·tine -> daily routine)
     * - Strips IPA stress marks (ˈdeɪli -> deɪli)
     * - Strips leading/trailing punctuation (. , ! ? ; : ( ) etc.)
     * - Normalizes multiple spaces and trims
     */
    fun sanitizeVocabularyWord(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.trim()

        // 1. Strip enclosing or stray quotation marks
        s = s.replace(Regex("[\"“”'‘’`«»]"), "")

        // 2. Strip phonetic brackets and slashes
        s = s.replace(Regex("[\\[\\]/\\\\]"), "")

        // 3. Strip syllable dividers, middle dots, bullets
        s = s.replace(Regex("[·•‧]"), "")

        // 4. Strip phonetic stress marks
        s = s.replace(Regex("[ˈˌˈˏˊˋ]"), "")

        // 5. Strip leading and trailing non-alphanumeric punctuation (keep hyphens inside compound words)
        s = s.replace(Regex("^[\\s\\p{Punct}&&[^-]]+|[\\s\\p{Punct}&&[^-]]+$"), "")

        // 6. Normalize multiple internal whitespaces to a single space
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }
}