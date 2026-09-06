package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_editor)

        isWeeklyAssessment = intent.getBooleanExtra("IS_WEEKLY_ASSESSMENT", false)
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""
        sourceType = intent.getStringExtra("SOURCE_TYPE") ?: "custom"
        sourceRef = intent.getStringExtra("SOURCE_REF") ?: ""

        if (!isWeeklyAssessment) {
            materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()
        }

        val initialJson = intent.getStringExtra("QUIZ_JSON") ?: "[]"
        val targetItems = intent.getIntExtra("TARGET_ITEMS", if (isWeeklyAssessment) 10 else 5)
        val defaultTitle = intent.getStringExtra("ASSESSMENT_TITLE") ?: if (subjectName.isNotEmpty()) "$subjectName Assessment" else "Weekly Assessment"

        container = findViewById(R.id.questions_container)
        findViewById<EditText>(R.id.et_target_items).setText(targetItems.toString())

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_add_manual).setOnClickListener { showManualAddDialog() }
        findViewById<Button>(R.id.btn_add_ai).setOnClickListener { promptForAiGenerate() }
        findViewById<Button>(R.id.btn_save_quiz).setOnClickListener { saveAndPublish() }

        val cardMeta = findViewById<View>(R.id.card_assessment_meta)
        val etTitle = findViewById<EditText>(R.id.et_assessment_title)
        val tvDueDate = findViewById<TextView>(R.id.tv_due_date)

        if (isWeeklyAssessment) {
            cardMeta.visibility = View.VISIBLE
            etTitle.setText(defaultTitle)
            updateDueDateDisplay(tvDueDate)

            tvDueDate.setOnClickListener {
                showDueDatePicker(tvDueDate)
            }

            findViewById<TextView>(R.id.tv_editor_title)?.text = "Weekly Assessment Builder"
            findViewById<Button>(R.id.btn_save_quiz)?.text = "Publish to Class ➔"

            loadTeacherClasses()
        } else {
            cardMeta.visibility = View.GONE
        }

        loadTeacherInfo()
        loadInitialQuestions(initialJson)
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

                if (enrolledClasses.isEmpty()) {
                    val emptyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("No Classes Available (Create a class first)"))
                    spinnerClass.adapter = emptyAdapter
                } else {
                    val classNames = enrolledClasses.map { it.name }.toTypedArray()
                    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, classNames)
                    spinnerClass.adapter = adapter
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
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Question?")
                .setMessage("Are you sure you want to remove this question?")
                .setPositiveButton("Delete") { _, _ ->
                    container.removeView(view)
                    updateQuestionNumbers()
                    GabAIUtils.showSnackbar(this, "Question removed.")
                }
                .setNegativeButton("Cancel", null)
                .show()
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
            setPadding(50, 40, 50, 40)
        }

        val etQ = EditText(this).apply { hint = "Enter Question"; setPadding(0, 0, 0, 20) }
        val etO0 = EditText(this).apply { hint = "Option 1" }
        val etO1 = EditText(this).apply { hint = "Option 2" }
        val etO2 = EditText(this).apply { hint = "Option 3" }
        val etO3 = EditText(this).apply { hint = "Option 4" }

        val tvAns = TextView(this).apply {
            text = "Correct Answer:"
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuizEditorActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Option 1", "Option 2", "Option 3", "Option 4"))
        }

        val tvExp = TextView(this).apply {
            text = "Educational Explanation:"
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val etExp = EditText(this).apply {
            hint = "Explain why this answer is correct..."
            minLines = 2
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

        val scrollView = ScrollView(this).apply { addView(layout) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Custom Question")
            .setView(scrollView)
            .setPositiveButton("Add") { _, _ ->
                val q = etQ.text.toString().trim()
                val opts = listOf(etO0.text.toString().trim(), etO1.text.toString().trim(), etO2.text.toString().trim(), etO3.text.toString().trim())
                val ans = spinner.selectedItemPosition
                val exp = etExp.text.toString().trim()

                if (q.isNotEmpty()) {
                    addQuestionToUI(q, opts, ans, exp)
                    GabAIUtils.showSnackbar(this, "Question added!")
                    scrollToBottom()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Generate how many items? (Max $maxAllowed)"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("✨ Generate More Questions")
            .setMessage("Gemini will generate and append new questions with educational explanations.\n\nCurrent questions: $currentCount. You can generate up to $maxAllowed more.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                var count = input.text.toString().toIntOrNull() ?: maxAllowed
                if (count > maxAllowed) count = maxAllowed
                if (count > 0) generateMoreQuestions(count)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateMoreQuestions(count: Int) {
        GabAIUtils.showGlobalLoading(this, "AI is generating $count questions...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prompt = """
                    You are an expert DepEd curriculum designer.
                    Subject: $subjectName
                    Grade: $grade
                    Assessment Focus: ${findViewById<EditText>(R.id.et_assessment_title)?.text?.toString() ?: subjectName}

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

            val spinnerClass = findViewById<Spinner>(R.id.spinner_target_class)
            val selectedClassPos = spinnerClass?.selectedItemPosition ?: -1
            if (enrolledClasses.isEmpty() || selectedClassPos < 0 || selectedClassPos >= enrolledClasses.size) {
                GabAIUtils.showSnackbar(this, "Please select an enrolled class to assign this assessment.")
                return
            }

            val targetClass = enrolledClasses[selectedClassPos]
            val uid = auth.currentUser?.uid ?: ""

            GabAIUtils.showGlobalLoading(this, "Publishing Weekly Assessment...")

            val assessmentData = hashMapOf(
                "title" to title,
                "subjectId" to subjectId,
                "subjectName" to subjectName,
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