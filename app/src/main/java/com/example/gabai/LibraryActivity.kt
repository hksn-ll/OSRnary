package com.example.gabai

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_library)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_library)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_library)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        val headerInner = findViewById<View>(R.id.library_header)
        val scrollContent = findViewById<View>(R.id.scroll_library)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        loadAssignedMaterials()

        // --- QUEST TRIGGER: LIBRARY ---
        ApprenticeQuestHelper.completeQuest(this, ApprenticeQuestHelper.QUEST_LIBRARY)
    }

    private fun loadAssignedMaterials() {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showGlobalLoading(this)

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val studentSection = userDoc.getString("section") ?: ""

            // 1. Find all materials assigned to this student's section
            db.collection("library_materials")
                .whereArrayContains("assignedSections", uid)
                .get()
                .addOnSuccessListener { materials ->
                    container.removeAllViews()

                    // 🟢 ALWAYS PRESENT: DepEd Reading Treasury Standalone Core Resource
                    val treasuryCard = addStandaloneTreasuryCard(container)

                    if (materials.isEmpty) {
                        GabAIUtils.hideGlobalLoading(this@LibraryActivity)
                        showEmptyTeacherNotice(container, "Your teacher hasn't assigned additional classroom reading materials yet. Explore the DepEd Reading Treasury above!")
                        GabAIUtils.animateCascade(listOf(treasuryCard), 35L)
                        return@addOnSuccessListener
                    }

                    // 2. Extract unique Folder IDs
                    val subjectIds = materials.documents.mapNotNull { it.getString("subjectId") }.distinct().take(10)

                    if (subjectIds.isEmpty()) {
                        GabAIUtils.hideGlobalLoading(this@LibraryActivity)
                        showEmptyTeacherNotice(container, "No active classroom reading subjects available. Start with the DepEd Reading Treasury above!")
                        GabAIUtils.animateCascade(listOf(treasuryCard), 35L)
                        return@addOnSuccessListener
                    }

                    // 3. Fetch specific folders
                    db.collection("library_subjects")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), subjectIds)
                        .get()
                        .addOnSuccessListener { subjects ->
                            val views = mutableListOf<View>(treasuryCard)
                            val headerView = addSectionHeader(container, "ASSIGNED CLASSROOM SUBJECTS")
                            views.add(headerView)

                            for (subject in subjects) {
                                val subjectName = subject.getString("name") ?: "Unnamed Subject"
                                val teacherName = subject.getString("teacherName") ?: "Teacher"
                                val v = addFolderView(subject.id, subjectName, teacherName, studentSection)
                                views.add(v)
                            }
                            GabAIUtils.hideGlobalLoading(this@LibraryActivity)
                            GabAIUtils.animateCascade(views, 35L)
                        }
                        .addOnFailureListener { e ->
                            GabAIUtils.hideGlobalLoading(this@LibraryActivity)
                            GabAIUtils.showSnackbar(this@LibraryActivity, "Failed to load subjects: ${e.message}")
                        }
                }
                .addOnFailureListener { e ->
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Failed to load materials: ${e.message}")
                }
        }.addOnFailureListener { e ->
            GabAIUtils.hideGlobalLoading(this)
            GabAIUtils.showSnackbar(this, "Failed to verify student section: ${e.message}")
        }
    }

    private fun addSectionHeader(container: LinearLayout, titleText: String): View {
        val density = resources.displayMetrics.density
        val fontJakarta = try { ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans) } catch (_: Exception) { null }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * density).toInt(), (14 * density).toInt(), (4 * density).toInt(), (8 * density).toInt())
        }

        val pill = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((3 * density).toInt(), (14 * density).toInt()).apply {
                marginEnd = (8 * density).toInt()
            }
            setBackgroundColor(Color.parseColor("#5341CD"))
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(Color.parseColor("#1E293B"))
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
        }

        row.addView(pill)
        row.addView(title)
        container.addView(row)
        return row
    }

    private fun addFolderView(subjectId: String, subjectName: String, teacherName: String, studentSection: String): View {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val itemView = layoutInflater.inflate(R.layout.item_folder, container, false)

        itemView.findViewById<TextView>(R.id.tv_folder_name).text = subjectName
        val tvTeacher = itemView.findViewById<TextView>(R.id.tv_folder_teacher)
        if (tvTeacher != null) tvTeacher.text = "By: $teacherName"

        // HIDE ADMIN BUTTONS FROM STUDENT
        itemView.findViewById<View>(R.id.btn_edit_folder)?.visibility = View.GONE
        itemView.findViewById<View>(R.id.btn_delete_folder)?.visibility = View.GONE

        val launchAction = {
            val intent = Intent(this, StudentSubjectActivity::class.java).apply {
                putExtra("SUBJECT_ID", subjectId)
                putExtra("SUBJECT_NAME", subjectName)
                putExtra("STUDENT_SECTION", studentSection)
            }
            startActivity(intent)
        }
        itemView.setOnClickListener { launchAction() }
        GabAIUtils.addSpringPressEffect(itemView) { launchAction() }
        container.addView(itemView)
        return itemView
    }

    private fun addStandaloneTreasuryCard(container: LinearLayout): View {
        val fontJakarta = try { ResourcesCompat.getFont(this, R.font.font_plus_jakarta_sans) } catch (_: Exception) { null }
        val fontNunito = try { ResourcesCompat.getFont(this, R.font.font_nunito_sans) } catch (_: Exception) { null }
        val density = resources.displayMetrics.density

        val readerCard = MaterialCardView(this).apply {
            radius = 24 * density
            cardElevation = 0f
            strokeWidth = (1.5 * density).toInt()
            setStrokeColor(Color.parseColor("#E0E7FF"))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (10 * density).toInt(), 0, (12 * density).toInt()) }
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
        }

        val badge = TextView(this).apply {
            text = "🌟 READY TO READ • DEPED OFFICIAL"
            textSize = 11f
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5341CD"))
            setBackgroundResource(R.drawable.bg_tag_k12)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "DepEd Reading Treasury 📖"
            textSize = 17f
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }

        val subtitle = TextView(this).apply {
            text = "Official Philippine literary anthology and guided reading selections from the DepEd National Reading Program."
            textSize = 13f
            setTypeface(fontNunito ?: typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#64748B"))
            setLineSpacing(0f, 1.25f)
        }

        val btnOpen = com.google.android.material.button.MaterialButton(this).apply {
            text = "Open Reading Treasury 📖"
            setBackgroundColor(Color.parseColor("#5341CD"))
            setTextColor(Color.WHITE)
            cornerRadius = (14 * density).toInt()
            setTypeface(fontJakarta ?: typeface, Typeface.BOLD)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply { topMargin = (16 * density).toInt() }
            layoutParams = params
        }

        val openAction = {
            val intent = Intent(this, PdfViewerActivity::class.java).apply {
                putExtra("PDF_URL", "asset://deped_reading_treasury.pdf")
                putExtra("PDF_TITLE", "DepEd Reading Treasury")
                putExtra("HAS_QUIZ", false)
            }
            startActivity(intent)
        }

        GabAIUtils.addSpringPressEffect(btnOpen) { openAction() }
        GabAIUtils.addSpringPressEffect(readerCard) { openAction() }

        cardContent.addView(badge)
        cardContent.addView(title)
        cardContent.addView(subtitle)
        cardContent.addView(btnOpen)
        readerCard.addView(cardContent)
        container.addView(readerCard)
        return readerCard
    }

    private fun showEmptyTeacherNotice(container: LinearLayout, msg: String) {
        val fontNunito = try { ResourcesCompat.getFont(this, R.font.font_nunito_sans) } catch (_: Exception) { null }
        val density = resources.displayMetrics.density

        val note = TextView(this).apply {
            text = "💡 $msg"
            textSize = 12.5f
            setTypeface(fontNunito ?: typeface, Typeface.NORMAL)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        container.addView(note)
    }
}