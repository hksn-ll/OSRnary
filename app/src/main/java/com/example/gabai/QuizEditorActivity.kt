package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
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

    private var aiCreditsUsed = 0

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_editor)

        materialId = intent.getStringExtra("MATERIAL_ID") ?: return finish()

        // We still grab the fallback intent data just in case, but we will fetch the real draft below
        val initialJson = intent.getStringExtra("QUIZ_JSON") ?: "[]"

        var targetItems = intent.getIntExtra("TARGET_ITEMS", 5)
        if (targetItems > 10) targetItems = 10

        container = findViewById(R.id.questions_container)
        findViewById<EditText>(R.id.et_target_items).setText(targetItems.toString())

        // 🟢 INTERCEPT THE BACK BUTTONS 🟢
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { showExitConfirmationDialog() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        findViewById<Button>(R.id.btn_add_manual).setOnClickListener { showManualAddDialog() }

        val btnAi = findViewById<Button>(R.id.btn_add_ai)
        btnAi.setOnClickListener { promptForAiGenerate() }
        findViewById<Button>(R.id.btn_save_quiz).setOnClickListener { saveAndPublish() }

        // 🟢 FETCH CREDITS AND CHECK FOR DRAFTS 🟢
        GabAIUtils.showGlobalLoading(this, "Loading Editor...")
        db.collection("library_materials").document(materialId).get().addOnSuccessListener { doc ->
            GabAIUtils.hideGlobalLoading(this)

            aiCreditsUsed = doc.getLong("ai_credits_used")?.toInt() ?: 0
            updateAiButtonUI()

            // DRAFT LOGIC: Load the hidden draft if it exists, otherwise load the published pool
            val draftJson = doc.getString("quiz_draft_json")
            val poolJson = doc.getString("quiz_pool_json") ?: "[]"
            val jsonToLoad = if (!draftJson.isNullOrEmpty() && draftJson != "[]") draftJson else poolJson

            loadInitialQuestions(jsonToLoad)
        }.addOnFailureListener {
            GabAIUtils.hideGlobalLoading(this)
            loadInitialQuestions(initialJson) // Fallback if internet fails
        }
    }

    // ========================================================================
    // 🟢 NEW: DRAFT & EXIT LOGIC 🟢
    // ========================================================================
    private fun showExitConfirmationDialog() {
        val currentCount = container.childCount
        if (currentCount == 0) {
            finish() // Nothing to save
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved Changes ⚠️")
            .setMessage("Do you want to save your current questions as a draft before exiting? Students will not see these changes until you Publish.")
            .setPositiveButton("Save Draft") { _, _ ->
                saveDraftAndExit()
            }
            .setNegativeButton("Discard Changes") { _, _ ->
                finish() // Exits without saving
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun saveDraftAndExit() {
        val jsonArray = JSONArray()

        // Grab everything currently on the screen, even if it has blank fields
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            val q = view.findViewById<EditText>(R.id.et_question).text.toString().trim()
            val o0 = view.findViewById<EditText>(R.id.et_opt_0).text.toString().trim()
            val o1 = view.findViewById<EditText>(R.id.et_opt_1).text.toString().trim()
            val o2 = view.findViewById<EditText>(R.id.et_opt_2).text.toString().trim()
            val o3 = view.findViewById<EditText>(R.id.et_opt_3).text.toString().trim()
            val ans = view.findViewById<Spinner>(R.id.spinner_correct_ans).selectedItemPosition

            val optsArray = JSONArray().apply { put(o0); put(o1); put(o2); put(o3) }
            val qObj = JSONObject().apply {
                put("q", q)
                put("options", optsArray)
                put("ans", ans)
            }
            jsonArray.put(qObj)
        }

        val targetItemsStr = findViewById<EditText>(R.id.et_target_items).text.toString()
        val targetItems = targetItemsStr.toIntOrNull() ?: 10

        GabAIUtils.showGlobalLoading(this, "Saving draft...")

        // 🟢 Save to the hidden 'quiz_draft_json' field so students can't see it!
        db.collection("library_materials").document(materialId).update(
            "quiz_draft_json", jsonArray.toString(),
            "quiz_max_items", targetItems
        ).addOnSuccessListener {
            GabAIUtils.hideGlobalLoading(this)
            Toast.makeText(this, "Draft Saved Successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            GabAIUtils.hideGlobalLoading(this)
            GabAIUtils.showSnackbar(this, "Failed to save draft.")
        }
    }

    // ========================================================================
    // STANDARD EDITOR LOGIC
    // ========================================================================

    private fun updateAiButtonUI() {
        val btnAi = findViewById<Button>(R.id.btn_add_ai)
        val creditsLeft = 15 - aiCreditsUsed

        if (creditsLeft <= 0) {
            btnAi.isEnabled = false
            btnAi.text = "✨ 0 Credits"
            btnAi.alpha = 0.5f
        } else {
            btnAi.isEnabled = true
            btnAi.text = "✨ AI ($creditsLeft left)"
            btnAi.alpha = 1.0f
        }
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

        btnDelete.setOnClickListener {
            val targetItems = findViewById<EditText>(R.id.et_target_items).text.toString().toIntOrNull() ?: 5
            val requiredPool = targetItems + 5

            if (container.childCount <= requiredPool) {
                GabAIUtils.showSnackbar(this, "Minimum $requiredPool questions required for randomness! You cannot delete this. Please edit the text directly instead to save your AI credits.")
                return@setOnClickListener
            }

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

    private fun showManualAddDialog() {
        if (container.childCount >= 20) {
            GabAIUtils.showSnackbar(this, "Limit reached: The question pool cannot exceed 20 items.")
            return
        }

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
                scrollToBottom()
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

    private fun promptForAiGenerate(prefillAmount: Int? = null) {
        val currentCount = container.childCount
        val creditsLeft = 15 - aiCreditsUsed
        val spaceLeft = 20 - currentCount

        val maxCanGenerate = minOf(creditsLeft, spaceLeft)

        if (maxCanGenerate <= 0) {
            if (creditsLeft <= 0) {
                GabAIUtils.showSnackbar(this, "You have 0 AI credits remaining for this material.")
            } else {
                GabAIUtils.showSnackbar(this, "Your question pool is full (Max 20). Delete some to add more.")
            }
            return
        }

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Questions to generate (Max: $maxCanGenerate)"
            setPadding(50, 40, 50, 40)

            if (prefillAmount != null) {
                val safeAmount = minOf(prefillAmount, maxCanGenerate)
                setText(safeAmount.toString())
                setSelection(text.length)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("✨ Generate AI Questions")
            .setMessage("You have $creditsLeft AI credits remaining for this file.\n\nHow many questions do you want to generate right now? (Max $maxCanGenerate)")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val requested = input.text.toString().toIntOrNull() ?: 0
                if (requested in 1..maxCanGenerate) {
                    generateAiQuestions(requested)
                } else {
                    GabAIUtils.showSnackbar(this, "Please enter a valid number between 1 and $maxCanGenerate.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateAiQuestions(count: Int) {
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

                    aiCreditsUsed += count
                    db.collection("library_materials").document(materialId).update("ai_credits_used", aiCreditsUsed)

                    updateAiButtonUI()

                    GabAIUtils.showSnackbar(this@QuizEditorActivity, "Successfully added $count new questions!")
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
        val targetItemsStr = findViewById<EditText>(R.id.et_target_items).text.toString()
        val targetItems = targetItemsStr.toIntOrNull() ?: 10

        if (targetItems > 10) {
            GabAIUtils.showSnackbar(this, "Error: The maximum questions a student can face is 10.")
            return
        }
        if (targetItems < 1) {
            GabAIUtils.showSnackbar(this, "Error: Students must face at least 1 question.")
            return
        }

        val currentCount = container.childCount
        val requiredPool = targetItems + 5

        if (currentCount < requiredPool) {
            val needed = requiredPool - currentCount
            val creditsLeft = 15 - aiCreditsUsed

            val builder = MaterialAlertDialogBuilder(this)
                .setTitle("More Questions Needed")
                .setMessage("To ensure student quizzes are randomized, your pool needs at least $requiredPool questions.\n\nYou currently have $currentCount questions. You need $needed more.")

            if (creditsLeft > 0) {
                builder.setPositiveButton("Use AI Credits") { _, _ -> promptForAiGenerate(needed) }
                builder.setNegativeButton("I'll add manually", null)
            } else {
                builder.setPositiveButton("Understood", null)
            }
            builder.show()
            return
        }

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
                GabAIUtils.showSnackbar(this, "Question ${i+1} has blank fields. Please fill them out.")
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

        GabAIUtils.showGlobalLoading(this, "Publishing to class...")

        // 🟢 Save to BOTH the active pool and the draft so they stay perfectly in sync!
        db.collection("library_materials").document(materialId).update(
            "quiz_pool_json", jsonArray.toString(),
            "quiz_draft_json", jsonArray.toString(),
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