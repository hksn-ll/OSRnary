package com.example.gabai

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentSubjectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_student_subject)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_subject)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_subject)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val subjectId = intent.getStringExtra("SUBJECT_ID") ?: return finish()
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val studentSection = intent.getStringExtra("STUDENT_SECTION") ?: ""

        findViewById<TextView>(R.id.tv_subject_title).text = subjectName

        val headerInner = findViewById<View>(R.id.subject_header)
        val initialPaddingTop = headerInner.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(headerInner) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + initialPaddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        loadPdfs(subjectId, studentSection)
    }

    private fun loadPdfs(subjectId: String, studentSection: String) {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container)
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        GabAIUtils.showGlobalLoading(this)

        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .whereArrayContains("assignedSections", uid)
            .addSnapshotListener { snapshots, e ->
                GabAIUtils.hideGlobalLoading(this@StudentSubjectActivity)

                if (e != null || snapshots == null) return@addSnapshotListener
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    showEmptyState("No lesson materials have been uploaded for this subject yet.")
                    return@addSnapshotListener
                }

                val rowViews = mutableListOf<View>()
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
                            val decodedBytes = Base64.decode(thumbStr, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            imgThumb.setImageBitmap(bitmap)
                        } catch (_: Exception) {
                            imgThumb.setImageResource(R.drawable.ic_menu_book)
                        }
                    } else {
                        imgThumb.setImageResource(R.drawable.ic_menu_book)
                    }

                    // STUDENT LAUNCH LOGIC
                    val launchAction = {
                        val intent = Intent(this@StudentSubjectActivity, PdfViewerActivity::class.java).apply {
                            putExtra("PDF_URL", pdfUrl)
                            putExtra("PDF_TITLE", title)
                            putExtra("MATERIAL_ID", doc.id)
                            putExtra("IS_TEACHER", false)
                            putExtra("HAS_QUIZ", hasQuiz)
                        }
                        startActivity(intent)
                    }
                    row.setOnClickListener { launchAction() }
                    GabAIUtils.addSpringPressEffect(row) { launchAction() }

                    container.addView(row)
                    rowViews.add(row)
                }

                GabAIUtils.animateCascade(rowViews, 30L)
            }
    }

    private fun showEmptyState(msg: String) {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container)
        val fontJakarta = try { ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans) } catch (_: Exception) { null }
        val density = resources.displayMetrics.density

        val card = MaterialCardView(this).apply {
            radius = 24 * density
            cardElevation = 0f
            strokeWidth = (1.5 * density).toInt()
            setStrokeColor(Color.parseColor("#E2E8F0"))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (24 * density).toInt(), 0, 0) }
        }

        val emptyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (36 * density).toInt(), (24 * density).toInt(), (36 * density).toInt())
        }

        val icon = TextView(this).apply {
            text = "📄✨"
            textSize = 44f
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "No Lessons Available"
            textSize = 17f
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, (6 * density).toInt())
        }

        val desc = TextView(this).apply {
            text = msg
            textSize = 13.5f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(0f, 1.25f)
        }

        emptyLayout.addView(icon)
        emptyLayout.addView(title)
        emptyLayout.addView(desc)
        card.addView(emptyLayout)
        container.addView(card)
    }
}
