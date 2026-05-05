package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class TeacherLibraryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private var teacherFullName: String = "Teacher"
    // --- PASTE YOUR GOOGLE APPS SCRIPT WEB APP URL HERE ---
    // Make sure it ends in /exec !
    private val driveApiUrl = "https://script.google.com/macros/s/AKfycbxmlWtZXkpYqbgQU8wZ6Qdga9ImIHhlP5kMUSdujH8y2Db9SdP_DLswqoTO1-FDcf9CaQ/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_library)

        // Fix Status Bar
        val header = findViewById<View>(R.id.lib_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_create_folder).setOnClickListener { showFolderDialog(null, "") }

        loadFolders()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                teacherFullName = "${doc.getString("firstName")} ${doc.getString("lastName")}"
            }
        }
    }

    private fun loadFolders() {
        if (uid == null) return
        val container = findViewById<LinearLayout>(R.id.folder_list_container)

        // Query just the teacher's subjects and sort locally to bypass Firebase strict indexes
        db.collection("library_subjects")
            .whereEqualTo("teacherId", uid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    com.example.gabai.GabAIUtils.showSnackbar(this, "Error loading folders: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                container.removeAllViews()

                if (snapshots.isEmpty) {
                    container.addView(TextView(this).apply {
                        text = "No subjects created yet."
                        setPadding(0, 20, 0, 0)
                    })
                    return@addSnapshotListener
                }

                // Sort locally for instant UI updates
                val sortedDocs = snapshots.documents.sortedBy { it.getLong("timestamp") ?: 0L }

                for (doc in sortedDocs) {
                    val subjectName = doc.getString("name") ?: "Unnamed Subject"
                    val teacherName = doc.getString("teacherName") ?: "Teacher"
                    val row = layoutInflater.inflate(R.layout.item_folder, container, false)
                    row.findViewById<TextView>(R.id.tv_folder_name).text = subjectName

                    val tvTeacher = row.findViewById<TextView>(R.id.tv_folder_teacher)
                    if (tvTeacher != null) tvTeacher.text = "By: $teacherName"

                    // EDIT
                    row.findViewById<ImageButton>(R.id.btn_edit_folder).setOnClickListener {
                        showFolderDialog(doc.id, subjectName)
                    }

                    // DELETE
                    row.findViewById<ImageButton>(R.id.btn_delete_folder).setOnClickListener {
                        confirmDelete(doc.id, subjectName)
                    }

                    // OPEN FOLDER
                    row.setOnClickListener {
                        val intent = Intent(this, SubjectDetailActivity::class.java)
                        intent.putExtra("SUBJECT_ID", doc.id)
                        intent.putExtra("SUBJECT_NAME", subjectName)
                        startActivity(intent)
                    }

                    container.addView(row)
                }
            }
    }

    private fun showFolderDialog(docId: String?, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            hint = "e.g. Mathematics"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (docId == null) "New Subject" else "Rename Subject")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (docId == null) {
                        // Create
                        val data = hashMapOf("name" to newName, "teacherId" to uid, "teacherName" to teacherFullName, "timestamp" to System.currentTimeMillis())
                        db.collection("library_subjects").add(data)
                    } else {
                        // Update
                        db.collection("library_subjects").document(docId).update("name", newName)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 1. UPDATED DELETION CONFIRMATION
    private fun confirmDelete(subjectId: String, subjectName: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete $subjectName?")
            .setMessage("This will permanently delete this folder AND all the PDFs inside it from both the app and your Google Drive. Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSubjectAndContents(subjectId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 2. THE NEW CASCADE DELETE LOGIC
    // 2. THE NEW CASCADE DELETE LOGIC
    private fun deleteSubjectAndContents(subjectId: String) {
        // TURN ON SPINNER
        GabAIUtils.showGlobalLoading(this)

        // Step A: Find all PDFs inside this subject
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .get()
            .addOnSuccessListener { snapshots ->

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient()

                        // Step B: Loop through every PDF and tell Google Drive to trash it
                        for (doc in snapshots.documents) {
                            val fileId = doc.getString("driveFileId") ?: ""

                            if (fileId.isNotEmpty()) {
                                val formBody = okhttp3.FormBody.Builder()
                                    .add("action", "delete")
                                    .add("fileId", fileId)
                                    .build()
                                val request = okhttp3.Request.Builder().url(driveApiUrl).post(formBody).build()
                                client.newCall(request).execute() // Execute delete command
                            }

                            // Delete the PDF metadata from Firestore
                            db.collection("library_materials").document(doc.id).delete()
                        }

                        // Step C: Finally, delete the actual Subject Folder
                        withContext(Dispatchers.Main) {
                            db.collection("library_subjects").document(subjectId).delete()
                            // TURN OFF SPINNER
                            GabAIUtils.hideGlobalLoading(this@TeacherLibraryActivity)
                            GabAIUtils.showSnackbar(this@TeacherLibraryActivity, "Subject and all files deleted!")
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            GabAIUtils.hideGlobalLoading(this@TeacherLibraryActivity)
                            GabAIUtils.showSnackbar(this@TeacherLibraryActivity, "Error deleting files: ${e.message}")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(this)
                GabAIUtils.showSnackbar(this, "Error fetching files: ${e.message}")
            }
    }
}