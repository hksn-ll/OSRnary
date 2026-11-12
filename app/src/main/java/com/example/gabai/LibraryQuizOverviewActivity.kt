package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibraryQuizOverviewActivity : AppCompatActivity() {

    private var isWeeklyAssessment: Boolean = false
    private var assessmentId: String = ""
    private var materialId: String = ""
    private var materialTitle: String = "Reading Quiz"
    private var subjectId: String = ""
    private var subjectName: String = "Reading Material"
    private var weeklyClassId: String = ""
    private var weeklyClassName: String = ""
    private var weeklySectionName: String = ""
    private var weeklyGrade: String = ""
    private var weeklySchoolId: String = ""
    private var weeklyPassage: String = ""
    private var weeklyTargetItems: Int = 10
    private var assignedSectionsFromIntent: ArrayList<String> = arrayListOf()
    private var hasInitializedOnce: Boolean = false

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var parsedQuestions = mutableListOf<QuestionModel>()
    private var allTeacherClasses = mutableListOf<ClassModel>()
    private var selectedClassId: String = "ALL" // "ALL" or specific classId
    private var allStudentSubmissions = mutableListOf<StudentSubmissionState>()
    private var currentFilter: String = "ALL" // "ALL", "COMPLETED", "PENDING"
    private var currentTab: String = "SUBMISSIONS" // "SUBMISSIONS", "QUESTIONS"
    private var adminRemarksListener: com.google.firebase.firestore.ListenerRegistration? = null

    data class ClassModel(
        val id: String,
        val className: String,
        val sectionName: String,
        val grade: String,
        val joinedStudents: List<String>
    )

    data class QuestionModel(
        val index: Int,
        val question: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String = ""
    )

    data class StudentSubmissionState(
        val studentId: String,
        val studentName: String,
        val photoUrl: String?,
        val section: String,
        val hasSubmitted: Boolean,
        val score: Int = 0,
        val totalQuestions: Int = 0,
        val percentage: Int = 0,
        val isPassed: Boolean = false,
        val attempts: Int = 0,
        val timestamp: Long = 0L,
        val xpEarned: Int = 0,
        val items: List<Map<String, Any>> = emptyList(),
        val submissionDocId: String = "",
        val teacherRemark: String = ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)

        val currentUid = auth.currentUser?.uid
        val prefs = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE)
        val cachedRole = prefs.getString("cached_user_role", null) ?: prefs.getString("user_role", null)

        // Strict role security: Only educators can access class quiz overview
        if (currentUid == null || (cachedRole != null && cachedRole != "teacher")) {
            GabAIUtils.showSnackbar(this, "Access restricted to educators only.")
            finish()
            return
        }

        setContentView(R.layout.activity_library_quiz_overview)

        isWeeklyAssessment = intent.getBooleanExtra("IS_WEEKLY_ASSESSMENT", false)
        assessmentId = intent.getStringExtra("ASSESSMENT_ID") ?: ""
        if (assessmentId.isNotEmpty()) isWeeklyAssessment = true

        if (isWeeklyAssessment) {
            if (assessmentId.isEmpty()) {
                GabAIUtils.showSnackbar(this, "Missing assessment reference")
                finish()
                return
            }
            weeklyClassId = intent.getStringExtra("CLASS_ID") ?: ""
            weeklyClassName = intent.getStringExtra("CLASS_NAME") ?: ""
            weeklySectionName = intent.getStringExtra("SECTION_NAME") ?: ""
            weeklyGrade = intent.getStringExtra("GRADE") ?: ""
            weeklySchoolId = intent.getStringExtra("SCHOOL_ID") ?: ""
            if (weeklyClassId.isNotEmpty()) {
                selectedClassId = weeklyClassId
            }
        } else {
            materialId = intent.getStringExtra("MATERIAL_ID") ?: run {
                GabAIUtils.showSnackbar(this, "Missing material reference")
                finish()
                return
            }
        }

        materialTitle = intent.getStringExtra("MATERIAL_TITLE") ?: if (isWeeklyAssessment) "Weekly Assessment" else "Reading Quiz"
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: if (isWeeklyAssessment) weeklyClassName.ifEmpty { "Subject" } else "Reading Material"
        assignedSectionsFromIntent = intent.getStringArrayListExtra("ASSIGNED_SECTIONS") ?: arrayListOf()

        setupHeader()
        setupAdminRemarksObserver()
        setupTabSwitcher()
        setupFilterChips()
        loadMaterialAndMetrics()
        hasInitializedOnce = true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            adminRemarksListener?.remove()
            adminRemarksListener = null
        } catch (_: Exception) {}
    }

    private fun setupAdminRemarksObserver() {
        val targetType = if (isWeeklyAssessment) {
            AdminRemarksRepository.TARGET_ASSESSMENT
        } else {
            AdminRemarksRepository.TARGET_MATERIAL
        }
        val targetId = if (isWeeklyAssessment) assessmentId else materialId
        if (targetId.isBlank()) return

        val cardRemarks = findViewById<View>(R.id.card_overview_admin_remarks) ?: return
        val tvTitle = findViewById<TextView>(R.id.tv_overview_admin_remarks_title)
        val tvPreview = findViewById<TextView>(R.id.tv_overview_admin_remarks_preview)
        val tvCta = findViewById<TextView>(R.id.tv_overview_admin_remarks_cta)

        adminRemarksListener?.remove()
        adminRemarksListener = AdminRemarksRepository.observeRemarks(targetType, targetId, materialTitle) { remarks ->
            if (isFinishing || isDestroyed) return@observeRemarks
            if (remarks.isEmpty()) {
                cardRemarks.visibility = View.GONE
                return@observeRemarks
            }
            cardRemarks.visibility = View.VISIBLE
            val adminCount = remarks.count { it.isAdminAuthor }
            val latest = remarks.first()
            tvTitle?.text = if (adminCount > 0) {
                val noun = if (adminCount == 1) "Admin Remark" else "Admin Remarks"
                "School Admin Oversight • $adminCount $noun"
            } else {
                "Oversight Discussion • ${remarks.size} Note${if (remarks.size == 1) "" else "s"}"
            }
            tvPreview?.text = "${latest.authorName}: ${latest.text}"
            tvCta?.text = "View (${remarks.size})"

            GabAIUtils.addSpringPressEffect(cardRemarks) {
                GabAIUtils.performHaptic(cardRemarks)
                GabAIDialogs.showAdminRemarksDialog(
                    this@LibraryQuizOverviewActivity,
                    targetType,
                    targetId,
                    materialTitle
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasInitializedOnce) {
            loadMaterialAndMetrics()
        }
    }

    private fun setupHeader() {
        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_quiz_overview)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_quiz_overview)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.header_inner_quiz_overview)
        val scrollContent = findViewById<View>(R.id.scroll_quiz_overview)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        findViewById<TextView>(R.id.tv_header_quiz_title).text = materialTitle
        val headerTag = if (isWeeklyAssessment) "$subjectName • WEEKLY ASSESSMENT" else "$subjectName • QUIZ OVERVIEW"
        findViewById<TextView>(R.id.tv_header_subject_tag).text = headerTag.uppercase()

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        val btnEditQuiz = findViewById<View>(R.id.btn_edit_quiz)
        btnEditQuiz?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) {
                openQuizEditor()
            }
        }

        val btnExport = findViewById<ImageButton>(R.id.btn_export_csv)
        btnExport?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) {
                exportSubmissionsCsv()
            }
        }
    }

    private fun openQuizEditor() {
        if (isWeeklyAssessment) {
            val intent = Intent(this, QuizEditorActivity::class.java).apply {
                putExtra("IS_WEEKLY_ASSESSMENT", true)
                putExtra("ASSESSMENT_ID", assessmentId)
                putExtra("TARGET_CLASS_ID", weeklyClassId)
                putExtra("TARGET_CLASS_NAME", weeklyClassName)
                putExtra("SECTION_NAME", weeklySectionName)
                putExtra("GRADE", weeklyGrade)
                putExtra("SCHOOL_ID", weeklySchoolId)
                putExtra("SUBJECT_NAME", subjectName)
                putExtra("ASSESSMENT_TITLE", materialTitle)
                putExtra("TARGET_ITEMS", weeklyTargetItems)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, QuizEditorActivity::class.java).apply {
                putExtra("MATERIAL_ID", materialId)
                putExtra("TARGET_ITEMS", parsedQuestions.size.coerceAtLeast(5))
            }
            startActivity(intent)
        }
    }

    private fun setupTabSwitcher() {
        val btnSubmissions = findViewById<TextView>(R.id.btn_tab_submissions)
        val btnQuestions = findViewById<TextView>(R.id.btn_tab_questions)
        val viewSubmissions = findViewById<View>(R.id.layout_submissions_view)
        val viewQuestions = findViewById<View>(R.id.layout_questions_view)

        fun updateTabStyles() {
            if (currentTab == "SUBMISSIONS") {
                btnSubmissions.setBackgroundResource(R.drawable.bg_pill_white)
                btnSubmissions.setTextColor(Color.parseColor("#5341CD"))
                btnQuestions.setBackgroundColor(Color.TRANSPARENT)
                btnQuestions.setTextColor(Color.parseColor("#64748B"))
                viewSubmissions.visibility = View.VISIBLE
                viewQuestions.visibility = View.GONE
            } else {
                btnQuestions.setBackgroundResource(R.drawable.bg_pill_white)
                btnQuestions.setTextColor(Color.parseColor("#5341CD"))
                btnSubmissions.setBackgroundColor(Color.TRANSPARENT)
                btnSubmissions.setTextColor(Color.parseColor("#64748B"))
                viewSubmissions.visibility = View.GONE
                viewQuestions.visibility = View.VISIBLE
            }
        }

        GabAIUtils.addSpringPressEffect(btnSubmissions) {
            GabAIUtils.performHaptic(btnSubmissions)
            currentTab = "SUBMISSIONS"
            updateTabStyles()
        }

        GabAIUtils.addSpringPressEffect(btnQuestions) {
            GabAIUtils.performHaptic(btnQuestions)
            currentTab = "QUESTIONS"
            updateTabStyles()
        }
    }

    private fun setupFilterChips() {
        val chipAll = findViewById<TextView>(R.id.chip_filter_all)
        val chipCompleted = findViewById<TextView>(R.id.chip_filter_completed)
        val chipPending = findViewById<TextView>(R.id.chip_filter_pending)

        val chips = listOf(
            Triple("ALL", chipAll, R.drawable.bg_bento_purple),
            Triple("COMPLETED", chipCompleted, R.drawable.bg_pill_white),
            Triple("PENDING", chipPending, R.drawable.bg_pill_white)
        )

        fun updateChipsUi() {
            chips.forEach { (filterKey, chipView, _) ->
                if (filterKey == currentFilter) {
                    chipView.setBackgroundResource(R.drawable.bg_bento_purple)
                    chipView.setTextColor(Color.parseColor("#5341CD"))
                } else {
                    chipView.setBackgroundResource(R.drawable.bg_pill_white)
                    chipView.setTextColor(Color.parseColor("#64748B"))
                }
            }
            renderSubmissionsList()
        }

        chips.forEach { (filterKey, chipView, _) ->
            GabAIUtils.addSpringPressEffect(chipView) {
                GabAIUtils.performHaptic(chipView)
                currentFilter = filterKey
                updateChipsUi()
            }
        }
    }

    private fun loadMaterialAndMetrics() {
        GabAIUtils.showGlobalLoading(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentUid = auth.currentUser?.uid ?: ""
                if (currentUid.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                        finish()
                    }
                    return@launch
                }
                val teacherDoc = db.collection("users").document(currentUid).get().await()
                if (teacherDoc.getString("role") != "teacher") {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                        GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Access restricted to educators.")
                        finish()
                    }
                    return@launch
                }

                var quizJsonStr = "[]"
                var assignedSections: List<String> = assignedSectionsFromIntent

                if (isWeeklyAssessment) {
                    val assessDoc = db.collection("weekly_assessments").document(assessmentId).get().await()
                    if (!assessDoc.exists()) {
                        withContext(Dispatchers.Main) {
                            GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                            GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Weekly Assessment not found.")
                            finish()
                        }
                        return@launch
                    }
                    materialTitle = assessDoc.getString("title") ?: materialTitle
                    subjectName = assessDoc.getString("subjectName") ?: subjectName
                    weeklyPassage = assessDoc.getString("passage") ?: ""
                    weeklyTargetItems = assessDoc.getLong("targetItems")?.toInt() ?: assessDoc.getLong("questionCount")?.toInt() ?: 10
                    val docClassId = assessDoc.getString("classId") ?: ""
                    if (docClassId.isNotEmpty() && weeklyClassId.isEmpty()) {
                        weeklyClassId = docClassId
                    }
                    if (weeklyClassId.isNotEmpty() && selectedClassId == "ALL") {
                        selectedClassId = weeklyClassId
                    }
                    weeklyClassName = assessDoc.getString("className") ?: weeklyClassName
                    weeklyGrade = assessDoc.getString("grade") ?: weeklyGrade
                    weeklySchoolId = assessDoc.getString("schoolId") ?: weeklySchoolId
                    quizJsonStr = assessDoc.getString("quiz_pool_json") ?: "[]"
                    assignedSections = if (weeklyClassId.isNotEmpty()) listOf(weeklyClassId) else emptyList()
                } else {
                    // 1. Fetch Material Document & Quiz Pool JSON
                    val matDoc = db.collection("library_materials").document(materialId).get().await()
                    if (!matDoc.exists()) {
                        withContext(Dispatchers.Main) {
                            GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                            GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Material not found.")
                            finish()
                        }
                        return@launch
                    }

                    val title = matDoc.getString("title") ?: materialTitle
                    materialTitle = title
                    quizJsonStr = matDoc.getString("quiz_pool_json") ?: "[]"
                    assignedSections = matDoc.get("assignedSections") as? List<String> ?: assignedSectionsFromIntent
                }

                // Parse Questions
                parsedQuestions.clear()
                try {
                    val arr = JSONArray(quizJsonStr)
                    for (i in 0 until arr.length()) {
                        val qObj = arr.getJSONObject(i)
                        val qText = qObj.optString("q", "")
                        val optArr = qObj.optJSONArray("options")
                        val opts = mutableListOf<String>()
                        if (optArr != null) {
                            for (j in 0 until optArr.length()) opts.add(optArr.getString(j))
                        }
                        val correctIdx = qObj.optInt("ans", 0)
                        val exp = qObj.optString("explanation", "")
                        if (qText.isNotBlank()) {
                            parsedQuestions.add(QuestionModel(i, qText, opts, correctIdx, exp))
                        }
                    }
                } catch (_: Exception) {}

                // 2. Fetch Classes Taught by Educator
                val classesSnaps = db.collection("classes")
                    .whereArrayContains("teacherIds", currentUid)
                    .get()
                    .await()

                allTeacherClasses.clear()
                for (doc in classesSnaps.documents) {
                    val cName = doc.getString("className") ?: "Class"
                    val sec = doc.getString("section") ?: doc.getString("sectionName") ?: ""
                    val grd = doc.getString("grade") ?: ""
                    val joined = doc.get("joinedStudents") as? List<String> ?: listOf()
                    allTeacherClasses.add(ClassModel(doc.id, cName, sec, grd, joined))
                }

                // If teacher has classes where they are single teacherId (fallback)
                if (allTeacherClasses.isEmpty() && currentUid.isNotEmpty()) {
                    val singleTeacherSnaps = db.collection("classes")
                        .whereEqualTo("teacherId", currentUid)
                        .get()
                        .await()
                    for (doc in singleTeacherSnaps.documents) {
                        val cName = doc.getString("className") ?: "Class"
                        val sec = doc.getString("section") ?: doc.getString("sectionName") ?: ""
                        val grd = doc.getString("grade") ?: ""
                        val joined = doc.get("joinedStudents") as? List<String> ?: listOf()
                        allTeacherClasses.add(ClassModel(doc.id, cName, sec, grd, joined))
                    }
                }

                // Ensure weeklyClassId is present in allTeacherClasses
                if (isWeeklyAssessment && weeklyClassId.isNotEmpty() && allTeacherClasses.none { it.id == weeklyClassId }) {
                    val classDoc = db.collection("classes").document(weeklyClassId).get().await()
                    if (classDoc.exists()) {
                        val cName = classDoc.getString("className") ?: weeklyClassName.ifEmpty { "Class" }
                        val sec = classDoc.getString("section") ?: classDoc.getString("sectionName") ?: weeklySectionName
                        val grd = classDoc.getString("grade") ?: weeklyGrade
                        val joined = classDoc.get("joinedStudents") as? List<String> ?: listOf()
                        allTeacherClasses.add(0, ClassModel(classDoc.id, cName, sec, grd, joined))
                    }
                }

                withContext(Dispatchers.Main) {
                    findViewById<TextView>(R.id.tv_header_quiz_title).text = materialTitle
                    val headerTag = if (isWeeklyAssessment) "$subjectName • WEEKLY ASSESSMENT" else "$subjectName • QUIZ OVERVIEW"
                    findViewById<TextView>(R.id.tv_header_subject_tag).text = headerTag.uppercase()
                    renderSectionPills(assignedSections)
                    loadSubmissionsForCurrentCohort()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                    GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Error loading quiz data: ${e.message}")
                }
            }
        }
    }

    private fun renderSectionPills(assignedSections: List<String>) {
        val container = findViewById<LinearLayout>(R.id.container_section_pills) ?: return
        container.removeAllViews()

        val density = resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        // 1. "All Cohorts" Pill
        val pillAll = createSectionPill(
            label = "🌟 All Assigned (${allTeacherClasses.size} Sections)",
            isSelected = (selectedClassId == "ALL"),
            density = density,
            fontJakarta = fontJakarta
        ) {
            if (selectedClassId != "ALL") {
                selectedClassId = "ALL"
                renderSectionPills(assignedSections)
                loadSubmissionsForCurrentCohort()
            }
        }
        container.addView(pillAll)

        // 2. Individual Section Pills
        for (c in allTeacherClasses) {
            val displayName = if (c.sectionName.isNotBlank()) "${c.className} • ${c.sectionName}" else c.className
            val isAssigned = assignedSections.isEmpty() || assignedSections.contains(c.sectionName) || assignedSections.contains(c.id)
            val badgePrefix = if (isAssigned) "📌 " else ""
            val pill = createSectionPill(
                label = "$badgePrefix$displayName",
                isSelected = (selectedClassId == c.id),
                density = density,
                fontJakarta = fontJakarta
            ) {
                if (selectedClassId != c.id) {
                    selectedClassId = c.id
                    renderSectionPills(assignedSections)
                    loadSubmissionsForCurrentCohort()
                }
            }
            container.addView(pill)
        }
    }

    private fun createSectionPill(
        label: String,
        isSelected: Boolean,
        density: Float,
        fontJakarta: Typeface?,
        onClick: () -> Unit
    ): View {
        val pill = TextView(this).apply {
            text = label
            textSize = 12f
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
            val (bgRes, txtColor) = if (isSelected) {
                Pair(R.drawable.bg_bento_purple, "#5341CD")
            } else {
                Pair(R.drawable.bg_pill_white, "#64748B")
            }
            setBackgroundResource(bgRes)
            setTextColor(Color.parseColor(txtColor))
            gravity = Gravity.CENTER
            setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, (8 * density).toInt(), 0) }
        }
        GabAIUtils.addSpringPressEffect(pill) {
            GabAIUtils.performHaptic(pill)
            onClick()
        }
        return pill
    }

    private fun loadSubmissionsForCurrentCohort() {
        GabAIUtils.showGlobalLoading(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Determine target student IDs
                val targetStudentIds = mutableSetOf<String>()
                val targetClasses = if (selectedClassId == "ALL") {
                    allTeacherClasses
                } else {
                    allTeacherClasses.filter { it.id == selectedClassId }
                }

                for (c in targetClasses) {
                    targetStudentIds.addAll(c.joinedStudents)
                }

                // If advisory sections exist, also include user docs matching section
                val currentUid = auth.currentUser?.uid ?: ""
                val schoolDoc = db.collection("users").document(currentUid).get().await()
                val schoolId = (schoolDoc.getString("schoolId") ?: weeklySchoolId).trim()

                if (schoolId.isNotBlank() && targetClasses.isNotEmpty()) {
                    for (c in targetClasses) {
                        if (c.sectionName.isNotBlank()) {
                            val sectionStudents = db.collection("users")
                                .whereEqualTo("role", "student")
                                .whereEqualTo("schoolId", schoolId)
                                .whereEqualTo("section", c.sectionName)
                                .get()
                                .await()
                            for (doc in sectionStudents.documents) {
                                targetStudentIds.add(doc.id)
                            }
                        }
                    }
                }

                // Pre-fetch weekly assessment submissions if in weekly assessment mode
                val weeklySubmissionsMap = mutableMapOf<String, DocumentSnapshot>()
                if (isWeeklyAssessment && assessmentId.isNotEmpty()) {
                    val subSnaps = db.collection("assessment_submissions")
                        .whereEqualTo("assessmentId", assessmentId)
                        .get()
                        .await()
                    for (sDoc in subSnaps.documents) {
                        val sUid = sDoc.getString("studentUid") ?: ""
                        if (sUid.isNotEmpty() && sUid != currentUid) {
                            weeklySubmissionsMap[sUid] = sDoc
                            if (selectedClassId == "ALL" || sDoc.getString("classId") == selectedClassId) {
                                targetStudentIds.add(sUid)
                            }
                        }
                    }
                }

                // Never include the current teacher UID in student cohort
                targetStudentIds.remove(currentUid)

                if (targetStudentIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                        allStudentSubmissions.clear()
                        updateBenchmarks(0, 0, parsedQuestions.size, 0, 0)
                        renderSubmissionsList()
                        renderQuestionsAnalysis()
                    }
                    return@launch
                }

                // Parallel query for students' user docs and quiz history
                val submissionResults = targetStudentIds.chunked(25).map { chunk ->
                    async {
                        val userDocs = db.collection("users")
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                            .get()
                            .await()
                            .documents
                            .filter { (it.getString("role") ?: "student") == "student" }

                        userDocs.map { uDoc ->
                            val sId = uDoc.id
                            val fName = uDoc.getString("firstName") ?: ""
                            val lName = uDoc.getString("lastName") ?: ""
                            val username = uDoc.getString("username") ?: ""
                            val sName = "$fName $lName".trim().ifEmpty { username.ifEmpty { "Student" } }
                            val photoUrl = uDoc.getString("photoUrl")
                            val sSection = uDoc.getString("section") ?: ""

                            if (isWeeklyAssessment) {
                                val directSubDoc = weeklySubmissionsMap[sId]
                                if (directSubDoc != null) {
                                    val score = directSubDoc.getLong("score")?.toInt() ?: 0
                                    val totalQ = directSubDoc.getLong("totalQuestions")?.toInt() ?: parsedQuestions.size.coerceAtLeast(1)
                                    val pct = directSubDoc.getLong("percentage")?.toInt() ?: if (totalQ > 0) ((score.toDouble() / totalQ) * 100).toInt() else 0
                                    val isPassed = pct >= 50
                                    val time = directSubDoc.getLong("submittedAt") ?: 0L
                                    val xp = if (isPassed) 50 else 0
                                    val remark = directSubDoc.getString("teacherRemark") ?: ""
                                    @Suppress("UNCHECKED_CAST")
                                    val items = directSubDoc.get("answers") as? List<Map<String, Any>> ?: emptyList()

                                    StudentSubmissionState(
                                        studentId = sId,
                                        studentName = sName,
                                        photoUrl = photoUrl,
                                        section = sSection,
                                        hasSubmitted = true,
                                        score = score,
                                        totalQuestions = totalQ,
                                        percentage = pct,
                                        isPassed = isPassed,
                                        attempts = 1,
                                        timestamp = time,
                                        xpEarned = xp,
                                        items = items,
                                        submissionDocId = directSubDoc.id,
                                        teacherRemark = remark
                                    )
                                } else {
                                    StudentSubmissionState(
                                        studentId = sId,
                                        studentName = sName,
                                        photoUrl = photoUrl,
                                        section = sSection,
                                        hasSubmitted = false
                                    )
                                }
                            } else {
                                // Query material quiz history for this student
                                val quizSnaps = db.collection("users").document(sId)
                                    .collection("quiz_history")
                                    .whereEqualTo("materialId", materialId)
                                    .get()
                                    .await()

                                val passedDoc = quizSnaps.documents.maxByOrNull { it.getLong("timestamp") ?: 0L }

                                if (passedDoc != null) {
                                    val score = passedDoc.getLong("finalScore")?.toInt() ?: 0
                                    val totalQ = passedDoc.getLong("totalQuestions")?.toInt() ?: parsedQuestions.size.coerceAtLeast(1)
                                    val pct = if (totalQ > 0) ((score.toDouble() / totalQ) * 100).toInt() else 0
                                    val isPassed = passedDoc.getBoolean("isPassed") ?: (pct >= 70)
                                    val attempts = passedDoc.getLong("totalAttempts")?.toInt() ?: 1
                                    val time = passedDoc.getLong("timestamp") ?: 0L
                                    val xp = passedDoc.getLong("xpEarned")?.toInt() ?: 0
                                    @Suppress("UNCHECKED_CAST")
                                    val items = passedDoc.get("items") as? List<Map<String, Any>> ?: emptyList()

                                    StudentSubmissionState(
                                        studentId = sId,
                                        studentName = sName,
                                        photoUrl = photoUrl,
                                        section = sSection,
                                        hasSubmitted = true,
                                        score = score,
                                        totalQuestions = totalQ,
                                        percentage = pct,
                                        isPassed = isPassed,
                                        attempts = attempts,
                                        timestamp = time,
                                        xpEarned = xp,
                                        items = items
                                    )
                                } else {
                                    StudentSubmissionState(
                                        studentId = sId,
                                        studentName = sName,
                                        photoUrl = photoUrl,
                                        section = sSection,
                                        hasSubmitted = false
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll().flatten()

                allStudentSubmissions = submissionResults.toMutableList()

                // Calculate Benchmarks
                val totalStudents = allStudentSubmissions.size
                val submittedList = allStudentSubmissions.filter { it.hasSubmitted }
                val completedCount = submittedList.size
                val passCount = submittedList.count { it.isPassed }
                val passRate = if (completedCount > 0) (passCount * 100) / completedCount else 0
                val avgScoreSum = submittedList.sumOf { it.score }
                val avgScoreTotalSum = submittedList.sumOf { it.totalQuestions }
                val avgScoreVal = if (completedCount > 0) avgScoreSum.toDouble() / completedCount else 0.0
                val avgTotalVal = if (completedCount > 0) avgScoreTotalSum.toDouble() / completedCount else (parsedQuestions.size.toDouble())

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                    updateBenchmarks(passRate, avgScoreVal.toInt(), avgTotalVal.toInt(), completedCount, totalStudents)
                    renderSubmissionsList()
                    renderQuestionsAnalysis()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@LibraryQuizOverviewActivity)
                    GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Error evaluating submissions: ${e.message}")
                }
            }
        }
    }

    private fun updateBenchmarks(passRate: Int, avgScore: Int, avgTotal: Int, completed: Int, total: Int = 0) {
        val tvPassRate = findViewById<TextView>(R.id.tv_stat_pass_rate)
        val tvAvgScore = findViewById<TextView>(R.id.tv_stat_avg_score)
        val tvCompleted = findViewById<TextView>(R.id.tv_stat_completion_count)

        tvPassRate.text = "$passRate%"
        tvAvgScore.text = "$avgScore / $avgTotal"
        tvCompleted.text = "$completed / $total"

        val chipAll = findViewById<TextView>(R.id.chip_filter_all)
        val chipCompleted = findViewById<TextView>(R.id.chip_filter_completed)
        val chipPending = findViewById<TextView>(R.id.chip_filter_pending)

        val pendingCount = total - completed
        chipAll.text = "All Scholars ($total)"
        chipCompleted.text = "Completed ($completed)"
        chipPending.text = "Not Yet Taken ($pendingCount)"

        val btnTabSubmissions = findViewById<TextView>(R.id.btn_tab_submissions)
        btnTabSubmissions.text = "👥 Submissions ($completed)"
    }

    private fun renderSubmissionsList() {
        val container = findViewById<LinearLayout>(R.id.container_submissions) ?: return
        val emptyCard = findViewById<View>(R.id.card_empty_submissions)
        container.removeAllViews()

        val filtered = when (currentFilter) {
            "COMPLETED" -> allStudentSubmissions.filter { it.hasSubmitted }
            "PENDING" -> allStudentSubmissions.filter { !it.hasSubmitted }
            else -> allStudentSubmissions
        }.sortedWith(compareByDescending<StudentSubmissionState> { it.hasSubmitted }
            .thenByDescending { it.score }
            .thenBy { it.studentName })

        if (filtered.isEmpty()) {
            emptyCard?.visibility = View.VISIBLE
            return
        }
        emptyCard?.visibility = View.GONE

        val density = resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        val dateFormat = SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault())
        val rowViews = mutableListOf<View>()

        for (sub in filtered) {
            val card = MaterialCardView(this).apply {
                radius = 18 * density
                cardElevation = 0f
                strokeWidth = (1.2 * density).toInt()
                setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (10 * density).toInt()) }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (14 * density).toInt()
                setPadding(p, p, p, p)
            }

            // Avatar Monogram / Photo
            val avatarFrame = FrameLayout(this).apply {
                val s = (44 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply {
                    marginEnd = (12 * density).toInt()
                }
                setBackgroundResource(R.drawable.bg_avatar_squircle_border)
                clipToOutline = true
            }

            val tvInitial = TextView(this).apply {
                text = sub.studentName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
                textSize = 17f
                setTextColor(Color.parseColor("#5341CD"))
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            val ivPhoto = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            avatarFrame.addView(tvInitial)
            avatarFrame.addView(ivPhoto)
            GabAIUtils.loadAvatarImage(sub.photoUrl, ivPhoto, tvInitial)

            // Middle Column
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = sub.studentName
                textSize = 14.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
            }

            val subtitleText = if (sub.hasSubmitted) {
                val timeStr = if (sub.timestamp > 0) dateFormat.format(Date(sub.timestamp)) else "Submitted"
                "$timeStr • +${sub.xpEarned} XP"
            } else {
                if (sub.section.isNotBlank()) "Section: ${sub.section} • Pending Assessment" else "Pending Assessment Attempt"
            }

            val tvSub = TextView(this).apply {
                text = subtitleText
                textSize = 11.5f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, (2 * density).toInt(), 0, 0)
            }

            textCol.addView(tvName)
            textCol.addView(tvSub)

            if (sub.teacherRemark.isNotBlank()) {
                val tvRemark = TextView(this).apply {
                    text = "💬 Commendation: ${sub.teacherRemark}"
                    textSize = 11.5f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#4F46E5"))
                    setPadding(0, (4 * density).toInt(), 0, 0)
                }
                textCol.addView(tvRemark)
            }

            // Right Status Badge / Action
            val rightPill = TextView(this).apply {
                if (sub.hasSubmitted) {
                    text = "${sub.score} / ${sub.totalQuestions} (${sub.percentage}%)"
                    textSize = 12f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    val (bColor, tColor) = if (sub.isPassed) Pair("#ECFDF5", "#059669") else Pair("#FFF1F2", "#E11D48")
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * density
                        setColor(Color.parseColor(bColor))
                    }
                    setTextColor(Color.parseColor(tColor))
                } else {
                    text = "⏳ Not Taken"
                    textSize = 11f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * density
                        setColor(Color.parseColor("#F1F5F9"))
                    }
                    setTextColor(Color.parseColor("#64748B"))
                }
                setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
            }

            row.addView(avatarFrame)
            row.addView(textCol)
            row.addView(rightPill)
            card.addView(row)

            // Click Interaction
            if (sub.hasSubmitted) {
                GabAIUtils.addSpringPressEffect(card) {
                    GabAIUtils.performHaptic(card)
                    val dateFormatted = if (sub.timestamp > 0) dateFormat.format(Date(sub.timestamp)) else "Recent"
                    val canCommend = isWeeklyAssessment && sub.submissionDocId.isNotEmpty()
                    val commendBtnLabel = if (canCommend) {
                        if (sub.teacherRemark.isNotBlank()) "💬 Edit Remark" else "💬 Add Remark"
                    } else null

                    QuizReviewDialogHelper.showReviewDialog(
                        context = this@LibraryQuizOverviewActivity,
                        title = "${sub.studentName}'s Submission",
                        subtitle = "$materialTitle • $dateFormatted • ${sub.score}/${sub.totalQuestions} (${sub.percentage}%)",
                        score = sub.score,
                        totalQuestions = sub.totalQuestions,
                        xpEarned = sub.xpEarned,
                        items = sub.items,
                        badgeIcon = if (sub.isPassed) "🟢" else "🔴",
                        confirmText = commendBtnLabel,
                        onConfirm = if (canCommend) {
                            { showAddTeacherRemarkDialog(sub.submissionDocId, sub.studentName, sub.teacherRemark) }
                        } else null
                    )
                }
            } else {
                GabAIUtils.addSpringPressEffect(card) {
                    GabAIUtils.performHaptic(card)
                    GabAIDialogs.showConfirmDialog(
                        context = this@LibraryQuizOverviewActivity,
                        title = "Nudge Learner",
                        message = "Send a reminder notification to ${sub.studentName} to complete \"$materialTitle\"?",
                        confirmText = "Send Reminder 🔔",
                        cancelText = "Cancel",
                        badgeIcon = "📩"
                    ) {
                        GabAIUtils.showSnackbar(this@LibraryQuizOverviewActivity, "Reminder sent to ${sub.studentName}!")
                    }
                }
            }

            container.addView(card)
            rowViews.add(card)
        }

        GabAIUtils.animateCascade(rowViews, 25L)
    }

    private fun showAddTeacherRemarkDialog(submissionDocId: String, studentName: String, existingRemark: String) {
        val input = android.widget.EditText(this).apply {
            setText(existingRemark)
            hint = "e.g., Great mastery of reading comprehension! Keep it up."
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding(32, 24, 32, 24)
            setTextColor(Color.parseColor("#1E293B"))
            textSize = 14f
        }

        GabAIDialogs.showCustomDialog(
            context = this,
            title = "Teacher Commendation",
            subtitle = "Feedback for $studentName",
            customView = input,
            confirmText = "Save Remark",
            cancelText = "Cancel",
            badgeIcon = "💬",
            onConfirm = { dialog ->
                val newRemark = input.text.toString().trim()
                db.collection("assessment_submissions").document(submissionDocId)
                    .update("teacherRemark", newRemark)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        GabAIUtils.showSnackbar(this, "Commendation saved for $studentName! ⭐")
                        loadSubmissionsForCurrentCohort()
                    }
                    .addOnFailureListener { e ->
                        GabAIUtils.showSnackbar(this, "Failed to save remark: ${e.message}")
                    }
            }
        )
    }

    private fun renderQuestionsAnalysis() {
        val container = findViewById<LinearLayout>(R.id.container_questions_analysis) ?: return
        val emptyCard = findViewById<View>(R.id.card_empty_questions)
        container.removeAllViews()

        if (parsedQuestions.isEmpty()) {
            emptyCard?.visibility = View.VISIBLE
            return
        }
        emptyCard?.visibility = View.GONE

        val submittedList = allStudentSubmissions.filter { it.hasSubmitted }

        val density = resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        val rowViews = mutableListOf<View>()

        // Show Reading Passage Card at top of Question Analysis when available
        if (isWeeklyAssessment && weeklyPassage.isNotBlank()) {
            val passageCard = MaterialCardView(this).apply {
                radius = 18 * density
                cardElevation = 0f
                strokeWidth = (1.2 * density).toInt()
                setStrokeColor(ColorStateList.valueOf(Color.parseColor("#C7D2FE")))
                setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (14 * density).toInt()) }
            }

            val pLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * density).toInt()
                setPadding(p, p, p, p)
            }

            val pHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val wordCount = weeklyPassage.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            val tvPassageLabel = TextView(this).apply {
                text = "📖 READING PASSAGE ($wordCount WORDS)"
                textSize = 11f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#5341CD"))
                letterSpacing = 0.06f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvToggle = TextView(this).apply {
                text = "Show Passage ▼"
                textSize = 11f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#5341CD"))
                setBackgroundResource(R.drawable.bg_pill_translucent)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEF2FF"))
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            }

            val tvBody = TextView(this).apply {
                text = weeklyPassage
                textSize = 13.5f
                setTextColor(Color.parseColor("#1E293B"))
                setLineSpacing(3f * density, 1f)
                setPadding(0, (10 * density).toInt(), 0, 0)
                visibility = View.GONE
            }

            val toggleAction = View.OnClickListener {
                if (tvBody.visibility == View.VISIBLE) {
                    tvBody.visibility = View.GONE
                    tvToggle.text = "Show Passage ▼"
                } else {
                    tvBody.visibility = View.VISIBLE
                    tvToggle.text = "Hide Passage ▲"
                }
            }
            pHeader.setOnClickListener(toggleAction)
            tvToggle.setOnClickListener(toggleAction)

            pHeader.addView(tvPassageLabel)
            pHeader.addView(tvToggle)
            pLayout.addView(pHeader)
            pLayout.addView(tvBody)
            passageCard.addView(pLayout)
            container.addView(passageCard)
            rowViews.add(passageCard)
        }

        for ((idx, q) in parsedQuestions.withIndex()) {
            // Count correct answers for this question across submissions
            var answeredCount = 0
            var correctCount = 0
            val wrongChoicesMap = mutableMapOf<String, Int>()

            for (sub in submittedList) {
                val item = sub.items.find { (it["question"] as? String)?.trim() == q.question.trim() }
                    ?: sub.items.getOrNull(idx)
                if (item != null) {
                    answeredCount++
                    val isCorrect = item["isCorrect"] as? Boolean ?: false
                    if (isCorrect) {
                        correctCount++
                    } else {
                        val userChoice = (item["userAnswer"] as? String) ?: (item["selectedText"] as? String) ?: ""
                        if (userChoice.isNotBlank()) {
                            val cleanChoice = userChoice.replace("*", "").trim().removeSuffix(".")
                            wrongChoicesMap[cleanChoice] = (wrongChoicesMap[cleanChoice] ?: 0) + 1
                        }
                    }
                }
            }

            val accuracyPct = if (answeredCount > 0) ((correctCount.toDouble() / answeredCount) * 100).toInt() else 0
            val (statusText, statusBg, statusColor) = when {
                answeredCount == 0 -> Triple("📋 Answer Key", "#EEF2FF", "#5341CD")
                accuracyPct >= 80 -> Triple("🟢 Well Mastered ($accuracyPct%)", "#ECFDF5", "#059669")
                accuracyPct >= 60 -> Triple("🟡 Review Needed ($accuracyPct%)", "#FEF3C7", "#D97706")
                else -> Triple("🔴 Common Pitfall ($accuracyPct%)", "#FFF1F2", "#E11D48")
            }

            val card = MaterialCardView(this).apply {
                radius = 18 * density
                cardElevation = 0f
                strokeWidth = (1.2 * density).toInt()
                setStrokeColor(ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (12 * density).toInt()) }
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * density).toInt()
                setPadding(p, p, p, p)
            }

            // Top Row
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvQNum = TextView(this).apply {
                text = "QUESTION ${idx + 1}"
                textSize = 10.5f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#64748B"))
                letterSpacing = 0.06f
            }

            val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }

            val tvAccuracyPill = TextView(this).apply {
                text = statusText
                textSize = 11f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(Color.parseColor(statusBg))
                }
                setTextColor(Color.parseColor(statusColor))
                setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            }

            topRow.addView(tvQNum)
            topRow.addView(spacer)
            topRow.addView(tvAccuracyPill)
            layout.addView(topRow)

            // Question Prompt
            val tvPrompt = TextView(this).apply {
                text = q.question
                textSize = 14f
                setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                setLineSpacing(2.5f * density, 1f)
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            layout.addView(tvPrompt)

            // Correct Answer Tag
            val correctChoice = q.options.getOrNull(q.correctIndex) ?: ""
            if (correctChoice.isNotBlank()) {
                val tvCorrect = TextView(this).apply {
                    text = "✔ Key: $correctChoice"
                    textSize = 12f
                    setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#059669"))
                }
                layout.addView(tvCorrect)
            }

            if (q.explanation.isNotBlank()) {
                val tvExp = TextView(this).apply {
                    text = "💡 ${q.explanation}"
                    textSize = 11.5f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, (4 * density).toInt(), 0, 0)
                }
                layout.addView(tvExp)
            }

            // Common Pitfall Alert
            if (wrongChoicesMap.isNotEmpty()) {
                val mostFrequentWrong = wrongChoicesMap.maxByOrNull { it.value }
                if (mostFrequentWrong != null && mostFrequentWrong.value >= 2) {
                    val pitfallCard = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 10 * density
                            setColor(Color.parseColor("#FFF1F2"))
                        }
                        setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, (8 * density).toInt(), 0, 0)
                        }
                    }

                    val tvPitfall = TextView(this).apply {
                        text = "⚠️ Frequent Misconception: ${mostFrequentWrong.value} scholars picked \"${mostFrequentWrong.key}\""
                        textSize = 11.5f
                        setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#BE123C"))
                    }
                    pitfallCard.addView(tvPitfall)
                    layout.addView(pitfallCard)
                }
            }

            card.addView(layout)
            container.addView(card)
            rowViews.add(card)
        }

        GabAIUtils.animateCascade(rowViews, 25L)
    }

    private fun Float.spText(density: Float): Float = this

    private fun exportSubmissionsCsv() {
        if (allStudentSubmissions.isEmpty()) {
            GabAIUtils.showSnackbar(this, "No submission data to export.")
            return
        }

        try {
            val fileName = "Quiz_${materialTitle.replace(Regex("[^a-zA-Z0-9]"), "_")}_Report.csv"
            val file = File(getExternalFilesDir(null), fileName)
            val fos = FileOutputStream(file)

            val csvContent = buildString {
                appendLine("Student Name,Section,Quiz Status,Score,Total Questions,Percentage,Attempts,Submitted Date")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                for (s in allStudentSubmissions) {
                    val statusStr = if (s.hasSubmitted) (if (s.isPassed) "PASSED" else "RETAKE") else "NOT SUBMITTED"
                    val dateStr = if (s.timestamp > 0) sdf.format(Date(s.timestamp)) else "N/A"
                    appendLine("\"${s.studentName}\",\"${s.section}\",\"$statusStr\",${s.score},${s.totalQuestions},${s.percentage}%,${s.attempts},\"$dateStr\"")
                }
            }

            fos.write(csvContent.toByteArray())
            fos.close()

            val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "$materialTitle - Quiz Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Quiz Submissions CSV"))
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(this, "Failed to export CSV: ${e.message}")
        }
    }
}
