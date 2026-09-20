package com.example.gabai

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.nl.languageid.LanguageIdentification
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class OverviewActivity : AppCompatActivity() {

    private var lastAiResult: String = ""
    private var lastExplanationAudioText: String = ""
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    // Target content retention for dynamic language switching
    private var currentInputText: String = ""
    private var currentSurroundingSentence: String = ""
    private var currentIsSingleWord: Boolean = false
    private var currentIsPhrase: Boolean = false
    private var currentIsSentence: Boolean = false

    // In-memory cache for on-demand related question answers (pay-per-need token optimization)
    private val questionAnswers = mutableMapOf<String, String>()

    // Visual Context State
    private var curatedBitmap: Bitmap? = null
    private var curatedTitle: String = ""
    private var curatedCaption: String = ""
    private var currentVisualTerm: String = ""
    private var defaultVisualQuery: String = ""
    private var activeVisualMode: VisualMode = VisualMode.OVERVIEW

    private enum class VisualMode {
        OVERVIEW, REAL_WORLD, DIAGRAMS
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overview)
        WebView.setWebContentsDebuggingEnabled(true)

        // Handle Window Insets for top header
        val header = findViewById<View>(R.id.header_container)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        // Back button navigation
        findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        val scannedText = intent.getStringExtra("SELECTED_TEXT")?.trim() ?: ""
        val surroundingSentenceRaw = intent.getStringExtra("SURROUNDING_SENTENCE")?.trim() ?: ""
        val surroundingSentence = if (surroundingSentenceRaw.isNotBlank()) surroundingSentenceRaw else scannedText

        // Classify selection mode
        val words = scannedText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val isSingleWord = words.size == 1
        val isPhrase = words.size in 2..5 && !scannedText.contains(Regex("[.?!]"))
        val isSentence = !isSingleWord && !isPhrase

        // Configure Hero Context Card
        configureHeroContextCard(scannedText, surroundingSentence, isSingleWord, isPhrase, isSentence)

        // Initialize Text-To-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }

        // Setup audio buttons
        findViewById<View>(R.id.btn_speak_word)?.setOnClickListener {
            speakWithDetection(scannedText)
        }

        findViewById<View>(R.id.btn_speak_sentence)?.setOnClickListener {
            speakWithDetection(surroundingSentence)
        }

        findViewById<ImageButton>(R.id.btn_speak_explanation)?.setOnClickListener {
            val audioText = if (lastExplanationAudioText.isNotEmpty()) lastExplanationAudioText else lastAiResult
            speakExplanation(audioText)
        }

        // Favorite button
        val favoriteBtn = findViewById<ImageButton>(R.id.btn_favorite)
        favoriteBtn?.setOnClickListener {
            if (lastAiResult.isNotEmpty()) {
                saveToFavorites(scannedText, lastAiResult)
                favoriteBtn.setImageResource(R.drawable.ic_star_filled)
                GabAIUtils.showSnackbar(this, "Saved to Favorites! ⭐")
            }
        }

        currentInputText = scannedText
        currentSurroundingSentence = surroundingSentence
        currentIsSingleWord = isSingleWord
        currentIsPhrase = isPhrase
        currentIsSentence = isSentence

        setupLanguageBadge()

        if (scannedText.isNotEmpty()) {
            // Generate AI Overview and Question Prompts
            generateAIOverview(scannedText, surroundingSentence, isSingleWord, isPhrase, isSentence)

            // Zero-AI Curated Wikimedia Diagram & Deterministic Visual Context
            val visualQuery = buildDeterministicVisualQuery(scannedText, surroundingSentence, isSentence)
            defaultVisualQuery = visualQuery
            currentVisualTerm = if (isSingleWord || isPhrase) {
                scannedText.trim()
            } else {
                extractCoreSubject(surroundingSentence)
            }
            setupVisualContainer(currentVisualTerm, defaultVisualQuery)
        } else {
            GabAIUtils.showSnackbar(this, "No text provided")
        }
    }

    private fun setupLanguageBadge() {
        val tvLanguageBadge = findViewById<TextView>(R.id.tv_language_badge) ?: return
        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("ai_language_pref", "English") ?: "English"
        tvLanguageBadge.text = "🌐 $currentLang ▾"

        tvLanguageBadge.setOnClickListener {
            val languages = arrayOf("English", "Taglish", "Tagalog")
            val activeLang = prefs.getString("ai_language_pref", "English") ?: "English"
            val selectedIndex = languages.indexOf(activeLang).let { if (it >= 0) it else 0 }

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("AI Explanation Language")
                .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                    val chosen = languages[which]
                    if (chosen != activeLang) {
                        prefs.edit().putString("ai_language_pref", chosen).apply()
                        tvLanguageBadge.text = "🌐 $chosen ▾"
                        GabAIUtils.showSnackbar(this, "AI explanation switched to $chosen")
                        questionAnswers.clear()
                        if (currentInputText.isNotEmpty()) {
                            generateAIOverview(
                                currentInputText,
                                currentSurroundingSentence,
                                currentIsSingleWord,
                                currentIsPhrase,
                                currentIsSentence
                            )
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = android.content.Intent(this, FloatingControlService::class.java)
        intent.action = "ACTION_HIDE"
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) {
            tts.stop()
        }
        val isEnabled = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).getBoolean("bubble_enabled", false)
        if (isEnabled) {
            val intent = android.content.Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    // =========================================================================
    // 1. HERO CONTEXT CARD CONFIGURATION & SPANNABLE PILL HIGHLIGHT
    // =========================================================================
    private fun configureHeroContextCard(
        targetText: String,
        surroundingSentence: String,
        isSingleWord: Boolean,
        isPhrase: Boolean,
        isSentence: Boolean
    ) {
        val contextBadge = findViewById<TextView>(R.id.tv_context_badge)
        val selectedTextView = findViewById<TextView>(R.id.selected_text_view)
        val wordSpeakBtn = findViewById<View>(R.id.btn_speak_word)
        val sentenceSpeakBtn = findViewById<View>(R.id.btn_speak_sentence)
        val wordSpeakLabel = findViewById<TextView>(R.id.tv_btn_speak_word)

        when {
            isSingleWord -> {
                contextBadge?.text = "IN-SENTENCE CONTEXT"
                wordSpeakLabel?.text = "Word"
                wordSpeakBtn?.visibility = View.VISIBLE
                sentenceSpeakBtn?.visibility = View.VISIBLE
            }
            isPhrase -> {
                contextBadge?.text = "PHRASE IN CONTEXT"
                wordSpeakLabel?.text = "Phrase"
                wordSpeakBtn?.visibility = View.VISIBLE
                sentenceSpeakBtn?.visibility = View.VISIBLE
            }
            else -> {
                contextBadge?.text = "FULL STATEMENT"
                wordSpeakLabel?.text = "Listen"
                wordSpeakBtn?.visibility = View.VISIBLE
                sentenceSpeakBtn?.visibility = View.GONE
            }
        }

        // Render sentence with highlight
        if (isSentence) {
            selectedTextView?.text = surroundingSentence
        } else {
            val spannable = SpannableStringBuilder(surroundingSentence)
            val startIndex = surroundingSentence.indexOf(targetText, ignoreCase = true)
            if (startIndex >= 0) {
                val endIndex = startIndex + targetText.length
                // High-visibility gold text with translucent pill background on gradient
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#40FFFFFF")),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#FFE600")),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            selectedTextView?.text = spannable
        }
    }

    // =========================================================================
    // 2. ZERO-AI DETERMINISTIC VISUAL CONTEXT SEARCH & CURATED DIAGRAM
    // =========================================================================
    private fun extractCoreSubject(sentence: String): String {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "by", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after", "above",
            "below", "to", "from", "up", "down", "off", "over", "under", "again", "further", "then",
            "once", "here", "there", "when", "where", "why", "how", "all", "any", "both", "each",
            "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own",
            "same", "so", "than", "too", "very", "can", "will", "just", "should", "now", "and",
            "or", "but", "if", "because", "as", "until", "while", "of", "that", "this", "these",
            "those", "their", "they", "its", "it", "which", "what", "who", "whom"
        )
        val words = sentence
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it.lowercase() !in stopWords }

        return words.maxByOrNull { it.length } ?: sentence.take(20)
    }

    private fun buildDeterministicVisualQuery(
        targetText: String,
        surroundingSentence: String,
        isSentence: Boolean
    ): String {
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "by", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after", "above",
            "below", "to", "from", "up", "down", "in", "out", "off", "over", "under", "again",
            "further", "then", "once", "here", "there", "when", "where", "why", "how", "all", "any",
            "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not",
            "only", "own", "same", "so", "than", "too", "very", "can", "will", "just", "should",
            "now", "and", "or", "but", "if", "because", "as", "until", "while", "of", "that",
            "this", "these", "those", "their", "they", "its", "it", "which", "what", "who", "whom"
        )

        if (!isSentence) {
            val targetClean = targetText.trim().replace(Regex("[^a-zA-Z0-9\\s]"), "")
            // Extract top context noun from surrounding sentence that isn't the target word
            val sentenceWords = surroundingSentence
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 3 && it.lowercase() !in stopWords && !targetClean.contains(it, ignoreCase = true) }

            val contextNoun = sentenceWords.firstOrNull() ?: ""
            return if (contextNoun.isNotEmpty()) {
                "$targetClean $contextNoun"
            } else {
                targetClean
            }
        } else {
            // For a full sentence: extract top 2-3 longest keywords
            val keywords = surroundingSentence
                .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length > 3 && it.lowercase() !in stopWords }
                .distinctBy { it.lowercase() }
                .sortedByDescending { it.length }
                .take(2)
                .joinToString(" ")

            return if (keywords.isNotEmpty()) keywords else targetText
        }
    }

    private fun setupVisualContainer(term: String, fallbackQuery: String) {
        val visualsContainer = findViewById<View>(R.id.visuals_container)
        visualsContainer?.visibility = View.VISIBLE

        val chipDiagram = findViewById<TextView>(R.id.chip_diagram)
        val chipMicroscopic = findViewById<TextView>(R.id.chip_microscopic)
        val chipProcess = findViewById<TextView>(R.id.chip_process)
        val btnFullscreen = findViewById<ImageButton>(R.id.btn_fullscreen_visual)
        val ivDiagram = findViewById<ImageView>(R.id.iv_curated_diagram)
        val tvBadge = findViewById<TextView>(R.id.tv_visual_badge)
        val curatedContainer = findViewById<View>(R.id.container_curated_diagram)
        val imageWebView = findViewById<WebView>(R.id.image_webview)
        val progressVisual = findViewById<ProgressBar>(R.id.progress_visual)

        // Show progress spinner initially
        progressVisual?.visibility = View.VISIBLE
        curatedContainer?.visibility = View.GONE
        imageWebView?.visibility = View.GONE

        // Lightbox trigger
        btnFullscreen?.setOnClickListener {
            if (curatedBitmap != null) {
                showFullscreenLightbox(curatedBitmap, curatedTitle, curatedCaption)
            }
        }
        ivDiagram?.setOnClickListener {
            if (curatedBitmap != null) {
                showFullscreenLightbox(curatedBitmap, curatedTitle, curatedCaption)
            }
        }

        // Chip Clicks
        chipDiagram?.setOnClickListener {
            activeVisualMode = VisualMode.OVERVIEW
            if (chipMicroscopic != null && chipProcess != null) {
                updateChipStyle(chipDiagram, listOf(chipMicroscopic, chipProcess))
            }
            if (curatedBitmap != null) {
                tvBadge?.text = "ENCYCLOPEDIA"
                curatedContainer?.visibility = View.VISIBLE
                imageWebView?.visibility = View.GONE
                progressVisual?.visibility = View.GONE
            } else {
                tvBadge?.text = "WEB VISUALS"
                curatedContainer?.visibility = View.GONE
                imageWebView?.visibility = View.VISIBLE
                progressVisual?.visibility = View.GONE
                if (imageWebView != null) loadGoogleImages(imageWebView, fallbackQuery)
            }
        }

        chipMicroscopic?.setOnClickListener {
            activeVisualMode = VisualMode.REAL_WORLD
            if (chipDiagram != null && chipProcess != null) {
                updateChipStyle(chipMicroscopic, listOf(chipDiagram, chipProcess))
            }
            tvBadge?.text = "REAL-WORLD"
            curatedContainer?.visibility = View.GONE
            imageWebView?.visibility = View.VISIBLE
            progressVisual?.visibility = View.GONE
            val realWorldQuery = "$term real world photo example"
            if (imageWebView != null) loadGoogleImages(imageWebView, realWorldQuery)
        }

        chipProcess?.setOnClickListener {
            activeVisualMode = VisualMode.DIAGRAMS
            if (chipDiagram != null && chipMicroscopic != null) {
                updateChipStyle(chipProcess, listOf(chipDiagram, chipMicroscopic))
            }
            tvBadge?.text = "DIAGRAMS & CHARTS"
            curatedContainer?.visibility = View.GONE
            imageWebView?.visibility = View.VISIBLE
            progressVisual?.visibility = View.GONE
            val diagramQuery = "$term diagram chart infographic"
            if (imageWebView != null) loadGoogleImages(imageWebView, diagramQuery)
        }

        // Fetch curated diagram asynchronously
        fetchCuratedDiagram(term, fallbackQuery)
    }

    private fun updateChipStyle(selected: TextView, others: List<TextView>) {
        selected.setBackgroundResource(R.drawable.bg_chip_selected)
        selected.setTextColor(Color.WHITE)
        for (other in others) {
            other.setBackgroundResource(R.drawable.bg_chip_unselected)
            other.setTextColor(Color.parseColor("#475569"))
        }
    }

    private fun fetchCuratedDiagram(term: String, fallbackQuery: String) {
        val cleanTerm = term.replace(Regex("[^a-zA-Z0-9\\s-]"), "").trim()
        if (cleanTerm.isEmpty()) {
            fallbackToWebSearch(fallbackQuery)
            return
        }

        val encodedTerm = URLEncoder.encode(cleanTerm.replace(" ", "_"), "UTF-8")
        val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTerm"

        val request = Request.Builder()
            .url(summaryUrl)
            .header("User-Agent", "GabAI-Android-App/1.0 (educational-reading-assistant)")
            .build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()
                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val title = json.optString("title", cleanTerm)
                    val description = json.optString("description", "")
                    val thumbnailObj = json.optJSONObject("thumbnail")
                    val originalObj = json.optJSONObject("originalimage")

                    var imageUrl = thumbnailObj?.optString("source") ?: ""
                    if (imageUrl.isEmpty()) {
                        val orig = originalObj?.optString("source") ?: ""
                        if (!orig.endsWith(".svg", ignoreCase = true)) {
                            imageUrl = orig
                        }
                    }

                    if (imageUrl.isNotEmpty()) {
                        if (imageUrl.startsWith("//")) {
                            imageUrl = "https:$imageUrl"
                        }
                        // Sharpen thumbnail if available by requesting 640px preview
                        val hiresUrl = if (imageUrl.contains("/320px-")) {
                            imageUrl.replace("/320px-", "/640px-")
                        } else {
                            imageUrl
                        }

                        var imgReq = Request.Builder()
                            .url(hiresUrl)
                            .header("User-Agent", "GabAI-Android-App/1.0 (educational-reading-assistant)")
                            .build()
                        var imgResp = httpClient.newCall(imgReq).execute()

                        if (!imgResp.isSuccessful && hiresUrl != imageUrl) {
                            imgReq = Request.Builder()
                                .url(imageUrl)
                                .header("User-Agent", "GabAI-Android-App/1.0 (educational-reading-assistant)")
                                .build()
                            imgResp = httpClient.newCall(imgReq).execute()
                        }

                        if (imgResp.isSuccessful) {
                            val inputStream = imgResp.body?.byteStream()
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            if (bitmap != null) {
                                curatedBitmap = bitmap
                                curatedTitle = title
                                curatedCaption = if (description.isNotBlank()) description else title

                                launch(Dispatchers.Main) {
                                    val curatedContainer = findViewById<View>(R.id.container_curated_diagram)
                                    val ivDiagram = findViewById<ImageView>(R.id.iv_curated_diagram)
                                    val tvCaption = findViewById<TextView>(R.id.tv_diagram_caption)
                                    val tvBadge = findViewById<TextView>(R.id.tv_visual_badge)
                                    val progressVisual = findViewById<ProgressBar>(R.id.progress_visual)
                                    val imageWebView = findViewById<WebView>(R.id.image_webview)

                                    progressVisual?.visibility = View.GONE
                                    ivDiagram?.setImageBitmap(bitmap)
                                    tvCaption?.text = curatedCaption

                                    if (activeVisualMode == VisualMode.OVERVIEW) {
                                        tvBadge?.text = "ENCYCLOPEDIA"
                                        curatedContainer?.visibility = View.VISIBLE
                                        imageWebView?.visibility = View.GONE
                                    }
                                }
                                return@launch
                            }
                        }
                    }
                }
                // Fallback to web search
                launch(Dispatchers.Main) {
                    fallbackToWebSearch(fallbackQuery)
                }
            } catch (_: Exception) {
                launch(Dispatchers.Main) {
                    fallbackToWebSearch(fallbackQuery)
                }
            }
        }
    }

    private fun fallbackToWebSearch(query: String) {
        val progressVisual = findViewById<ProgressBar>(R.id.progress_visual)
        val curatedContainer = findViewById<View>(R.id.container_curated_diagram)
        val imageWebView = findViewById<WebView>(R.id.image_webview)
        val tvBadge = findViewById<TextView>(R.id.tv_visual_badge)

        progressVisual?.visibility = View.GONE
        curatedContainer?.visibility = View.GONE
        imageWebView?.visibility = View.VISIBLE
        tvBadge?.text = "WEB VISUALS"

        if (imageWebView != null) {
            loadGoogleImages(imageWebView, query)
        }
    }

    private fun showFullscreenLightbox(bitmap: Bitmap?, title: String?, caption: String?) {
        if (bitmap == null) return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#EE0F172A")))

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(24, 140, 24, 160)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
        }
        root.addView(imageView)

        // Top Bar
        val header = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                setMargins(40, 60, 40, 0)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvHeader = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = title ?: "Educational Diagram"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(tvHeader)

        val btnClose = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding(20, 20, 20, 20)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(btnClose)
        root.addView(header)

        // Bottom Caption
        if (!caption.isNullOrBlank()) {
            val tvCaption = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(32, 0, 32, 60)
                }
                text = caption
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 13f
                gravity = Gravity.CENTER
            }
            root.addView(tvCaption)
        }

        root.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun loadGoogleImages(webView: WebView, query: String) {
        val visualsContainer = findViewById<View>(R.id.visuals_container)
        visualsContainer?.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.101 Mobile Safari/537.36"

        // Prevent parent NestedScrollView from stealing touch events
        webView.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Clean Google Images clutter while allowing smooth scrolling
                view?.evaluateJavascript(
                    """
                    (function() {
                        function hide(selector) {
                            var elements = document.querySelectorAll(selector);
                            for (var i = 0; i < elements.length; i++) {
                                elements[i].style.display = 'none';
                            }
                        }
                        hide('.eK9Ieb'); 
                        hide('.dmFHw');
                        hide('header');
                        hide('#header');
                        document.addEventListener('click', function(e) {
                            e.stopImmediatePropagation();
                            e.preventDefault();
                            return false;
                        }, true);
                    })();
                    """.trimIndent(), null
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encodedQuery"
        webView.loadUrl(searchUrl)
    }

    // =========================================================================
    // 3. AI OVERVIEW & STRUCTURED RESPONSE GENERATION
    // =========================================================================
    private fun generateAIOverview(
        inputText: String,
        surroundingSentence: String,
        isSingleWord: Boolean,
        isPhrase: Boolean,
        isSentence: Boolean
    ) {
        val loadingContainer = findViewById<View>(R.id.loading_container)
        val resultContainer = findViewById<View>(R.id.result_container)
        val targetWordView = findViewById<TextView>(R.id.tv_target_word)
        val phoneticsView = findViewById<TextView>(R.id.tv_phonetics)
        val posView = findViewById<TextView>(R.id.tv_part_of_speech)
        val definitionTextView = findViewById<TextView>(R.id.ai_result_text)
        val inSentenceLabel = findViewById<TextView>(R.id.tv_in_sentence_label)
        val inSentenceTextView = findViewById<TextView>(R.id.tv_in_sentence)
        val inSentenceContainer = findViewById<View>(R.id.ll_in_sentence_container)

        loadingContainer?.visibility = View.VISIBLE
        resultContainer?.visibility = View.GONE

        val markwon = Markwon.create(this)

        lifecycleScope.launch {
            try {
                val selectionType = when {
                    isSingleWord -> "single word"
                    isPhrase -> "multi-word phrase"
                    else -> "full sentence"
                }

                val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
                val aiLanguage = prefs.getString("ai_language_pref", "English") ?: "English"

                val languageDirective = when (aiLanguage) {
                    "Tagalog" -> "CRITICAL LANGUAGE DIRECTIVE: The user requested explanations in Filipino / Tagalog. You MUST write the 'definition', 'inSentenceRole', and all 'relatedQuestions' in clear, fluent, natural Tagalog/Filipino. Technical, medical, scientific, or loan words may retain standard terminology or common Filipino equivalents."
                    "Taglish" -> "CRITICAL LANGUAGE DIRECTIVE: The user requested explanations in Taglish (Filipino mixed with English). You MUST write the 'definition', 'inSentenceRole', and all 'relatedQuestions' in conversational Taglish as used by Filipino students. Keep scientific, medical, and academic terms in English while explaining concepts and sentence roles in conversational Filipino/Taglish."
                    else -> "CRITICAL LANGUAGE DIRECTIVE: Write the 'definition', 'inSentenceRole', and all 'relatedQuestions' in clear, concise educational English suitable for high school students."
                }

                val prompt = """
                    You are an educational tutor helping a high school student understand this reading material.
                    Target Selection: "$inputText"
                    Enclosing Sentence: "$surroundingSentence"
                    Selection Type: $selectionType
                    $languageDirective

                    Analyze the selection in context and return ONLY a valid JSON object matching this schema without markdown fences:
                    {
                      "phonetics": "/.../ (IPA pronunciation, or empty string if phrase/sentence)",
                      "partOfSpeech": "noun / verb / adjective / phrase / clause / statement",
                      "definition": "Clear, concise definition or core meaning in 1-2 sentences. Avoid storytelling framing, avoid filler.",
                      "inSentenceRole": "1-2 sentences explaining specifically how this selection operates or functions within the enclosing sentence.",
                      "relatedQuestions": [
                        "Direct cause, effect, or function question about this concept",
                        "Direct comparative or mechanism question",
                        "Direct real-world or critical thinking question"
                      ]
                    }
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val rawText = response.text?.trim() ?: ""

                loadingContainer?.visibility = View.GONE
                resultContainer?.visibility = View.VISIBLE

                // Parse structured JSON
                val cleanJson = rawText
                    .substringAfter("```json", "")
                    .substringBeforeLast("```")
                    .ifEmpty { rawText.substringAfter("```", "").substringBeforeLast("```") }
                    .ifEmpty { rawText }
                    .trim()

                var phonetics = ""
                var partOfSpeech = if (isSingleWord) "WORD" else if (isPhrase) "PHRASE" else "STATEMENT"
                var definition = ""
                var inSentenceRole = ""
                val relatedQuestions = mutableListOf<String>()

                try {
                    val json = JSONObject(cleanJson)
                    phonetics = json.optString("phonetics", "").trim()
                    partOfSpeech = json.optString("partOfSpeech", partOfSpeech).trim().uppercase()
                    definition = json.optString("definition", "").trim()
                    inSentenceRole = json.optString("inSentenceRole", "").trim()
                    val questionsArray = json.optJSONArray("relatedQuestions")
                    if (questionsArray != null) {
                        for (i in 0 until questionsArray.length()) {
                            val q = questionsArray.optString(i).trim()
                            if (q.isNotEmpty()) relatedQuestions.add(q)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback in case raw text wasn't strict JSON
                    definition = rawText
                    val fallbackQuestions = when (aiLanguage) {
                        "Tagalog" -> listOf(
                            "Paano gumagana ang konseptong ito sa kontekstong ito?",
                            "Bakit mahalaga ito sa paksang binabasa?",
                            "Ano ang mangyayari kung babaguhin ang prosesong ito?"
                        )
                        "Taglish" -> listOf(
                            "Paano nagfa-function ang concept na ito sa context?",
                            "Bakit essential ito sa topic na binabasa?",
                            "Ano ang mangyayari kung ma-alter ang process na ito?"
                        )
                        else -> listOf(
                            "How does this concept function in this context?",
                            "Why is this essential to the topic?",
                            "What happens if this process is altered?"
                        )
                    }
                    relatedQuestions.addAll(fallbackQuestions)
                }

                lastAiResult = definition
                lastExplanationAudioText = if (inSentenceRole.isNotEmpty()) "$definition. $inSentenceRole" else definition

                // Populate UI
                if (isSentence) {
                    targetWordView?.text = "Sentence Breakdown"
                    phoneticsView?.visibility = View.GONE
                    posView?.text = if (partOfSpeech.isNotEmpty()) partOfSpeech else "STATEMENT"
                    inSentenceLabel?.text = "CORE PROPOSITION & STRUCTURE"
                } else {
                    targetWordView?.text = inputText
                    if (phonetics.isNotEmpty()) {
                        phoneticsView?.text = phonetics
                        phoneticsView?.visibility = View.VISIBLE
                    } else {
                        phoneticsView?.visibility = View.GONE
                    }
                    posView?.text = partOfSpeech
                    inSentenceLabel?.text = "ROLE IN THIS SENTENCE"
                }

                markwon.setMarkdown(definitionTextView, definition)

                if (inSentenceRole.isNotEmpty()) {
                    inSentenceTextView?.text = inSentenceRole
                    inSentenceContainer?.visibility = View.VISIBLE
                } else {
                    inSentenceContainer?.visibility = View.GONE
                }

                // Populate Related Questions Vertically
                populateRelatedQuestions(relatedQuestions, inputText, surroundingSentence)

                // Save to History with actual enclosing sentence as originalContext
                saveToHistory(inputText, definition, surroundingSentence)

            } catch (e: Exception) {
                loadingContainer?.visibility = View.GONE
                definitionTextView.text = "Connection Error: ${e.localizedMessage}"
                resultContainer?.visibility = View.VISIBLE
            }
        }
    }

    // =========================================================================
    // 4. VERTICAL RELATED QUESTIONS (ON-DEMAND PAY-PER-NEED ANSWERS)
    // =========================================================================
    private fun populateRelatedQuestions(
        questions: List<String>,
        targetText: String,
        surroundingSentence: String
    ) {
        val questionsContainer = findViewById<LinearLayout>(R.id.ll_related_questions) ?: return
        questionsContainer.removeAllViews()

        if (questions.isEmpty()) {
            findViewById<View>(R.id.container_related_questions)?.visibility = View.GONE
            return
        }

        findViewById<View>(R.id.container_related_questions)?.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)

        for (questionText in questions) {
            val itemView = inflater.inflate(R.layout.item_related_question, questionsContainer, false)
            val tvTitle = itemView.findViewById<TextView>(R.id.tv_question_title)
            val ivChevron = itemView.findViewById<ImageView>(R.id.iv_question_chevron)
            val progressBar = itemView.findViewById<ProgressBar>(R.id.progress_question)
            val answerContainer = itemView.findViewById<View>(R.id.ll_answer_container)
            val tvAnswer = itemView.findViewById<TextView>(R.id.tv_question_answer)

            tvTitle.text = questionText

            itemView.setOnClickListener {
                val isCurrentlyExpanded = answerContainer.visibility == View.VISIBLE

                if (isCurrentlyExpanded) {
                    answerContainer.visibility = View.GONE
                    ivChevron.setImageResource(R.drawable.ic_expand_more)
                } else {
                    val cachedAnswer = questionAnswers[questionText]
                    if (cachedAnswer != null) {
                        tvAnswer.text = cachedAnswer
                        answerContainer.visibility = View.VISIBLE
                        ivChevron.setImageResource(R.drawable.ic_expand_less)
                    } else {
                        // Micro on-demand Gemini call (fast, token-capped)
                        progressBar.visibility = View.VISIBLE
                        ivChevron.visibility = View.GONE

                        lifecycleScope.launch {
                            try {
                                val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
                                val aiLanguage = prefs.getString("ai_language_pref", "English") ?: "English"
                                val langDirective = when (aiLanguage) {
                                    "Tagalog" -> "Answer directly in natural Filipino / Tagalog."
                                    "Taglish" -> "Answer directly in conversational Taglish (Filipino mixed with English)."
                                    else -> "Answer directly in clear educational English."
                                }

                                val answerPrompt = """
                                    You are an educational tutor for high school students.
                                    Target Selection: "$targetText"
                                    Context: "$surroundingSentence"
                                    Question: "$questionText"
                                    $langDirective

                                    Provide a concise, direct 2-sentence answer directly addressing the question. Avoid introductory fluff.
                                """.trimIndent()

                                val resp = generativeModel.generateContent(answerPrompt)
                                val generatedAnswer = resp.text?.trim() ?: "Answer currently unavailable."

                                questionAnswers[questionText] = generatedAnswer
                                progressBar.visibility = View.GONE
                                ivChevron.visibility = View.VISIBLE
                                ivChevron.setImageResource(R.drawable.ic_expand_less)
                                tvAnswer.text = generatedAnswer
                                answerContainer.visibility = View.VISIBLE
                            } catch (_: Exception) {
                                progressBar.visibility = View.GONE
                                ivChevron.visibility = View.VISIBLE
                                tvAnswer.text = "Could not load answer. Please check your network connection."
                                answerContainer.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }

            questionsContainer.addView(itemView)
        }
    }

    // =========================================================================
    // 5. FIRESTORE PERSISTENCE (FAVORITES & HISTORY)
    // =========================================================================
    private fun saveToFavorites(word: String, definition: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        if (XPManager.canEarnXP(this)) {
            val leveledUp = XPManager.addXP(this, 10)
            if (leveledUp) {
                GabAIUtils.showSnackbar(this, "LEVEL UP! You are now Level ${XPManager.getLevel(this)}! 🎉")
            } else {
                GabAIUtils.showSnackbar(this, "Saved! +10 XP gained")
            }
        } else {
            GabAIUtils.showSnackbar(this, "Saved to Favorites! ⭐")
        }

        val favEntry = hashMapOf(
            "word" to word,
            "definition" to definition,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .collection("favorites").document(word)
            .set(favEntry)
            .addOnSuccessListener {
                if (BuildConfig.DEBUG) android.util.Log.d("GabAI_DB", "Favorite synced to cloud")
                QuestManager.addProgress(this, QuestManager.QUEST_SAVE)
                db.collection("users").document(uid).update(
                    "quests_completed",
                    com.google.firebase.firestore.FieldValue.arrayUnion("save")
                )
            }
    }

    private fun saveToHistory(text: String, aiResult: String, originalContext: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val timestamp = System.currentTimeMillis()

        val historyEntry = hashMapOf(
            "word" to text,
            "explanation" to aiResult,
            "timestamp" to timestamp,
            "originalContext" to originalContext,
            "nextReview" to timestamp,
            "interval" to 1,
            "easeFactor" to 2.5
        )

        db.collection("users").document(uid)
            .collection("history").document(timestamp.toString())
            .set(historyEntry)
            .addOnSuccessListener {
                if (BuildConfig.DEBUG) android.util.Log.d("GabAI_DB", "Cloud save successful!")
            }
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) android.util.Log.e("GabAI_DB", "Error saving history: ", e)
            }
    }

    // =========================================================================
    // 6. TEXT-TO-SPEECH (TTS)
    // =========================================================================
    private fun speakWithDetection(text: String) {
        if (!isTtsReady || text.isEmpty()) return

        val loader = findViewById<ProgressBar>(R.id.progress_tts_selected)
        loader?.visibility = View.VISIBLE

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                loader?.visibility = View.GONE
                val locale = if (languageCode == "fil" || languageCode == "tl") {
                    Locale("fil", "PH")
                } else {
                    Locale.US
                }
                tts.language = locale
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
            .addOnFailureListener {
                loader?.visibility = View.GONE
                tts.language = Locale.US
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
    }

    private fun speakExplanation(text: String) {
        if (!isTtsReady || text.isEmpty()) return

        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val selectedLang = prefs.getString("ai_language_pref", "English") ?: "English"

        val filLocale = Locale("fil", "PH")
        val tlLocale = Locale("tl", "PH")

        val locale = if (selectedLang == "Tagalog" || selectedLang == "Taglish") {
            if (tts.isLanguageAvailable(filLocale) >= TextToSpeech.LANG_AVAILABLE) {
                filLocale
            } else if (tts.isLanguageAvailable(tlLocale) >= TextToSpeech.LANG_AVAILABLE) {
                tlLocale
            } else {
                Locale.US
            }
        } else {
            Locale.US
        }

        tts.language = locale
        val cleanText = text.replace(Regex("[#*<>_]"), "")
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}