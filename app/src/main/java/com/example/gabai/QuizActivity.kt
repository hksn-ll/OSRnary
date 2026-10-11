package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class QuizActivity : AppCompatActivity() {

    // --- Dynamic Limits (Fetched from Teacher's Settings) ---
    private var maxItemsPerSession = 5
    private var maxSessionsPerDay = 3

    enum class MechanicType {
        CLOZE,
        CONTEXT_MEANING,
        IMPOSTOR,
        GRAMMAR_LANDMINE,
        RAPID_MATCH
    }

    // --- Data Model for Pre-Generated Questions ---
    data class QuizQuestionItem(
        val docId: String = "",
        val interval: Int = 1,
        val word: String,
        val question: String,
        val options: List<String>,
        val correct: String,
        val explanation: String,
        val mechanic: MechanicType = MechanicType.CLOZE,
        val contextSentence: String = "",
        val genuineSentence: String = "",
        val impostorSentence: String = "",
        val briefMeaning: String = "",
        val backupRapidWords: List<Map<String, String>> = emptyList(),
        val landmineSentence: String = "",
        val landmineSegments: List<String> = emptyList(),
        val landmineFaultySegment: String = "",
        val landmineCorrection: String = ""
    )

    // --- Session Trackers ---
    private val quizQueue = mutableListOf<QuizQuestionItem>()
    private var currentActiveItem: QuizQuestionItem? = null
    private val sessionResults = mutableListOf<Map<String, Any>>()
    private val sessionDocSnapshots = mutableListOf<DocumentSnapshot>()

    private var totalQuestionsInSession = 0
    private var currentQuestionIndex = 0

    private var score = 0
    private var totalAttempts = 0
    private var startTime: Long = 0

    // State guards & Anti-Exploit tracking
    private var isCurrentAnswerEvaluated = false
    private var isTransitioningQuestion = false
    private var lastSelectedOptionText: String? = null

    // Combo & XP tracking
    private var comboStreak = 0
    private var maxComboStreak = 0
    private var totalXPEarned = 0
    private var baseXP = 0
    private var comboXP = 0
    private var bonusXP = 0

    // Lifeline states per question
    private var isCrossOutUsed = false
    private var isDoubleDownActive = false
    private var isClueRevealed = false

    // Rapid Match state
    private var rapidSelectedWordBtn: MaterialButton? = null
    private var rapidSelectedWord: String? = null
    private var rapidPairsCompleted = 0
    private var rapidStartTime: Long = 0
    private var rapidDocId1: String = ""
    private var rapidDocId2: String = ""
    private var rapidDocId3: String = ""

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // View references
    private lateinit var quizHeader: TextView
    private lateinit var tvFuelPods: TextView
    private lateinit var tvComboStreak: TextView
    private lateinit var tvProgressCounter: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressQuiz: ProgressBar
    private lateinit var segmentedProgressLayout: LinearLayout
    private lateinit var layoutLifelines: View
    private lateinit var btnCrossOut: MaterialButton
    private lateinit var btnDoubleDown: MaterialButton
    private lateinit var btnShowClue: MaterialButton
    private lateinit var questionCard: MaterialCardView
    private lateinit var tvQuestionBadge: TextView
    private lateinit var tvQuestionStepHint: TextView
    private lateinit var questionText: TextView
    private lateinit var layoutClozeSlot: LinearLayout
    private lateinit var tvClozeSlotText: TextView
    private lateinit var tvGuidanceTip: TextView
    private lateinit var layoutGuidanceStrip: View
    private lateinit var viewGuidanceDivider: View
    private lateinit var optionsContainer: View
    private lateinit var optionButtons: List<MaterialButton>
    private lateinit var layoutImpostorContainer: View
    private lateinit var cardImpostorA: MaterialCardView
    private lateinit var tvImpostorAText: TextView
    private lateinit var cardImpostorB: MaterialCardView
    private lateinit var tvImpostorBText: TextView
    private var impostorCorrectCardIsA = true
    private lateinit var layoutRapidMatch: View
    private lateinit var rapidWordButtons: List<MaterialButton>
    private lateinit var rapidDefButtons: List<MaterialButton>
    private lateinit var cardExplanation: View
    private lateinit var tvExplanation: TextView
    private lateinit var btnNextQuestion: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_quiz)

        // Fix Status Bar
        val root = findViewById<View>(R.id.quiz_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 16, v.paddingRight, v.paddingBottom)
            insets
        }

        bindViews()
        setupListeners()
        setupBackProtection()

        initializeQuizSession()
    }

    override fun onStart() {
        super.onStart()
        try {
            val intent = Intent(this, FloatingControlService::class.java).apply {
                action = "ACTION_HIDE"
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        saveCurrentSession()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val intent = Intent(this, FloatingControlService::class.java).apply {
                action = "ACTION_SHOW"
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun bindViews() {
        quizHeader = findViewById(R.id.quiz_header)
        tvFuelPods = findViewById(R.id.tv_fuel_pods)
        tvComboStreak = findViewById(R.id.tv_combo_streak)
        tvProgressCounter = findViewById(R.id.tv_progress_counter)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
        progressQuiz = findViewById(R.id.progress_quiz)
        segmentedProgressLayout = findViewById(R.id.layout_segmented_progress)
        layoutLifelines = findViewById(R.id.layout_lifelines)
        btnCrossOut = findViewById(R.id.btn_cross_out)
        btnDoubleDown = findViewById(R.id.btn_double_down)
        btnShowClue = findViewById(R.id.btn_show_clue)
        questionCard = findViewById(R.id.question_card)
        tvQuestionBadge = findViewById(R.id.tv_question_badge)
        tvQuestionStepHint = findViewById(R.id.tv_question_step_hint)
        questionText = findViewById(R.id.question_text)
        layoutClozeSlot = findViewById(R.id.layout_cloze_slot)
        tvClozeSlotText = findViewById(R.id.tv_cloze_slot_text)
        tvGuidanceTip = findViewById(R.id.tv_guidance_tip)
        layoutGuidanceStrip = findViewById(R.id.layout_guidance_strip)
        viewGuidanceDivider = findViewById(R.id.view_guidance_divider)
        optionsContainer = findViewById(R.id.options_container)

        optionButtons = listOf(
            findViewById(R.id.btn_choice1),
            findViewById(R.id.btn_choice2),
            findViewById(R.id.btn_choice3),
            findViewById(R.id.btn_choice4)
        )

        layoutImpostorContainer = findViewById(R.id.layout_impostor_container)
        cardImpostorA = findViewById(R.id.card_impostor_a)
        tvImpostorAText = findViewById(R.id.tv_impostor_a_text)
        cardImpostorB = findViewById(R.id.card_impostor_b)
        tvImpostorBText = findViewById(R.id.tv_impostor_b_text)

        layoutRapidMatch = findViewById(R.id.layout_rapid_match)
        rapidWordButtons = listOf(
            findViewById(R.id.btn_rapid_w1),
            findViewById(R.id.btn_rapid_w2),
            findViewById(R.id.btn_rapid_w3)
        )
        rapidDefButtons = listOf(
            findViewById(R.id.btn_rapid_d1),
            findViewById(R.id.btn_rapid_d2),
            findViewById(R.id.btn_rapid_d3)
        )

        cardExplanation = findViewById(R.id.card_explanation)
        tvExplanation = findViewById(R.id.tv_explanation)
        btnNextQuestion = findViewById(R.id.btn_next_question)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            confirmExitQuiz()
        }

        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_quiz_history)?.setOnClickListener {
            startActivity(Intent(this, QuizHistoryActivity::class.java))
            finish()
        }

        btnNextQuestion.setOnClickListener {
            if (isTransitioningQuestion) return@setOnClickListener
            isTransitioningQuestion = true
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            cardExplanation.visibility = View.GONE
            btnNextQuestion.visibility = View.GONE
            displayNextQuestion()
        }

        btnCrossOut.setOnClickListener {
            handleCrossOutLifeline()
        }

        btnDoubleDown.setOnClickListener {
            toggleConfidenceMultiplier()
        }

        btnShowClue.setOnClickListener {
            handleShowClue()
        }
    }

    private fun setupBackProtection() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExitQuiz()
            }
        })
    }

    private fun confirmExitQuiz() {
        val resultView = findViewById<View>(R.id.result_view)
        if (resultView.visibility == View.VISIBLE || (quizQueue.isEmpty() && currentActiveItem == null)) {
            finish()
            return
        }

        saveCurrentSession()
        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Leave Quiz Session?",
            message = "Your session progress is safely saved. You can leave and continue anytime right where you left off!",
            confirmText = "Leave & Save",
            cancelText = "Keep Playing",
            isDestructive = false,
            badgeIcon = "💾",
            onConfirm = {
                saveCurrentSession()
                finish()
            }
        )
    }

    // ========================================================================
    // STEP 1: FETCH TEACHER'S LIMITS
    // ========================================================================
    private fun initializeQuizSession() {
        val userId = auth.currentUser?.uid ?: return

        if (restoreSavedSession(userId)) {
            return
        }

        questionText.text = "Loading classroom settings..."
        optionsContainer.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

        db.collection("users").document(userId).get().addOnSuccessListener { userDoc ->
            val schoolId = userDoc.getString("schoolId") ?: ""
            val section = userDoc.getString("section") ?: ""
            val classId = userDoc.getString("classId") ?: ""

            if (classId.isNotBlank()) {
                db.collection("classes").document(classId).get().addOnSuccessListener { cDoc ->
                    if (cDoc.exists()) {
                        maxSessionsPerDay = (cDoc.getLong("maxSessionsPerDay")?.toInt() ?: 3).coerceIn(3, 5)
                        val rawItems = cDoc.getLong("maxItemsPerSession")?.toInt() ?: 5
                        maxItemsPerSession = rawItems.coerceIn(3, 10)
                        checkDailyLimits(userId)
                    } else {
                        fallbackFetchClass(schoolId, section, userId)
                    }
                }.addOnFailureListener { fallbackFetchClass(schoolId, section, userId) }
            } else {
                fallbackFetchClass(schoolId, section, userId)
            }
        }.addOnFailureListener { showLoadError() }
    }

    private fun fallbackFetchClass(schoolId: String, section: String, userId: String) {
        db.collection("classes")
            .whereEqualTo("schoolId", schoolId)
            .whereArrayContains("joinedStudents", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { studentClassDocs ->
                if (!studentClassDocs.isEmpty) {
                    val classDoc = studentClassDocs.documents[0]
                    maxSessionsPerDay = (classDoc.getLong("maxSessionsPerDay")?.toInt() ?: 3).coerceIn(3, 5)
                    val rawItems = classDoc.getLong("maxItemsPerSession")?.toInt() ?: 5
                    maxItemsPerSession = rawItems.coerceIn(3, 10)
                    checkDailyLimits(userId)
                } else {
                    db.collection("classes")
                        .whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("section", section)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { classDocs ->
                            if (!classDocs.isEmpty) {
                                val classDoc = classDocs.documents[0]
                                maxSessionsPerDay = (classDoc.getLong("maxSessionsPerDay")?.toInt() ?: 3).coerceIn(3, 5)
                                val rawItems = classDoc.getLong("maxItemsPerSession")?.toInt() ?: 5
                                maxItemsPerSession = rawItems.coerceIn(3, 10)
                            }
                            checkDailyLimits(userId)
                        }
                        .addOnFailureListener { showLoadError() }
                }
            }
            .addOnFailureListener { showLoadError() }
    }

    private fun showLoadError() {
        optionsContainer.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE
        questionText.text = getString(R.string.error_load_settings_failed)
        val resultView = findViewById<View>(R.id.result_view)
        val btnRestart = findViewById<Button>(R.id.btn_restart)
        val scoreText = findViewById<TextView>(R.id.final_score_text)
        scoreText.visibility = View.VISIBLE
        scoreText.text = getString(R.string.error_load_settings_failed)
        btnRestart.text = getString(R.string.action_retry)
        btnRestart.setOnClickListener { finish() }
        resultView.visibility = View.VISIBLE
    }

    // ========================================================================
    // STEP 2: CHECK DAILY LIMITS
    // ========================================================================
    private fun checkDailyLimits(userId: String) {
        questionText.text = "Checking practice energy..."

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        db.collection("users").document(userId).collection("quiz_history")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .get()
            .addOnSuccessListener { historyDocs ->
                val sessionsToday = historyDocs.documents.count { it.getString("quizType") != "material" }
                val remainingPods = (maxSessionsPerDay - sessionsToday).coerceAtLeast(0)
                updateFuelPodsDisplay(remainingPods, maxSessionsPerDay)

                if (sessionsToday >= maxSessionsPerDay) {
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)
                    val titleText = findViewById<TextView>(R.id.tv_result_title)
                    val emojiText = findViewById<TextView>(R.id.tv_result_emoji)
                    val xpSummary = findViewById<View>(R.id.layout_xp_summary)

                    emojiText.text = "⚡"
                    titleText.text = "Practice Energy Complete!"
                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "Great job today!\n\nYou've completed all $maxSessionsPerDay daily practice sessions.\n\nYour practice energy refills tomorrow at 12:00 AM so your memory can consolidate!"
                    xpSummary.visibility = View.GONE
                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Dashboard"
                    btnRestart.setOnClickListener { finish() }
                } else {
                    loadWordsForSession(userId)
                }
            }
            .addOnFailureListener { showLoadError() }
    }

    private fun updateFuelPodsDisplay(remaining: Int, max: Int) {
        val podBuilder = StringBuilder()
        for (i in 0 until max) {
            podBuilder.append(if (i < remaining) "⚡ " else "⚪ ")
        }
        podBuilder.append("Daily Practice Energy")
        tvFuelPods.text = podBuilder.toString()
    }

    // ========================================================================
    // STEP 3: LOAD WORDS & BATCH PRE-GENERATE ALL QUESTIONS
    // ========================================================================
    private fun loadWordsForSession(userId: String) {
        val currentTime = System.currentTimeMillis()
        questionText.text = "Loading your review words..."

        db.collection("users").document(userId).collection("history")
            .whereLessThanOrEqualTo("nextReview", currentTime)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val eligibleDocs = documents.documents.filter { doc ->
                    val rawW = doc.getString("word")?.trim() ?: ""
                    val w = GabAIUtils.sanitizeVocabularyWord(rawW)
                    val wordCount = w.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    w.isNotBlank() && wordCount in 1..4
                }
                val readyCount = eligibleDocs.size

                if (readyCount < maxItemsPerSession) {
                    val needed = maxItemsPerSession - readyCount
                    val resultView = findViewById<View>(R.id.result_view)
                    val btnRestart = findViewById<Button>(R.id.btn_restart)
                    val scoreText = findViewById<TextView>(R.id.final_score_text)
                    val titleText = findViewById<TextView>(R.id.tv_result_title)
                    val emojiText = findViewById<TextView>(R.id.tv_result_emoji)
                    val xpSummary = findViewById<View>(R.id.layout_xp_summary)

                    emojiText.text = "🔒"
                    titleText.text = "Session Locked"
                    scoreText.visibility = View.VISIBLE
                    scoreText.text = "You have $readyCount word(s) ready for review.\n\n" +
                            "You need $needed more saved word(s) to unlock this $maxItemsPerSession-item quiz.\n\n" +
                            "Keep reading, exploring, and scanning words to build your vocabulary deck!"
                    xpSummary.visibility = View.GONE
                    resultView.visibility = View.VISIBLE
                    btnRestart.text = "Return to Reading"
                    btnRestart.setOnClickListener { finish() }
                } else {
                    val targetDocs = eligibleDocs.take(maxItemsPerSession)
                    sessionDocSnapshots.clear()
                    sessionDocSnapshots.addAll(targetDocs)
                    batchGenerateQuizQuestions(targetDocs, userId)
                }
            }
            .addOnFailureListener { e ->
                questionText.text = "Failed to load session: ${e.message}"
            }
    }

    // ========================================================================
    // BATCH AI PRE-GENERATION (Single Request for All Session Words)
    // ========================================================================
    private fun batchGenerateQuizQuestions(docs: List<DocumentSnapshot>, userId: String) {
        questionText.text = "GabAI is preparing your dynamic challenge quest..."
        optionsContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val itemsSpec = StringBuilder()
                docs.forEachIndexed { i, doc ->
                    val rawW = doc.getString("word")?.trim() ?: ""
                    val w = GabAIUtils.sanitizeVocabularyWord(rawW).ifBlank { rawW }
                    val d = doc.getString("explanation")?.trim() ?: ""
                    val c = doc.getString("originalContext")?.trim() ?: ""
                    val pos = doc.getString("partOfSpeech")?.trim() ?: ""
                    val role = doc.getString("inSentenceRole")?.trim() ?: ""
                    itemsSpec.append("${i + 1}. Word: \"$w\"\n   Part of Speech: \"${if (pos.isNotBlank()) pos else "Unspecified"}\"\n   Grammatical Role: \"${if (role.isNotBlank()) role else "Unspecified"}\"\n   Definition: \"$d\"\n   Context: \"$c\"\n\n")
                }

                val prompt = """
                    You are an expert pedagogical assessment designer creating high-school-level vocabulary challenge questions for Grade 10 students.
                    Generate exactly ${docs.size} UNAMBIGUOUS challenge questions, one for each target word provided below:

                    $itemsSpec

                    CRITICAL MULTI-MECHANIC & GRAMMAR-AWARE SPECIFICATIONS:
                    1. STRICT GRAMMATICAL AWARENESS & PART-OF-SPEECH CONCORDANCE:
                       - "clozeOptions": MUST contain 4 options that share the EXACT same part of speech, exact same grammatical tense (e.g., all past tense -ed verbs), exact same grammatical number (all plural or all singular), and exact syntactic function as the target word. A student must NEVER be able to eliminate options merely through grammar elimination.
                       - "clozeSentence": The surrounding sentence grammar (subject-verb agreement, verb tense, prepositional pairing, article "a/an" compatibility) must strictly agree with the target word.
                    2. CONTEXT MEANING DETECTIVE:
                       - "contextSentence": A vivid, context-rich sentence featuring the target word naturally.
                       - "contextOptions": 4 clear, plausible definition choices sharing the exact same grammatical phrasing (e.g. all infinitive verb phrases "to [action]..." or all noun phrases). Exactly 1 is the accurate contextual meaning.
                       - "contextCorrect": The accurate contextual definition among the 4 choices.
                    3. SPOT THE IMPOSTOR (GLITCH HUNT):
                       - "genuineSentence": A sophisticated, natural sentence demonstrating correct usage of the target word.
                       - "impostorSentence": An absurd, comical, or logically flawed sentence misusing the target word.
                    4. GRAMMAR LANDMINE (SENTENCE DEFUSAL 💣):
                       - "landmineSentence": A compelling, full sentence featuring the target word, containing ONE subtle grammatical trap (e.g. subject-verb agreement error, wrong verb tense, broken preposition, or wrong inflection).
                       - "landmineSegments": An array of exactly 4 short phrases/segments (1-3 words each) taken directly from the "landmineSentence". Exactly 3 segments are grammatically SAFE, and exactly 1 segment is the FAULTY LANDMINE wire.
                       - "landmineFaultySegment": The exact faulty segment among the 4 segments that contains the grammatical error.
                       - "landmineCorrection": The clean, corrected replacement for the faulty segment (e.g., "was conclusive").
                       - "landmineExplanation": A 1-2 sentence explanation of why the faulty wire was a grammatical error and how to fix it.
                    5. RAPID MATCH:
                       - "briefMeaning": A concise 2-to-4 word definition of the target word.
                    6. EDUCATIONAL EXPLANATION:
                       - 1-2 sentence pedagogical insight explaining why the answer is correct based on grammar and context clues.

                    CRITICAL: Output ONLY a valid JSON array of objects. No markdown backticks outside JSON, no commentary.
                    JSON Format:
                    [
                      {
                        "itemIndex": 1,
                        "word": "cascade",
                        "clozeSentence": "Her sparkling hair fell like a _______ of waters down her back.",
                        "clozeOptions": ["cascade", "torrent", "current", "stream"],
                        "clozeCorrect": "cascade",
                        "contextSentence": "The mountain stream created a beautiful cascade of clear waters over the rocky ledge.",
                        "contextOptions": ["a steep waterfall or shower of water", "a sudden drought or lack of moisture", "a small pile of dry leaves", "a solid barrier blocking the river"],
                        "contextCorrect": "a steep waterfall or shower of water",
                        "genuineSentence": "The mountain stream created a beautiful cascade of clear waters.",
                        "impostorSentence": "He decided to eat a crunchy cascade for lunch with soup.",
                        "landmineSentence": "The clear water cascades down the mountain, but neither of the streams reach the ocean.",
                        "landmineSegments": ["water cascades", "down the mountain", "neither of the streams", "reach the ocean"],
                        "landmineFaultySegment": "reach the ocean",
                        "landmineCorrection": "reaches the ocean",
                        "landmineExplanation": "Because 'neither' is grammatically singular, the verb must be 'reaches the ocean', not 'reach'.",
                        "briefMeaning": "flowing waterfall",
                        "explanation": "The context describes 'waters down her back' and 'waters over the rocky ledge', indicating a flowing cascade."
                      }
                    ]
                """.trimIndent()

                val response = withContext(Dispatchers.IO) {
                    generativeModel.generateContent(prompt)
                }

                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) {
                    jsonStr = jsonStr.substring(startIndex, endIndex + 1)
                }

                val jsonArray = JSONArray(jsonStr)
                quizQueue.clear()

                val allRapidBackup = docs.map { d ->
                    val rawWord = d.getString("word")?.trim() ?: ""
                    val cleanW = GabAIUtils.sanitizeVocabularyWord(rawWord).ifBlank { rawWord }
                    mapOf(
                        "docId" to d.id,
                        "word" to cleanW,
                        "explanation" to (d.getString("explanation")?.trim() ?: "")
                    )
                }

                for (i in 0 until jsonArray.length().coerceAtMost(docs.size)) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawTargetWord = docs[i].getString("word")?.trim() ?: obj.optString("word", "").trim()
                    val targetWord = GabAIUtils.sanitizeVocabularyWord(rawTargetWord).ifBlank { rawTargetWord }
                    val cloze = obj.optString("clozeSentence", obj.optString("question", "")).replace("**", "").trim()
                    val contextSent = obj.optString("contextSentence", cloze.replace("_______", targetWord)).replace("**", "").trim()
                    val genuine = obj.optString("genuineSentence", "The word '$targetWord' is used appropriately here.").replace("**", "").trim()
                    val impostor = obj.optString("impostorSentence", "The word '$targetWord' is improperly used here.").replace("**", "").trim()
                    val meaning = obj.optString("briefMeaning", docs[i].getString("explanation") ?: "Key vocabulary word").trim()
                    val explanation = obj.optString("explanation", "Review the sentence clues and definition.").trim()

                    // Grammar Landmine Parsing
                    val lSentence = obj.optString("landmineSentence", "").replace("**", "").trim()
                    val lFaulty = obj.optString("landmineFaultySegment", "").replace("**", "").trim()
                    val lCorrection = obj.optString("landmineCorrection", "").replace("**", "").trim()
                    val lExp = obj.optString("landmineExplanation", explanation).replace("**", "").trim()
                    val lSegmentsArr = obj.optJSONArray("landmineSegments")
                    val lSegmentsList = mutableListOf<String>()
                    if (lSegmentsArr != null) {
                        for (s in 0 until lSegmentsArr.length()) {
                            lSegmentsList.add(lSegmentsArr.getString(s).replace("**", "").trim())
                        }
                    }

                    val assignedMechanic = determineMechanic(i, docs.size)

                    val displayQuestion: String
                    val optionsList: List<String>
                    val correctAnswer: String
                    val itemExplanation: String

                    if (assignedMechanic == MechanicType.CONTEXT_MEANING) {
                        displayQuestion = "In this sentence context, what does \"$targetWord\" mean?"
                        val cOptsArr = obj.optJSONArray("contextOptions") ?: obj.optJSONArray("options")
                        val rawList = mutableListOf<String>()
                        if (cOptsArr != null) {
                            for (k in 0 until cOptsArr.length()) {
                                rawList.add(cOptsArr.getString(k).replace("**", "").trim())
                            }
                        }
                        val cCorrect = obj.optString("contextCorrect", docs[i].getString("explanation") ?: meaning).replace("**", "").trim()
                        val fullList = if (rawList.contains(cCorrect)) rawList else (rawList.take(3) + cCorrect)
                        optionsList = fullList.shuffled()
                        correctAnswer = cCorrect
                        itemExplanation = explanation
                    } else if (assignedMechanic == MechanicType.GRAMMAR_LANDMINE) {
                        displayQuestion = if (lSentence.isNotBlank()) lSentence else contextSent
                        correctAnswer = if (lFaulty.isNotBlank()) lFaulty else targetWord
                        val finalCorrection = if (lCorrection.isNotBlank()) lCorrection else targetWord
                        val finalSegments = if (lSegmentsList.size == 4) {
                            if (lSegmentsList.contains(correctAnswer)) lSegmentsList else (lSegmentsList.take(3) + correctAnswer)
                        } else {
                            listOf(correctAnswer, "Subject phrase", "Verb tense", "Preposition link")
                        }
                        optionsList = finalSegments.shuffled()
                        itemExplanation = lExp
                    } else {
                        displayQuestion = cloze
                        val clOptsArr = obj.optJSONArray("clozeOptions") ?: obj.optJSONArray("options")
                        val rawList = mutableListOf<String>()
                        if (clOptsArr != null) {
                            for (k in 0 until clOptsArr.length()) {
                                rawList.add(clOptsArr.getString(k).replace("**", "").trim())
                            }
                        }
                        val clCorrect = obj.optString("clozeCorrect", obj.optString("correct", targetWord)).replace("**", "").trim()
                        val fullList = if (rawList.contains(clCorrect)) rawList else (rawList.take(3) + clCorrect)
                        optionsList = fullList.shuffled()
                        correctAnswer = clCorrect
                        itemExplanation = explanation
                    }

                    quizQueue.add(
                        QuizQuestionItem(
                            docId = docs[i].id,
                            interval = docs[i].getLong("interval")?.toInt() ?: 1,
                            word = targetWord,
                            question = displayQuestion,
                            options = optionsList,
                            correct = correctAnswer,
                            explanation = itemExplanation,
                            mechanic = assignedMechanic,
                            contextSentence = contextSent,
                            genuineSentence = genuine,
                            impostorSentence = impostor,
                            briefMeaning = meaning,
                            backupRapidWords = allRapidBackup,
                            landmineSentence = if (lSentence.isNotBlank()) lSentence else contextSent,
                            landmineSegments = optionsList,
                            landmineFaultySegment = correctAnswer,
                            landmineCorrection = if (lCorrection.isNotBlank()) lCorrection else correctAnswer
                        )
                    )
                }

                if (quizQueue.isEmpty()) throw Exception("AI did not produce valid questions.")

                totalQuestionsInSession = quizQueue.size
                currentQuestionIndex = 0
                saveCurrentSession()
                displayNextQuestion()

            } catch (e: Exception) {
                val resultView = findViewById<View>(R.id.result_view)
                val btnRestart = findViewById<Button>(R.id.btn_restart)
                val scoreText = findViewById<TextView>(R.id.final_score_text)
                val titleText = findViewById<TextView>(R.id.tv_result_title)

                titleText.text = "Connection Notice"
                scoreText.visibility = View.VISIBLE
                scoreText.text = "Could not generate questions (${e.localizedMessage ?: "network issue"}).\nPlease check your internet connection and try again."
                btnRestart.text = "Retry Session"
                btnRestart.setOnClickListener {
                    resultView.visibility = View.GONE
                    batchGenerateQuizQuestions(docs, userId)
                }
                resultView.visibility = View.VISIBLE
            }
        }
    }

    private fun determineMechanic(index: Int, totalItems: Int): MechanicType {
        if (index == totalItems - 1) return MechanicType.RAPID_MATCH
        if (totalItems <= 4) {
            return when (index) {
                0 -> MechanicType.CLOZE
                1 -> MechanicType.CONTEXT_MEANING
                2 -> MechanicType.IMPOSTOR
                else -> MechanicType.GRAMMAR_LANDMINE
            }
        }
        if (totalItems == 5) {
            return when (index) {
                0 -> MechanicType.CLOZE
                1 -> MechanicType.CONTEXT_MEANING
                2 -> MechanicType.IMPOSTOR
                3 -> MechanicType.GRAMMAR_LANDMINE
                else -> MechanicType.RAPID_MATCH
            }
        }
        return when (index % 4) {
            0 -> MechanicType.CLOZE
            1 -> MechanicType.CONTEXT_MEANING
            2 -> MechanicType.IMPOSTOR
            else -> MechanicType.GRAMMAR_LANDMINE
        }
    }

    // ========================================================================
    // DISPLAY NEXT QUESTION FROM PRE-GENERATED QUEUE
    // ========================================================================
    private fun displayNextQuestion() {
        if (quizQueue.isEmpty()) {
            showFinalResults()
            return
        }

        isTransitioningQuestion = false
        isCurrentAnswerEvaluated = false
        lastSelectedOptionText = null
        currentActiveItem = quizQueue.removeAt(0)
        currentQuestionIndex++

        val item = currentActiveItem ?: return

        // Update Progress UI
        tvProgressCounter.text = "Question $currentQuestionIndex of $totalQuestionsInSession"
        val pct = (currentQuestionIndex * 100) / totalQuestionsInSession.coerceAtLeast(1)
        tvProgressPercent.text = "$pct%"
        progressQuiz.max = totalQuestionsInSession
        progressQuiz.progress = currentQuestionIndex
        updateSegmentedPills(currentQuestionIndex, totalQuestionsInSession)

        resetLifelineToolbar()
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

        animateCardFlip {
            when (item.mechanic) {
                MechanicType.CLOZE -> renderClozeMechanic(item)
                MechanicType.CONTEXT_MEANING -> renderContextMeaningMechanic(item)
                MechanicType.IMPOSTOR -> renderImpostorMechanic(item)
                MechanicType.GRAMMAR_LANDMINE -> renderGrammarLandmineMechanic(item)
                MechanicType.RAPID_MATCH -> renderRapidMatchMechanic(item)
            }
            startTime = System.currentTimeMillis()
            saveCurrentSession()
        }
    }

    private fun updateSegmentedPills(current: Int, total: Int) {
        segmentedProgressLayout.removeAllViews()
        val density = resources.displayMetrics.density
        val safeTotal = total.coerceAtLeast(1)
        segmentedProgressLayout.weightSum = safeTotal.toFloat()

        val activeIndex = current - 1
        for (i in 0 until safeTotal) {
            val pill = View(this)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (i < safeTotal - 1) {
                params.marginEnd = (3 * density).toInt()
            }
            pill.layoutParams = params
            pill.setBackgroundResource(R.drawable.bg_pill_translucent)
            val color = when {
                i < activeIndex -> Color.parseColor("#00B894") // Completed (Mint Green)
                i == activeIndex -> Color.parseColor("#6C5CE7") // Active Question (Purple)
                else -> Color.parseColor("#E2E8F0") // Upcoming (Gray)
            }
            pill.backgroundTintList = ColorStateList.valueOf(color)
            segmentedProgressLayout.addView(pill)
        }
    }

    private fun resetLifelineToolbar() {
        isCrossOutUsed = false
        btnCrossOut.isEnabled = true
        btnCrossOut.text = "✏️ Cross-Out"
        btnCrossOut.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        btnCrossOut.strokeColor = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
        btnCrossOut.setTextColor(Color.parseColor("#64748B"))

        isDoubleDownActive = false
        btnDoubleDown.isEnabled = true
        btnDoubleDown.text = "🌱 Safe (1x)"
        btnDoubleDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
        btnDoubleDown.strokeColor = ColorStateList.valueOf(Color.parseColor("#A7F3D0"))
        btnDoubleDown.setTextColor(Color.parseColor("#059669"))

        isClueRevealed = false
        btnShowClue.isEnabled = true
        btnShowClue.text = "💡 Clue"
        tvGuidanceTip.text = "Look for context clues in the surrounding words."
    }

    private fun renderGrammarLandmineMechanic(item: QuizQuestionItem) {
        tvQuestionBadge.text = "GRAMMAR LANDMINE 💣"
        tvQuestionStepHint.text = "CUT THE FAULTY WIRE"

        val rawSentence = item.landmineSentence.ifBlank { item.question }
        var formattedSentence = rawSentence

        val wireColors = listOf("#2563EB", "#D97706", "#DC2626", "#7C3AED")
        item.options.forEachIndexed { idx, segment ->
            if (segment.isNotBlank() && formattedSentence.contains(segment, ignoreCase = true)) {
                val wireNum = idx + 1
                val color = wireColors.getOrElse(idx) { "#2563EB" }
                val escapedSeg = Regex.escape(segment)
                formattedSentence = formattedSentence.replaceFirst(
                    Regex("(?i)$escapedSeg"),
                    "<font color='$color'><b>[Wire $wireNum: $segment]</b></font>"
                )
            }
        }

        val promptHtml = "<b>⚠️ Rigged Sentence:</b><br><br><i>\"$formattedSentence\"</i><br><br>One segment breaks grammar rules. <b>Cut the faulty wire to defuse the trap!</b>"
        questionText.text = HtmlCompat.fromHtml(promptHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

        layoutLifelines.visibility = View.VISIBLE
        layoutClozeSlot.visibility = View.VISIBLE
        tvClozeSlotText.text = "⚡ Select the wire segment with the grammatical error"
        tvClozeSlotText.setTextColor(Color.parseColor("#B45309"))
        layoutClozeSlot.setBackgroundResource(R.drawable.bg_pill_translucent)
        layoutClozeSlot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFBEB"))

        layoutImpostorContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.GONE
        optionsContainer.visibility = View.VISIBLE

        layoutGuidanceStrip.visibility = View.VISIBLE
        viewGuidanceDivider.visibility = View.VISIBLE
        tvGuidanceTip.text = "Look for subject-verb agreement, tense mismatch, or broken prepositions."

        resetOptionButtonStyles()

        val wireLabels = listOf("Wire 1", "Wire 2", "Wire 3", "Wire 4")
        for (i in optionButtons.indices) {
            if (i < item.options.size) {
                val opt = item.options[i]
                val wire = wireLabels.getOrElse(i) { "Wire ${i + 1}" }
                optionButtons[i].visibility = View.VISIBLE
                optionButtons[i].isEnabled = true
                optionButtons[i].text = "✂️ $wire: \"$opt\""
                optionButtons[i].setOnClickListener {
                    checkAnswer(opt, optionButtons[i], item)
                }
            } else {
                optionButtons[i].visibility = View.GONE
            }
        }
    }

    private fun renderClozeMechanic(item: QuizQuestionItem) {
        tvQuestionBadge.text = "SENTENCE CLOZE"
        tvQuestionStepHint.text = "FILL IN THE BLANK"
        questionText.text = item.question

        layoutLifelines.visibility = View.VISIBLE
        layoutClozeSlot.visibility = View.VISIBLE
        tvClozeSlotText.text = "👉 Tap an option below to fill the blank"
        tvClozeSlotText.setTextColor(Color.parseColor("#64748B"))
        layoutClozeSlot.setBackgroundResource(R.drawable.bg_cloze_slot)

        layoutGuidanceStrip.visibility = View.VISIBLE
        viewGuidanceDivider.visibility = View.VISIBLE
        tvGuidanceTip.text = "Look for context clues in the surrounding words."

        layoutImpostorContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.GONE
        optionsContainer.visibility = View.VISIBLE

        resetOptionButtonStyles()

        for (i in optionButtons.indices) {
            if (i < item.options.size) {
                val opt = item.options[i]
                val letter = ('A' + i)
                optionButtons[i].visibility = View.VISIBLE
                optionButtons[i].isEnabled = true
                optionButtons[i].text = "$letter)  $opt"
                optionButtons[i].setOnClickListener {
                    tvClozeSlotText.text = "✔ $opt"
                    tvClozeSlotText.setTextColor(Color.parseColor("#161D1F"))
                    checkAnswer(opt, optionButtons[i], item)
                }
            } else {
                optionButtons[i].visibility = View.GONE
            }
        }
    }

    private fun renderContextMeaningMechanic(item: QuizQuestionItem) {
        tvQuestionBadge.text = "CONTEXT MEANING 🔍"
        tvQuestionStepHint.text = "READ CONTEXT & DEFINE"

        val rawSentence = item.contextSentence.ifBlank { item.question }
        val targetWord = item.word.trim()
        val escapedTarget = Regex.escape(targetWord)
        val formattedSentence = if (rawSentence.contains(targetWord, ignoreCase = true)) {
            rawSentence.replace(Regex("(?i)\\b$escapedTarget\\b"), "<font color='#5341CD'><b><u>$targetWord</u></b></font>")
        } else {
            "$rawSentence (<font color='#5341CD'><b><u>$targetWord</u></b></font>)"
        }

        val promptHtml = "<b>Read the passage below:</b><br><br><i>\"$formattedSentence\"</i><br><br>What is the best meaning of <b><font color='#5341CD'>$targetWord</font></b> as used in this sentence?"
        questionText.text = HtmlCompat.fromHtml(promptHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)

        layoutLifelines.visibility = View.VISIBLE
        layoutClozeSlot.visibility = View.GONE
        layoutImpostorContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.GONE
        optionsContainer.visibility = View.VISIBLE

        layoutGuidanceStrip.visibility = View.VISIBLE
        viewGuidanceDivider.visibility = View.VISIBLE
        tvGuidanceTip.text = "Look for context clues in the surrounding words."

        resetOptionButtonStyles()

        for (i in optionButtons.indices) {
            if (i < item.options.size) {
                val opt = item.options[i]
                val letter = ('A' + i)
                optionButtons[i].visibility = View.VISIBLE
                optionButtons[i].isEnabled = true
                optionButtons[i].text = "$letter)  $opt"
                optionButtons[i].setOnClickListener {
                    checkAnswer(opt, optionButtons[i], item)
                }
            } else {
                optionButtons[i].visibility = View.GONE
            }
        }
    }

    private fun renderImpostorMechanic(item: QuizQuestionItem, reuseGeneratedPlacement: Boolean = false) {
        tvQuestionBadge.text = "SPOT THE IMPOSTOR 👾"
        tvQuestionStepHint.text = "GLITCH HUNT"
        questionText.text = "Target Word: \"${item.word}\"\nOne sentence is genuine, and one is an absurd glitch!"

        layoutLifelines.visibility = View.GONE
        layoutClozeSlot.visibility = View.GONE
        optionsContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.GONE
        layoutImpostorContainer.visibility = View.VISIBLE
        layoutGuidanceStrip.visibility = View.GONE
        viewGuidanceDivider.visibility = View.GONE

        if (!reuseGeneratedPlacement) {
            impostorCorrectCardIsA = (0..1).random() == 0
        }
        if (impostorCorrectCardIsA) {
            tvImpostorAText.text = item.genuineSentence
            tvImpostorBText.text = item.impostorSentence
        } else {
            tvImpostorAText.text = item.impostorSentence
            tvImpostorBText.text = item.genuineSentence
        }

        resetImpostorCardStyles()

        cardImpostorA.setOnClickListener {
            handleImpostorSelection(true, item)
        }
        cardImpostorB.setOnClickListener {
            handleImpostorSelection(false, item)
        }
    }

    private fun resetImpostorCardStyles() {
        cardImpostorA.isEnabled = true
        cardImpostorA.setCardBackgroundColor(Color.WHITE)
        cardImpostorA.strokeColor = Color.parseColor("#E2E8F0")

        cardImpostorB.isEnabled = true
        cardImpostorB.setCardBackgroundColor(Color.WHITE)
        cardImpostorB.strokeColor = Color.parseColor("#E2E8F0")
    }

    private fun handleImpostorSelection(selectedA: Boolean, item: QuizQuestionItem) {
        cardImpostorA.isEnabled = false
        cardImpostorB.isEnabled = false

        val isCorrect = (selectedA == impostorCorrectCardIsA)
        val chosenCard = if (selectedA) cardImpostorA else cardImpostorB
        val correctCard = if (impostorCorrectCardIsA) cardImpostorA else cardImpostorB
        val chosenSentence = if (selectedA) tvImpostorAText.text.toString() else tvImpostorBText.text.toString()

        if (isCorrect) {
            chosenCard.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
            chosenCard.strokeColor = Color.parseColor("#10B981")
            bonusXP += 5
            AchievementManager.recordGlitchBuster(this)
            checkAnswer(chosenSentence, null, item, forceCorrect = true, customCorrectAnswer = item.genuineSentence)
        } else {
            chosenCard.setCardBackgroundColor(Color.parseColor("#FEF2F2"))
            chosenCard.strokeColor = Color.parseColor("#EF4444")
            correctCard.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
            correctCard.strokeColor = Color.parseColor("#10B981")
            checkAnswer(chosenSentence, null, item, forceCorrect = false, customCorrectAnswer = item.genuineSentence)
        }
    }

    private fun renderRapidMatchMechanic(item: QuizQuestionItem) {
        tvQuestionBadge.text = "RAPID MATCH ⚡"
        tvQuestionStepHint.text = "FINALE PAIRING"
        questionText.text = "Match the 3 vocabulary words with their meanings to finish the session!"

        layoutLifelines.visibility = View.GONE
        layoutClozeSlot.visibility = View.GONE
        optionsContainer.visibility = View.GONE
        layoutImpostorContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.VISIBLE
        layoutGuidanceStrip.visibility = View.GONE
        viewGuidanceDivider.visibility = View.GONE

        rapidPairsCompleted = 0
        rapidSelectedWord = null
        rapidSelectedWordBtn = null
        rapidStartTime = System.currentTimeMillis()

        val word1 = item.word
        val def1 = item.briefMeaning
        rapidDocId1 = item.docId

        val otherCandidates = if (sessionDocSnapshots.isNotEmpty()) {
            sessionDocSnapshots.filter { (it.getString("word") ?: "").trim() != item.word.trim() }.map {
                Triple(it.id, it.getString("word")?.trim() ?: "", it.getString("explanation")?.trim() ?: "")
            }
        } else {
            item.backupRapidWords.filter { (it["word"] ?: "").trim() != item.word.trim() }.map {
                Triple(it["docId"] ?: "", it["word"] ?: "", it["explanation"] ?: "")
            }
        }

        val cand2 = otherCandidates.getOrNull(0)
        val word2 = cand2?.second?.ifBlank { null } ?: "agile"
        val def2 = cand2?.third?.ifBlank { null } ?: "quick and nimble"
        rapidDocId2 = cand2?.first ?: ""

        val cand3 = otherCandidates.getOrNull(1)
        val word3 = cand3?.second?.ifBlank { null } ?: "arid"
        val def3 = cand3?.third?.ifBlank { null } ?: "extremely dry"
        rapidDocId3 = cand3?.first ?: ""

        val pairs = listOf(
            word1 to def1,
            word2 to def2,
            word3 to def3
        )
        val words = pairs.map { it.first }
        val defs = pairs.map { it.second }.shuffled()

        for (i in 0 until 3) {
            val wBtn = rapidWordButtons[i]
            val dBtn = rapidDefButtons[i]

            wBtn.isEnabled = true
            wBtn.text = words[i]
            resetRapidButtonStyle(wBtn)

            dBtn.isEnabled = true
            dBtn.text = defs[i]
            resetRapidButtonStyle(dBtn)

            wBtn.setOnClickListener {
                handleRapidWordTap(wBtn, words[i])
            }
            dBtn.setOnClickListener {
                handleRapidDefTap(dBtn, defs[i], pairs, item)
            }
        }
    }

    private fun resetRapidButtonStyle(btn: MaterialButton) {
        btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
        btn.setTextColor(Color.parseColor("#1E293B"))
    }

    private fun handleRapidWordTap(btn: MaterialButton, word: String) {
        rapidSelectedWordBtn?.let { resetRapidButtonStyle(it) }
        rapidSelectedWord = word
        rapidSelectedWordBtn = btn

        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#6366F1"))
        btn.setTextColor(Color.parseColor("#4338CA"))
        GabAIUtils.performHaptic(btn, HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun handleRapidDefTap(
        defBtn: MaterialButton,
        defText: String,
        pairs: List<Pair<String, String>>,
        item: QuizQuestionItem
    ) {
        val selectedWord = rapidSelectedWord ?: return
        val wordBtn = rapidSelectedWordBtn ?: return

        val expectedDef = pairs.find { it.first == selectedWord }?.second
        if (expectedDef == defText) {
            wordBtn.isEnabled = false
            defBtn.isEnabled = false
            wordBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
            wordBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            wordBtn.setTextColor(Color.parseColor("#065F46"))

            defBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
            defBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            defBtn.setTextColor(Color.parseColor("#065F46"))

            GabAIUtils.performHaptic(defBtn, HapticFeedbackConstants.CONFIRM)
            rapidSelectedWord = null
            rapidSelectedWordBtn = null
            rapidPairsCompleted++

            if (rapidPairsCompleted >= 3) {
                val clearDuration = System.currentTimeMillis() - rapidStartTime
                AchievementManager.recordRapidClear(this, clearDuration)
                bonusXP += 10
                if (rapidDocId2.isNotBlank()) updateSRSMetadata(rapidDocId2, 1, true, clearDuration)
                if (rapidDocId3.isNotBlank()) updateSRSMetadata(rapidDocId3, 1, true, clearDuration)
                val summaryMatched = pairs.joinToString("; ") { "${it.first} ➔ ${it.second}" }
                checkAnswer(summaryMatched, null, item, forceCorrect = true, customCorrectAnswer = summaryMatched)
            }
        } else {
            GabAIUtils.performHaptic(defBtn, HapticFeedbackConstants.REJECT)
            defBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            defBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            defBtn.postDelayed({
                resetRapidButtonStyle(defBtn)
            }, 500)
        }
    }

    // ========================================================================
    // LIFELINE HANDLERS
    // ========================================================================
    private fun handleCrossOutLifeline() {
        val item = currentActiveItem ?: return
        if (isCrossOutUsed) return
        if (item.mechanic != MechanicType.CLOZE && item.mechanic != MechanicType.CONTEXT_MEANING && item.mechanic != MechanicType.GRAMMAR_LANDMINE) return

        isCrossOutUsed = true
        btnCrossOut.isEnabled = false
        btnCrossOut.text = "✏️ Cross-Out (Used)"
        btnCrossOut.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))

        val cleanCorrect = item.correct.replace("*", "").trim().removeSuffix(".")
        var crossedOut = 0
        for ((idx, btn) in optionButtons.withIndex()) {
            val opt = item.options.getOrNull(idx) ?: btn.text.toString().substringAfter(") ").trim()
            val cleanOption = opt.replace("*", "").trim().removeSuffix(".")
            if (!cleanOption.equals(cleanCorrect, ignoreCase = true) && crossedOut < 2 && btn.visibility == View.VISIBLE) {
                btn.paintFlags = btn.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                btn.alpha = 0.45f
                btn.isEnabled = false
                crossedOut++
            }
        }
        GabAIUtils.performHaptic(btnCrossOut, HapticFeedbackConstants.CLOCK_TICK)
        AchievementManager.recordCrossOutUse(this)
    }

    private fun toggleConfidenceMultiplier() {
        isDoubleDownActive = !isDoubleDownActive
        if (isDoubleDownActive) {
            btnDoubleDown.text = "🔥 100% Sure (2x)"
            btnDoubleDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            btnDoubleDown.strokeColor = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
            btnDoubleDown.setTextColor(Color.parseColor("#D97706"))
        } else {
            btnDoubleDown.text = "🌱 Safe (1x)"
            btnDoubleDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
            btnDoubleDown.strokeColor = ColorStateList.valueOf(Color.parseColor("#A7F3D0"))
            btnDoubleDown.setTextColor(Color.parseColor("#059669"))
        }
        GabAIUtils.performHaptic(btnDoubleDown, HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun handleShowClue() {
        val item = currentActiveItem ?: return
        if (isClueRevealed) return
        isClueRevealed = true
        btnShowClue.isEnabled = false
        btnShowClue.text = "💡 Clue Shown"
        if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
            tvGuidanceTip.text = "Hint: Inspect grammatical agreement (singular/plural) or verb tense consistency."
        } else {
            tvGuidanceTip.text = "Hint: Meaning relates to '${item.briefMeaning}'."
        }
        GabAIUtils.performHaptic(btnShowClue, HapticFeedbackConstants.CLOCK_TICK)
    }

    // ========================================================================
    // TWO-STEP ANSWER FLOW & EDUCATIONAL INSIGHT PILL
    // ========================================================================
    private fun checkAnswer(
        selectedOption: String,
        selectedBtn: MaterialButton?,
        item: QuizQuestionItem,
        forceCorrect: Boolean? = null,
        customCorrectAnswer: String? = null
    ) {
        val responseTime = System.currentTimeMillis() - startTime
        totalAttempts++
        isCurrentAnswerEvaluated = true
        lastSelectedOptionText = selectedOption

        // Freeze all buttons immediately to prevent duplicate presses
        for (btn in optionButtons) {
            btn.isEnabled = false
        }

        val cleanSelected = selectedOption.replace("*", "").trim().removeSuffix(".")
        val cleanCorrect = (customCorrectAnswer ?: item.correct).replace("*", "").trim().removeSuffix(".")
        val isCorrect = forceCorrect ?: cleanSelected.equals(cleanCorrect, ignoreCase = true)

        // 1. Audit Trail: Save with educational explanation
        val resultItem = hashMapOf(
            "question" to item.question,
            "targetWord" to item.word,
            "correctAnswer" to cleanCorrect,
            "userAnswer" to cleanSelected,
            "isCorrect" to isCorrect,
            "explanation" to item.explanation,
            "mechanic" to item.mechanic.name,
            "options" to item.options,
            "contextSentence" to item.contextSentence,
            "genuineSentence" to item.genuineSentence,
            "impostorSentence" to item.impostorSentence,
            "briefMeaning" to item.briefMeaning,
            "landmineSentence" to item.landmineSentence,
            "landmineFaultySegment" to item.landmineFaultySegment,
            "landmineCorrection" to item.landmineCorrection
        )
        sessionResults.add(resultItem)

        // 2. High-Contrast Two-Step Button Feedback & XP Calculation
        if (isCorrect) {
            score++
            comboStreak++
            if (comboStreak > maxComboStreak) maxComboStreak = comboStreak

            baseXP += 10
            val addedCombo = when {
                comboStreak == 2 -> 2
                comboStreak == 3 -> 5
                comboStreak == 4 -> 8
                comboStreak >= 5 -> 15
                else -> 0
            }
            comboXP += addedCombo

            if (responseTime < 4000) bonusXP += 3
            if (isDoubleDownActive) bonusXP += 10
            if (item.mechanic == MechanicType.CONTEXT_MEANING) {
                bonusXP += 5
                AchievementManager.recordDetectiveClue(this)
            } else if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                bonusXP += 5
            }

            AchievementManager.recordWordMastered(this, item.word)
            if (comboStreak >= 5) {
                AchievementManager.recordComboFlawless(this, comboStreak)
            }

            updateSRSMetadata(item.docId, item.interval, true, responseTime)

            // Mint green success styling
            selectedBtn?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
            selectedBtn?.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
            selectedBtn?.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            selectedBtn?.setTextColor(Color.parseColor("#065F46"))

            if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                layoutClozeSlot.setBackgroundResource(R.drawable.bg_pill_translucent)
                layoutClozeSlot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                tvClozeSlotText.text = "🛡️ DEFUSED: Faulty wire cut successfully!"
                tvClozeSlotText.setTextColor(Color.parseColor("#059669"))
                selectedBtn?.text = "🛡️ DEFUSED: \"$cleanCorrect\" ➔ ${item.landmineCorrection}"
            } else {
                layoutClozeSlot.setBackgroundResource(R.drawable.bg_cloze_slot_filled)
                tvClozeSlotText.text = "✔ $cleanCorrect"
                tvClozeSlotText.setTextColor(Color.parseColor("#059669"))
            }

            updateComboBadge(comboStreak)
        } else {
            comboStreak = 0
            tvComboStreak.visibility = View.GONE
            if (isDoubleDownActive) {
                // Double Down penalty: deduct 5 bonus XP (floored at 0)
                bonusXP = (bonusXP - 5).coerceAtLeast(0)
            }
            updateSRSMetadata(item.docId, item.interval, false, responseTime)
            GabAIUtils.performHaptic(selectedBtn ?: questionCard, HapticFeedbackConstants.REJECT)

            // Rose red error styling on selected button
            selectedBtn?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
            selectedBtn?.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            selectedBtn?.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            selectedBtn?.setTextColor(Color.parseColor("#991B1B"))

            if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                layoutClozeSlot.setBackgroundResource(R.drawable.bg_pill_translucent)
                layoutClozeSlot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                tvClozeSlotText.text = "💥 DETONATED: Cut a safe wire!"
                tvClozeSlotText.setTextColor(Color.parseColor("#DC2626"))
                selectedBtn?.text = "💥 DETONATED: Safe Wire"
            } else if (item.mechanic == MechanicType.CLOZE) {
                layoutClozeSlot.setBackgroundResource(R.drawable.bg_cloze_slot)
                tvClozeSlotText.text = "✘ $cleanSelected"
                tvClozeSlotText.setTextColor(Color.parseColor("#991B1B"))
            }

            // Highlight the correct answer in mint so the user learns
            for ((idx, opt) in item.options.withIndex()) {
                val cleanOpt = opt.replace("*", "").trim().removeSuffix(".")
                if (cleanOpt.equals(cleanCorrect, ignoreCase = true) && idx < optionButtons.size) {
                    val correctBtn = optionButtons[idx]
                    correctBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                    correctBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
                    correctBtn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
                    correctBtn.setTextColor(Color.parseColor("#065F46"))
                    if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                        correctBtn.text = "🛡️ TRAP WIRE: \"$cleanCorrect\" ➔ ${item.landmineCorrection}"
                    }
                }
            }
        }

        // Save progress immediately after evaluating answer
        saveCurrentSession()

        // 3. Reveal Educational Insight Pill
        tvExplanation.text = item.explanation
        cardExplanation.alpha = 0f
        cardExplanation.visibility = View.VISIBLE
        cardExplanation.animate().alpha(1f).setDuration(200).start()

        // 4. Reveal "Next Question ➔" button
        if (quizQueue.isEmpty()) {
            btnNextQuestion.text = "Complete Quest 🏆"
            btnNextQuestion.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00B894"))
        } else {
            btnNextQuestion.text = "Next Question ➔"
            btnNextQuestion.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#6C5CE7"))
        }
        btnNextQuestion.alpha = 0f
        btnNextQuestion.visibility = View.VISIBLE
        btnNextQuestion.animate().alpha(1f).setDuration(200).start()
    }

    private fun updateComboBadge(combo: Int) {
        if (combo <= 0) {
            tvComboStreak.visibility = View.GONE
            return
        }

        val badgeText: String
        val bgColorHex: String
        val textColorHex: String

        when (combo) {
            1 -> {
                badgeText = "⚡ 1x Combo"
                bgColorHex = "#EEF2FF"
                textColorHex = "#4338CA"
            }
            2 -> {
                badgeText = "⚡ 2x Combo"
                bgColorHex = "#FEF3C7"
                textColorHex = "#D97706"
            }
            3 -> {
                badgeText = "🔥 3x Combo!"
                bgColorHex = "#EDE9FE"
                textColorHex = "#7C3AED"
            }
            4 -> {
                badgeText = "🌟 4x On Fire!"
                bgColorHex = "#FCE7F3"
                textColorHex = "#DB2777"
            }
            5 -> {
                badgeText = "👑 5x Flawless!"
                bgColorHex = "#D1FAE5"
                textColorHex = "#059669"
            }
            else -> {
                badgeText = "👑 ${combo}x Flawless Legend!"
                bgColorHex = "#D1FAE5"
                textColorHex = "#059669"
            }
        }

        tvComboStreak.text = badgeText
        tvComboStreak.backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgColorHex))
        tvComboStreak.setTextColor(Color.parseColor(textColorHex))
        tvComboStreak.visibility = View.VISIBLE
        tvComboStreak.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(120)?.withEndAction {
            tvComboStreak.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(120)?.start()
        }?.start()
        GabAIUtils.performHaptic(tvComboStreak, HapticFeedbackConstants.CONFIRM)
    }

    private fun resetOptionButtonStyles() {
        for (btn in optionButtons) {
            btn.isEnabled = true
            btn.alpha = 1.0f
            btn.paintFlags = btn.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            btn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EDF2F7"))
            btn.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
            btn.setTextColor(Color.parseColor("#161D1F"))
        }
    }

    private fun animateCardFlip(onHalfway: () -> Unit) {
        questionCard.cameraDistance = 8000f * resources.displayMetrics.density
        optionsContainer.animate().alpha(0f).setDuration(120).start()
        layoutImpostorContainer.animate().alpha(0f).setDuration(120).start()
        layoutRapidMatch.animate().alpha(0f).setDuration(120).start()
        questionCard.animate()
            .rotationY(90f)
            .setDuration(130)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                onHalfway()
                optionsContainer.alpha = 1f
                layoutImpostorContainer.alpha = 1f
                layoutRapidMatch.alpha = 1f
                questionCard.rotationY = -90f
                questionCard.animate()
                    .rotationY(0f)
                    .setDuration(130)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    // ========================================================================
    // SPACED REPETITION MATH
    // ========================================================================
    private fun updateSRSMetadata(docId: String, currentInterval: Int, isCorrect: Boolean, latency: Long) {
        val userId = auth.currentUser?.uid ?: return
        if (docId.isBlank()) return
        val newInterval: Int
        val nextReviewDate: Long

        if (!isCorrect) {
            newInterval = 1
            // HAUNTING: Review this word again in 30 seconds
            nextReviewDate = System.currentTimeMillis() + (30 * 1000)
        } else {
            val intervalStep = when (currentInterval) {
                1 -> 2
                2 -> 4
                4 -> 7
                7 -> 14
                else -> (currentInterval * 1.8).toInt().coerceAtLeast(currentInterval + 1)
            }
            newInterval = if (latency < 4000) (intervalStep * 1.3).toInt().coerceAtLeast(intervalStep + 1) else intervalStep
            // Escalating review interval: in progressive days
            nextReviewDate = System.currentTimeMillis() + (newInterval * 24L * 60 * 60 * 1000)
        }

        val updates = hashMapOf(
            "interval" to newInterval,
            "nextReview" to nextReviewDate
        )

        db.collection("users").document(userId)
            .collection("history").document(docId)
            .update(updates as Map<String, Any>)
    }

    // ========================================================================
    // FINISH AND SAVE SESSION WITH EDUCATIONAL INSIGHTS
    // ========================================================================
    private fun showFinalResults() {
        val userId = auth.currentUser?.uid
        clearSavedSession(userId)

        totalXPEarned = (baseXP + comboXP + bonusXP).coerceAtMost(300)
        if (XPManager.canEarnXP(this)) {
            XPManager.addXP(this, totalXPEarned)
        }
        QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)

        // Save session with explanations to quiz_history
        if (userId != null && sessionResults.isNotEmpty()) {
            val historyData = hashMapOf(
                "quizType" to "recall",
                "timestamp" to System.currentTimeMillis(),
                "finalScore" to score,
                "totalAttempts" to totalAttempts,
                "xpEarned" to totalXPEarned,
                "items" to sessionResults
            )
            db.collection("users").document(userId).collection("quiz_history").add(historyData)
        }

        optionsContainer.visibility = View.GONE
        layoutLifelines.visibility = View.GONE
        layoutImpostorContainer.visibility = View.GONE
        layoutRapidMatch.visibility = View.GONE
        cardExplanation.visibility = View.GONE
        btnNextQuestion.visibility = View.GONE

        val resultView = findViewById<View>(R.id.result_view)
        val scoreText = findViewById<TextView>(R.id.final_score_text)
        val tvXpTotal = findViewById<TextView>(R.id.tv_xp_total)
        val tvXpDetails = findViewById<TextView>(R.id.tv_xp_details)
        val layoutXpSummary = findViewById<View>(R.id.layout_xp_summary)

        val totalQuestions = if (sessionResults.isNotEmpty()) sessionResults.size else totalQuestionsInSession.coerceAtLeast(1)
        scoreText.visibility = View.VISIBLE
        val streakMsg = if (maxComboStreak >= 2) "\n🔥 Max Streak: ${maxComboStreak}x Combo!" else ""
        scoreText.text = "Session Complete!\nYou scored $score / $totalQuestions Questions Correct$streakMsg"

        layoutXpSummary.visibility = View.VISIBLE
        tvXpTotal.text = "+$totalXPEarned XP Earned!"
        tvXpDetails.text = "Base: +$baseXP XP • Combos: +$comboXP XP • Bonuses: +$bonusXP XP"

        val btnRestart = findViewById<Button>(R.id.btn_restart)
        btnRestart.text = "Return to Dashboard"
        btnRestart.setOnClickListener { finish() }

        resultView.visibility = View.VISIBLE
    }

    // ========================================================================
    // PERSISTENCE ENGINE: SAVE, RESTORE, AND CLEAR QUIZ SESSIONS
    // ========================================================================
    private fun saveCurrentSession() {
        val userId = auth.currentUser?.uid ?: return
        val resultView = findViewById<View>(R.id.result_view)
        if (resultView.visibility == View.VISIBLE || (quizQueue.isEmpty() && currentActiveItem == null)) {
            clearSavedSession(userId)
            return
        }

        try {
            val prefs = getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
            val sessionJson = JSONObject()
            sessionJson.put("userId", userId)
            sessionJson.put("timestamp", System.currentTimeMillis())
            sessionJson.put("totalQuestionsInSession", totalQuestionsInSession)
            sessionJson.put("currentQuestionIndex", currentQuestionIndex)
            sessionJson.put("score", score)
            sessionJson.put("totalAttempts", totalAttempts)
            sessionJson.put("comboStreak", comboStreak)
            sessionJson.put("maxComboStreak", maxComboStreak)
            sessionJson.put("baseXP", baseXP)
            sessionJson.put("comboXP", comboXP)
            sessionJson.put("bonusXP", bonusXP)

            // Dynamic session question state
            sessionJson.put("isCurrentAnswerEvaluated", isCurrentAnswerEvaluated)
            sessionJson.put("isCrossOutUsed", isCrossOutUsed)
            sessionJson.put("isDoubleDownActive", isDoubleDownActive)
            sessionJson.put("isClueRevealed", isClueRevealed)
            sessionJson.put("impostorCorrectCardIsA", impostorCorrectCardIsA)
            sessionJson.put("lastSelectedOptionText", lastSelectedOptionText ?: "")

            currentActiveItem?.let { item ->
                sessionJson.put("currentActiveItem", itemToJson(item))
            }

            val queueArr = JSONArray()
            for (item in quizQueue) {
                queueArr.put(itemToJson(item))
            }
            sessionJson.put("quizQueue", queueArr)

            val resultsArr = JSONArray()
            for (res in sessionResults) {
                val rObj = JSONObject()
                res.forEach { (k, v) -> rObj.put(k, v) }
                resultsArr.put(rObj)
            }
            sessionJson.put("sessionResults", resultsArr)

            prefs.edit().putString("active_session_$userId", sessionJson.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun itemToJson(item: QuizQuestionItem): JSONObject {
        return JSONObject().apply {
            put("docId", item.docId)
            put("interval", item.interval)
            put("word", item.word)
            put("question", item.question)
            put("correct", item.correct)
            put("explanation", item.explanation)
            put("mechanic", item.mechanic.name)
            put("contextSentence", item.contextSentence)
            put("genuineSentence", item.genuineSentence)
            put("impostorSentence", item.impostorSentence)
            put("briefMeaning", item.briefMeaning)
            put("landmineSentence", item.landmineSentence)
            put("landmineFaultySegment", item.landmineFaultySegment)
            put("landmineCorrection", item.landmineCorrection)
            val lmArr = JSONArray()
            item.landmineSegments.forEach { lmArr.put(it) }
            put("landmineSegments", lmArr)

            val optsArr = JSONArray()
            item.options.forEach { optsArr.put(it) }
            put("options", optsArr)

            val rapidArr = JSONArray()
            for (map in item.backupRapidWords) {
                val mObj = JSONObject()
                map.forEach { (k, v) -> mObj.put(k, v) }
                rapidArr.put(mObj)
            }
            put("backupRapidWords", rapidArr)
        }
    }

    private fun jsonToItem(qObj: JSONObject): QuizQuestionItem {
        val optsArr = qObj.optJSONArray("options")
        val opts = mutableListOf<String>()
        if (optsArr != null) {
            for (k in 0 until optsArr.length()) {
                opts.add(optsArr.getString(k))
            }
        }
        val lmArr = qObj.optJSONArray("landmineSegments")
        val lmList = mutableListOf<String>()
        if (lmArr != null) {
            for (k in 0 until lmArr.length()) {
                lmList.add(lmArr.getString(k))
            }
        }
        val rapidBackup = mutableListOf<Map<String, String>>()
        val rapidArr = qObj.optJSONArray("backupRapidWords")
        if (rapidArr != null) {
            for (k in 0 until rapidArr.length()) {
                val rObj = rapidArr.getJSONObject(k)
                val map = mutableMapOf<String, String>()
                val keys = rObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = rObj.optString(key, "")
                }
                rapidBackup.add(map)
            }
        }
        val mechName = qObj.optString("mechanic", "CLOZE")
        val mech = try {
            if (mechName == "DETECTIVE") MechanicType.CONTEXT_MEANING else MechanicType.valueOf(mechName)
        } catch (_: Exception) {
            MechanicType.CLOZE
        }
        return QuizQuestionItem(
            docId = qObj.optString("docId", ""),
            interval = qObj.optInt("interval", 1),
            word = qObj.optString("word", ""),
            question = qObj.optString("question", ""),
            options = opts,
            correct = qObj.optString("correct", ""),
            explanation = qObj.optString("explanation", ""),
            mechanic = mech,
            contextSentence = qObj.optString("contextSentence", qObj.optString("clueSentence", "")),
            genuineSentence = qObj.optString("genuineSentence", ""),
            impostorSentence = qObj.optString("impostorSentence", ""),
            briefMeaning = qObj.optString("briefMeaning", ""),
            backupRapidWords = rapidBackup,
            landmineSentence = qObj.optString("landmineSentence", ""),
            landmineSegments = lmList,
            landmineFaultySegment = qObj.optString("landmineFaultySegment", ""),
            landmineCorrection = qObj.optString("landmineCorrection", "")
        )
    }

    private fun restoreSavedSession(userId: String): Boolean {
        try {
            val prefs = getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
            val jsonStr = prefs.getString("active_session_$userId", null) ?: return false
            val sessionJson = JSONObject(jsonStr)

            val sessionUserId = sessionJson.optString("userId", "")
            if (sessionUserId != userId) return false

            val timestamp = sessionJson.optLong("timestamp", 0L)
            if (System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000L) {
                clearSavedSession(userId)
                return false
            }

            totalQuestionsInSession = sessionJson.optInt("totalQuestionsInSession", 0)
            currentQuestionIndex = sessionJson.optInt("currentQuestionIndex", 0)
            score = sessionJson.optInt("score", 0)
            totalAttempts = sessionJson.optInt("totalAttempts", 0)
            comboStreak = sessionJson.optInt("comboStreak", 0)
            maxComboStreak = sessionJson.optInt("maxComboStreak", 0)
            baseXP = sessionJson.optInt("baseXP", 0)
            comboXP = sessionJson.optInt("comboXP", 0)
            bonusXP = sessionJson.optInt("bonusXP", 0)

            isCurrentAnswerEvaluated = sessionJson.optBoolean("isCurrentAnswerEvaluated", false)
            isCrossOutUsed = sessionJson.optBoolean("isCrossOutUsed", false)
            isDoubleDownActive = sessionJson.optBoolean("isDoubleDownActive", false)
            isClueRevealed = sessionJson.optBoolean("isClueRevealed", false)
            impostorCorrectCardIsA = sessionJson.optBoolean("impostorCorrectCardIsA", true)
            lastSelectedOptionText = sessionJson.optString("lastSelectedOptionText", "").takeIf { it.isNotBlank() }

            sessionResults.clear()
            val resArr = sessionJson.optJSONArray("sessionResults")
            if (resArr != null) {
                for (i in 0 until resArr.length()) {
                    val obj = resArr.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = obj.get(key)
                    }
                    sessionResults.add(map)
                }
            }

            quizQueue.clear()
            val queueArr = sessionJson.optJSONArray("quizQueue")
            if (queueArr != null) {
                for (i in 0 until queueArr.length()) {
                    quizQueue.add(jsonToItem(queueArr.getJSONObject(i)))
                }
            }

            val activeObj = sessionJson.optJSONObject("currentActiveItem")
            if (activeObj != null) {
                currentActiveItem = jsonToItem(activeObj)
            } else if (quizQueue.isNotEmpty()) {
                currentActiveItem = quizQueue.removeAt(0)
            }

            if (currentActiveItem == null && quizQueue.isEmpty()) {
                clearSavedSession(userId)
                return false
            }

            displayActiveRestoredQuestion()
            GabAIUtils.showSnackbar(this, "Resumed saved quiz quest (Item $currentQuestionIndex of $totalQuestionsInSession) 🔄")
            return true
        } catch (e: Exception) {
            clearSavedSession(userId)
            return false
        }
    }

    private fun displayActiveRestoredQuestion() {
        val item = currentActiveItem ?: return

        tvProgressCounter.text = "Question $currentQuestionIndex of $totalQuestionsInSession"
        val pct = (currentQuestionIndex * 100) / totalQuestionsInSession.coerceAtLeast(1)
        tvProgressPercent.text = "$pct%"
        progressQuiz.max = totalQuestionsInSession
        progressQuiz.progress = currentQuestionIndex
        updateSegmentedPills(currentQuestionIndex, totalQuestionsInSession)

        updateComboBadge(comboStreak)

        when (item.mechanic) {
            MechanicType.CLOZE -> renderClozeMechanic(item)
            MechanicType.CONTEXT_MEANING -> renderContextMeaningMechanic(item)
            MechanicType.IMPOSTOR -> renderImpostorMechanic(item, reuseGeneratedPlacement = true)
            MechanicType.GRAMMAR_LANDMINE -> renderGrammarLandmineMechanic(item)
            MechanicType.RAPID_MATCH -> renderRapidMatchMechanic(item)
        }

        if (isCurrentAnswerEvaluated) {
            // Already evaluated before app pause/destruction
            btnNextQuestion.visibility = View.VISIBLE
            cardExplanation.visibility = View.VISIBLE
            tvExplanation.text = item.explanation
            layoutLifelines.visibility = View.GONE

            if (item.mechanic == MechanicType.CLOZE || item.mechanic == MechanicType.CONTEXT_MEANING || item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                val cleanCorrect = item.correct.replace("*", "").trim().removeSuffix(".")
                val cleanSelected = lastSelectedOptionText?.replace("*", "")?.trim()?.removeSuffix(".")
                for ((idx, btn) in optionButtons.withIndex()) {
                    btn.isEnabled = false
                    val opt = item.options.getOrNull(idx) ?: btn.text.toString().substringAfter(") ").trim()
                    val cleanOpt = opt.replace("*", "").trim().removeSuffix(".")
                    if (cleanOpt.equals(cleanCorrect, ignoreCase = true)) {
                        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ECFDF5"))
                        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#00B894"))
                        btn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
                        btn.setTextColor(Color.parseColor("#065F46"))
                        if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                            btn.text = "🛡️ DEFUSED: \"$cleanCorrect\" ➔ ${item.landmineCorrection}"
                        }
                    } else if (cleanOpt.equals(cleanSelected, ignoreCase = true)) {
                        btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF2F2"))
                        btn.strokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                        btn.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
                        btn.setTextColor(Color.parseColor("#991B1B"))
                        if (item.mechanic == MechanicType.GRAMMAR_LANDMINE) {
                            btn.text = "💥 DETONATED: Safe Wire"
                        }
                    }
                }
            } else if (item.mechanic == MechanicType.IMPOSTOR) {
                cardImpostorA.isEnabled = false
                cardImpostorB.isEnabled = false
                val isSelectedA = lastSelectedOptionText == tvImpostorAText.text.toString()
                val chosenCard = if (isSelectedA) cardImpostorA else cardImpostorB
                val correctCard = if (impostorCorrectCardIsA) cardImpostorA else cardImpostorB
                val isCorrect = (isSelectedA == impostorCorrectCardIsA)
                if (isCorrect) {
                    chosenCard.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
                    chosenCard.strokeColor = Color.parseColor("#10B981")
                } else {
                    chosenCard.setCardBackgroundColor(Color.parseColor("#FEF2F2"))
                    chosenCard.strokeColor = Color.parseColor("#EF4444")
                    correctCard.setCardBackgroundColor(Color.parseColor("#ECFDF5"))
                    correctCard.strokeColor = Color.parseColor("#10B981")
                }
            }
        } else {
            resetLifelineToolbar()
            cardExplanation.visibility = View.GONE
            btnNextQuestion.visibility = View.GONE

            if (isCrossOutUsed) {
                btnCrossOut.isEnabled = false
                btnCrossOut.text = "✏️ Cross-Out (Used)"
                btnCrossOut.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
                val cleanCorrect = item.correct.replace("*", "").trim().removeSuffix(".")
                var crossedOut = 0
                for (btn in optionButtons) {
                    val txt = btn.text.toString().substringAfter(") ").trim()
                    val cleanOption = txt.replace("*", "").trim().removeSuffix(".")
                    if (!cleanOption.equals(cleanCorrect, ignoreCase = true) && crossedOut < 2 && btn.visibility == View.VISIBLE) {
                        btn.paintFlags = btn.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        btn.alpha = 0.45f
                        btn.isEnabled = false
                        crossedOut++
                    }
                }
            }
            if (isDoubleDownActive) {
                btnDoubleDown.text = "🔥 100% Sure (2x)"
                btnDoubleDown.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                btnDoubleDown.strokeColor = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
                btnDoubleDown.setTextColor(Color.parseColor("#D97706"))
            }
            if (isClueRevealed) {
                btnShowClue.isEnabled = false
                btnShowClue.text = "💡 Clue Shown"
                tvGuidanceTip.text = "Hint: Meaning relates to '${item.briefMeaning}'."
            }
        }

        startTime = System.currentTimeMillis()
    }

    private fun clearSavedSession(userId: String?) {
        if (userId == null) return
        try {
            getSharedPreferences("quiz_session_prefs", MODE_PRIVATE)
                .edit()
                .remove("active_session_$userId")
                .apply()
        } catch (_: Exception) {}
    }
}