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

class QuizEditorActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var materialId = ""

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_editor)

        materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()
        val initialJson = intent.getStringExtra("QUIZ_JSON") ?: "[]"
        val targetItems = intent.getIntExtra("TARGET_ITEMS", 5)

        container = findViewById(R.id.questions_container)
        findViewById<EditText>(R.id.et_target_items).setText(targetItems.toString())

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        // 🟢 CHANGED: Now opens the Dialog instead of instantly appending!
        findViewById<Button>(R.id.btn_add_manual).setOnClickListener { showManualAddDialog() }

        findViewById<Button>(R.id.btn_add_ai).setOnClickListener { promptForAiGenerate() }
        findViewById<Button>(R.id.btn_save_quiz).setOnClickListener { saveAndPublish() }

        loadInitialQuestions(initialJson)
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
                addQuestionToUI(q, optList, ans)
            }
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(this, "Failed to parse AI output. Add manually.")
        }
    }

    private fun addQuestionToUI(question: String, options: List<String>, correctIdx: Int) {
        val view = layoutInflater.inflate(R.layout.item_edit_question, container, false)

        val etQ = view.findViewById<EditText>(R.id.et_question)
        val etOpts = listOf(
            view.findViewById<EditText>(R.id.et_opt_0),
            view.findViewById<EditText>(R.id.et_opt_1),
            view.findViewById<EditText>(R.id.et_opt_2),
            view.findViewById<EditText>(R.id.et_opt_3)
        )
        val spinner = view.findViewById<Spinner>(R.id.spinner_correct_ans)
        val btnDelete = view.findViewById<ImageButton>(R.id.btn_delete_q)

        etQ.setText(question)
        for (i in 0..3) {
            etOpts[i].setText(options.getOrNull(i) ?: "")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Option 1", "Option 2", "Option 3", "Option 4"))
        spinner.adapter = adapter
        spinner.setSelection(correctIdx)

        // 🟢 CHANGED: Added Confirmation Dialog for Deletion!
        btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Question?")
                .setMessage("Are you sure you want to remove this question from the quiz pool?")
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
            container.getChildAt(i).findViewById<TextView>(R.id.tv_q_number).text = "Question ${i + 1}"
        }
    }

    // 🟢 NEW: Manual Add Dialog
    private fun showManualAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val etQ = EditText(this).apply { hint = "Enter Question"; setPadding(0,0,0,30) }
        val etO0 = EditText(this).apply { hint = "Option 1" }
        val etO1 = EditText(this).apply { hint = "Option 2" }
        val etO2 = EditText(this).apply { hint = "Option 3" }
        val etO3 = EditText(this).apply { hint = "Option 4" }

        val tvAns = TextView(this).apply { text = "Correct Answer:"; setPadding(0, 30, 0, 10); setTypeface(null, android.graphics.Typeface.BOLD) }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@QuizEditorActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Option 1", "Option 2", "Option 3", "Option 4"))
        }

        layout.addView(etQ); layout.addView(etO0); layout.addView(etO1)
        layout.addView(etO2); layout.addView(etO3); layout.addView(tvAns); layout.addView(spinner)

        val scrollView = ScrollView(this).apply { addView(layout) }

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Custom Question")
            .setView(scrollView)
            .setPositiveButton("Add") { _, _ ->
                val q = etQ.text.toString()
                val opts = listOf(etO0.text.toString(), etO1.text.toString(), etO2.text.toString(), etO3.text.toString())
                val ans = spinner.selectedItemPosition

                addQuestionToUI(q, opts, ans)
                GabAIUtils.showSnackbar(this, "Question added!")
                scrollToBottom() // 🟢 SCROLL TO BOTTOM
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 🟢 NEW: Helper function to auto-scroll to the bottom of the editor
    private fun scrollToBottom() {
        val scrollView = container.parent as? ScrollView
        scrollView?.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // 🟢 UPDATED: ANTI-SPAM AI ADD FUNCTION 🟢
    private fun promptForAiGenerate() {
        val currentCount = container.childCount
        val maxAllowed = 15 - currentCount

        // 1. HARD BLOCK: If they already have 15 or more, stop them immediately.
        if (maxAllowed <= 0) {
            GabAIUtils.showSnackbar(this, "AI generation limit reached! (Max 15 questions per material).")
            return
        }

        // 2. DYNAMIC LIMIT: Only let them ask for the remaining allowance
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Generate how many items? (Max $maxAllowed)"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("✨ Generate More Questions")
            .setMessage("GabAI will read the document and append new questions.\n\nYou currently have $currentCount questions. You can use the AI to generate up to $maxAllowed more.")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                var count = input.text.toString().toIntOrNull() ?: maxAllowed

                // 3. ENFORCE THE DYNAMIC LIMIT
                if (count > maxAllowed) {
                    count = maxAllowed
                    GabAIUtils.showSnackbar(this, "Limited to $maxAllowed to prevent exceeding the 15 question AI limit.")
                }

                if (count > 0) generateMoreQuestions(count)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateMoreQuestions(count: Int) {
        val filePath = intent.getStringExtra("PDF_FILE_PATH") ?: return
        GabAIUtils.showGlobalLoading(this, "AI is generating $count questions...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val document = PDDocument.load(File(filePath))
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()

                val prompt = """
                    You are an AI teacher. Read this text:
                    ${text.take(10000)}
                    Create exactly $count multiple choice questions based on the text.
                    Return ONLY a valid JSON array. Format:
                    [ { "q": "Question?", "options": ["A", "B", "C", "D"], "ans": 0 } ]
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
                    scrollToBottom() // 🟢 SCROLL TO BOTTOM AFTER AI GENERATION
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
            val q = view.findViewById<EditText>(R.id.et_question).text.toString().trim()
            val o0 = view.findViewById<EditText>(R.id.et_opt_0).text.toString().trim()
            val o1 = view.findViewById<EditText>(R.id.et_opt_1).text.toString().trim()
            val o2 = view.findViewById<EditText>(R.id.et_opt_2).text.toString().trim()
            val o3 = view.findViewById<EditText>(R.id.et_opt_3).text.toString().trim()
            val ans = view.findViewById<Spinner>(R.id.spinner_correct_ans).selectedItemPosition

            if (q.isEmpty() || o0.isEmpty() || o1.isEmpty() || o2.isEmpty() || o3.isEmpty()) {
                GabAIUtils.showSnackbar(this, "Question ${i+1} has blank fields. Please fill them out or delete it.")
                return
            }

            val optsArray = JSONArray().apply { put(o0); put(o1); put(o2); put(o3) }
            val qObj = JSONObject().apply {
                put("q", q)
                put("options", optsArray)
                put("ans", ans)
            }
            jsonArray.put(qObj)
        }

        var targetItems = findViewById<EditText>(R.id.et_target_items).text.toString().toIntOrNull() ?: 5
        if (targetItems > 20) targetItems = 20

        // 🟢 STRICT CHECK: Block publishing if they don't have enough questions!
        if (container.childCount < targetItems) {
            GabAIUtils.showSnackbar(this, "Cannot publish! You need at least $targetItems questions, but only have ${container.childCount}. Please add more.")
            return
        }

        GabAIUtils.showGlobalLoading(this, "Publishing to class...")

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