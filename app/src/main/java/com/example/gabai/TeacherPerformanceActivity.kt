package com.example.gabai

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TeacherPerformanceActivity : AppCompatActivity() {

    private lateinit var classId: String
    private lateinit var className: String
    private lateinit var sectionName: String
    private lateinit var schoolId: String
    private lateinit var grade: String

    private val db = FirebaseFirestore.getInstance()

    data class StudentStats(
        val uid: String,
        val name: String,
        val level: Int,
        var streak: Int = 0,
        var totalQuizzes: Int = 0,
        var averageScore: Int = 0,
        val photoUrl: String? = null
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

                for (userDoc in targetStudents) {
                    val studentId = userDoc.id
                    val fName = userDoc.getString("firstName") ?: ""
                    val lName = userDoc.getString("lastName") ?: ""
                    val photoUrl = userDoc.getString("photoUrl")
                    val studentLevel = userDoc.getLong("level")?.toInt() ?: 1
                    val stats = StudentStats(studentId, "$fName $lName", studentLevel, photoUrl = photoUrl)

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

                    classTotalScore += studentScore
                    classTotalAttempts += studentAttempts
                    classTotalStreak += streak
                    classTotalQuizzes += stats.totalQuizzes

                    compiledStats.add(stats)
                }

                val avgClassScore = if (classTotalAttempts > 0) ((classTotalScore.toDouble() / classTotalAttempts) * 100).toInt() else 0
                val avgClassStreak = if (compiledStats.isNotEmpty()) classTotalStreak / compiledStats.size else 0

                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)

                    findViewById<TextView>(R.id.tv_avg_score).text = "$avgClassScore%"
                    findViewById<TextView>(R.id.tv_avg_streak).text = "$avgClassStreak"
                    findViewById<TextView>(R.id.tv_total_quizzes).text = "$classTotalQuizzes"

                    compiledStats.sortByDescending { it.averageScore }

                    val searchCard = findViewById<View>(R.id.card_search_perf_students)
                    val searchInput = findViewById<EditText>(R.id.et_search_perf_students)
                    val btnClearSearch = findViewById<ImageButton>(R.id.btn_clear_search_perf_students)
                    val noSearchCard = findViewById<View>(R.id.card_no_search_students)

                    searchCard?.visibility = View.VISIBLE
                    val studentCards = mutableListOf<Pair<StudentStats, View>>()
                    val rowViews = mutableListOf<View>()

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

                        // Squircle avatar with rounded border
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

                        val nameText = TextView(this@TeacherPerformanceActivity).apply {
                            text = student.name
                            textSize = 15f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                        }

                        val statsText = TextView(this@TeacherPerformanceActivity).apply {
                            text = "Level ${student.level} • ${student.totalQuizzes} Quizzes • Streak ${student.streak} 🔥"
                            textSize = 12f
                            setTextColor(Color.parseColor("#64748B"))
                            setPadding(0, 2, 0, 0)
                        }
                        col.addView(nameText)
                        col.addView(statsText)

                        // Right Pill: Score Accuracy
                        val scoreBadge = TextView(this@TeacherPerformanceActivity).apply {
                            text = "${student.averageScore}%"
                            textSize = 12f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            val scoreBg = if (student.averageScore >= 75) R.drawable.bg_bento_mint else R.drawable.bg_bento_orange
                            val scoreColor = if (student.averageScore >= 75) Color.parseColor("#00875A") else Color.parseColor("#D97706")
                            setBackgroundResource(scoreBg)
                            setTextColor(scoreColor)
                            val hp = (resources.displayMetrics.density * 10).toInt()
                            val vp = (resources.displayMetrics.density * 4).toInt()
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

                    searchInput?.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            val q = s?.toString()?.trim()?.lowercase() ?: ""
                            btnClearSearch?.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                            var matchCount = 0
                            studentCards.forEach { (st, cardView) ->
                                val match = q.isEmpty() || st.name.lowercase().contains(q)
                                cardView.visibility = if (match) View.VISIBLE else View.GONE
                                if (match) matchCount++
                            }
                            noSearchCard?.visibility = if (matchCount == 0 && q.isNotEmpty()) View.VISIBLE else View.GONE
                        }
                        override fun afterTextChanged(s: Editable?) {}
                    })

                    btnClearSearch?.let { btn ->
                        GabAIUtils.addSpringPressEffect(btn) {
                            searchInput?.setText("")
                        }
                    }

                    GabAIUtils.animateCascade(rowViews, 30L)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)
                    GabAIUtils.showSnackbar(this@TeacherPerformanceActivity, "Error loading data: ${e.message}")
                }
            }
        }
    }
}