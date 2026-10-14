package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class OverviewActivity : AppCompatActivity() {

    private var lastAiResult: String = ""
    private var lastExplanationAudioText: String = ""
    private var lastPartOfSpeech: String = ""
    private var lastPhonetics: String = ""
    private var lastInSentenceRole: String = ""
    private var selectionTypeLabel: String = "Word"
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private var isTtsInitializing = false
    private var pendingTtsAction: (() -> Unit)? = null

    // TTS Play / Pause / Resume State
    private enum class TtsPlaybackState { IDLE, LOADING, PLAYING, PAUSED }
    private var ttsExplanationState = TtsPlaybackState.IDLE
    private var fullExplanationText: String = ""
    private var lastCharOffset: Int = 0
    private var currentUtteranceOffset: Int = 0

    // In-memory cache for on-demand related question answers (pay-per-need token optimization)
    private val questionAnswers = mutableMapOf<String, String>()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = com.google.ai.client.generativeai.type.generationConfig {
            temperature = 0.2f
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overview)

        // Handle Window Insets for top header
        val header = findViewById<View>(R.id.header_container)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        // Setup Frosted Header Backdrop Blur
        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_overview)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_overview)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        // Back button navigation
        findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        val scannedText = intent.getStringExtra("SELECTED_TEXT")?.trim() ?: ""
        val surroundingSentenceRaw = intent.getStringExtra("SURROUNDING_SENTENCE")?.trim() ?: ""
        val surroundingSentence = if (surroundingSentenceRaw.isNotBlank()) surroundingSentenceRaw else scannedText

        // Classify selection mode (Word vs Phrase vs Sentence aware)
        val textClassification = GabAIUtils.classifyTextSpan(scannedText)
        val isSingleWord = textClassification.type == GabAIUtils.TextSpanType.WORD
        val isPhrase = textClassification.type == GabAIUtils.TextSpanType.PHRASE
        val isSentence = textClassification.type == GabAIUtils.TextSpanType.SENTENCE
        val hasEnclosingContext = surroundingSentenceRaw.isNotBlank() &&
                !surroundingSentence.trim().equals(scannedText.trim(), ignoreCase = true)

        selectionTypeLabel = textClassification.targetSpeakLabel

        // Configure Hero Context Card
        configureHeroContextCard(scannedText, surroundingSentence, isSingleWord, isPhrase, isSentence, hasEnclosingContext)

        // Setup audio buttons (immediate visual loading feedback on click + eager pre-warming)
        findViewById<View>(R.id.btn_speak_word)?.setOnClickListener {
            stopExplanationAudioIfPlaying()
            findViewById<View>(R.id.progress_tts_selected)?.visibility = View.VISIBLE
            ensureTts { speakWithDetection(scannedText, "WORD_UTTERANCE") }
        }

        findViewById<View>(R.id.btn_speak_sentence)?.setOnClickListener {
            stopExplanationAudioIfPlaying()
            findViewById<View>(R.id.progress_tts_selected)?.visibility = View.VISIBLE
            ensureTts { speakWithDetection(surroundingSentence, "SENTENCE_UTTERANCE") }
        }

        findViewById<ImageButton>(R.id.btn_speak_explanation)?.setOnClickListener {
            val audioText = if (lastExplanationAudioText.isNotEmpty()) lastExplanationAudioText else lastAiResult
            if (ttsExplanationState != TtsPlaybackState.PLAYING) {
                updateExplanationAudioUi(TtsPlaybackState.LOADING)
            }
            ensureTts { toggleExplanationPlayback(audioText) }
        }

        // Eagerly pre-warm TextToSpeech and ML Kit LanguageIdentifier non-blocking in background
        ensureTts {}
        try {
            LanguageIdentification.getClient().identifyLanguage("warmup")
        } catch (_: Exception) {}

        // Favorite button
        val favoriteBtn = findViewById<ImageButton>(R.id.btn_favorite)
        val isFavoriteInit = intent.getBooleanExtra("IS_FAVORITE", false)
        var isCurrentlyFavorite = isFavoriteInit
        if (isCurrentlyFavorite) {
            favoriteBtn?.setImageResource(R.drawable.ic_star_filled)
        } else {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null && scannedText.isNotEmpty()) {
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("favorites").document(scannedText).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            isCurrentlyFavorite = true
                            favoriteBtn?.setImageResource(R.drawable.ic_star_filled)
                        }
                    }
            }
        }

        favoriteBtn?.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val db = FirebaseFirestore.getInstance()
            if (isCurrentlyFavorite) {
                // Remove from favorites
                db.collection("users").document(uid).collection("favorites").document(scannedText)
                    .delete()
                    .addOnSuccessListener {
                        isCurrentlyFavorite = false
                        favoriteBtn?.setImageResource(R.drawable.ic_star_outline)
                        GabAIUtils.performHaptic(favoriteBtn)
                        GabAIUtils.showSnackbar(this, "Removed from Favorites")
                    }
            } else {
                if (lastAiResult.isNotEmpty()) {
                    saveToFavorites(
                        word = scannedText,
                        definition = lastAiResult,
                        originalContext = surroundingSentence,
                        partOfSpeech = lastPartOfSpeech,
                        phonetics = lastPhonetics,
                        inSentenceRole = lastInSentenceRole
                    )
                    isCurrentlyFavorite = true
                    favoriteBtn?.setImageResource(R.drawable.ic_star_filled)
                    GabAIUtils.performHaptic(favoriteBtn, android.view.HapticFeedbackConstants.CONFIRM)
                }
            }
        }

        val preloadedExplanation = intent.getStringExtra("PRELOADED_EXPLANATION")
        if (!preloadedExplanation.isNullOrBlank()) {
            val preloadedPos = intent.getStringExtra("PART_OF_SPEECH") ?: ""
            val preloadedPhonetics = intent.getStringExtra("PHONETICS") ?: ""
            val preloadedInSentenceRole = intent.getStringExtra("IN_SENTENCE_ROLE") ?: ""

            lastPartOfSpeech = preloadedPos
            lastPhonetics = preloadedPhonetics
            lastInSentenceRole = preloadedInSentenceRole
            lastAiResult = preloadedExplanation
            lastExplanationAudioText = if (preloadedInSentenceRole.isNotEmpty()) "$preloadedExplanation. $preloadedInSentenceRole" else preloadedExplanation

            val resultTextView = findViewById<TextView>(R.id.ai_result_text)
            val resultContainer = findViewById<View>(R.id.result_container)
            val loadingContainer = findViewById<View>(R.id.loading_container)
            loadingContainer?.visibility = View.GONE
            resultContainer?.visibility = View.VISIBLE
            stopSkeletonPulse()

            val targetWordView = findViewById<TextView>(R.id.tv_target_word)
            val phoneticsView = findViewById<TextView>(R.id.tv_phonetics)
            val posView = findViewById<TextView>(R.id.tv_part_of_speech)
            val inSentenceContainer = findViewById<View>(R.id.ll_in_sentence_container)
            val inSentenceTextView = findViewById<TextView>(R.id.tv_in_sentence)
            val inSentenceLabel = findViewById<TextView>(R.id.tv_in_sentence_label)
            val isAllSelected = isSentence || scannedText.trim().equals(surroundingSentence.trim(), ignoreCase = true)

            if (isSentence) {
                targetWordView?.text = "Sentence Breakdown"
                targetWordView?.textSize = 16f
                phoneticsView?.visibility = View.GONE
                posView?.text = if (preloadedPos.isNotEmpty()) preloadedPos else "ANALYSIS"
            } else if (isPhrase) {
                targetWordView?.text = scannedText
                targetWordView?.textSize = 22f
                if (preloadedPhonetics.isNotEmpty()) {
                    phoneticsView?.text = preloadedPhonetics
                    phoneticsView?.visibility = View.VISIBLE
                } else {
                    phoneticsView?.visibility = View.GONE
                }
                posView?.text = if (preloadedPos.isNotEmpty()) preloadedPos else "PHRASE"
            } else {
                targetWordView?.text = scannedText
                targetWordView?.textSize = 22f
                if (preloadedPhonetics.isNotEmpty()) {
                    phoneticsView?.text = preloadedPhonetics
                    phoneticsView?.visibility = View.VISIBLE
                } else {
                    phoneticsView?.visibility = View.GONE
                }
                posView?.text = if (preloadedPos.isNotEmpty()) preloadedPos else "WORD"
            }

            if (!isAllSelected && preloadedInSentenceRole.isNotEmpty()) {
                inSentenceLabel?.text = if (isSentence) "SENTENCE ROLE IN CONTEXT" else if (isPhrase) "PHRASE ROLE IN THIS SENTENCE" else "WORD ROLE IN THIS SENTENCE"
                inSentenceTextView?.text = preloadedInSentenceRole
                inSentenceContainer?.visibility = View.VISIBLE
            } else {
                inSentenceContainer?.visibility = View.GONE
            }

            val markwon = Markwon.create(this)
            resultTextView?.let { markwon.setMarkdown(it, preloadedExplanation) }

            // Populate related questions for exploration
            val preloadedQuestions = listOf(
                "How do I use \"$scannedText\" in everyday conversation?",
                "Can you give common synonyms and examples?",
                "What is an easy memory trick or mnemonic for this?"
            )
            populateRelatedQuestions(preloadedQuestions, scannedText, surroundingSentence)

            // Cache preloaded explanation to avoid re-querying Gemini
            OverviewCache.put(
                scannedText,
                surroundingSentence,
                OverviewCache.CachedOverview(
                    targetWord = scannedText,
                    phonetics = lastPhonetics,
                    partOfSpeech = lastPartOfSpeech,
                    definition = lastAiResult,
                    inSentenceRole = lastInSentenceRole,
                    relatedQuestions = preloadedQuestions
                )
            )

            // Trigger Detail Quest progress
            ApprenticeQuestHelper.completeQuest(this, ApprenticeQuestHelper.QUEST_DETAIL)
        } else if (scannedText.isNotEmpty()) {
            // Generate AI Overview and Question Prompts
            generateAIOverview(scannedText, surroundingSentence, isSingleWord, isPhrase, isSentence)
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
        if (::tts.isInitialized && isTtsReady) {
            tts.stop()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            Thread {
                try {
                    tts.shutdown()
                } catch (_: Exception) {}
            }.start()
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
        isSentence: Boolean,
        hasEnclosingContext: Boolean = false
    ) {
        val contextBadge = findViewById<TextView>(R.id.tv_context_badge)
        val selectedTextView = findViewById<TextView>(R.id.selected_text_view)
        val wordSpeakBtn = findViewById<View>(R.id.btn_speak_word)
        val sentenceSpeakBtn = findViewById<View>(R.id.btn_speak_sentence)
        val wordSpeakLabel = findViewById<TextView>(R.id.tv_btn_speak_word)
        val sentenceSpeakLabel = findViewById<TextView>(R.id.tv_btn_speak_sentence)

        val contextType = GabAIUtils.classifyContextLabel(surroundingSentence)

        when {
            isSingleWord -> {
                contextBadge?.text = if (hasEnclosingContext) "WORD IN CONTEXT" else "WORD CONTEXT"
                wordSpeakLabel?.text = "Word"
                wordSpeakBtn?.contentDescription = "Speak word"
                wordSpeakBtn?.visibility = View.VISIBLE

                if (hasEnclosingContext) {
                    sentenceSpeakLabel?.text = contextType
                    sentenceSpeakBtn?.contentDescription = "Speak full $contextType"
                    sentenceSpeakBtn?.visibility = View.VISIBLE
                } else {
                    sentenceSpeakBtn?.visibility = View.GONE
                }
            }
            isPhrase -> {
                contextBadge?.text = if (hasEnclosingContext) "PHRASE IN CONTEXT" else "KEY PHRASE"
                wordSpeakLabel?.text = "Phrase"
                wordSpeakBtn?.contentDescription = "Speak phrase"
                wordSpeakBtn?.visibility = View.VISIBLE

                if (hasEnclosingContext) {
                    sentenceSpeakLabel?.text = contextType
                    sentenceSpeakBtn?.contentDescription = "Speak full $contextType"
                    sentenceSpeakBtn?.visibility = View.VISIBLE
                } else {
                    sentenceSpeakBtn?.visibility = View.GONE
                }
            }
            else -> {
                contextBadge?.text = "FULL SENTENCE"
                wordSpeakLabel?.text = "Sentence"
                wordSpeakBtn?.contentDescription = "Speak full sentence"
                wordSpeakBtn?.visibility = View.VISIBLE

                if (hasEnclosingContext) {
                    sentenceSpeakLabel?.text = "Passage"
                    sentenceSpeakBtn?.contentDescription = "Speak full passage"
                    sentenceSpeakBtn?.visibility = View.VISIBLE
                } else {
                    sentenceSpeakBtn?.visibility = View.GONE
                }
            }
        }

        if (isSentence) {
            val padH = (14 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            val heroLayout = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_sentence_context)?.getChildAt(0) as? LinearLayout
            heroLayout?.setPadding(padH, padV, padH, padV)
            selectedTextView?.textSize = 14f
        }

        // Render highlighted text/passage
        if (hasEnclosingContext) {
            val startIndex = surroundingSentence.indexOf(targetText, ignoreCase = true)
            if (startIndex >= 0) {
                val endIndex = startIndex + targetText.length
                val spannable = SpannableStringBuilder(surroundingSentence)
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
                selectedTextView?.text = spannable
            } else {
                selectedTextView?.text = surroundingSentence
            }
        } else {
            selectedTextView?.text = targetText
        }
    }

    // =========================================================================
    // 2. AI OVERVIEW & STRUCTURED RESPONSE GENERATION
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
        val errorContainer = findViewById<View>(R.id.error_container)
        val errorMessageView = findViewById<TextView>(R.id.tv_error_message)
        val retryButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_retry_definition)
        val targetWordView = findViewById<TextView>(R.id.tv_target_word)
        val phoneticsView = findViewById<TextView>(R.id.tv_phonetics)
        val posView = findViewById<TextView>(R.id.tv_part_of_speech)
        val definitionTextView = findViewById<TextView>(R.id.ai_result_text)
        val inSentenceLabel = findViewById<TextView>(R.id.tv_in_sentence_label)
        val inSentenceTextView = findViewById<TextView>(R.id.tv_in_sentence)
        val inSentenceContainer = findViewById<View>(R.id.ll_in_sentence_container)
        val markwon = Markwon.create(this)

        // 0. Check in-memory OverviewCache immediately to avoid duplicate Gemini token consumption and zero-latency display
        val cached = OverviewCache.get(inputText, surroundingSentence)
        if (cached != null) {
            loadingContainer?.visibility = View.GONE
            errorContainer?.visibility = View.GONE
            resultContainer?.visibility = View.VISIBLE

            lastAiResult = cached.definition
            lastPartOfSpeech = cached.partOfSpeech
            lastPhonetics = cached.phonetics
            lastInSentenceRole = cached.inSentenceRole
            lastExplanationAudioText = if (lastInSentenceRole.isNotEmpty()) "${cached.definition}. $lastInSentenceRole" else cached.definition

            if (isSentence) {
                targetWordView?.text = "Sentence Breakdown"
                targetWordView?.textSize = 16f
                phoneticsView?.visibility = View.GONE
                posView?.text = if (cached.partOfSpeech.isNotEmpty()) cached.partOfSpeech else "ANALYSIS"
            } else if (isPhrase) {
                targetWordView?.text = inputText
                targetWordView?.textSize = 22f
                if (cached.phonetics.isNotEmpty()) {
                    phoneticsView?.text = cached.phonetics
                    phoneticsView?.visibility = View.VISIBLE
                } else {
                    phoneticsView?.visibility = View.GONE
                }
                posView?.text = if (cached.partOfSpeech.isNotEmpty()) cached.partOfSpeech else "PHRASE"
            } else {
                targetWordView?.text = inputText
                targetWordView?.textSize = 22f
                if (cached.phonetics.isNotEmpty()) {
                    phoneticsView?.text = cached.phonetics
                    phoneticsView?.visibility = View.VISIBLE
                } else {
                    phoneticsView?.visibility = View.GONE
                }
                posView?.text = cached.partOfSpeech
            }

            markwon.setMarkdown(definitionTextView, cached.definition)

            val isAllSelected = isSentence || inputText.trim().equals(surroundingSentence.trim(), ignoreCase = true)
            if (!isAllSelected && cached.inSentenceRole.isNotEmpty()) {
                inSentenceLabel?.text = if (isSentence) "SENTENCE ROLE IN CONTEXT" else if (isPhrase) "PHRASE ROLE IN THIS SENTENCE" else "WORD ROLE IN THIS SENTENCE"
                inSentenceTextView?.text = cached.inSentenceRole
                inSentenceContainer?.visibility = View.VISIBLE
            } else {
                inSentenceContainer?.visibility = View.GONE
            }

            populateRelatedQuestions(cached.relatedQuestions, inputText, surroundingSentence)
            completeScanQuestIfApplicable()
            return
        }

        loadingContainer?.visibility = View.VISIBLE
        startSkeletonPulse()
        errorContainer?.visibility = View.GONE
        resultContainer?.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val selectionType = when {
                    isSingleWord -> "single word"
                    isPhrase -> "multi-word phrase"
                    else -> "full sentence"
                }

                val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
                val aiLanguage = prefs.getString("ai_language_pref", "English") ?: "English"

                val isAllSelected = isSentence || inputText.trim().equals(surroundingSentence.trim(), ignoreCase = true)

                data class LangConfig(
                    val directive: String,
                    val definitionDesc: String,
                    val roleDesc: String,
                    val questionDescs: List<String>
                )

                val langConfig = when (aiLanguage) {
                    "Taglish" -> LangConfig(
                        directive = """
                            CRITICAL LANGUAGE DIRECTIVE (MANDATORY TARGET LANGUAGE: AUTHENTIC STUDENT TAGLISH):
                            You MUST explain in authentic, casual, and relatable Taglish (conversational Filipino code-switched with English), exactly how modern Filipino high school and college students talk and study together.
                            - Student Code-Switching: Seamlessly attach Filipino affixes to English technical terms (e.g., "nagfa-function ito", "ma-alter ang process", "ina-absorb", "nako-convert into chemical energy", "nag-e-explain", "na-a-analyze").
                            - Natural Openers: Use friendly student openers (e.g., "Ito yung process kung saan...", "Sa sentence na 'to, nagfa-function siya as...", "Kaya importante 'to kasi...").
                            - STRICT PROHIBITION: Do NOT output English for 'definition', 'inSentenceRole', or 'relatedQuestions'. Everything except the IPA phonetics and partOfSpeech must be in natural student Taglish. Do NOT use stiff textbook Tagalog.
                        """.trimIndent(),
                        definitionDesc = "1-2 sentences in conversational student Taglish explaining the core meaning clearly.",
                        roleDesc = if (isAllSelected) {
                            "Entire sentence was selected. Return empty string \"\"."
                        } else if (isPhrase) {
                            "1-2 sentences in conversational student Taglish explaining kung paano nagfa-function itong multi-word phrase sa loob ng enclosing sentence."
                        } else {
                            "1-2 sentences in conversational student Taglish explaining kung paano nagfa-function itong salita sa loob ng enclosing sentence."
                        },
                        questionDescs = listOf(
                            "Direct cause, effect, or function question in student Taglish",
                            "Direct comparative or mechanism question in student Taglish",
                            "Direct real-world or critical thinking question in student Taglish"
                        )
                    )
                    "Tagalog" -> LangConfig(
                        directive = """
                            CRITICAL LANGUAGE DIRECTIVE (MANDATORY TARGET LANGUAGE: MODERN NATURAL FILIPINO):
                            You MUST explain in clear, natural, contemporary Filipino suitable for students.
                            - Tone & Style: Natural, conversational Filipino. Avoid archaic words (no "salumpuwit", "sipnayan", "talatinigan"). Common modern loan terms (like oxygen, gravity, DNA) are completely fine to retain.
                            - Sentence Openers: Natural student-friendly Filipino (e.g., "Ito ang proseso kung saan...", "Sa pangungusap na ito, nagsisilbi itong...").
                            - STRICT PROHIBITION: Do NOT output English for 'definition', 'inSentenceRole', or 'relatedQuestions'. Everything except the IPA phonetics and partOfSpeech must be in natural modern Filipino.
                        """.trimIndent(),
                        definitionDesc = "1-2 pangungusap sa natural at modernong Filipino na nagpapaliwanag ng kahulugan.",
                        roleDesc = if (isAllSelected) {
                            "Buong pangungusap ang napili. Ibalik ang walang laman na string \"\"."
                        } else if (isPhrase) {
                            "1-2 pangungusap sa natural at modernong Filipino na nagpapaliwanag sa papel ng pariralang ito (phrase) sa loob ng pangungusap."
                        } else {
                            "1-2 pangungusap sa natural at modernong Filipino na nagpapaliwanag sa papel ng salita sa loob ng pangungusap."
                        },
                        questionDescs = listOf(
                            "Tanong tungkol sa sanhi o gamit sa natural na Filipino",
                            "Tanong na naghahambing o tungkol sa proseso sa natural na Filipino",
                            "Pang-araw-araw o kritikal na tanong sa natural na Filipino"
                        )
                    )
                    else -> LangConfig(
                        directive = "CRITICAL LANGUAGE DIRECTIVE: Write the 'definition', 'inSentenceRole', and all 'relatedQuestions' in clear, concise educational English suitable for high school students.",
                        definitionDesc = "Clear, concise definition or core meaning in 1-2 sentences. Avoid storytelling framing, avoid filler.",
                        roleDesc = if (isAllSelected) {
                            "Entire sentence was selected. Return empty string \"\"."
                        } else if (isPhrase) {
                            "1-2 sentences explaining specifically how this entire multi-word phrase functions within the enclosing sentence."
                        } else {
                            "1-2 sentences explaining specifically how this word operates within the enclosing sentence."
                        },
                        questionDescs = listOf(
                            "Direct cause, effect, or function question about this concept",
                            "Direct comparative or mechanism question",
                            "Direct real-world or critical thinking question"
                        )
                    )
                }

                val prompt = """
                    You are GabAI, an educational tutor helping a high school student understand this reading material.
                    Context Note: If the text refers to "GabAI" or "gabai", it refers to this application — an intelligent AI reading assistant and tutor designed for Filipino students (derived from the Filipino/Tagalog word "gabay", meaning guide or mentor). Do not confuse it with GABA neurochemistry, Gabapentin, or other medications unless the surrounding text explicitly discusses medicine.
                    Target Selection: "$inputText"
                    Enclosing Sentence: "$surroundingSentence"
                    Selection Type: $selectionType
                    ${langConfig.directive}

                    CRITICAL SAFETY & TRUTHFULNESS DIRECTIVES:
                    1. FACTUAL ACCURACY (NO HALLUCINATIONS):
                       State what the entity actually is with 100% truth. DO NOT hallucinate, assume, or invent that an unknown brand, commercial app, game, company, or website is an "educational platform" or "study hub" just because you are an educational tutor.
                    2. GAMBLING, CASINO, & ADULT PLATFORM SAFEGUARD:
                       If the target refers to an online casino, slot machine, betting app, or gambling service (such as ArionPlay, OKBet, e-bingo, slots, etc.), identify it truthfully as an online gambling/casino platform. Explicitly state that it is NOT an educational tool, and warn that gambling is strictly age-restricted (21+) and involves financial risk. NEVER describe gambling platforms as learning materials, modules, or study apps.
                    3. UNKNOWN ENTITIES: If a term is unknown or ambiguous, explain only what can be factually deduced from context. Do NOT invent fake educational features or study activities.

                    Analyze the selection in context and return ONLY a valid JSON object matching this schema without markdown fences:
                    {
                      "phonetics": "/.../ (IPA pronunciation, or empty string if phrase/sentence)",
                      "partOfSpeech": "noun / verb / adjective / phrase / clause / statement",
                      "definition": "${langConfig.definitionDesc}",
                      "inSentenceRole": "${langConfig.roleDesc}",
                      "relatedQuestions": [
                        "${langConfig.questionDescs[0]}",
                        "${langConfig.questionDescs[1]}",
                        "${langConfig.questionDescs[2]}"
                      ]
                    }
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val rawText = response.text?.trim() ?: ""

                stopSkeletonPulse()
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
                var partOfSpeech = if (isSingleWord) "WORD" else if (isPhrase) "PHRASE" else "SENTENCE"
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
                             "Paano gumagana ang konseptong ito sa binabasa mo?",
                             "Bakit mahalaga ito sa paksang pinag-aaralan?",
                             "Ano ang mangyayari kung magbabago ang prosesong ito?"
                        )
                        "Taglish" -> listOf(
                             "Paano nagfa-function ang concept na ito sa kabuuang topic?",
                             "Bakit important ito sa binabasa mo?",
                             "Ano ang mangyayari kung ma-alter o magbago ang process na ito?"
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
                lastPartOfSpeech = partOfSpeech
                lastPhonetics = phonetics
                lastInSentenceRole = if (isAllSelected) "" else inSentenceRole
                lastExplanationAudioText = if (lastInSentenceRole.isNotEmpty()) "$definition. $lastInSentenceRole" else definition

                // Populate UI
                if (isSentence) {
                    targetWordView?.text = "Sentence Breakdown"
                    targetWordView?.textSize = 16f
                    phoneticsView?.visibility = View.GONE
                    posView?.text = if (partOfSpeech.isNotEmpty()) partOfSpeech else "ANALYSIS"
                } else if (isPhrase) {
                    targetWordView?.text = inputText
                    targetWordView?.textSize = 22f
                    if (phonetics.isNotEmpty()) {
                        phoneticsView?.text = phonetics
                        phoneticsView?.visibility = View.VISIBLE
                    } else {
                        phoneticsView?.visibility = View.GONE
                    }
                    posView?.text = if (partOfSpeech.isNotEmpty()) partOfSpeech else "PHRASE"
                } else {
                    targetWordView?.text = inputText
                    targetWordView?.textSize = 22f
                    if (phonetics.isNotEmpty()) {
                        phoneticsView?.text = phonetics
                        phoneticsView?.visibility = View.VISIBLE
                    } else {
                        phoneticsView?.visibility = View.GONE
                    }
                    posView?.text = partOfSpeech
                }

                markwon.setMarkdown(definitionTextView, definition)

                if (!isAllSelected && inSentenceRole.isNotEmpty()) {
                    inSentenceLabel?.text = if (isSentence) "SENTENCE ROLE IN CONTEXT" else if (isPhrase) "PHRASE ROLE IN THIS SENTENCE" else "WORD ROLE IN THIS SENTENCE"
                    inSentenceTextView?.text = inSentenceRole
                    inSentenceContainer?.visibility = View.VISIBLE
                } else {
                    inSentenceContainer?.visibility = View.GONE
                }

                // Populate Related Questions Vertically
                populateRelatedQuestions(relatedQuestions, inputText, surroundingSentence)

                // Save to History with actual enclosing sentence as originalContext and grammatical metadata
                saveToHistory(
                    text = GabAIUtils.sanitizeVocabularyWord(inputText).ifBlank { inputText },
                    aiResult = definition,
                    originalContext = surroundingSentence,
                    partOfSpeech = partOfSpeech,
                    phonetics = phonetics,
                    inSentenceRole = if (isAllSelected) "" else inSentenceRole
                )

                // Cache response in memory to eliminate duplicate Gemini token calls
                OverviewCache.put(
                    inputText,
                    surroundingSentence,
                    OverviewCache.CachedOverview(
                        targetWord = inputText,
                        phonetics = phonetics,
                        partOfSpeech = partOfSpeech,
                        definition = definition,
                        inSentenceRole = if (isAllSelected) "" else inSentenceRole,
                        relatedQuestions = relatedQuestions
                    )
                )
                completeScanQuestIfApplicable()

            } catch (e: Exception) {
                stopSkeletonPulse()
                loadingContainer?.visibility = View.GONE
                resultContainer?.visibility = View.GONE
                val friendlyMessage = if (e is java.net.UnknownHostException || e is java.io.IOException) {
                    "No internet connection. Please check your network and tap retry."
                } else {
                    "Unable to connect to AI tutor (${e.localizedMessage ?: "timeout"})."
                }
                errorMessageView?.text = friendlyMessage
                retryButton?.setOnClickListener {
                    generateAIOverview(inputText, surroundingSentence, isSingleWord, isPhrase, isSentence)
                }
                errorContainer?.visibility = View.VISIBLE
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
            val progressBar = itemView.findViewById<View>(R.id.progress_question)
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
                                    "Tagalog" -> "Answer directly in natural, conversational, modern Filipino suitable for students. Avoid archaic phrasing. Do NOT reply in English."
                                    "Taglish" -> "Answer directly in authentic, friendly, conversational Taglish (the way modern Filipino students talk, using natural student code-switching like 'nagfa-function', 'ma-alter', 'nako-connect', 'ina-absorb'). Do NOT reply in pure English."
                                    else -> "Answer directly in clear educational English."
                                }

                                val answerPrompt = """
                                    You are GabAI, an educational reading tutor for high school students.
                                    Context Note: If referring to "GabAI" or "gabai", it refers to the GabAI educational reading tutor app (from Tagalog "gabay" meaning guide), not medicine.
                                    Target Selection: "$targetText"
                                    Context: "$surroundingSentence"
                                    Question: "$questionText"
                                    $langDirective

                                    CRITICAL SAFETY & TRUTHFULNESS DIRECTIVES:
                                    1. FACTUAL TRUTH (NO HALLUCINATIONS): Answer with strict factual accuracy. NEVER hallucinate or invent that commercial websites, games, casinos, or betting platforms are "educational platforms", "interactive modules", or "learning materials".
                                    2. GAMBLING & CASINO PLATFORMS: If the platform in question is an online casino, slot game, or gambling service (such as ArionPlay, OKBet, etc.), state clearly, honestly, and neutrally that it is an online gambling/casino platform for adults. Explicitly state that it is NOT an educational platform and that gambling is age-restricted (21+) involving financial risk.
                                    3. CONCISE RESPONSE: Provide a concise, direct 2-sentence answer directly addressing the question. Avoid introductory fluff.
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
    private fun saveToFavorites(
        word: String,
        definition: String,
        originalContext: String = "",
        partOfSpeech: String = "",
        phonetics: String = "",
        inSentenceRole: String = ""
    ) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        GabAIUtils.showSnackbar(this, "Saved $selectionTypeLabel to Favorites! ⭐")

        val favEntry = hashMapOf(
            "word" to word,
            "definition" to definition,
            "timestamp" to System.currentTimeMillis(),
            "originalContext" to originalContext,
            "partOfSpeech" to partOfSpeech,
            "phonetics" to phonetics,
            "inSentenceRole" to inSentenceRole
        )

        db.collection("users").document(uid)
            .collection("favorites").document(word)
            .set(favEntry)
            .addOnSuccessListener {
                if (BuildConfig.DEBUG) android.util.Log.d("GabAI_DB", "Favorite synced to cloud")
                ApprenticeQuestHelper.completeQuest(this, ApprenticeQuestHelper.QUEST_SAVE)
            }
    }

    private fun completeScanQuestIfApplicable() {
        val scanSource = intent.getStringExtra("SCAN_SOURCE") ?: "camera"
        val questKey = if (scanSource.equals(ApprenticeQuestHelper.QUEST_BUBBLE, ignoreCase = true)) {
            ApprenticeQuestHelper.QUEST_BUBBLE_SCAN
        } else {
            ApprenticeQuestHelper.QUEST_SCAN
        }
        ApprenticeQuestHelper.completeQuest(this, questKey)
        QuestManager.addProgress(this, QuestManager.QUEST_SCAN)
    }

    private fun saveToHistory(
        text: String,
        aiResult: String,
        originalContext: String,
        partOfSpeech: String = "",
        phonetics: String = "",
        inSentenceRole: String = ""
    ) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val timestamp = System.currentTimeMillis()

        val historyEntry = hashMapOf(
            "word" to text,
            "explanation" to aiResult,
            "timestamp" to timestamp,
            "originalContext" to originalContext,
            "partOfSpeech" to partOfSpeech,
            "phonetics" to phonetics,
            "inSentenceRole" to inSentenceRole,
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

    private fun startSkeletonPulse() {
        val skeleton = findViewById<View>(R.id.ll_skeleton_shimmer) ?: return
        val pulseAnim = android.view.animation.AlphaAnimation(0.45f, 1.0f).apply {
            duration = 800
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        skeleton.startAnimation(pulseAnim)
    }

    private fun stopSkeletonPulse() {
        val skeleton = findViewById<View>(R.id.ll_skeleton_shimmer) ?: return
        skeleton.clearAnimation()
    }

    // =========================================================================
    // 6. TEXT-TO-SPEECH (TTS)
    // =========================================================================
    private fun updateExplanationAudioUi(state: TtsPlaybackState) {
        val btn = findViewById<ImageButton>(R.id.btn_speak_explanation)
        val progress = findViewById<View>(R.id.progress_tts_explanation)
        when (state) {
            TtsPlaybackState.LOADING -> {
                progress?.visibility = View.VISIBLE
                btn?.visibility = View.GONE
            }
            TtsPlaybackState.PLAYING -> {
                progress?.visibility = View.GONE
                btn?.visibility = View.VISIBLE
                btn?.setImageResource(R.drawable.ic_pause)
            }
            TtsPlaybackState.PAUSED -> {
                progress?.visibility = View.GONE
                btn?.visibility = View.VISIBLE
                btn?.setImageResource(R.drawable.ic_play_arrow)
            }
            TtsPlaybackState.IDLE -> {
                progress?.visibility = View.GONE
                btn?.visibility = View.VISIBLE
                btn?.setImageResource(R.drawable.ic_volume_up)
            }
        }
    }

    private fun ensureTts(onReady: () -> Unit) {
        if (isTtsReady && ::tts.isInitialized) {
            onReady()
            return
        }
        pendingTtsAction = onReady
        if (isTtsInitializing) return
        isTtsInitializing = true

        tts = TextToSpeech(this) { status ->
            isTtsInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                tts.setSpeechRate(1.0f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread {
                            if (utteranceId == "EXPLANATION_UTTERANCE") {
                                ttsExplanationState = TtsPlaybackState.PLAYING
                                updateExplanationAudioUi(TtsPlaybackState.PLAYING)
                            } else if (utteranceId == "WORD_UTTERANCE" || utteranceId == "SENTENCE_UTTERANCE") {
                                findViewById<View>(R.id.progress_tts_selected)?.visibility = View.GONE
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            if (utteranceId == "EXPLANATION_UTTERANCE") {
                                ttsExplanationState = TtsPlaybackState.IDLE
                                lastCharOffset = 0
                                currentUtteranceOffset = 0
                                updateExplanationAudioUi(TtsPlaybackState.IDLE)
                            } else if (utteranceId == "WORD_UTTERANCE" || utteranceId == "SENTENCE_UTTERANCE") {
                                findViewById<View>(R.id.progress_tts_selected)?.visibility = View.GONE
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            if (utteranceId == "EXPLANATION_UTTERANCE") {
                                ttsExplanationState = TtsPlaybackState.IDLE
                                lastCharOffset = 0
                                currentUtteranceOffset = 0
                                updateExplanationAudioUi(TtsPlaybackState.IDLE)
                            } else if (utteranceId == "WORD_UTTERANCE" || utteranceId == "SENTENCE_UTTERANCE") {
                                findViewById<View>(R.id.progress_tts_selected)?.visibility = View.GONE
                            }
                        }
                    }

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        if (utteranceId == "EXPLANATION_UTTERANCE") {
                            lastCharOffset = currentUtteranceOffset + start
                        }
                    }
                })
                runOnUiThread {
                    val act = pendingTtsAction
                    pendingTtsAction = null
                    act?.invoke()
                }
            } else {
                runOnUiThread {
                    findViewById<View>(R.id.progress_tts_selected)?.visibility = View.GONE
                    updateExplanationAudioUi(TtsPlaybackState.IDLE)
                }
            }
        }
    }

    private fun speakWithDetection(text: String, utteranceId: String) {
        if (!isTtsReady || !::tts.isInitialized || text.isEmpty()) {
            findViewById<View>(R.id.progress_tts_selected)?.visibility = View.GONE
            return
        }

        val loader = findViewById<View>(R.id.progress_tts_selected)
        loader?.visibility = View.VISIBLE

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val locale = if (languageCode == "fil" || languageCode == "tl") {
                    Locale("fil", "PH")
                } else {
                    Locale.US
                }
                tts.language = locale
                tts.setSpeechRate(1.0f)
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
            .addOnFailureListener {
                loader?.visibility = View.GONE
                tts.language = Locale.US
                tts.setSpeechRate(1.0f)
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
    }

    private fun stopExplanationAudioIfPlaying() {
        if (!isTtsReady || !::tts.isInitialized) return
        if (ttsExplanationState != TtsPlaybackState.IDLE) {
            tts.stop()
            ttsExplanationState = TtsPlaybackState.IDLE
            lastCharOffset = 0
            currentUtteranceOffset = 0
            updateExplanationAudioUi(TtsPlaybackState.IDLE)
        }
    }

    private fun toggleExplanationPlayback(text: String) {
        if (!isTtsReady || !::tts.isInitialized || text.isEmpty()) return

        when (ttsExplanationState) {
            TtsPlaybackState.LOADING -> {
                tts.stop()
                ttsExplanationState = TtsPlaybackState.IDLE
                updateExplanationAudioUi(TtsPlaybackState.IDLE)
            }
            TtsPlaybackState.PLAYING -> {
                // Pause playback
                tts.stop()
                ttsExplanationState = TtsPlaybackState.PAUSED
                updateExplanationAudioUi(TtsPlaybackState.PAUSED)
                val btn = findViewById<ImageButton>(R.id.btn_speak_explanation)
                GabAIUtils.performHaptic(btn, android.view.HapticFeedbackConstants.CLOCK_TICK)
                GabAIUtils.showSnackbar(this, "Audio Paused ⏸️")
            }
            TtsPlaybackState.PAUSED -> {
                // Resume from last paused position
                val targetText = if (lastCharOffset in 1 until fullExplanationText.length) {
                    fullExplanationText.substring(lastCharOffset).trim()
                } else {
                    fullExplanationText
                }
                currentUtteranceOffset = lastCharOffset
                ttsExplanationState = TtsPlaybackState.LOADING
                updateExplanationAudioUi(TtsPlaybackState.LOADING)

                lifecycleScope.launch(Dispatchers.Default) {
                    applyTtsLocale()
                    withContext(Dispatchers.Main) {
                        val params = Bundle().apply {
                            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EXPLANATION_UTTERANCE")
                        }
                        tts.speak(targetText, TextToSpeech.QUEUE_FLUSH, params, "EXPLANATION_UTTERANCE")
                    }
                }
            }
            TtsPlaybackState.IDLE -> {
                // Start playback from beginning
                fullExplanationText = text.replace(Regex("[#*<>_]"), "").trim()
                lastCharOffset = 0
                currentUtteranceOffset = 0
                ttsExplanationState = TtsPlaybackState.LOADING
                updateExplanationAudioUi(TtsPlaybackState.LOADING)

                lifecycleScope.launch(Dispatchers.Default) {
                    applyTtsLocale()
                    withContext(Dispatchers.Main) {
                        val params = Bundle().apply {
                            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "EXPLANATION_UTTERANCE")
                        }
                        tts.speak(fullExplanationText, TextToSpeech.QUEUE_FLUSH, params, "EXPLANATION_UTTERANCE")
                    }
                }
            }
        }
    }

    private fun applyTtsLocale() {
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
        tts.setSpeechRate(1.0f)
    }
}