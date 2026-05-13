package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class StudentSubjectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_subject)

        val subjectId = intent.getStringExtra("SUBJECT_ID") ?: return finish()
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val studentSection = intent.getStringExtra("STUDENT_SECTION") ?: ""

        findViewById<TextView>(R.id.tv_subject_title).text = subjectName
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val header = findViewById<View>(R.id.subject_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        loadPdfs(subjectId, studentSection)
    }

    private fun loadPdfs(subjectId: String, studentSection: String) {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container)
        val db = FirebaseFirestore.getInstance()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        GabAIUtils.showGlobalLoading(this)

        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .whereArrayContains("assignedSections", uid)
            .addSnapshotListener { snapshots, e ->
                GabAIUtils.hideGlobalLoading(this@StudentSubjectActivity)

                if (e != null || snapshots == null) return@addSnapshotListener
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    container.addView(TextView(this).apply { text = "No PDFs available here yet." })
                    return@addSnapshotListener
                }

                for (doc in snapshots) {
                    val title = doc.getString("title") ?: "Document"
                    val pdfUrl = doc.getString("pdfUrl") ?: ""
                    val thumbStr = doc.getString("thumbnail") ?: ""
                    val uploader = doc.getString("uploaderName") ?: "Teacher"
                    val quizJson = doc.getString("quiz_pool_json") ?: "[]"
                    val hasQuiz = quizJson.length > 5

                    val row = layoutInflater.inflate(R.layout.item_pdf_document, container, false)
                    row.findViewById<TextView>(R.id.tv_pdf_title).text = title
                    row.findViewById<TextView>(R.id.tv_uploader_name)?.text = "Uploaded by: $uploader"

                    val imgThumb = row.findViewById<ImageView>(R.id.img_pdf_thumb)
                    if (thumbStr.isNotEmpty()) {
                        try {
                            val decodedBytes = android.util.Base64.decode(thumbStr, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            imgThumb.setImageBitmap(bitmap)
                        } catch (e: Exception) { imgThumb.setImageResource(android.R.drawable.ic_menu_report_image) }
                    } else imgThumb.setImageResource(android.R.drawable.ic_menu_report_image)

                    // 🟢 STUDENT LAUNCH LOGIC 🟢
                    row.setOnClickListener {
                        val intent = Intent(this@StudentSubjectActivity, PdfViewerActivity::class.java).apply {
                            putExtra("PDF_URL", pdfUrl)
                            putExtra("PDF_TITLE", title)
                            putExtra("MATERIAL_ID", doc.id)
                            putExtra("IS_TEACHER", false)
                            putExtra("HAS_QUIZ", hasQuiz)
                        }
                        startActivity(intent)
                    }

                    container.addView(row)
                }
            }
    }
}
