package com.example.gabai

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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

    private val questions = mutableListOf<TimedQuestion>()
    private var currentIndex = 0
    private var studentAnswers = IntArray(0)

    private var countDownTimer: CountDownTimer? = null
    private var isSubmitted = false
    private var isPassageVisible = false

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

    // Runner views
    private lateinit var tvProgressCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardPassage: MaterialCardView
    private lateinit var tvPassageContent: TextView
    private lateinit var btnTogglePassage: View
    private lateinit var tvTogglePassageLabel: TextView
    private lateinit var tvQuestionBadge: TextView
    private lateinit var tvCompetencyPill: TextView
    private lateinit var tvQuestionText: TextView

    private val optCards = mutableListOf<MaterialCardView>()
    private val optBadges = mutableListOf<TextView>()
    private val optTexts = mutableListOf<TextView>()

    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

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

        tvProgressCounter = findViewById(R.id.tv_timed_progress_counter)
        progressBar = findViewById(R.id.progress_timed_quiz)
        cardPassage = findViewById(R.id.card_timed_passage)
        tvPassageContent = findViewById(R.id.tv_timed_passage_content)
        btnTogglePassage = findViewById(R.id.btn_toggle_passage)
        tvTogglePassageLabel = findViewById(R.id.tv_toggle_passage_label)
        tvQuestionBadge = findViewById(R.id.tv_timed_question_badge)
        tvCompetencyPill = findViewById(R.id.tv_timed_competency_pill)
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
            startTimedAssessment()
        }

        btnTogglePassage.setOnClickListener {
            togglePassageVisibility()
        }

        findViewById<View>(R.id.btn_close_passage_inline)?.setOnClickListener {
            togglePassageVisibility()
        }

        findViewById<View>(R.id.btn_open_timed_palette)?.setOnClickListener {
            showQuestionPaletteDialog()
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
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val first = doc.getString("firstName") ?: ""
            val last = doc.getString("lastName") ?: ""
            if (first.isNotEmpty() || last.isNotEmpty()) {
                studentFullName = "$first $last".trim()
            }
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
                    val answersList = doc.get("answers") as? List<Map<String, Any>> ?: listOf()

                    showResultsView(score, total, pct, elapsedMillis, answersList)
                } else {
                    layoutBriefing.visibility = View.VISIBLE
                    layoutRunner.visibility = View.GONE
                    layoutResults.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                layoutBriefing.visibility = View.VISIBLE
                layoutRunner.visibility = View.GONE
                layoutResults.visibility = View.GONE
            }
    }

    private fun startTimedAssessment() {
        layoutBriefing.visibility = View.GONE
        layoutRunner.visibility = View.VISIBLE
        layoutResults.visibility = View.GONE
        chipTimerContainer.visibility = View.VISIBLE

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

        val compLabel = when (item.competency.uppercase()) {
            "LITERAL" -> "LITERAL COMPREHENSION"
            "INFERENTIAL" -> "INFERENTIAL REASONING"
            "CRITICAL" -> "CRITICAL EVALUATION (HOTS)"
            else -> item.competency.uppercase()
        }
        tvQuestionBadge.text = "QUESTION ${currentIndex + 1}"
        tvCompetencyPill.text = compLabel
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

    private fun showQuestionPaletteDialog() {
        if (questions.isEmpty()) return
        val density = resources.displayMetrics.density
        val grid = GridLayout(this).apply {
            columnCount = 5
            alignmentMode = GridLayout.ALIGN_MARGINS
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        var dialog: android.app.Dialog? = null

        for (i in questions.indices) {
            val isCurrent = (i == currentIndex)
            val isAnswered = (studentAnswers.getOrElse(i) { -1 } != -1)

            val btn = TextView(this).apply {
                text = "${i + 1}"
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)

                val sizePx = (46 * density).toInt()
                val params = GridLayout.LayoutParams().apply {
                    width = sizePx
                    height = sizePx
                    setMargins((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
                }
                layoutParams = params

                when {
                    isCurrent -> {
                        setBackgroundResource(R.drawable.bg_tag_k12)
                        setTextColor(Color.parseColor("#5341CD"))
                    }
                    isAnswered -> {
                        setBackgroundColor(Color.parseColor("#5341CD"))
                        setTextColor(Color.WHITE)
                    }
                    else -> {
                        setBackgroundColor(Color.parseColor("#F1F5F9"))
                        setTextColor(Color.parseColor("#64748B"))
                    }
                }

                setOnClickListener {
                    GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
                    currentIndex = i
                    displayCurrentQuestion()
                    dialog?.dismiss()
                }
            }
            grid.addView(btn)
        }

        val scroll = ScrollView(this).apply { addView(grid) }

        dialog = GabAIDialogs.showCustomDialog(
            context = this,
            title = "Question Palette",
            subtitle = "Purple = Answered • Gray = Unanswered",
            customView = scroll,
            confirmText = null,
            cancelText = "Close",
            badgeIcon = "🔢"
        )
    }

    private fun confirmExit() {
        if (layoutResults.visibility == View.VISIBLE || layoutBriefing.visibility == View.VISIBLE) {
            finish()
            return
        }

        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Exit Timed Assessment?",
            message = "Your countdown timer is still running. Answers you have already selected are safely preserved in this session.",
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
                message = "Ready to submit your standardized assessment? You will immediately see your official DepEd Phil-IRI reading profile.",
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

        // DepEd Phil-IRI Official Proficiency Classification
        val philIriLevel = when {
            percentage >= 80 -> "Independent"
            percentage >= 59 -> "Instructional"
            else -> "Frustration"
        }

        val submissionDocId = "${assessmentId}_${uid}"
        val submissionData = hashMapOf(
            "assessmentId" to assessmentId,
            "assessmentTitle" to assessmentTitle,
            "assessmentPurpose" to assessmentPurpose,
            "districtScope" to districtScope,
            "grade" to targetGrade,
            "studentUid" to uid,
            "studentName" to studentFullName,
            "score" to score,
            "totalQuestions" to total,
            "percentage" to percentage,
            "elapsedTimeMillis" to elapsedMillis,
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
            // Log to user's quiz_history
            val historyData = hashMapOf(
                "quizType" to "division_timed_assessment",
                "assessmentId" to assessmentId,
                "title" to assessmentTitle,
                "finalScore" to score,
                "totalAttempts" to total,
                "percentage" to percentage,
                "philIriLevel" to philIriLevel,
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
            showResultsView(score, total, percentage, elapsedMillis, submissionItems)
        }.addOnFailureListener { e ->
            clearSavedSession()
            GabAIUtils.hideGlobalLoading(this)
            GabAIUtils.showSnackbar(this, "Submission completed locally: ${e.message}")
            showResultsView(score, total, percentage, elapsedMillis, submissionItems)
        }
    }

    private fun showResultsView(
        score: Int,
        total: Int,
        percentage: Int,
        elapsedMillis: Long,
        answersList: List<Map<String, Any>>
    ) {
        layoutBriefing.visibility = View.GONE
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

        val elapsedSecs = (elapsedMillis / 1000).coerceAtLeast(0)
        val eMins = elapsedSecs / 60
        val eSecs = elapsedSecs % 60
        tvTimeElapsed.text = String.format(Locale.getDefault(), "%dm %ds", eMins, eSecs)

        // Phil-IRI Diagnostic Profile
        when {
            percentage >= 80 -> {
                tvIcon.text = "🏆"
                tvLevelBadge.text = "INDEPENDENT LEVEL (80% - 100%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#00B894"))
                tvHeadline.text = "Outstanding Reading Mastery!"
                tvHeadline.setTextColor(Color.parseColor("#00B894"))
                tvRecommendation.text = "Learner reads and comprehends the text completely without teacher assistance. Ready for advanced enrichment texts."
                tvFinalScore.setTextColor(Color.parseColor("#00B894"))
            }
            percentage >= 59 -> {
                tvIcon.text = "🎉"
                tvLevelBadge.text = "INSTRUCTIONAL LEVEL (59% - 79%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#5341CD"))
                tvHeadline.text = "Proficient Reading Comprehension"
                tvHeadline.setTextColor(Color.parseColor("#5341CD"))
                tvRecommendation.text = "Learner comprehends adequately with teacher guidance and instructional scaffolding."
                tvFinalScore.setTextColor(Color.parseColor("#5341CD"))
            }
            else -> {
                tvIcon.text = "📚"
                tvLevelBadge.text = "FRUSTRATION LEVEL (≤ 58%)"
                tvLevelBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvHeadline.text = "Needs Reading Support"
                tvHeadline.setTextColor(Color.parseColor("#EF4444"))
                tvRecommendation.text = "Material poses significant challenge. Recommended for targeted remedial reading intervention and vocabulary reinforcement."
                tvFinalScore.setTextColor(Color.parseColor("#EF4444"))
            }
        }

        // Compute 3 cognitive dimensions from answersList
        var litC = 0; var litT = 0
        var infC = 0; var infT = 0
        var criC = 0; var criT = 0

        for (item in answersList) {
            val comp = (item["competency"] as? String ?: "").uppercase()
            val isC = item["isCorrect"] as? Boolean ?: false
            when {
                comp.contains("LITERAL") -> {
                    litT++
                    if (isC) litC++
                }
                comp.contains("INFERENTIAL") -> {
                    infT++
                    if (isC) infC++
                }
                else -> {
                    criT++
                    if (isC) criC++
                }
            }
        }

        val litPct = if (litT > 0) (litC * 100) / litT else 100
        val infPct = if (infT > 0) (infC * 100) / infT else 100
        val criPct = if (criT > 0) (criC * 100) / criT else 100

        findViewById<TextView>(R.id.tv_dim_literal_score).text = "$litC/$litT ($litPct%)"
        findViewById<ProgressBar>(R.id.progress_dim_literal).progress = litPct

        findViewById<TextView>(R.id.tv_dim_inferential_score).text = "$infC/$infT ($infPct%)"
        findViewById<ProgressBar>(R.id.progress_dim_inferential).progress = infPct

        findViewById<TextView>(R.id.tv_dim_critical_score).text = "$criC/$criT ($criPct%)"
        findViewById<ProgressBar>(R.id.progress_dim_critical).progress = criPct

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
