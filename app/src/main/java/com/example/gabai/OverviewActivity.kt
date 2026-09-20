package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class OverviewActivity : AppCompatActivity() {

    private var lastAiResult: String = ""
    private var lastExplanationAudioText: String = ""
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false

    // In-memory cache for on-demand related question answers (pay-per-need token optimization)
    private val questionAnswers = mutableMapOf<String, String>()

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

        if (scannedText.isNotEmpty()) {
            // Generate AI Overview and Question Prompts
            generateAIOverview(scannedText, surroundingSentence, isSingleWord, isPhrase, isSentence)

            // Deterministic, Zero-AI Context-Aware Visual Query
            val visualQuery = buildDeterministicVisualQuery(scannedText, surroundingSentence, isSentence)
            val imageWebView = findViewById<WebView>(R.id.image_webview)
            loadGoogleImages(imageWebView, visualQuery)
        } else {
            GabAIUtils.showSnackbar(this, "No text provided")
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
    // 2. ZERO-AI DETERMINISTIC VISUAL CONTEXT SEARCH
    // =========================================================================
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
                "$targetClean $contextNoun diagram"
            } else {
                "$targetClean diagram"
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

            return if (keywords.isNotEmpty()) "$keywords diagram" else "$targetText diagram"
        }
    }

    private fun loadGoogleImages(webView: WebView, query: String) {
        val visualsContainer = findViewById<View>(R.id.visuals_container)
        visualsContainer?.visibility = View.VISIBLE

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

        val markwon = Markwon.create(this)

        lifecycleScope.launch {
            try {
                val selectionType = when {
                    isSingleWord -> "single word"
                    isPhrase -> "multi-word phrase"
                    else -> "full sentence"
                }

                val prompt = """
                    You are an educational tutor helping a high school student understand this reading material.
                    Target Selection: "$inputText"
                    Enclosing Sentence: "$surroundingSentence"
                    Selection Type: $selectionType

                    Analyze the selection in context and return ONLY a valid JSON object matching this schema without markdown fences:
                    {
                      "phonetics": "/.../ (IPA pronunciation, or empty string if phrase/sentence)",
                      "partOfSpeech": "noun / verb / adjective / phrase / clause / statement",
                      "definition": "Clear, concise definition or core meaning in 1-2 sentences. Use clean educational language. Avoid storytelling framing, avoid filler.",
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
                    relatedQuestions.add("How does this concept function in this context?")
                    relatedQuestions.add("Why is this essential to the topic?")
                    relatedQuestions.add("What happens if this process is altered?")
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
                                val answerPrompt = """
                                    You are an educational tutor for high school students.
                                    Target Selection: "$targetText"
                                    Context: "$surroundingSentence"
                                    Question: "$questionText"

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

        val locale = if (selectedLang == "Tagalog" || selectedLang == "Taglish") {
            Locale("fil", "PH")
        } else {
            Locale.US
        }

        tts.language = locale
        val cleanText = text.replace(Regex("[#*<>_]"), "")
        tts.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}