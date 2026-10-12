package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class TeacherPerformanceActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var sectionName: String
    private lateinit var schoolId: String
    private lateinit var grade: String

    private val db = FirebaseFirestore.getInstance()
    private var allCompiledStats: List<StudentStats> = listOf()

    data class StudentStats(
        val uid: String,
        val name: String,
        val level: Int,
        var streak: Int = 0,
        var totalQuizzes: Int = 0,
        var averageScore: Int = 0,
        var wordsMastered: Int = 0,
        var masteryTier: String = "Needs Support",
        var isAtRisk: Boolean = false,
        val photoUrl: String? = null,
        var totalXp: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_teacher_performance)

        classId = intent.getStringExtra("CLASS_ID") ?: return finish()
        className = intent.getStringExtra("CLASS_NAME") ?: "Class Performance"
        sectionName = intent.getStringExtra("SECTION_NAME") ?: ""
        schoolId = intent.getStringExtra("SCHOOL_ID") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""

        findViewById<TextView>(R.id.tv_class_name).text = className

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_performance)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_performance)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.perf_header)
        val scrollContent = findViewById<View>(R.id.scroll_teacher_performance)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        val btnExport = findViewById<View>(R.id.btn_export_roster)
        if (btnExport != null) {
            GabAIUtils.addSpringPressEffect(btnExport) {
                exportRosterToCsv(allCompiledStats, "roster")
            }
        }

        loadPerformanceData()
    }

    private fun loadPerformanceData() {
        val container = findViewById<LinearLayout>(R.id.student_list_container)
        GabAIUtils.showGlobalLoading(this)

        val fontJakarta = try {
            ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val classDoc = db.collection("classes").document(classId).get().await()
                val ownerTeacherId = classDoc.getString("teacherId") ?: ""
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val isAdviser = (classDoc.getBoolean("isAdviser") == true && ownerTeacherId == currentUid)
                val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

                val studentSnaps = db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .get()
                    .await()

                val targetStudents = if (isAdviser) {
                    studentSnaps.documents
                } else {
                    studentSnaps.documents.filter { joinedStudents.contains(it.id) }
                }

                if (targetStudents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)
                        findViewById<View>(R.id.card_search_perf_students)?.visibility = View.GONE
                        container.addView(TextView(this@TeacherPerformanceActivity).apply {
                            text = "No students enrolled in this section yet."
                            textSize = 15f
                            setTextColor(Color.parseColor("#64748B"))
                            setPadding(0, 40, 0, 0)
                        })
                    }
                    return@launch
                }

                val compiledStats = mutableListOf<StudentStats>()
                var classTotalScore = 0
                var classTotalAttempts = 0
                var classTotalStreak = 0
                var classTotalQuizzes = 0
                var classTotalWords = 0

                for (userDoc in targetStudents) {
                    val studentId = userDoc.id
                    val fName = userDoc.getString("firstName") ?: ""
                    val lName = userDoc.getString("lastName") ?: ""
                    val username = userDoc.getString("username") ?: ""
                    val rawName = "$fName $lName".trim().ifEmpty { username.ifEmpty { "Student" } }
                    val displayName = if (username.isNotBlank()) "$rawName (@$username)" else rawName
                    val photoUrl = userDoc.getString("photoUrl")
                    val studentLevel = userDoc.getLong("level")?.toInt() ?: 1
                    val stats = StudentStats(studentId, displayName, studentLevel, photoUrl = photoUrl, totalXp = userDoc.getLong("current_xp")?.toInt() ?: 0)

                    val quizDocs = db.collection("users").document(studentId)
                        .collection("quiz_history").get().await()

                    var studentScore = 0
                    var studentAttempts = 0
                    stats.totalQuizzes = quizDocs.size()

                    for (quiz in quizDocs) {
                        studentScore += quiz.getLong("finalScore")?.toInt() ?: 0
                        studentAttempts += quiz.getLong("totalAttempts")?.toInt() ?: 0
                    }

                    if (studentAttempts > 0) {
                        stats.averageScore = ((studentScore.toDouble() / studentAttempts) * 100).toInt()
                    }

                    val streak = userDoc.getLong("current_streak")?.toInt() ?: 0
                    stats.streak = streak

                    // Fetch words mastered from OCR history (interval >= 4)
                    val wordDocs = try {
                        db.collection("users").document(studentId).collection("history")
                            .whereGreaterThanOrEqualTo("interval", 4).get().await()
                    } catch (_: Exception) {
                        null
                    }
                    stats.wordsMastered = wordDocs?.size() ?: 0
                    classTotalWords += stats.wordsMastered

                    // Determine Reading Mastery Tier
                    stats.masteryTier = when {
                        stats.totalQuizzes == 0 -> "Needs Support"
                        stats.averageScore >= 80 -> "Mastery"
                        stats.averageScore >= 60 -> "Proficient"
                        else -> "Needs Support"
                    }
                    stats.isAtRisk = (stats.totalQuizzes == 0 || stats.averageScore < 60)

                    classTotalScore += studentScore
                    classTotalAttempts += studentAttempts
                    classTotalStreak += streak
                    classTotalQuizzes += stats.totalQuizzes

                    compiledStats.add(stats)
                }

                val avgClassScore = if (classTotalAttempts > 0) ((classTotalScore.toDouble() / classTotalAttempts) * 100).toInt() else 0
                val avgClassStreak = if (compiledStats.isNotEmpty()) classTotalStreak / compiledStats.size else 0

                val totalStudentsCount = compiledStats.size
                var masteryCount = 0
                var proficientCount = 0
                var supportCount = 0

                for (s in compiledStats) {
                    when (s.masteryTier) {
                        "Mastery" -> masteryCount++
                        "Proficient" -> proficientCount++
                        else -> supportCount++
                    }
                }

                val masteryPct = if (totalStudentsCount > 0) (masteryCount * 100) / totalStudentsCount else 0
                val proficientPct = if (totalStudentsCount > 0) (proficientCount * 100) / totalStudentsCount else 0
                val supportPct = if (totalStudentsCount > 0) (supportCount * 100) / totalStudentsCount else 0

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)

                    // 1. Overall Benchmarks
                    findViewById<TextView>(R.id.tv_avg_score).text = "$avgClassScore%"
                    findViewById<TextView>(R.id.tv_avg_streak).text = "$avgClassStreak"
                    findViewById<TextView>(R.id.tv_total_quizzes).text = "$classTotalQuizzes"
                    findViewById<TextView>(R.id.tv_total_words_mastered)?.text = "$classTotalWords Words Mastered 📚"

                    // 2. Multi-segment Mastery Bar
                    val barAdv = findViewById<View>(R.id.bar_mastery_advanced)
                    val barProf = findViewById<View>(R.id.bar_mastery_proficient)
                    val barSupp = findViewById<View>(R.id.bar_mastery_support)

                    barAdv?.layoutParams = (barAdv?.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        weight = if (totalStudentsCount > 0) masteryCount.coerceAtLeast(0).toFloat() else 1f
                    }
                    barProf?.layoutParams = (barProf?.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        weight = if (totalStudentsCount > 0) proficientCount.coerceAtLeast(0).toFloat() else 1f
                    }
                    barSupp?.layoutParams = (barSupp?.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        weight = if (totalStudentsCount > 0) supportCount.coerceAtLeast(0).toFloat() else 1f
                    }

                    findViewById<TextView>(R.id.tv_stat_mastery)?.text = "$masteryCount ($masteryPct%)"
                    findViewById<TextView>(R.id.tv_stat_proficient)?.text = "$proficientCount ($proficientPct%)"
                    findViewById<TextView>(R.id.tv_stat_support)?.text = "$supportCount ($supportPct%)"

                    // 3. Early Support Attention Banner
                    val cardAlert = findViewById<View>(R.id.card_at_risk_alert)
                    val tvAlertText = findViewById<TextView>(R.id.tv_at_risk_alert_text)
                    val btnAlertAction = findViewById<TextView>(R.id.btn_filter_at_risk_action)

                    if (supportCount > 0) {
                        cardAlert?.visibility = View.VISIBLE
                        tvAlertText?.text = "$supportCount learner(s) scored below 60% or have not taken quizzes"
                    } else {
                        cardAlert?.visibility = View.GONE
                    }

                    compiledStats.sortByDescending { it.averageScore }
                    allCompiledStats = compiledStats

                    val btnExportAtRisk = findViewById<View>(R.id.btn_export_at_risk)
                    if (btnExportAtRisk != null) {
                        GabAIUtils.addSpringPressEffect(btnExportAtRisk) {
                            val atRiskList = allCompiledStats.filter { it.isAtRisk }
                            exportRosterToCsv(atRiskList, "support_learners")
                        }
                    }

                    val searchCard = findViewById<View>(R.id.card_search_perf_students)
                    val searchInput = findViewById<EditText>(R.id.et_search_perf_students)
                    val btnClearSearch = findViewById<ImageButton>(R.id.btn_clear_search_perf_students)
                    val noSearchCard = findViewById<View>(R.id.card_no_search_students)

                    searchCard?.visibility = View.VISIBLE
                    val studentCards = mutableListOf<Pair<StudentStats, View>>()
                    val rowViews = mutableListOf<View>()

                    container.removeAllViews()

                    for (student in compiledStats) {
                        val card = com.google.android.material.card.MaterialCardView(this@TeacherPerformanceActivity).apply {
                            radius = resources.displayMetrics.density * 18
                            strokeWidth = (resources.displayMetrics.density * 1.2f).toInt()
                            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
                            setCardBackgroundColor(Color.WHITE)
                            cardElevation = 0f
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, (resources.displayMetrics.density * 10).toInt()) }
                        }

                        val row = LinearLayout(this@TeacherPerformanceActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            val p = (resources.displayMetrics.density * 14).toInt()
                            setPadding(p, p, p, p)
                        }

                        // Squircle avatar
                        val avatar = android.widget.FrameLayout(this@TeacherPerformanceActivity).apply {
                            val size = (resources.displayMetrics.density * 44).toInt()
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                marginEnd = (resources.displayMetrics.density * 12).toInt()
                            }
                            setBackgroundResource(R.drawable.bg_avatar_squircle_border)
                            clipToOutline = true
                        }
                        val avatarMonogram = TextView(this@TeacherPerformanceActivity).apply {
                            text = student.name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "👤"
                            textSize = 17f
                            setTextColor(Color.parseColor("#5341CD"))
                            typeface = fontJakarta ?: typeface
                            setTypeface(typeface, Typeface.BOLD)
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.view.Gravity.CENTER
                            )
                        }
                        val avatarPhoto = android.widget.ImageView(this@TeacherPerformanceActivity).apply {
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            visibility = View.GONE
                        }
                        avatar.addView(avatarMonogram)
                        avatar.addView(avatarPhoto)

                        GabAIUtils.loadAvatarImage(student.photoUrl, avatarPhoto, avatarMonogram)

                        // Middle column
                        val col = LinearLayout(this@TeacherPerformanceActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        // Name + Mastery Tag row
                        val topRow = LinearLayout(this@TeacherPerformanceActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                        }

                        val nameText = TextView(this@TeacherPerformanceActivity).apply {
                            text = student.name
                            textSize = 14.5f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                        }

                        val tierBadge = TextView(this@TeacherPerformanceActivity).apply {
                            text = when (student.masteryTier) {
                                "Mastery" -> "🟢 Mastery"
                                "Proficient" -> "🟡 Proficient"
                                else -> "🔴 Support"
                            }
                            textSize = 10f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            val (bgColor, txtColor) = when (student.masteryTier) {
                                "Mastery" -> Pair("#D1FAE5", "#047857")
                                "Proficient" -> Pair("#FEF3C7", "#D97706")
                                else -> Pair("#FFE4E6", "#BE123C")
                            }
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = resources.displayMetrics.density * 10
                                setColor(Color.parseColor(bgColor))
                            }
                            setTextColor(Color.parseColor(txtColor))
                            val hp = (resources.displayMetrics.density * 7).toInt()
                            val vp = (resources.displayMetrics.density * 2).toInt()
                            setPadding(hp, vp, hp, vp)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { marginStart = (resources.displayMetrics.density * 8).toInt() }
                        }

                        topRow.addView(nameText)
                        topRow.addView(tierBadge)

                        val statsText = TextView(this@TeacherPerformanceActivity).apply {
                            text = "Lvl ${student.level} • ${student.totalQuizzes} Quizzes • ${student.wordsMastered} Words 📚 • Streak ${student.streak} 🔥"
                            textSize = 11.5f
                            setTextColor(Color.parseColor("#64748B"))
                            setPadding(0, 3, 0, 0)
                        }
                        col.addView(topRow)
                        col.addView(statsText)

                        // Right Pill: Score Accuracy
                        val scoreBadge = TextView(this@TeacherPerformanceActivity).apply {
                            text = "${student.averageScore}%"
                            textSize = 12.5f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            val scoreBg = when {
                                student.averageScore >= 80 -> R.drawable.bg_bento_mint
                                student.averageScore >= 60 -> R.drawable.bg_bento_orange
                                else -> R.drawable.bg_bento_rose
                            }
                            val scoreColor = when {
                                student.averageScore >= 80 -> Color.parseColor("#00875A")
                                student.averageScore >= 60 -> Color.parseColor("#D97706")
                                else -> Color.parseColor("#E11D48")
                            }
                            setBackgroundResource(scoreBg)
                            setTextColor(scoreColor)
                            val hp = (resources.displayMetrics.density * 10).toInt()
                            val vp = (resources.displayMetrics.density * 5).toInt()
                            setPadding(hp, vp, hp, vp)
                        }

                        row.addView(avatar)
                        row.addView(col)
                        row.addView(scoreBadge)

                        card.addView(row)

                        GabAIUtils.addSpringPressEffect(card) {
                            val sheet = StudentProfileBottomSheet.newInstance(
                                studentId = student.uid,
                                studentName = student.name,
                                grade = grade,
                                section = sectionName,
                                level = student.level,
                                xp = student.totalQuizzes * 25
                            )
                            sheet.show(supportFragmentManager, "StudentProfileSheet")
                        }

                        container.addView(card)
                        rowViews.add(card)
                        studentCards.add(Pair(student, card))
                    }

                    // 4. Interactive Filter Chips logic
                    var currentFilter = "ALL"
                    val chipAll = findViewById<TextView>(R.id.chip_filter_all)
                    val chipMastery = findViewById<TextView>(R.id.chip_filter_mastery)
                    val chipProficient = findViewById<TextView>(R.id.chip_filter_proficient)
                    val chipSupport = findViewById<TextView>(R.id.chip_filter_support)
                    val chipTop = findViewById<TextView>(R.id.chip_filter_top)
                    val chipStreaks = findViewById<TextView>(R.id.chip_filter_streaks)

                    chipAll?.text = "All ($totalStudentsCount)"
                    chipMastery?.text = "Mastery 🟢 ($masteryCount)"
                    chipProficient?.text = "Proficient 🟡 ($proficientCount)"
                    chipSupport?.text = "Needs Support ⚠️ ($supportCount)"

                    val allChips = listOf(
                        Pair("ALL", chipAll),
                        Pair("MASTERY", chipMastery),
                        Pair("PROFICIENT", chipProficient),
                        Pair("SUPPORT", chipSupport),
                        Pair("TOP", chipTop),
                        Pair("STREAKS", chipStreaks)
                    )

                    fun applyFilterAndSearch() {
                        val q = searchInput?.text?.toString()?.trim()?.lowercase() ?: ""
                        btnClearSearch?.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                        var matchCount = 0

                        allChips.forEach { (filterKey, chipView) ->
                            if (chipView != null) {
                                if (filterKey == currentFilter) {
                                    chipView.setBackgroundResource(R.drawable.bg_bento_purple)
                                    chipView.setTextColor(Color.parseColor("#5341CD"))
                                } else {
                                    chipView.setBackgroundResource(R.drawable.bg_pill_white)
                                    chipView.setTextColor(Color.parseColor("#64748B"))
                                }
                            }
                        }

                        studentCards.forEach { (st, cardView) ->
                            val textMatch = q.isEmpty() || st.name.lowercase().contains(q)
                            val filterMatch = when (currentFilter) {
                                "ALL" -> true
                                "MASTERY" -> st.masteryTier == "Mastery"
                                "PROFICIENT" -> st.masteryTier == "Proficient"
                                "SUPPORT" -> st.isAtRisk
                                "TOP" -> st.averageScore >= 85
                                "STREAKS" -> st.streak > 0
                                else -> true
                            }
                            val shouldShow = textMatch && filterMatch
                            cardView.visibility = if (shouldShow) View.VISIBLE else View.GONE
                            if (shouldShow) matchCount++
                        }

                        noSearchCard?.visibility = if (matchCount == 0 && (q.isNotEmpty() || currentFilter != "ALL")) View.VISIBLE else View.GONE
                    }

                    allChips.forEach { (filterKey, chipView) ->
                        chipView?.let { btn ->
                            GabAIUtils.addSpringPressEffect(btn) {
                                GabAIUtils.performHaptic(btn, android.view.HapticFeedbackConstants.CLOCK_TICK)
                                currentFilter = filterKey
                                applyFilterAndSearch()
                            }
                        }
                    }

                    btnAlertAction?.let { btn ->
                        GabAIUtils.addSpringPressEffect(btn) {
                            GabAIUtils.performHaptic(btn, android.view.HapticFeedbackConstants.CONFIRM)
                            currentFilter = "SUPPORT"
                            applyFilterAndSearch()
                        }
                    }

                    searchInput?.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            applyFilterAndSearch()
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })

                    btnClearSearch?.let { btn ->
                        GabAIUtils.addSpringPressEffect(btn) {
                            searchInput?.setText("")
                        }
                    }

                    applyFilterAndSearch()
                    GabAIUtils.animateCascade(rowViews, 25L)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)
                    GabAIUtils.showSnackbar(this@TeacherPerformanceActivity, "Error loading data: ${e.message}")
                }
            }
        }
    }

    private fun exportRosterToCsv(studentsToExport: List<StudentStats>, filenamePrefix: String) {
        if (studentsToExport.isEmpty()) {
            Toast.makeText(this, "No learner data available to export.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val safeSection = if (sectionName.isNotBlank()) sectionName else className
            val cleanPrefix = if (filenamePrefix == "support_learners") "Academic_Support_Radar" else "Class_Reading_Mastery"
            val fileName = "${cleanPrefix}_${safeSection.replace("\\s+".toRegex(), "_")}.csv"
            val file = File(cacheDir, fileName)

            val csvContent = buildString {
                append("\uFEFF") // UTF-8 BOM for Microsoft Excel compatibility
                appendLine("Student Name,Mastery Tier,Quiz Accuracy (%),Words Retained,Quizzes Taken,Streak (Days),Level,Total XP")
                for (s in studentsToExport) {
                    val cleanName = s.name.replace("\"", "\"\"")
                    val cleanTier = s.masteryTier.replace("\"", "\"\"")
                    appendLine("\"$cleanName\",\"$cleanTier\",${s.averageScore},${s.wordsMastered},${s.totalQuizzes},${s.streak},${s.level},${s.totalXp}")
                }
            }

            file.writeText(csvContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "GabAI Class Analytics - $className")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Class Performance CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}