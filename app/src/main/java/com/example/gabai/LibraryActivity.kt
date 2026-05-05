package com.example.gabai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LibraryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        // Fix Status Bar Overlap
        val header = findViewById<View>(R.id.library_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        loadAssignedMaterials()
        // --- QUEST TRIGGER: LIBRARY ---
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("library"))
        }
    }

    private fun loadAssignedMaterials() {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // TURN ON SPINNER
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

                    // 2. Extract the unique Folder IDs those materials belong to
                    val subjectIds = materials.documents.mapNotNull { it.getString("subjectId") }.distinct().take(10)

                    if (subjectIds.isEmpty()) {
                        GabAIUtils.hideGlobalLoading(this)
                        return@addOnSuccessListener
                    }

                    // 3. Fetch those specific Folders to get their Names and display them!
                    db.collection("library_subjects")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), subjectIds)
                        .get()
                        .addOnSuccessListener { subjects ->
                            for (subject in subjects) {
                                val subjectName = subject.getString("name") ?: "Unnamed Subject"
                                val teacherName = subject.getString("teacherName") ?: "Teacher"
                                addFolderView(subject.id, subjectName, teacherName, studentSection)
                            }
                            // TURN OFF SPINNER
                            GabAIUtils.hideGlobalLoading(this)
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

    private fun addFolderView(subjectId: String, subjectName: String, teacherName: String, studentSection: String) {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val itemView = layoutInflater.inflate(R.layout.item_folder, container, false)

        itemView.findViewById<TextView>(R.id.tv_folder_name).text = subjectName
        val tvTeacher = itemView.findViewById<TextView>(R.id.tv_folder_teacher)
        if (tvTeacher != null) tvTeacher.text = "By: $teacherName"
        // HIDE ADMIN BUTTONS FROM STUDENT
        itemView.findViewById<View>(R.id.btn_edit_folder).visibility = View.GONE
        itemView.findViewById<View>(R.id.btn_delete_folder).visibility = View.GONE

        itemView.setOnClickListener {
            val intent = Intent(this, StudentSubjectActivity::class.java)
            intent.putExtra("SUBJECT_ID", subjectId)
            intent.putExtra("SUBJECT_NAME", subjectName)
            intent.putExtra("STUDENT_SECTION", studentSection)
            startActivity(intent)
        }
        container.addView(itemView)
    }

    private fun showEmptyState(msg: String) {
        val container = findViewById<LinearLayout>(R.id.library_list_container)

        val emptyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(60, 100, 60, 100)
        }

        val icon = TextView(this).apply {
            text = "📚✨"
            textSize = 64f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val title = TextView(this).apply {
            text = "Your Library is Clear!"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#2D3436"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val desc = TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#636E72"))
            gravity = android.view.Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(0f, 1.3f)
        }

        emptyLayout.addView(icon)
        emptyLayout.addView(title)
        emptyLayout.addView(desc)
        container.addView(emptyLayout)
    }
}