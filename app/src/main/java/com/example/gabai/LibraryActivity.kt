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
    }

    private fun loadAssignedMaterials() {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Get Student's School and Section
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val studentSchoolId = userDoc.getString("schoolId") ?: ""
            val studentSection = userDoc.getString("section") ?: ""

            // 2. Fetch materials assigned to this specific school and section
            // Note: We filter by schoolId first for security/performance
            db.collection("library")
                .whereEqualTo("schoolId", studentSchoolId)
                .get()
                .addOnSuccessListener { documents ->
                    container.removeAllViews()

                    if (documents.isEmpty) {
                        showEmptyState("No materials assigned yet.")
                        return@addOnSuccessListener
                    }

                    var count = 0
                    for (doc in documents) {
                        val targetClassName = doc.getString("targetClassName") ?: ""

                        // Only show if the student's section matches the class name (e.g. "Rizal" in "Grade 10 - Rizal")
                        if (targetClassName.contains(studentSection, ignoreCase = true)) {
                            addMaterialView(doc.getString("title") ?: "", doc.getString("content") ?: "", doc.getString("pdfUrl") ?: "")
                            count++
                        }
                    }

                    if (count == 0) showEmptyState("No materials assigned for section $studentSection.")
                }
        }
    }

    private fun addMaterialView(title: String, summary: String, url: String) {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val itemView = layoutInflater.inflate(R.layout.item_favorite, container, false)

        itemView.findViewById<TextView>(R.id.fav_title).text = title
        itemView.findViewById<TextView>(R.id.fav_content).text = "Summary: $summary"

        // Change delete icon to "Open" icon (or just hide delete)
        itemView.findViewById<View>(R.id.btn_remove_fav).visibility = View.GONE

        itemView.setOnClickListener {
            if (url.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } else {
                Toast.makeText(this, "No link available for this material", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(itemView)
    }

    private fun showEmptyState(msg: String) {
        val container = findViewById<LinearLayout>(R.id.library_list_container)
        val tv = TextView(this).apply { text = msg; setPadding(40, 40, 40, 40) }
        container.addView(tv)
    }
}