package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
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
        var averageScore: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_performance)

        classId = intent.getStringExtra("CLASS_ID") ?: return finish()
        className = intent.getStringExtra("CLASS_NAME") ?: "Class Performance"
        sectionName = intent.getStringExtra("SECTION_NAME") ?: ""
        schoolId = intent.getStringExtra("SCHOOL_ID") ?: ""
        grade = intent.getStringExtra("GRADE") ?: ""

        findViewById<TextView>(R.id.tv_class_name).text = className

        val header = findViewById<View>(R.id.perf_header)
        GabAIUtils.applyFrostedGlass(header, 28f)

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
                val isAdviser = classDoc.getBoolean("isAdviser") ?: false
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
                    val level = userDoc.getLong("level")?.toInt() ?: 1

                    val stats = StudentStats(studentId, "$fName $lName", level)

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

                    val rowViews = mutableListOf<View>()

                    for (student in compiledStats) {
                        val row = LinearLayout(this@TeacherPerformanceActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_glass_card_bento)
                            setPadding(40, 36, 40, 36)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 20) }
                        }

                        val nameText = TextView(this@TeacherPerformanceActivity).apply {
                            text = student.name
                            textSize = 16f
                            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
                            setTextColor(Color.parseColor("#161D1F"))
                        }

                        val statsText = TextView(this@TeacherPerformanceActivity).apply {
                            text = "Avg: ${student.averageScore}% • Quizzes: ${student.totalQuizzes} • Streak: ${student.streak} 🔥"
                            textSize = 13f
                            setTypeface(fontJakarta ?: typeface, Typeface.NORMAL)
                            setTextColor(Color.parseColor("#5A6472"))
                            setPadding(0, 6, 0, 0)
                        }

                        row.addView(nameText)
                        row.addView(statsText)

                        GabAIUtils.addSpringPressEffect(row) {
                            GabAIUtils.showSnackbar(this@TeacherPerformanceActivity, "${student.name} • Level ${student.level}")
                        }

                        container.addView(row)
                        rowViews.add(row)
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