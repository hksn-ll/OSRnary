package com.example.gabai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TimedAssessmentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var assessmentId = ""
    private var assessmentTitle = "Standardized Timed Assessment"
    private var assessmentPurpose = "Phil-IRI Pre-Test (Diagnostic)"
    private var districtScope = "ALL"
    private var targetGrade = "Grades 7-10"
    private var readingPassage = ""
    private var timerMinutes = 20
    private var totalDurationMillis = 20 * 60 * 1000L
    private var remainingTimeMillis = 20 * 60 * 1000L
    private var studentFullName = "Student"
    private var studentSchoolId = ""
    private var studentSection = ""

    private val questions = mutableListOf<TimedQuestion>()
    private var currentIndex = 0
    private var studentAnswers = IntArray(0)

    private var countDownTimer: CountDownTimer? = null
    private var isSubmitted = false
    private var isPassageVisible = false

    // Stage 1 (Timed Passage Reading) Telemetry
    private var readingStartTimeMillis = 0L
    private var passageReadingSecs = 0L
    private var readingSpeedWpm = 0
    private var readingSpeedWps = 0.0
    private var hasFinishedReading = false
    private val readingTimerHandler = Handler(Looper.getMainLooper())
    private var readingTimerRunnable: Runnable? = null

    private val prefName: String
        get() = "timed_assessment_session_${assessmentId}_${auth.currentUser?.uid ?: ""}"

    data class TimedQuestion(
        val q: String,
        val options: List<String>,
        val ans: Int,
        val competency: String,
        val explanation: String
    )

    // Layout views
    private lateinit var layoutBriefing: View
    private lateinit var layoutReading: View
    private lateinit var layoutRunner: View
    private lateinit var layoutResults: View

    // Header views
    private lateinit var tvHeaderDivisionTag: TextView
    private lateinit var tvHeaderTitle: TextView
    private lateinit var chipTimerContainer: View
    private lateinit var tvTimerCountdown: TextView

    // Briefing views
    private lateinit var tvBriefingTitle: TextView
    private lateinit var tvBriefingPurpose: TextView
    private lateinit var tvBriefingTimeLimit: TextView
    private lateinit var tvBriefingItemCount: TextView
    private lateinit var btnStartAssessment: View

    // Stage 1 Dedicated Reading views
    private lateinit var tvReadingWordCount: TextView
    private lateinit var tvReadingLiveTimer: TextView
    private lateinit var tvReadingPassageTitle: TextView
    private lateinit var tvReadingPassageBody: TextView
    private lateinit var btnDoneReading: MaterialButton

    // Runner views
    private lateinit var tvProgressCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardPassage: MaterialCardView
    private lateinit var tvPassageContent: TextView
    private lateinit var btnTogglePassage: View
    private lateinit var tvTogglePassageLabel: TextView
    private lateinit var tvQuestionBadge: TextView
    private lateinit var tvQuestionText: TextView

    private val optCards = mutableListOf<MaterialCardView>()
    private val optBadges = mutableListOf<TextView>()
    private val optTexts = mutableListOf<TextView>()

    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    // Results telemetry & remedial views
    private lateinit var tvReadingSpeedWpm: TextView
    private lateinit var tvReadingRateCategory: TextView
    private lateinit var tvReadingTimeElapsed: TextView
    private lateinit var cardFrustrationRemedial: View
    private lateinit var btnRemedialLibraryPractice: Button
    private lateinit var btnRemedialVocabDrills: Button

    // Cognitive dimension mastery views
    private lateinit var tvDimLiteralScore: TextView
    private lateinit var progressDimLiteral: ProgressBar
    private lateinit var tvDimInferentialScore: TextView
    private lateinit var progressDimInferential: ProgressBar
    private lateinit var tvDimCriticalScore: TextView
    private lateinit var progressDimCritical: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_timed_assessment)

        assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: return finish()

        initViewBindings()
        setupListeners()

        loadStudentInfo()
        loadAssessmentData()
    }

    private fun initViewBindings() {
        layoutBriefing = findViewById(R.id.layout_timed_briefing)
        layoutReading = findViewById(R.id.layout_timed_reading)
        layoutRunner = findViewById(R.id.layout_timed_runner)
        layoutResults = findViewById(R.id.layout_timed_results)

        tvHeaderDivisionTag = findViewById(R.id.tv_header_division_tag)
        tvHeaderTitle = findViewById(R.id.tv_header_timed_title)
        chipTimerContainer = findViewById(R.id.chip_timer_container)
        tvTimerCountdown = findViewById(R.id.tv_timer_countdown)

        tvBriefingTitle = findViewById(R.id.tv_briefing_title)
        tvBriefingPurpose = findViewById(R.id.tv_briefing_purpose)
        tvBriefingTimeLimit = findViewById(R.id.tv_briefing_time_limit)
        tvBriefingItemCount = findViewById(R.id.tv_briefing_item_count)
        btnStartAssessment = findViewById(R.id.btn_start_timed_assessment)

        // Stage 1 Dedicated Reading views
        tvReadingWordCount = findViewById(R.id.tv_reading_word_count)
        tvReadingLiveTimer = findViewById(R.id.tv_reading_live_timer)
        tvReadingPassageTitle = findViewById(R.id.tv_reading_passage_title)
        tvReadingPassageBody = findViewById(R.id.tv_reading_passage_body)
        btnDoneReading = findViewById(R.id.btn_done_reading)

        tvProgressCounter = findViewById(R.id.tv_timed_progress_counter)
        progressBar = findViewById(R.id.progress_timed_quiz)
        cardPassage = findViewById(R.id.card_timed_passage)
        tvPassageContent = findViewById(R.id.tv_timed_passage_content)
        btnTogglePassage = findViewById(R.id.btn_toggle_passage)
        tvTogglePassageLabel = findViewById(R.id.tv_toggle_passage_label)
        tvQuestionBadge = findViewById(R.id.tv_timed_question_badge)
        tvQuestionText = findViewById(R.id.tv_timed_question_text)

        optCards.add(findViewById(R.id.card_timed_opt_0))
        optCards.add(findViewById(R.id.card_timed_opt_1))
        optCards.add(findViewById(R.id.card_timed_opt_2))
        optCards.add(findViewById(R.id.card_timed_opt_3))

        optBadges.add(findViewById(R.id.badge_timed_opt_0))
        optBadges.add(findViewById(R.id.badge_timed_opt_1))
        optBadges.add(findViewById(R.id.badge_timed_opt_2))
        optBadges.add(findViewById(R.id.badge_timed_opt_3))

        optTexts.add(findViewById(R.id.tv_timed_opt_0))
        optTexts.add(findViewById(R.id.tv_timed_opt_1))
        optTexts.add(findViewById(R.id.tv_timed_opt_2))
        optTexts.add(findViewById(R.id.tv_timed_opt_3))

        btnPrev = findViewById(R.id.btn_prev_timed_question)
        btnNext = findViewById(R.id.btn_next_timed_question)

        tvReadingSpeedWpm = findViewById(R.id.tv_reading_speed_wpm)
        tvReadingRateCategory = findViewById(R.id.tv_reading_rate_category)
        tvReadingTimeElapsed = findViewById(R.id.tv_reading_time_elapsed)
        cardFrustrationRemedial = findViewById(R.id.card_frustration_remedial)
        btnRemedialLibraryPractice = findViewById(R.id.btn_remedial_library_practice)
        btnRemedialVocabDrills = findViewById(R.id.btn_remedial_vocab_drills)

        tvDimLiteralScore = findViewById(R.id.tv_dim_literal_score)
        progressDimLiteral = findViewById(R.id.progress_dim_literal)
        tvDimInferentialScore = findViewById(R.id.tv_dim_inferential_score)
        progressDimInferential = findViewById(R.id.progress_dim_inferential)
        tvDimCriticalScore = findViewById(R.id.tv_dim_critical_score)
        progressDimCritical = findViewById(R.id.progress_dim_critical)
    }

    private fun setupListeners() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExit()
            }
        })

        findViewById<ImageButton>(R.id.btn_close_timed_assessment).setOnClickListener {
            confirmExit()
        }

        btnStartAssessment.setOnClickListener {
            GabAIUtils.performHaptic(it)
            if (hasFinishedReading) {
                startQuestionPhase()
            } else {
                startReadingPhase()
            }
        }

        btnDoneReading.setOnClickListener {
            GabAIUtils.performHaptic(it)
            onDoneReadingClicked()
        }

        btnTogglePassage.setOnClickListener {
            togglePassageVisibility()
        }

        findViewById<View>(R.id.btn_close_passage_inline)?.setOnClickListener {
            togglePassageVisibility()
        }

        for (i in 0..3) {
            optCards[i].setOnClickListener {
                selectOption(i)
            }
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                displayCurrentQuestion()
            }
        }

        btnNext.setOnClickListener {
            if (studentAnswers.isNotEmpty() && studentAnswers[currentIndex] == -1) {
                GabAIUtils.showSnackbar(this, "Please choose an answer to proceed.")
                return@setOnClickListener
            }

            if (currentIndex < questions.size - 1) {
                currentIndex++
                displayCurrentQuestion()
            } else {
                confirmSubmit()
            }
        }

        findViewById<Button>(R.id.btn_finish_timed_assessment).setOnClickListener {
            finish()
        }

        GabAIUtils.addSpringPressEffect(btnRemedialLibraryPractice) {
            val intent = Intent(this, LibraryActivity::class.java)
            startActivity(intent)
            finish()
        }

        GabAIUtils.addSpringPressEffect(btnRemedialVocabDrills) {
            val intent = Intent(this, QuizActivity::class.java)
            startActivity(intent)
            finish()
        }
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
        countDownTimer?.cancel()
        countDownTimer = null
        readingTimerRunnable?.let { readingTimerHandler.removeCallbacks(it) }
    }

    private fun saveCurrentSession() {
        if (assessmentId.isEmpty() || questions.isEmpty() || isSubmitted) return
        try {
            val prefs = getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (q in questions) {
                val obj = JSONObject()
                obj.put("q", q.q)
                val optsArr = JSONArray()
                q.options.forEach { optsArr.put(it) }
                obj.put("opts", optsArr)
                obj.put("ans", q.ans)
                obj.put("competency", q.competency)
                obj.put("exp", q.explanation)
                jsonArray.put(obj)
            }
            val answersArray = JSONArray()
            studentAnswers.forEach { answersArray.put(it) }

            prefs.edit()
                .putBoolean("has_saved_session", true)
                .putBoolean("has_finished_reading", hasFinishedReading)
                .putLong("passage_reading_secs", passageReadingSecs)
                .putInt("reading_speed_wpm", readingSpeedWpm)
                .putFloat("reading_speed_wps", readingSpeedWps.toFloat())
                .putString("saved_questions", jsonArray.toString())
                .putString("saved_answers", answersArray.toString())
                .putInt("saved_current_index", currentIndex)
                .putLong("saved_remaining_millis", remainingTimeMillis)
                .apply()
        } catch (_: Exception) {}
    }

    private fun restoreSavedSession(): Boolean {
        if (assessmentId.isEmpty()) return false
        try {
            val prefs = getSharedPreferences(prefName, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("has_saved_session", false)) return false
            val qStr = prefs.getString("saved_questions", null) ?: return false
            val aStr = prefs.getString("saved_answers", null) ?: return false

            hasFinishedReading = prefs.getBoolean("has_finished_reading", false)
            passageReadingSecs = prefs.getLong("passage_reading_secs", 0L)
            readingSpeedWpm = prefs.getInt("reading_speed_wpm", 0)
            readingSpeedWps = prefs.getFloat("reading_speed_wps", 0f).toDouble()

            val qArr = JSONArray(qStr)
            val aArr = JSONArray(aStr)
            val restored = mutableListOf<TimedQuestion>()
            for (i in 0 until qArr.length()) {
                val obj = qArr.getJSONObject(i)
                val q = obj.getString("q")
                val optsArr = obj.getJSONArray("opts")
                val opts = mutableListOf<String>()
                for (j in 0 until optsArr.length()) {
                    opts.add(optsArr.getString(j))
                }
                val ans = obj.getInt("ans")
                val comp = obj.optString("competency", "LITERAL")
                val exp = obj.optString("exp", "")
                restored.add(TimedQuestion(q, opts, ans, comp, exp))
            }

            if (restored.isNotEmpty()) {
                questions.clear()
                questions.addAll(restored)
                studentAnswers = IntArray(restored.size) { idx ->
                    if (idx < aArr.length()) aArr.getInt(idx) else -1
                }
                currentIndex = prefs.getInt("saved_current_index", 0).coerceIn(0, restored.size - 1)
                remainingTimeMillis = prefs.getLong("saved_remaining_millis", totalDurationMillis)
                progressBar.max = questions.size
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun clearSavedSession() {
        try {
            getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
    }

    private fun loadStudentInfo() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        studentSchoolId = prefs.getString("cached_user_school_id", "") ?: ""
        studentSection = prefs.getString("cached_user_section", "") ?: ""
        val cachedFirst = prefs.getString("cached_user_first_name", "") ?: ""
        val cachedLast = prefs.getString("cached_user_last_name", "") ?: ""
        if (cachedFirst.isNotEmpty() || cachedLast.isNotEmpty()) {
            studentFullName = "$cachedFirst $cachedLast".trim()
        }

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val first = doc.getString("firstName") ?: ""
            val last = doc.getString("lastName") ?: ""
            if (first.isNotEmpty() || last.isNotEmpty()) {
                studentFullName = "$first $last".trim()
            }
            studentSchoolId = doc.getString("schoolId") ?: studentSchoolId
            studentSection = doc.getString("section") ?: studentSection
        }
    }

    private fun loadAssessmentData() {
        GabAIUtils.showGlobalLoading(this, "Loading standardized assessment...")

        // Query division_timed_assessments first, with fallback to legacy weekly_assessments if needed
        db.collection("division_timed_assessments").document(assessmentId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    GabAIUtils.hideGlobalLoading(this)
                    populateAssessmentFromDoc(doc)
                } else {
                    // Check legacy collection
                    db.collection("weekly_assessments").document(assessmentId).get()
                        .addOnSuccessListener { legacyDoc ->
                            GabAIUtils.hideGlobalLoading(this)
                            if (legacyDoc.exists()) {
                                populateAssessmentFromDoc(legacyDoc)
                            } else {
                                GabAIUtils.showSnackbar(this, "Standardized assessment not found.")
                                finish()
                            }
                        }
                        .addOnFailureListener {
                            GabAIUtils.hideGlobalLoading(this)
                            GabAIUtils.showSnackbar(this, "Assessment not found.")
                            finish()
                        }
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Error loading assessment: ${e.message}")
                finish()
            }
    }

    private fun populateAssessmentFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot) {
        assessmentTitle = doc.getString("title") ?: "Phil-IRI Reading Diagnostic"
        assessmentPurpose = doc.getString("assessmentPurpose") ?: "Phil-IRI Pre-Test (Diagnostic)"
        districtScope = doc.getString("districtScope") ?: "ALL"
        targetGrade = doc.getString("grade") ?: "Grades 7-10"
        readingPassage = doc.getString("passage") ?: ""

        val rawTimer = doc.getLong("timerMinutes")?.toInt() ?: doc.getLong("durationMinutes")?.toInt() ?: 20
        timerMinutes = rawTimer.coerceIn(5, 60)
        totalDurationMillis = timerMinutes * 60 * 1000L
        remainingTimeMillis = totalDurationMillis

        tvHeaderTitle.text = assessmentTitle
        tvHeaderDivisionTag.text = "SDO VALENZUELA • ${districtScope.uppercase()}"

        tvBriefingTitle.text = assessmentTitle
        tvBriefingPurpose.text = assessmentPurpose
        tvBriefingTimeLimit.text = "$timerMinutes Minutes"
        tvPassageContent.text = readingPassage.ifEmpty { "No passage provided." }

        val quizJson = doc.getString("quiz_pool_json") ?: "[]"
        val hasRestored = restoreSavedSession()
        if (!hasRestored) {
            parseQuestions(quizJson)
            if (questions.isEmpty()) {
                GabAIUtils.showSnackbar(this, "No valid question items found in this assessment.")
                finish()
                return
            }
            studentAnswers = IntArray(questions.size) { -1 }
        }

        tvBriefingItemCount.text = "${questions.size} Items"
        progressBar.max = questions.size

        checkExistingSubmission()
    }

    private fun parseQuestions(jsonStr: String) {
        questions.clear()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.optString("q", "Question")
                val optsArr = obj.optJSONArray("options") ?: JSONArray()
                val optList = mutableListOf<String>()
                for (j in 0 until 4) {
                    optList.add(optsArr.optString(j, "Option ${j + 1}"))
                }
                val ans = obj.optInt("ans", 0)
                val comp = obj.optString("competency", if (i % 3 == 0) "LITERAL" else if (i % 3 == 1) "INFERENTIAL" else "CRITICAL")
                val exp = obj.optString("explanation", "Review the key comprehension concepts for this selection.")

                // Anti-cheating: Shuffle the 4 options and update the correct index accordingly
                val correctText = optList.getOrElse(ans) { optList.firstOrNull() ?: "" }
                val shuffledOpts = optList.shuffled()
                val newAns = shuffledOpts.indexOf(correctText).coerceAtLeast(0)

                questions.add(TimedQuestion(q, shuffledOpts, newAns, comp, exp))
            }
            // Anti-cheating: Shuffle questions per student
            questions.shuffle()
        } catch (_: Exception) {}
    }

    private fun checkExistingSubmission() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("timed_assessment_submissions")
            .document("${assessmentId}_${uid}")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    clearSavedSession()
                    val score = doc.getLong("score")?.toInt() ?: 0
                    val total = doc.getLong("totalQuestions")?.toInt() ?: questions.size
                    val pct = doc.getLong("percentage")?.toInt() ?: ((score * 100) / total.coerceAtLeast(1))
                    val elapsedMillis = doc.getLong("elapsedTimeMillis") ?: 0L
                    val readingSecs = doc.getLong("passageReadingTimeSeconds") ?: (elapsedMillis / 1000).coerceAtLeast(1)
                    val wpm = doc.getLong("readingSpeedWpm")?.toInt() ?: 0
                    val wps = doc.getDouble("readingSpeedWps") ?: 0.0
                    val speedCat = doc.getString("speedCategory") ?: "Standard Pace"
                    val answersList = doc.get("answers") as? List<Map<String, Any>> ?: listOf()

                    val litMap = doc.get("dimensionLiteral") as? Map<String, Any>
                    val litCorr = (litMap?.get("correct") as? Number)?.toInt() ?: 0
                    val litTot = (litMap?.get("total") as? Number)?.toInt() ?: 0

                    val infMap = doc.get("dimensionInferential") as? Map<String, Any>
                    val infCorr = (infMap?.get("correct") as? Number)?.toInt() ?: 0
                    val infTot = (infMap?.get("total") as? Number)?.toInt() ?: 0

                    val critMap = doc.get("dimensionCritical") as? Map<String, Any>
                    val critCorr = (critMap?.get("correct") as? Number)?.toInt() ?: 0
                    val critTot = (critMap?.get("total") as? Number)?.toInt() ?: 0

                    showResultsView(
                        score = score,
                        total = total,
                        percentage = pct,
                        totalElapsedMillis = elapsedMillis,
                        readingSecs = readingSecs,
                        wpm = wpm,
                        wps = wps,
                        speedCat = speedCat,
                        literalCorr = litCorr,
                        literalTot = litTot,
                        inferentialCorr = infCorr,
                        inferentialTot = infTot,
                        criticalCorr = critCorr,
                        criticalTot = critTot,
                        answersList = answersList
                    )
                } else {
                    layoutBriefing.visibility = View.VISIBLE
                    layoutReading.visibility = View.GONE
                    layoutRunner.visibility = View.GONE
                    layoutResults.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                layoutBriefing.visibility = View.VISIBLE
                layoutReading.visibility = View.GONE
                layoutRunner.visibility = View.GONE
                layoutResults.visibility = View.GONE
            }
    }

    private fun startReadingPhase() {
        if (hasFinishedReading) {
            startQuestionPhase()
            return
        }
        layoutBriefing.visibility = View.GONE
        layoutReading.visibility = View.VISIBLE
        layoutRunner.visibility = View.GONE
        layoutResults.visibility = View.GONE
        chipTimerContainer.visibility = View.GONE

        val passageWords = readingPassage.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        tvReadingWordCount.text = "📖 $passageWords words"
        tvReadingPassageTitle.text = assessmentTitle
        tvReadingPassageBody.text = readingPassage.ifEmpty { "No passage provided for this assessment." }

        readingStartTimeMillis = System.currentTimeMillis()
        readingTimerRunnable?.let { readingTimerHandler.removeCallbacks(it) }
        readingTimerRunnable = object : Runnable {
            override fun run() {
                val elapsedSecs = (System.currentTimeMillis() - readingStartTimeMillis) / 1000
                val m = elapsedSecs / 60
                val s = elapsedSecs % 60
                tvReadingLiveTimer.text = String.format(Locale.getDefault(), "⏱️ %02d:%02d", m, s)
                readingTimerHandler.postDelayed(this, 1000L)
            }
        }
        readingTimerHandler.post(readingTimerRunnable!!)
    }

    private fun onDoneReadingClicked() {
        val elapsedMillis = System.currentTimeMillis() - readingStartTimeMillis
        val elapsedSecs = (elapsedMillis / 1000).coerceAtLeast(1)

        // Authenticity guardrail: Minimum 5 seconds to ensure student read the passage
        if (elapsedSecs < 5) {
            GabAIUtils.showSnackbar(this, "Please take your time to read the passage thoroughly before continuing.")
            return
        }

        readingTimerRunnable?.let { readingTimerHandler.removeCallbacks(it) }
        passageReadingSecs = elapsedSecs
        val passageWords = readingPassage.split(Regex("\\s+")).filter { it.isNotBlank() }.size.coerceAtLeast(1)
        readingSpeedWpm = ((passageWords.toDouble() / passageReadingSecs) * 60).toInt()
        readingSpeedWps = passageWords.toDouble() / passageReadingSecs
        hasFinishedReading = true
        saveCurrentSession()

        startQuestionPhase()
    }

    private fun startQuestionPhase() {
        layoutBriefing.visibility = View.GONE
        layoutReading.visibility = View.GONE
        layoutRunner.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        chipTimerContainer.visibility = View.VISIBLE

        // In Stage 2, passage is collapsed by default so question #1 is front and center
        isPassageVisible = false
        cardPassage.visibility = View.GONE
        tvTogglePassageLabel.text = "View Passage"

        displayCurrentQuestion()
        startGlobalCountdown()
    }

    private fun startGlobalCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished
                updateTimerDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                remainingTimeMillis = 0L
                updateTimerDisplay(0L)
                GabAIDialogs.showNoticeDialog(
                    context = this@TimedAssessmentActivity,
                    title = "Time Expired! ⏱️",
                    message = "The total assessment time limit has elapsed. Your answers are being submitted automatically.",
                    buttonText = "View Diagnostic Results",
                    badgeIcon = "⌛"
                )
                submitAssessment(isAutoSubmit = true)
            }
        }.start()
    }

    private fun updateTimerDisplay(millis: Long) {
        val totalSecs = millis / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        tvTimerCountdown.text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

        // Visual alerts for remaining time
        when {
            millis < 60 * 1000L -> {
                // Pulsing red under 1 minute
                tvTimerCountdown.setTextColor(Color.parseColor("#EF4444"))
                chipTimerContainer.setBackgroundColor(Color.parseColor("#FEE2E2"))
            }
            millis < 3 * 60 * 1000L -> {
                // Amber under 3 minutes
                tvTimerCountdown.setTextColor(Color.parseColor("#D97706"))
                chipTimerContainer.setBackgroundColor(Color.parseColor("#FEF3C7"))
            }
            else -> {
                tvTimerCountdown.setTextColor(Color.parseColor("#5341CD"))
                chipTimerContainer.setBackgroundColor(Color.parseColor("#F4F0FF"))
            }
        }
    }

    private fun togglePassageVisibility() {
        isPassageVisible = !isPassageVisible
        cardPassage.visibility = if (isPassageVisible) View.VISIBLE else View.GONE
        tvTogglePassageLabel.text = if (isPassageVisible) "Hide Passage" else "View Passage"
        GabAIUtils.performHaptic(btnTogglePassage, HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun displayCurrentQuestion() {
        if (currentIndex < 0 || currentIndex >= questions.size) return
        val item = questions[currentIndex]

        tvProgressCounter.text = "Question ${currentIndex + 1} of ${questions.size}"
        progressBar.progress = currentIndex + 1

        tvQuestionBadge.text = "QUESTION ${currentIndex + 1}"
        tvQuestionText.text = item.q

        val selected = studentAnswers[currentIndex]
        for (i in 0..3) {
            optTexts[i].text = item.options.getOrElse(i) { "Option ${i + 1}" }
            if (selected == i) {
                optCards[i].setCardBackgroundColor(Color.parseColor("#F4F0FF"))
                optCards[i].strokeColor = Color.parseColor("#6C5CE7")
                optCards[i].strokeWidth = 4
                optBadges[i].setBackgroundColor(Color.parseColor("#6C5CE7"))
                optBadges[i].setTextColor(Color.WHITE)
            } else {
                optCards[i].setCardBackgroundColor(Color.WHITE)
                optCards[i].strokeColor = Color.parseColor("#EDF2F7")
                optCards[i].strokeWidth = 2
                optBadges[i].setBackgroundColor(Color.parseColor("#F1F5F9"))
                optBadges[i].setTextColor(Color.parseColor("#5341CD"))
            }
        }

        btnPrev.visibility = if (currentIndex == 0) View.INVISIBLE else View.VISIBLE
        if (currentIndex == questions.size - 1) {
            btnNext.text = "Submit Assessment ✓"
            btnNext.setBackgroundColor(Color.parseColor("#00B894"))
        } else {
            btnNext.text = "Next Question ➔"
            btnNext.setBackgroundColor(Color.parseColor("#6C5CE7"))
        }
    }

    private fun selectOption(optionIndex: Int) {
        studentAnswers[currentIndex] = optionIndex
        saveCurrentSession()
        displayCurrentQuestion()
    }

    private fun confirmExit() {
        if (layoutResults.visibility == View.VISIBLE || layoutBriefing.visibility == View.VISIBLE) {
            finish()
            return
        }

        val message = if (layoutReading.visibility == View.VISIBLE) {
            "You are currently reading the assessment passage. If you exit now, your reading progress will reset."
        } else {
            "Your countdown timer is still running. Answers you have already selected are safely preserved in this session."
        }

        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Exit Timed Assessment?",
            message = message,
            confirmText = "Exit Test",
            cancelText = "Keep Going",
            isDestructive = true,
            badgeIcon = "⏱️",
            onConfirm = {
                saveCurrentSession()
                finish()
            }
        )
    }

    private fun confirmSubmit() {
        val unanswered = studentAnswers.count { it == -1 }
        if (unanswered > 0) {
            GabAIDialogs.showConfirmDialog(
                context = this,
                title = "Unanswered Items",
                message = "You have $unanswered unanswered question(s). Are you sure you want to finalize your submission?",
                confirmText = "Submit Anyway",
                cancelText = "Review Items",
                isDestructive = false,
                badgeIcon = "⚠️",
                onConfirm = { submitAssessment(isAutoSubmit = false) }
            )
        } else {
            GabAIDialogs.showConfirmDialog(
                context = this,
                title = "Finalize Assessment?",
                message = "Ready to submit your standardized assessment? You will immediately see your reading comprehension profile.",
                confirmText = "Submit Now",
                cancelText = "Review",
                isDestructive = false,
                badgeIcon = "🚀",
                onConfirm = { submitAssessment(isAutoSubmit = false) }
            )
        }
    }

    private fun submitAssessment(isAutoSubmit: Boolean) {
        if (isSubmitted) return
        isSubmitted = true
        countDownTimer?.cancel()
        countDownTimer = null

        val uid = auth.currentUser?.uid ?: return
        GabAIUtils.showGlobalLoading(this, "Diagnosing Phil-IRI proficiency & submitting...")

        var score = 0
        var literalCorrect = 0
        var literalTotal = 0
        var inferentialCorrect = 0
        var inferentialTotal = 0
        var criticalCorrect = 0
        var criticalTotal = 0

        val submissionItems = mutableListOf<Map<String, Any>>()

        for (i in questions.indices) {
            val q = questions[i]
            val chosen = studentAnswers.getOrElse(i) { -1 }
            val correctAns = q.ans
            val isCorrect = (chosen == correctAns)
            if (isCorrect) score++

            val comp = q.competency.uppercase()
            when {
                comp.contains("LITERAL") -> {
                    literalTotal++
                    if (isCorrect) literalCorrect++
                }
                comp.contains("INFERENTIAL") -> {
                    inferentialTotal++
                    if (isCorrect) inferentialCorrect++
                }
                else -> {
                    criticalTotal++
                    if (isCorrect) criticalCorrect++
                }
            }

            val chosenText = if (chosen in 0 until q.options.size) q.options[chosen] else "Unanswered"
            val correctText = if (correctAns in 0 until q.options.size) q.options[correctAns] else ""

            submissionItems.add(
                mapOf(
                    "qIndex" to i,
                    "question" to q.q,
                    "competency" to q.competency,
                    "selectedOption" to chosen,
                    "selectedText" to chosenText,
                    "correctOption" to correctAns,
                    "correctText" to correctText,
                    "isCorrect" to isCorrect,
                    "explanation" to q.explanation,
                    "options" to q.options
                )
            )
        }

        val total = questions.size
        val percentage = if (total > 0) (score * 100) / total else 0
        val elapsedMillis = totalDurationMillis - remainingTimeMillis
        val elapsedSecs = (elapsedMillis / 1000).coerceAtLeast(1)
        val passageWords = readingPassage.split(Regex("\\s+")).filter { it.isNotBlank() }.size.coerceAtLeast(1)

        // Truthful Phil-IRI Reading Speed: Based strictly on Stage 1 reading time
        val effectiveReadingSecs = if (passageReadingSecs > 0) passageReadingSecs else elapsedSecs
        val finalWpm = if (readingSpeedWpm > 0) readingSpeedWpm else ((passageWords.toDouble() / effectiveReadingSecs) * 60).toInt()
        val finalWps = if (readingSpeedWps > 0.0) readingSpeedWps else (passageWords.toDouble() / effectiveReadingSecs)

        // DepEd Phil-IRI Speed Norm Category
        val speedCategory = when {
            finalWpm > 140 -> "Fast Pace"
            finalWpm >= 90 -> "Standard Pace"
            else -> "Developing Pace"
        }

        // DepEd Phil-IRI Official Proficiency Classification
        val philIriLevel = when {
            percentage >= 80 -> "Independent"
            percentage >= 59 -> "Instructional"
            else -> "Frustration"
        }
        val needsIntervention = (philIriLevel == "Frustration")

        val studentGender = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).getString("cached_user_gender", "unspecified") ?: "unspecified"
        val submissionDocId = "${assessmentId}_${uid}"
        val submissionData = hashMapOf(
            "assessmentId" to assessmentId,
            "assessmentTitle" to assessmentTitle,
            "assessmentPurpose" to assessmentPurpose,
            "districtScope" to districtScope,
            "schoolId" to studentSchoolId,
            "className" to studentSection,
            "grade" to targetGrade,
            "studentUid" to uid,
            "studentName" to studentFullName,
            "studentGender" to studentGender,
            "score" to score,
            "totalQuestions" to total,
            "percentage" to percentage,
            "elapsedTimeMillis" to elapsedMillis,
            "passageReadingTimeSeconds" to effectiveReadingSecs,
            "passageWordCount" to passageWords,
            "readingSpeedWpm" to finalWpm,
            "readingSpeedWps" to finalWps,
            "speedCategory" to speedCategory,
            "needsIntervention" to needsIntervention,
            "philIriLevel" to philIriLevel,
            "dimensionLiteral" to mapOf("correct" to literalCorrect, "total" to literalTotal),
            "dimensionInferential" to mapOf("correct" to inferentialCorrect, "total" to inferentialTotal),
            "dimensionCritical" to mapOf("correct" to criticalCorrect, "total" to criticalTotal),
            "answers" to submissionItems,
            "submittedAt" to System.currentTimeMillis(),
            "autoSubmitted" to isAutoSubmit
        )

        val submissionRef = db.collection("timed_assessment_submissions").document(submissionDocId)
        submissionRef.set(submissionData).addOnSuccessListener {
            // Sync official DepEd Phil-IRI profile metrics to student's root user document
            val userUpdates = hashMapOf<String, Any>(
                "phil_iri_tier" to philIriLevel,
                "phil_iri_score" to score,
                "phil_iri_total" to total,
                "phil_iri_pct" to percentage,
                "phil_iri_wpm" to finalWpm,
                "phil_iri_wps" to finalWps,
                "phil_iri_reading_time_secs" to effectiveReadingSecs,
                "phil_iri_speed_category" to speedCategory,
                "phil_iri_needs_intervention" to needsIntervention,
                "phil_iri_last_assessed" to System.currentTimeMillis(),
                "phil_iri_assessment_title" to assessmentTitle,
                "phil_iri_assessment_id" to assessmentId
            )
            db.collection("users").document(uid).update(userUpdates)

            // Zero-latency caching in SharedPreferences
            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
                .putString("cached_phil_iri_tier", philIriLevel)
                .putInt("cached_phil_iri_pct", percentage)
                .putInt("cached_phil_iri_wpm", finalWpm)
                .putFloat("cached_phil_iri_wps", finalWps.toFloat())
                .putLong("cached_phil_iri_reading_time_secs", effectiveReadingSecs)
                .putBoolean("cached_phil_iri_needs_intervention", needsIntervention)
                .apply()

            // Log to user's quiz_history
            val historyData = hashMapOf(
                "quizType" to "division_timed_assessment",
                "assessmentId" to assessmentId,
                "title" to assessmentTitle,
                "schoolId" to studentSchoolId,
                "section" to studentSection,
                "finalScore" to score,
                "totalAttempts" to total,
                "percentage" to percentage,
                "philIriLevel" to philIriLevel,
                "readingSpeedWpm" to finalWpm,
                "speedCategory" to speedCategory,
                "needsIntervention" to needsIntervention,
                "studentGender" to studentGender,
                "timestamp" to System.currentTimeMillis(),
                "items" to submissionItems
            )
            db.collection("users").document(uid).collection("quiz_history").add(historyData)

            if (percentage >= 50) {
                if (XPManager.canEarnXP(this)) {
                    XPManager.addXP(this, 50)
                }
                QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)
            }

            clearSavedSession()
            GabAIUtils.hideGlobalLoading(this)
            showResultsView(
                score = score,
                total = total,
                percentage = percentage,
                totalElapsedMillis = elapsedMillis,
                readingSecs = effectiveReadingSecs,
                wpm = finalWpm,
                wps = finalWps,
                speedCat = speedCategory,
                literalCorr = literalCorrect,
                literalTot = literalTotal,
                inferentialCorr = inferentialCorrect,
                inferentialTot = inferentialTotal,
                criticalCorr = criticalCorrect,
                criticalTot = criticalTotal,
                answersList = submissionItems
            )
        }.addOnFailureListener { e ->
            clearSavedSession()
            GabAIUtils.hideGlobalLoading(this)
            GabAIUtils.showSnackbar(this, "Submission completed locally: ${e.message}")
            showResultsView(
                score = score,
                total = total,
                percentage = percentage,
                totalElapsedMillis = elapsedMillis,
                readingSecs = effectiveReadingSecs,
                wpm = finalWpm,
                wps = finalWps,
                speedCat = speedCategory,
                literalCorr = literalCorrect,
                literalTot = literalTotal,
                inferentialCorr = inferentialCorrect,
                inferentialTot = inferentialTotal,
                criticalCorr = criticalCorrect,
                criticalTot = criticalTotal,
                answersList = submissionItems
            )
        }
    }

    private fun showResultsView(
        score: Int,
        total: Int,
        percentage: Int,
        totalElapsedMillis: Long,
        readingSecs: Long,
        wpm: Int,
        wps: Double,
        speedCat: String,
        literalCorr: Int,
        literalTot: Int,
        inferentialCorr: Int,
        inferentialTot: Int,
        criticalCorr: Int,
        criticalTot: Int,
        answersList: List<Map<String, Any>>
    ) {
        layoutBriefing.visibility = View.GONE
        layoutReading.visibility = View.GONE
        layoutRunner.visibility = View.GONE
        layoutResults.visibility = View.VISIBLE
        chipTimerContainer.visibility = View.GONE

        val tvIcon = findViewById<TextView>(R.id.tv_result_icon)
        val tvLevelBadge = findViewById<TextView>(R.id.tv_phil_iri_level_badge)
        val tvHeadline = findViewById<TextView>(R.id.tv_result_headline)
        val tvRecommendation = findViewById<TextView>(R.id.tv_phil_iri_recommendation)
        val tvFinalScore = findViewById<TextView>(R.id.tv_final_score)
        val tvFinalPct = findViewById<TextView>(R.id.tv_final_percentage)
        val tvTimeElapsed = findViewById<TextView>(R.id.tv_time_elapsed)

        tvFinalScore.text = "$score / $total"
        tvFinalPct.text = "$percentage% MPS"

        // Pure Stage 1 Reading Speed & Duration
        tvReadingSpeedWpm.text = "$wpm WPM"
        tvReadingRateCategory.text = String.format(Locale.getDefault(), "%.2f WPS • %s", wps, speedCat)

        val rMins = readingSecs / 60
        val rSecs = readingSecs % 60
        tvReadingTimeElapsed.text = String.format(Locale.getDefault(), "%dm %02ds", rMins, rSecs)

        // Total test duration
        val elapsedSecs = (totalElapsedMillis / 1000).coerceAtLeast(0)
        val eMins = elapsedSecs / 60
        val eSecs = elapsedSecs % 60
        tvTimeElapsed.text = String.format(Locale.getDefault(), "Total: %dm %02ds", eMins, eSecs)

        // Cognitive Dimension Mastery (DepEd Phil-IRI 3 Levels)
        val litPct = if (literalTot > 0) (literalCorr * 100) / literalTot else 0
        tvDimLiteralScore.text = "$literalCorr / $literalTot ($litPct%)"
        progressDimLiteral.progress = litPct

        val infPct = if (inferentialTot > 0) (inferentialCorr * 100) / inferentialTot else 0
        tvDimInferentialScore.text = "$inferentialCorr / $inferentialTot ($infPct%)"
        progressDimInferential.progress = infPct

        val critPct = if (criticalTot > 0) (criticalCorr * 100) / criticalTot else 0
        tvDimCriticalScore.text = "$criticalCorr / $criticalTot ($critPct%)"
        progressDimCritical.progress = critPct

        // Frustration level remedial support card
        if (percentage <= 58) {
            cardFrustrationRemedial.visibility = View.VISIBLE
        } else {
            cardFrustrationRemedial.visibility = View.GONE
        }

        // Phil-IRI Diagnostic Profile
        when {
            percentage >= 80 -> {
                tvIcon.text = "🏆"
                tvLevelBadge.text = "INDEPENDENT LEVEL (80% - 100%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#00B894"))
                tvHeadline.text = "Outstanding Reading Mastery!"
                tvHeadline.setTextColor(Color.parseColor("#00B894"))
                tvRecommendation.text = "Awesome reading comprehension! You read independently with high speed and understanding."
                tvFinalScore.setTextColor(Color.parseColor("#00B894"))
            }
            percentage >= 59 -> {
                tvIcon.text = "🎉"
                tvLevelBadge.text = "INSTRUCTIONAL LEVEL (59% - 79%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#5341CD"))
                tvHeadline.text = "Proficient Reading Comprehension"
                tvHeadline.setTextColor(Color.parseColor("#5341CD"))
                tvRecommendation.text = "Good effort! You grasp key ideas well. Keep reading regularly to build speed and mastery."
                tvFinalScore.setTextColor(Color.parseColor("#5341CD"))
            }
            else -> {
                tvIcon.text = "📚"
                tvLevelBadge.text = "FRUSTRATION LEVEL (≤ 58%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvHeadline.text = "Needs Reading Support"
                tvHeadline.setTextColor(Color.parseColor("#EF4444"))
                tvRecommendation.text = "This passage was challenging. Guided stories and vocabulary drills will help build your reading fluency!"
                tvFinalScore.setTextColor(Color.parseColor("#EF4444"))
            }
        }

        // Render detailed item review cards
        val container = findViewById<LinearLayout>(R.id.timed_results_questions_container)
        container.removeAllViews()

        for (i in answersList.indices) {
            val item = answersList[i]
            val qText = item["question"] as? String ?: (questions.getOrNull(i)?.q ?: "Question ${i + 1}")
            val isCorrect = item["isCorrect"] as? Boolean ?: false
            val selectedText = item["selectedText"] as? String ?: "Unanswered"
            val correctText = item["correctText"] as? String ?: (questions.getOrNull(i)?.let { it.options.getOrNull(it.ans) } ?: "")
            val explanation = item["explanation"] as? String ?: (questions.getOrNull(i)?.explanation ?: "")

            val row = layoutInflater.inflate(R.layout.item_question_review, container, false)
            row.findViewById<TextView>(R.id.tv_review_q_number).text = "Question ${i + 1}"
            val badge = row.findViewById<TextView>(R.id.tv_review_badge)
            if (isCorrect) {
                badge.text = "Correct ✓"
                badge.setBackgroundColor(Color.parseColor("#D1FAE5"))
                badge.setTextColor(Color.parseColor("#065F46"))
            } else {
                badge.text = "Incorrect ✗"
                badge.setBackgroundColor(Color.parseColor("#FEE2E2"))
                badge.setTextColor(Color.parseColor("#B91C1C"))
            }

            row.findViewById<TextView>(R.id.tv_review_q_text).text = qText
            row.findViewById<TextView>(R.id.tv_review_student_ans).text = selectedText

            val correctContainer = row.findViewById<View>(R.id.container_correct_ans)
            if (!isCorrect) {
                correctContainer.visibility = View.VISIBLE
                row.findViewById<TextView>(R.id.tv_review_correct_ans).text = correctText
            } else {
                correctContainer.visibility = View.GONE
            }

            val tvExp = row.findViewById<TextView>(R.id.tv_review_explanation)
            tvExp.text = if (explanation.isNotBlank()) explanation else "Review key reading comprehension standards for this question."

            container.addView(row)
        }
    }
}
