package com.example.gabai

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

import android.app.DatePickerDialog
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class QuizEditorActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var container: LinearLayout
    private var materialId = ""
    private var assessmentId = ""
    private var pdfFilePath = ""

    private var isWeeklyAssessment = false
    private var subjectId = ""
    private var subjectName = ""
    private var grade = ""
    private var sourceType = ""
    private var sourceRef = ""
    private var teacherFullName = "Teacher"

    private var dueDateMillis = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
    private val enrolledClasses = mutableListOf<ClassInfo>()

    data class ClassInfo(val id: String, val name: String, val schoolId: String, val grade: String)

    private val generativeModel = GenerativeModel(
        modelName = "gemini-3.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_quiz_editor)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_quiz_editor)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_quiz_editor)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.editor_header)
        val scrollContent = findViewById<View>(R.id.scroll_quiz_editor)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        isWeeklyAssessment = intent.getBooleanExtra("IS_WEEKLY_ASSESSMENT", false)
        assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: ""
        if (assessmentId.isNotEmpty()) isWeeklyAssessment = true

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""
        sourceType = intent.getStringExtra("SOURCE_TYPE") ?: "custom"
        sourceRef = intent.getStringExtra("SOURCE_REF") ?: ""
        pdfFilePath = intent.getStringExtra("PDF_FILE_PATH") ?: ""

        if (!isWeeklyAssessment) {
            materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()
        }

        val initialJson = intent.getStringExtra("QUIZ_JSON") ?: "[]"
        val initialPassage = intent.getStringExtra("PASSAGE_TEXT") ?: ""
        val targetItems = intent.getIntExtra("TARGET_ITEMS", if (isWeeklyAssessment) 10 else 5)
        val defaultTitle = intent.getStringExtra("ASSESSMENT_TITLE") ?: if (subjectName.isNotEmpty()) "$subjectName Assessment" else "Weekly Assessment"

        container = findViewById(R.id.questions_container)
        findViewById<EditText>(R.id.et_target_items).setText(targetItems.toString())

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        val btnAddManual = findViewById<View>(R.id.btn_add_manual)
        GabAIUtils.addSpringPressEffect(btnAddManual) { showManualAddDialog() }

        val btnAddAi = findViewById<View>(R.id.btn_add_ai)
        GabAIUtils.addSpringPressEffect(btnAddAi) { promptForAiGenerate() }

        val btnSaveQuiz = findViewById<View>(R.id.btn_save_quiz)
        GabAIUtils.addSpringPressEffect(btnSaveQuiz) { saveAndPublish() }

        val cardMeta = findViewById<View>(R.id.card_assessment_meta)
        val etTitle = findViewById<EditText>(R.id.et_assessment_title)
        val tvDueDate = findViewById<TextView>(R.id.tv_due_date)
        val etPassage = findViewById<EditText>(R.id.et_assessment_passage)
        val tvPassageWordCount = findViewById<TextView>(R.id.tv_editor_passage_word_count)
        val btnTogglePassage = findViewById<TextView>(R.id.btn_toggle_editor_passage)
        val headerPassage = findViewById<View>(R.id.header_editor_passage)
        val btnDeleteAssessment = findViewById<ImageButton>(R.id.btn_delete_assessment)

        targetClassId = intent.getStringExtra("TARGET_CLASS_ID") ?: intent.getStringExtra("CLASS_ID") ?: ""
        targetClassName = intent.getStringExtra("TARGET_CLASS_NAME") ?: intent.getStringExtra("CLASS_NAME") ?: ""

        etPassage?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val words = s?.toString()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.size ?: 0
                tvPassageWordCount?.text = "$words words"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val togglePassageAction = View.OnClickListener {
            if (etPassage != null) {
                if (etPassage.visibility == View.VISIBLE) {
                    etPassage.visibility = View.GONE
                    btnTogglePassage?.text = "Show ▼"
                } else {
                    etPassage.visibility = View.VISIBLE
                    btnTogglePassage?.text = "Hide ▲"
                }
            }
        }
        btnTogglePassage?.setOnClickListener(togglePassageAction)
        headerPassage?.setOnClickListener(togglePassageAction)

        if (isWeeklyAssessment) {
            cardMeta.visibility = View.VISIBLE
            etTitle.setText(defaultTitle)
            if (initialPassage.isNotEmpty()) {
                etPassage?.setText(initialPassage)
            }
            updateDueDateDisplay(tvDueDate)

            tvDueDate.setOnClickListener {
                showDueDatePicker(tvDueDate)
            }

            if (assessmentId.isNotEmpty()) {
                findViewById<TextView>(R.id.tv_editor_subtitle)?.text = "WEEKLY ASSESSMENT EDITOR"
                findViewById<TextView>(R.id.tv_editor_title)?.text = "Edit Weekly Assessment"
                (btnSaveQuiz as? TextView)?.text = "Save Changes ➔"
                btnDeleteAssessment?.visibility = View.VISIBLE
                btnDeleteAssessment?.let { btn ->
                    GabAIUtils.addSpringPressEffect(btn) { confirmDeleteAssessment() }
                }
            } else {
                findViewById<TextView>(R.id.tv_editor_title)?.text = "Weekly Assessment Builder"
                (btnSaveQuiz as? TextView)?.text = "Publish to Class ➔"
                btnDeleteAssessment?.visibility = View.GONE
            }

            loadTeacherClasses()
            if (assessmentId.isNotEmpty()) {
                loadExistingWeeklyAssessment(initialJson)
            }
        } else {
            cardMeta.visibility = View.GONE
            btnDeleteAssessment?.visibility = View.GONE
            if (initialJson == "[]" && materialId.isNotEmpty()) {
                loadExistingMaterialQuiz()
            }
        }

        loadTeacherInfo()
        if (initialJson != "[]") {
            loadInitialQuestions(initialJson)
        }
    }

    private var targetClassId: String = ""
    private var targetClassName: String = ""

    private fun loadExistingWeeklyAssessment(passedJson: String) {
        GabAIUtils.showGlobalLoading(this, "Loading assessment details...")
        db.collection("weekly_assessments").document(assessmentId).get()
            .addOnSuccessListener { doc ->
                GabAIUtils.hideGlobalLoading(this)
                if (!doc.exists()) {
                    GabAIUtils.showSnackbar(this, "Assessment not found.")
                    finish()
                    return@addOnSuccessListener
                }

                val title = doc.getString("title") ?: "Weekly Assessment"
                val passage = doc.getString("passage") ?: ""
                val qCount = doc.getLong("targetItems")?.toInt() ?: doc.getLong("questionCount")?.toInt() ?: 10
                val due = doc.getLong("dueDate") ?: dueDateMillis
                val cId = doc.getString("classId") ?: ""
                val cName = doc.getString("className") ?: ""
                val quizJson = doc.getString("quiz_pool_json") ?: "[]"

                subjectId = doc.getString("subjectId") ?: subjectId
                subjectName = doc.getString("subjectName") ?: subjectName
                grade = doc.getString("grade") ?: grade
                sourceType = doc.getString("sourceType") ?: sourceType
                sourceRef = doc.getString("sourceRef") ?: sourceRef

                findViewById<EditText>(R.id.et_assessment_title)?.setText(title)
                findViewById<EditText>(R.id.et_assessment_passage)?.setText(passage)
                findViewById<EditText>(R.id.et_target_items)?.setText(qCount.toString())

                dueDateMillis = due
                findViewById<TextView>(R.id.tv_due_date)?.let { updateDueDateDisplay(it) }

                if (cId.isNotEmpty()) {
                    targetClassId = cId
                    targetClassName = cName
                    val spinnerClass = findViewById<Spinner>(R.id.spinner_target_class)
                    val idx = enrolledClasses.indexOfFirst { it.id == targetClassId }
                    if (idx != -1) {
                        spinnerClass?.setSelection(idx)
                    } else if (enrolledClasses.isNotEmpty()) {
                        loadTeacherClasses()
                    }
                }

                if (passedJson == "[]" && container.childCount == 0) {
                    loadInitialQuestions(quizJson)
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Error loading assessment: ${e.message}")
            }
    }

    private fun loadExistingMaterialQuiz() {
        GabAIUtils.showGlobalLoading(this, "Loading quiz questions...")
        db.collection("library_materials").document(materialId).get()
            .addOnSuccessListener { doc ->
                GabAIUtils.hideGlobalLoading(this)
                if (!doc.exists()) return@addOnSuccessListener
                val quizJson = doc.getString("quiz_pool_json") ?: "[]"
                val maxItems = doc.getLong("quiz_max_items")?.toInt() ?: 5
                findViewById<EditText>(R.id.et_target_items)?.setText(maxItems.toString())
                if (container.childCount == 0 && quizJson != "[]") {
                    loadInitialQuestions(quizJson)
                }
            }
            .addOnFailureListener {
                GabAIUtils.hideGlobalLoading(this)
            }
    }

    private fun confirmDeleteAssessment() {
        if (assessmentId.isEmpty()) return
        val title = findViewById<EditText>(R.id.et_assessment_title)?.text?.toString()?.trim()?.ifEmpty { "this assessment" } ?: "this assessment"
        GabAIDialogs.showConfirmDialog(
            context = this,
            title = "Delete Weekly Assessment?",
            message = "Are you sure you want to permanently delete \"$title\"? Students will no longer see it.",
            confirmText = "Delete Assessment",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "🗑️",
            iconRes = R.drawable.ic_trash,
            onConfirm = {
                GabAIUtils.showGlobalLoading(this, "Deleting assessment...")
                db.collection("weekly_assessments").document(assessmentId).delete()
                    .addOnSuccessListener {
                        GabAIUtils.hideGlobalLoading(this)
                        Toast.makeText(this, "Weekly Assessment deleted.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Failed to delete: ${e.message}")
                    }
            }
        )
    }

    private fun loadTeacherInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val first = doc.getString("firstName") ?: ""
            val last = doc.getString("lastName") ?: ""
            if (first.isNotEmpty() || last.isNotEmpty()) {
                teacherFullName = "$first $last".trim()
            }
        }
    }

    private fun loadTeacherClasses() {
        val uid = auth.currentUser?.uid ?: return
        val spinnerClass = findViewById<Spinner>(R.id.spinner_target_class)

        db.collection("classes")
            .whereArrayContains("teacherIds", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                enrolledClasses.clear()
                for (doc in snapshots) {
                    val cName = doc.getString("className") ?: "Class"
                    val cGrade = doc.getString("grade") ?: ""
                    val fullCName = if (cGrade.isNotEmpty() && !cName.contains(cGrade)) "$cGrade - $cName" else cName
                    val sId = doc.getString("schoolId") ?: ""
                    enrolledClasses.add(ClassInfo(doc.id, fullCName, sId, cGrade))
                }

                if (targetClassId.isNotEmpty() && enrolledClasses.none { it.id == targetClassId }) {
                    enrolledClasses.add(0, ClassInfo(targetClassId, if (targetClassName.isNotEmpty()) targetClassName else "Target Class", intent.getStringExtra("SCHOOL_ID") ?: "", intent.getStringExtra("GRADE") ?: ""))
                }

                if (enrolledClasses.isEmpty()) {
                    val emptyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("No Classes Available (Create a class first)"))
                    spinnerClass.adapter = emptyAdapter
                } else {
                    val classNames = enrolledClasses.map { it.name }.toTypedArray()
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classNames)
                    spinnerClass.adapter = adapter

                    if (targetClassId.isNotEmpty()) {
                        val targetIdx = enrolledClasses.indexOfFirst { it.id == targetClassId }
                        if (targetIdx != -1) {
                            spinnerClass.setSelection(targetIdx)
                        }
                    }
                }
            }
    }

    private fun showDueDatePicker(tvDueDate: TextView) {
        val cal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        val picker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                dueDateMillis = selectedCal.timeInMillis
                updateDueDateDisplay(tvDueDate)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.minDate = System.currentTimeMillis()
        picker.show()
    }

    private fun updateDueDateDisplay(tvDueDate: TextView) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvDueDate.text = "Due: ${sdf.format(Date(dueDateMillis))} 📅"
    }

    private fun loadInitialQuestions(jsonStr: String) {
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val q = obj.getString("q")
                val opts = obj.getJSONArray("options")
                val optList = listOf(opts.getString(0), opts.getString(1), opts.getString(2), opts.getString(3))
                val ans = obj.getInt("ans")
                val explanation = obj.optString("explanation", "")
                addQuestionToUI(q, optList, ans, explanation)
            }
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(this, "Loaded questions template.")
        }
    }

    private fun addQuestionToUI(question: String, options: List<String>, correctIdx: Int, explanation: String = "") {
        val view = layoutInflater.inflate(R.layout.item_edit_question, container, false)

        val etQ = view.findViewById<EditText>(R.id.et_question)
        val etOpts = listOf(
            view.findViewById<EditText>(R.id.et_opt_0),
            view.findViewById<EditText>(R.id.et_opt_1),
            view.findViewById<EditText>(R.id.et_opt_2),
            view.findViewById<EditText>(R.id.et_opt_3)
        )
        val spinner = view.findViewById<Spinner>(R.id.spinner_correct_ans)
        val etExplanation = view.findViewById<EditText>(R.id.et_explanation)
        val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete_q)
        val btnMoveUp = view.findViewById<ImageButton>(R.id.btn_move_up)
        val btnMoveDown = view.findViewById<ImageButton>(R.id.btn_move_down)

        etQ.setText(question)
        for (i in 0..3) {
            etOpts[i].setText(options.getOrNull(i) ?: "")
        }
        etExplanation?.setText(explanation)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Option 1", "Option 2", "Option 3", "Option 4"))
        spinner.adapter = adapter
        spinner.setSelection(correctIdx.coerceIn(0, 3))

        // Reordering Up
        btnMoveUp?.setOnClickListener {
            val idx = container.indexOfChild(view)
            if (idx > 0) {
                container.removeViewAt(idx)
                container.addView(view, idx - 1)
                updateQuestionNumbers()
            }
        }

        // Reordering Down
        btnMoveDown?.setOnClickListener {
            val idx = container.indexOfChild(view)
            if (idx < container.childCount - 1) {
                container.removeViewAt(idx)
                container.addView(view, idx + 1)
                updateQuestionNumbers()
            }
        }

        btnDelete.setOnClickListener {
            GabAIDialogs.showConfirmDialog(
                context = this,
                title = "Delete Question?",
                message = "Are you sure you want to remove this question?",
                confirmText = "Delete",
                cancelText = "Cancel",
                isDestructive = true,
                badgeIcon = "🗑️",
                iconRes = R.drawable.ic_trash,
                onConfirm = {
                    container.removeView(view)
                    updateQuestionNumbers()
                    GabAIUtils.showSnackbar(this, "Question removed.")
                }
            )
        }

        container.addView(view)
        updateQuestionNumbers()
    }

    private fun updateQuestionNumbers() {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).findViewById<TextView>(R.id.tv_q_number)?.text = "Question ${i + 1}"
        }
    }

    // 🟢 Manual Add Dialog (with Explanation field)
    private fun showManualAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        val etQ = EditText(this).apply {
            hint = "Enter Question"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
        }
        val etO0 = EditText(this).apply {
            hint = "Option 1"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
        }
        val etO1 = EditText(this).apply {
            hint = "Option 2"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
        }
        val etO2 = EditText(this).apply {
            hint = "Option 3"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
        }
        val etO3 = EditText(this).apply {
            hint = "Option 4"
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
        }

        val tvAns = TextView(this).apply {
            text = "Correct Answer:"
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuizEditorActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Option 1", "Option 2", "Option 3", "Option 4"))
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(24, 20, 24, 20)
        }

        val tvExp = TextView(this).apply {
            text = "Educational Explanation:"
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3436"))
        }
        val etExp = EditText(this).apply {
            hint = "Explain why this answer is correct..."
            minLines = 2
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
        }

        layout.addView(etQ)
        layout.addView(etO0)
        layout.addView(etO1)
        layout.addView(etO2)
        layout.addView(etO3)
        layout.addView(tvAns)
        layout.addView(spinner)
        layout.addView(tvExp)
        layout.addView(etExp)

        val scrollView = ScrollView(this).apply {
            addView(layout)
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 320).toInt())
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Add Custom Question",
            subtitle = "Enter question details, options, and explanation",
            customView = scrollView,
            confirmText = "Add",
            cancelText = "Cancel",
            badgeIcon = "✍️",
            onConfirm = { dialog ->
                val q = etQ.text.toString().trim()
                val opts = listOf(etO0.text.toString().trim(), etO1.text.toString().trim(), etO2.text.toString().trim(), etO3.text.toString().trim())
                val ans = spinner.selectedItemPosition
                val exp = etExp.text.toString().trim()

                if (q.isNotEmpty()) {
                    dialog.dismiss()
                    addQuestionToUI(q, opts, ans, exp)
                    GabAIUtils.showSnackbar(this, "Question added!")
                    scrollToBottom()
                } else {
                    GabAIUtils.showSnackbar(this, "Please enter a question.")
                }
            }
        )
    }

    private fun scrollToBottom() {
        val scrollView = container.parent as? ScrollView
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun promptForAiGenerate() {
        val currentCount = container.childCount
        val maxAllowed = 20 - currentCount

        if (maxAllowed <= 0) {
            GabAIUtils.showSnackbar(this, "Limit reached! (Max 20 questions).")
            return
        }

        GabAIDialogs.showInputDialog(
            context = this,
            title = "Generate More Questions",
            message = "Gemini will generate and append new questions with educational explanations.\n\nCurrent questions: $currentCount. You can generate up to $maxAllowed more.",
            hint = "Generate how many items? (Max $maxAllowed)",
            initialText = "5",
            confirmText = "Generate",
            cancelText = "Cancel",
            isNumeric = true,
            badgeIcon = "✨",
            onConfirm = { text ->
                var count = text.toIntOrNull() ?: maxAllowed
                if (count > maxAllowed) count = maxAllowed
                if (count > 0) generateMoreQuestions(count)
            }
        )
    }

    private fun generateMoreQuestions(count: Int) {
        GabAIUtils.showGlobalLoading(this, "AI is generating $count questions...")
        val currentPassage = findViewById<EditText>(R.id.et_assessment_passage)?.text?.toString()?.trim() ?: ""
        val currentTitle = findViewById<EditText>(R.id.et_assessment_title)?.text?.toString()?.trim() ?: subjectName

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var sourceMaterialText = currentPassage
                if (sourceMaterialText.isEmpty() && pdfFilePath.isNotEmpty()) {
                    try {
                        val file = File(pdfFilePath)
                        if (file.exists()) {
                            val doc = PDDocument.load(file)
                            sourceMaterialText = PDFTextStripper().getText(doc)
                            doc.close()
                        }
                    } catch (_: Exception) {}
                }

                val passageContextBlock = if (sourceMaterialText.isNotEmpty()) {
                    """
                    Base all questions strictly on the following reading passage:
                    \"\"\"
                    ${sourceMaterialText.take(10000)}
                    \"\"\"
                    """.trimIndent()
                } else {
                    "Generate reading comprehension questions appropriate for Grade $grade ($subjectName)."
                }

                val prompt = """
                    You are an expert educational reading and literacy assessment specialist.
                    Subject: $subjectName
                    Grade: $grade
                    Assessment Focus: $currentTitle

                    $passageContextBlock

                    CRITICAL CONTENT, SAFETY & NEUTRALITY DIRECTIVES:
                    1. OBJECTIVE EDUCATIONAL FOCUS: Generate clear, age-appropriate questions focused strictly on reading comprehension, vocabulary in context, and literary analysis for students.
                    2. NO GOVERNMENT IMPERSONATION OR POLICY CLAIMS: Do NOT roleplay, claim representation, or simulate official mandates or endorsement by the Department of Education (DepEd), the Republic of the Philippines, or any government body.
                    3. NO DANGEROUS, CONTROVERSIAL, OR HARMFUL CONTENT: Do NOT generate questions involving dangerous activities, weapons, illicit substances, self-harm, violence, hate speech, or partisan political opinions.
                    4. FACTUAL GROUNDING: Ensure questions and answers are clear, unambiguous, and factually accurate.

                    Generate exactly $count high-quality multiple choice assessment questions.
                    Each question must have:
                    - "q": clear question stem
                    - "options": 4 plausible choices
                    - "ans": 0-indexed correct answer (0, 1, 2, or 3)
                    - "explanation": clear educational explanation explaining why this answer is correct.

                    Return ONLY a valid JSON array. Format:
                    [
                      {
                        "q": "Question?",
                        "options": ["A", "B", "C", "D"],
                        "ans": 0,
                        "explanation": "Educational explanation."
                      }
                    ]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                var jsonStr = response.text ?: "[]"
                val startIndex = jsonStr.indexOf("[")
                val endIndex = jsonStr.lastIndexOf("]")
                if (startIndex != -1 && endIndex != -1) jsonStr = jsonStr.substring(startIndex, endIndex + 1)

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@QuizEditorActivity)
                    loadInitialQuestions(jsonStr)
                    GabAIUtils.showSnackbar(this@QuizEditorActivity, "Appended $count new questions!")
                    scrollToBottom()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@QuizEditorActivity)
                    GabAIUtils.showSnackbar(this@QuizEditorActivity, "Error: ${e.message}")
                }
            }
        }
    }

    private fun saveAndPublish() {
        val jsonArray = JSONArray()

        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            val q = view.findViewById<EditText>(R.id.et_question)?.text.toString().trim()
            val o0 = view.findViewById<EditText>(R.id.et_opt_0)?.text.toString().trim()
            val o1 = view.findViewById<EditText>(R.id.et_opt_1)?.text.toString().trim()
            val o2 = view.findViewById<EditText>(R.id.et_opt_2)?.text.toString().trim()
            val o3 = view.findViewById<EditText>(R.id.et_opt_3)?.text.toString().trim()
            val ans = view.findViewById<Spinner>(R.id.spinner_correct_ans)?.selectedItemPosition ?: 0
            val explanation = view.findViewById<EditText>(R.id.et_explanation)?.text.toString().trim()

            if (q.isEmpty() || o0.isEmpty() || o1.isEmpty() || o2.isEmpty() || o3.isEmpty()) {
                GabAIUtils.showSnackbar(this, "Question ${i + 1} has blank fields. Please fill them out or delete it.")
                return
            }

            val optsArray = JSONArray().apply { put(o0); put(o1); put(o2); put(o3) }
            val qObj = JSONObject().apply {
                put("q", q)
                put("options", optsArray)
                put("ans", ans)
                put("explanation", explanation)
            }
            jsonArray.put(qObj)
        }

        var targetItems = findViewById<EditText>(R.id.et_target_items)?.text.toString().toIntOrNull() ?: if (isWeeklyAssessment) 10 else 5
        if (targetItems > 20) targetItems = 20

        if (container.childCount < targetItems) {
            GabAIUtils.showSnackbar(this, "Cannot publish! You need at least $targetItems questions, but only have ${container.childCount}. Please add more.")
            return
        }

        if (isWeeklyAssessment) {
            val title = findViewById<EditText>(R.id.et_assessment_title)?.text.toString().trim()
            if (title.isEmpty()) {
                GabAIUtils.showSnackbar(this, "Please enter an Assessment Title.")
                return
            }

            val passageText = findViewById<EditText>(R.id.et_assessment_passage)?.text?.toString()?.trim() ?: ""
            if (passageText.isEmpty()) {
                GabAIUtils.showSnackbar(this, "Please provide a Reading Passage so students can read the text before answering.")
                findViewById<EditText>(R.id.et_assessment_passage)?.visibility = View.VISIBLE
                findViewById<EditText>(R.id.et_assessment_passage)?.requestFocus()
                return
            }

            val spinnerClass = findViewById<Spinner>(R.id.spinner_target_class)
            val selectedClassPos = spinnerClass?.selectedItemPosition ?: -1
            if (enrolledClasses.isEmpty() || selectedClassPos < 0 || selectedClassPos >= enrolledClasses.size) {
                GabAIUtils.showSnackbar(this, "Please select an enrolled class to assign this assessment.")
                return
            }

            val targetClass = enrolledClasses[selectedClassPos]
            val uid = auth.currentUser?.uid ?: ""

            if (assessmentId.isNotEmpty()) {
                GabAIUtils.showGlobalLoading(this, "Saving changes...")
                val updateData = mapOf<String, Any>(
                    "title" to title,
                    "passage" to passageText,
                    "subjectId" to subjectId,
                    "subjectName" to subjectName.ifEmpty { targetClass.name },
                    "grade" to if (grade.isNotEmpty()) grade else targetClass.grade,
                    "classId" to targetClass.id,
                    "className" to targetClass.name,
                    "schoolId" to targetClass.schoolId,
                    "dueDate" to dueDateMillis,
                    "quiz_pool_json" to jsonArray.toString(),
                    "questionCount" to container.childCount,
                    "targetItems" to targetItems,
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection("weekly_assessments").document(assessmentId).update(updateData)
                    .addOnSuccessListener {
                        GabAIUtils.hideGlobalLoading(this)
                        Toast.makeText(this, "Weekly Assessment Updated!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Failed to update: ${e.message}")
                    }
            } else {
                GabAIUtils.showGlobalLoading(this, "Publishing Weekly Assessment...")

                val assessmentData = hashMapOf(
                    "title" to title,
                    "passage" to passageText,
                    "subjectId" to subjectId,
                    "subjectName" to subjectName.ifEmpty { targetClass.name },
                    "grade" to if (grade.isNotEmpty()) grade else targetClass.grade,
                    "classId" to targetClass.id,
                    "className" to targetClass.name,
                    "schoolId" to targetClass.schoolId,
                    "teacherId" to uid,
                    "teacherName" to teacherFullName,
                    "dueDate" to dueDateMillis,
                    "sourceType" to sourceType,
                    "sourceRef" to sourceRef,
                    "quiz_pool_json" to jsonArray.toString(),
                    "questionCount" to container.childCount,
                    "targetItems" to targetItems,
                    "createdAt" to System.currentTimeMillis(),
                    "status" to "active"
                )

                db.collection("weekly_assessments").add(assessmentData)
                    .addOnSuccessListener {
                        GabAIUtils.hideGlobalLoading(this)
                        Toast.makeText(this, "Weekly Assessment Published to ${targetClass.name}!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        GabAIUtils.hideGlobalLoading(this)
                        GabAIUtils.showSnackbar(this, "Failed to publish: ${e.message}")
                    }
            }
        } else {
            GabAIUtils.showGlobalLoading(this, "Publishing to material...")

            db.collection("library_materials").document(materialId).update(
                "quiz_pool_json", jsonArray.toString(),
                "quiz_max_items", targetItems
            ).addOnSuccessListener {
                GabAIUtils.hideGlobalLoading(this)
                Toast.makeText(this, "Quiz Published!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Failed to publish quiz.")
            }
        }
    }
}