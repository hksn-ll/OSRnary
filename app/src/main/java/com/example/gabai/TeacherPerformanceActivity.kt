package com.example.gabai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var grade: String // 🟢 NEW

    private val db = FirebaseFirestore.getInstance()

    // Data class to hold compiled student stats
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
        grade = intent.getStringExtra("GRADE") ?: "" // 🟢 NEW

        findViewById<TextView>(R.id.tv_class_name).text = className

        val header = findViewById<View>(R.id.perf_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadPerformanceData()
    }

    private fun loadPerformanceData() {
        val container = findViewById<LinearLayout>(R.id.student_list_container)
        GabAIUtils.showGlobalLoading(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Get the class document to check privileges
                val classDoc = db.collection("classes").document(classId).get().await()
                val isAdviser = classDoc.getBoolean("isAdviser") ?: false
                val joinedStudents = classDoc.get("joinedStudents") as? List<String> ?: listOf()

                // 2. Fetch all students in the school/section
                val studentSnaps = db.collection("users")
                    .whereEqualTo("role", "student")
                    .whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("section", sectionName)
                    .whereEqualTo("grade", grade)
                    .get()
                    .await()

                // 3. GATEKEEPER: Filter based on Adviser vs Subject Teacher
                val targetStudents = if (isAdviser) {
                    studentSnaps.documents // Advisers see everyone in the section
                } else {
                    studentSnaps.documents.filter { joinedStudents.contains(it.id) } // Subject teachers only see joined
                }

                if (targetStudents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)
                        container.addView(TextView(this@TeacherPerformanceActivity).apply {
                            text = "No students found for this class."
                            setPadding(0, 20, 0, 0)
                        })
                    }
                    return@launch
                }

                val compiledStats = mutableListOf<StudentStats>()
                var classTotalScore = 0
                var classTotalAttempts = 0
                var classTotalStreak = 0
                var classTotalQuizzes = 0

                // 4. Loop through the filtered students to compile their stats
                for (userDoc in targetStudents) {
                    val studentId = userDoc.id
                    val fName = userDoc.getString("firstName") ?: ""
                    val lName = userDoc.getString("lastName") ?: ""
                    val level = userDoc.getLong("level")?.toInt() ?: 1

                    val stats = StudentStats(studentId, "$fName $lName", level)

                    // Get Quiz History for this student
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

                    // Add to class totals
                    classTotalScore += studentScore
                    classTotalAttempts += studentAttempts
                    classTotalStreak += streak
                    classTotalQuizzes += stats.totalQuizzes

                    compiledStats.add(stats)
                }

                // 5. Calculate Class Averages
                val avgClassScore = if (classTotalAttempts > 0) ((classTotalScore.toDouble() / classTotalAttempts) * 100).toInt() else 0
                val avgClassStreak = if (compiledStats.isNotEmpty()) classTotalStreak / compiledStats.size else 0

                // 6. Update UI
                withContext(Dispatchers.Main) {
                    GabAIUtils.hideGlobalLoading(this@TeacherPerformanceActivity)

                    findViewById<TextView>(R.id.tv_avg_score).text = "$avgClassScore%"
                    findViewById<TextView>(R.id.tv_avg_streak).text = "$avgClassStreak"
                    findViewById<TextView>(R.id.tv_total_quizzes).text = "$classTotalQuizzes"

                    // Sort students by Average Score (Descending)
                    compiledStats.sortByDescending { it.averageScore }

                    for (student in compiledStats) {
                        val row = LinearLayout(this@TeacherPerformanceActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundResource(R.drawable.bg_card_quiz)
                            setPadding(40, 30, 40, 30)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 16) }

                            // 🟢 ADD THESE LINES TO MAKE IT CLICKABLE 🟢
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                val intent = android.content.Intent(this@TeacherPerformanceActivity, IndividualStudentPerformanceActivity::class.java)
                                intent.putExtra("STUDENT_ID", student.uid)
                                intent.putExtra("STUDENT_NAME", student.name)
                                startActivity(intent)
                            }
                            // ------------------------------------------
                        }

                        val nameText = TextView(this@TeacherPerformanceActivity).apply {
                            text = student.name
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#2D3436"))
                        }

                        val statsText = TextView(this@TeacherPerformanceActivity).apply {
                            text = "Score: ${student.averageScore}% | Quizzes: ${student.totalQuizzes} | Streak: ${student.streak} 🔥"
                            textSize = 14f
                            setTextColor(Color.parseColor("#636E72"))
                            setPadding(0, 8, 0, 0)
                        }

                        row.addView(nameText)
                        row.addView(statsText)
                        container.addView(row)
                    }
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