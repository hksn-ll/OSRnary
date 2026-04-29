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

        // ONLY fetch PDFs assigned to this student's section!
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .whereArrayContains("assignedSections", studentSection)
            .addSnapshotListener { snapshots, e ->
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

                    val row = layoutInflater.inflate(R.layout.item_pdf_document, container, false)
                    row.findViewById<TextView>(R.id.tv_pdf_title).text = title
                    row.findViewById<TextView>(R.id.tv_uploader_name)?.text = "Uploaded by: $uploader"

                    // HIDE ALL ADMIN BUTTONS FROM STUDENT
                    row.findViewById<View>(R.id.btn_assign_pdf).visibility = View.GONE
                    row.findViewById<View>(R.id.btn_edit_pdf).visibility = View.GONE
                    row.findViewById<View>(R.id.btn_delete_pdf).visibility = View.GONE

                    // LOAD THUMBNAIL
                    val imgThumb = row.findViewById<ImageView>(R.id.img_pdf_thumb)
                    if (thumbStr.isNotEmpty()) {
                        try {
                            val decodedBytes = Base64.decode(thumbStr, Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            imgThumb.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            imgThumb.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    } else {
                        imgThumb.setImageResource(android.R.drawable.ic_menu_report_image)
                    }

                    // OPEN IN APP
                    row.findViewById<ImageButton>(R.id.btn_open_pdf).setOnClickListener {
                        val intent = Intent(this, PdfViewerActivity::class.java)
                        intent.putExtra("PDF_URL", pdfUrl)
                        intent.putExtra("PDF_TITLE", title)
                        startActivity(intent)
                    }
                    container.addView(row)
                }
            }
    }
}