package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

class WeeklyAssessmentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var assessmentId = ""
    private var assessmentTitle = "Weekly Assessment"
    private var subjectName = "Subject"
    private var classId = ""
    private var className = ""
    private var schoolId = ""
    private var studentFullName = "Student"

    private val questions = mutableListOf<AssessmentQuestion>()
    private var currentIndex = 0
    private var studentAnswers = IntArray(0)

    data class AssessmentQuestion(
        val q: String,
        val options: List<String>,
        val ans: Int,
        val explanation: String
    )

    private lateinit var layoutRunner: View
    private lateinit var layoutResults: View

    private lateinit var tvProgressCounter: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvQuestionBadge: TextView
    private lateinit var tvQuestionText: TextView

    private val optCards = mutableListOf<MaterialCardView>()
    private val optBadges = mutableListOf<TextView>()
    private val optTexts = mutableListOf<TextView>()

    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_weekly_assessment)

        assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: return finish()

        // Exit protection for hardware back button / gesture
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExit()
            }
        })

        // Bind header
        findViewById<ImageButton>(R.id.btn_close_assessment).setOnClickListener {
            confirmExit()
        }

        findViewById<View>(R.id.btn_open_palette)?.setOnClickListener {
            showQuestionPaletteDialog()
        }

        layoutRunner = findViewById(R.id.layout_quiz_runner)
        layoutResults = findViewById(R.id.layout_quiz_results)

        tvProgressCounter = findViewById(R.id.tv_progress_counter)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
        progressBar = findViewById(R.id.progress_quiz)
        tvQuestionBadge = findViewById(R.id.tv_question_badge)
        tvQuestionText = findViewById(R.id.tv_question_text)

        optCards.add(findViewById(R.id.card_opt_0))
        optCards.add(findViewById(R.id.card_opt_1))
        optCards.add(findViewById(R.id.card_opt_2))
        optCards.add(findViewById(R.id.card_opt_3))

        optBadges.add(findViewById(R.id.badge_opt_0))
        optBadges.add(findViewById(R.id.badge_opt_1))
        optBadges.add(findViewById(R.id.badge_opt_2))
        optBadges.add(findViewById(R.id.badge_opt_3))

        optTexts.add(findViewById(R.id.tv_opt_0))
        optTexts.add(findViewById(R.id.tv_opt_1))
        optTexts.add(findViewById(R.id.tv_opt_2))
        optTexts.add(findViewById(R.id.tv_opt_3))

        for (i in 0..3) {
            optCards[i].setOnClickListener {
                selectOption(i)
            }
        }

        btnPrev = findViewById(R.id.btn_prev_question)
        btnNext = findViewById(R.id.btn_next_question)

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                displayCurrentQuestion()
            }
        }

        btnNext.setOnClickListener {
            if (studentAnswers.isNotEmpty() && studentAnswers[currentIndex] == -1) {
                GabAIUtils.showSnackbar(this, "Please select an answer to continue.")
                return@setOnClickListener
            }

            if (currentIndex < questions.size - 1) {
                currentIndex++
                displayCurrentQuestion()
            } else {
                confirmSubmit()
            }
        }

        findViewById<Button>(R.id.btn_finish_assessment).setOnClickListener {
            finish()
        }

        loadStudentInfo()
        loadAssessmentData()
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
        GabAIUtils.showGlobalLoading(this, "Loading assessment...")

        db.collection("weekly_assessments").document(assessmentId).get()
            .addOnSuccessListener { doc ->
                GabAIUtils.hideGlobalLoading(this)
                if (!doc.exists()) {
                    GabAIUtils.showSnackbar(this, "Assessment not found.")
                    finish()
                    return@addOnSuccessListener
                }

                assessmentTitle = doc.getString("title") ?: "Weekly Assessment"
                subjectName = doc.getString("subjectName") ?: "Subject"
                classId = doc.getString("classId") ?: ""
                className = doc.getString("className") ?: ""
                schoolId = doc.getString("schoolId") ?: ""

                findViewById<TextView>(R.id.tv_header_title).text = assessmentTitle
                findViewById<TextView>(R.id.tv_header_subject).text = subjectName.uppercase()

                val quizJson = doc.getString("quiz_pool_json") ?: "[]"
                parseQuestions(quizJson)

                if (questions.isEmpty()) {
                    GabAIUtils.showSnackbar(this, "No questions found in this assessment.")
                    finish()
                    return@addOnSuccessListener
                }

                studentAnswers = IntArray(questions.size) { -1 }
                progressBar.max = questions.size

                checkExistingSubmission()
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Error loading assessment: ${e.message}")
                finish()
            }
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
                val exp = obj.optString("explanation", "Review the key learning objectives for this topic.")

                // Anti-cheating: Shuffle the 4 options and update the correct index accordingly
                val correctText = optList.getOrElse(ans) { optList.firstOrNull() ?: "" }
                val shuffledOpts = optList.shuffled()
                val newAns = shuffledOpts.indexOf(correctText).coerceAtLeast(0)

                questions.add(AssessmentQuestion(q, shuffledOpts, newAns, exp))
            }
            // Anti-cheating: Shuffle the question order for each student
            questions.shuffle()
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun checkExistingSubmission() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("assessment_submissions")
            .whereEqualTo("assessmentId", assessmentId)
            .whereEqualTo("studentUid", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    val submissionDoc = snapshots.documents[0]
                    val score = submissionDoc.getLong("score")?.toInt() ?: 0
                    val total = submissionDoc.getLong("totalQuestions")?.toInt() ?: questions.size
                    val percentage = submissionDoc.getLong("percentage")?.toInt() ?: ((score * 100) / total.coerceAtLeast(1))
                    val answersList = submissionDoc.get("answers") as? List<Map<String, Any>> ?: listOf()

                    showResultsView(score, total, percentage, answersList)
                } else {
                    layoutRunner.visibility = View.VISIBLE
                    layoutResults.visibility = View.GONE
                    currentIndex = 0
                    displayCurrentQuestion()
                }
            }
            .addOnFailureListener {
                layoutRunner.visibility = View.VISIBLE
                layoutResults.visibility = View.GONE
                currentIndex = 0
                displayCurrentQuestion()
            }
    }

    private fun displayCurrentQuestion() {
        if (currentIndex < 0 || currentIndex >= questions.size) return
        val question = questions[currentIndex]

        tvProgressCounter.text = "Question ${currentIndex + 1} of ${questions.size}"
        val pct = ((currentIndex + 1) * 100) / questions.size
        tvProgressPercent.text = "$pct%"
        progressBar.progress = currentIndex + 1

        tvQuestionBadge.text = "QUESTION ${currentIndex + 1}"
        tvQuestionText.text = question.q

        val selected = studentAnswers[currentIndex]

        for (i in 0..3) {
            optTexts[i].text = question.options.getOrElse(i) { "Option ${i + 1}" }
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
                    GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
                    currentIndex = i
                    displayCurrentQuestion()
                    dialog?.dismiss()
                }
            }
            grid.addView(btn)
        }

        val scroll = ScrollView(this).apply {
            addView(grid)
        }

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
        if (layoutResults.visibility == View.VISIBLE) {
            finish()
            return
        }

        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Exit Assessment?",
            message = "Your current progress will not be submitted until you complete all questions.",
            confirmText = "Exit",
            cancelText = "Keep Going",
            isDestructive = true,
            badgeIcon = "📝",
            onConfirm = { finish() }
        )
    }

    private fun confirmSubmit() {
        // Check if any unanswered
        val unanswered = studentAnswers.count { it == -1 }
        if (unanswered > 0) {
            GabAIDialogs.showConfirmDialog(
                context = this,
                title = "Unanswered Questions",
                message = "You have $unanswered unanswered question(s). Are you sure you want to submit?",
                confirmText = "Submit Anyway",
                cancelText = "Review Questions",
                isDestructive = false,
                badgeIcon = "⚠️",
                onConfirm = { submitAssessment() }
            )
        } else {
            GabAIDialogs.showConfirmDialog(
                context = this,
                title = "Submit Assessment?",
                message = "Are you ready to submit your answers? You will see your score and detailed educational explanations immediately.",
                confirmText = "Submit",
                cancelText = "Cancel",
                isDestructive = false,
                badgeIcon = "🚀",
                onConfirm = { submitAssessment() }
            )
        }
    }

    private fun submitAssessment() {
        val uid = auth.currentUser?.uid ?: return
        GabAIUtils.showGlobalLoading(this, "Calculating score and submitting...")

        var score = 0
        val submissionItems = mutableListOf<Map<String, Any>>()

        for (i in questions.indices) {
            val chosen = studentAnswers[i]
            val correctAns = questions[i].ans
            val isCorrect = (chosen == correctAns)
            if (isCorrect) score++

            submissionItems.add(
                mapOf(
                    "qIndex" to i,
                    "question" to questions[i].q,
                    "selectedOption" to chosen,
                    "selectedText" to if (chosen in 0..3) questions[i].options[chosen] else "Unanswered",
                    "correctOption" to correctAns,
                    "correctText" to questions[i].options[correctAns],
                    "isCorrect" to isCorrect,
                    "explanation" to questions[i].explanation
                )
            )
        }

        val total = questions.size
        val percentage = if (total > 0) (score * 100) / total else 0

        // 1. Save to assessment_submissions
        val submissionData = hashMapOf(
            "assessmentId" to assessmentId,
            "assessmentTitle" to assessmentTitle,
            "subjectName" to subjectName,
            "studentUid" to uid,
            "studentName" to studentFullName,
            "classId" to classId,
            "className" to className,
            "schoolId" to schoolId,
            "score" to score,
            "totalQuestions" to total,
            "percentage" to percentage,
            "answers" to submissionItems,
            "submittedAt" to System.currentTimeMillis()
        )

        db.collection("assessment_submissions").add(submissionData)
            .addOnSuccessListener {
                // 2. Save to user's quiz_history
                val historyData = hashMapOf(
                    "quizType" to "weekly_assessment",
                    "assessmentId" to assessmentId,
                    "title" to assessmentTitle,
                    "finalScore" to score,
                    "totalAttempts" to total,
                    "isPassed" to (percentage >= 50),
                    "timestamp" to System.currentTimeMillis(),
                    "items" to submissionItems
                )
                db.collection("users").document(uid).collection("quiz_history").add(historyData)

                // 3. Award XP
                if (XPManager.canEarnXP(this)) {
                    XPManager.addXP(this, 50)
                }
                QuestManager.addProgress(this, QuestManager.QUEST_QUIZ)

                GabAIUtils.hideGlobalLoading(this)
                showResultsView(score, total, percentage, submissionItems)
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to submit: ${e.message}")
            }
    }

    private fun showResultsView(
        score: Int,
        total: Int,
        percentage: Int,
        answersList: List<Map<String, Any>>
    ) {
        layoutRunner.visibility = View.GONE
        layoutResults.visibility = View.VISIBLE

        val tvIcon = findViewById<TextView>(R.id.tv_result_icon)
        val tvTitle = findViewById<TextView>(R.id.tv_result_title)
        val tvSubtitle = findViewById<TextView>(R.id.tv_result_subtitle)
        val tvFinalScore = findViewById<TextView>(R.id.tv_final_score)
        val tvFinalPct = findViewById<TextView>(R.id.tv_final_percentage)

        tvFinalScore.text = "$score / $total"
        tvFinalPct.text = "$percentage%"

        if (percentage >= 75) {
            tvIcon.text = "🏆"
            tvTitle.text = "Outstanding Work!"
            tvSubtitle.text = "You demonstrated strong mastery of this week's lesson!"
            tvFinalScore.setTextColor(Color.parseColor("#00B894"))
        } else if (percentage >= 50) {
            tvIcon.text = "🎉"
            tvTitle.text = "Assessment Passed!"
            tvSubtitle.text = "Good job! Review the explanations below to improve further."
            tvFinalScore.setTextColor(Color.parseColor("#5341CD"))
        } else {
            tvIcon.text = "📚"
            tvTitle.text = "Needs Review"
            tvSubtitle.text = "Review the educational explanations below and consult your teacher."
            tvFinalScore.setTextColor(Color.parseColor("#EF4444"))
        }

        // Render review question cards
        val container = findViewById<LinearLayout>(R.id.results_questions_container)
        container.removeAllViews()

        for (i in answersList.indices) {
            val item = answersList[i]
            val qText = item["question"] as? String ?: (questions.getOrNull(i)?.q ?: "Question ${i + 1}")
            val isCorrect = item["isCorrect"] as? Boolean ?: false
            val selectedText = item["selectedText"] as? String ?: "No Answer"
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
            tvExp.text = if (explanation.isNotBlank()) explanation else "Review the key reading concepts for this question."

            container.addView(row)
        }
    }

    override fun onBackPressed() {
        confirmExit()
    }
}
