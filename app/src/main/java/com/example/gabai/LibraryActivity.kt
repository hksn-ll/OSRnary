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
import com.google.firebase.firestore.FieldValue
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", FieldValue.arrayUnion("library"))
        }
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

                    if (materials.isEmpty) {
                        GabAIUtils.hideGlobalLoading(this)
                        showEmptyState("Your teacher hasn't assigned any reading materials to your section yet. Check back later!")
                        return@addOnSuccessListener
                    }

                    // 2. Extract unique Folder IDs
                    val subjectIds = materials.documents.mapNotNull { it.getString("subjectId") }.distinct().take(10)

                    if (subjectIds.isEmpty()) {
                        GabAIUtils.hideGlobalLoading(this)
                        showEmptyState("No active reading subjects available.")
                        return@addOnSuccessListener
                    }

                    // 3. Fetch specific folders
                    db.collection("library_subjects")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), subjectIds)
                        .get()
                        .addOnSuccessListener { subjects ->
                            val views = mutableListOf<View>()
                            for (subject in subjects) {
                                val subjectName = subject.getString("name") ?: "Unnamed Subject"
                                val teacherName = subject.getString("teacherName") ?: "Teacher"
                                val v = addFolderView(subject.id, subjectName, teacherName, studentSection)
                                views.add(v)
                            }
                            GabAIUtils.hideGlobalLoading(this)
                            GabAIUtils.animateCascade(views, 35L)
                        }
                        .addOnFailureListener { e ->
                            GabAIUtils.hideGlobalLoading(this)
                            GabAIUtils.showSnackbar(this, "Failed to load subjects: ${e.message}")
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

    private fun showEmptyState(msg: String) {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
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
            text = "📚✨"
            textSize = 44f
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "Your Reading Vault is Clear"
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