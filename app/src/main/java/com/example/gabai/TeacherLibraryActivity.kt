package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private val driveApiUrl = GabAIApp.DRIVE_API_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_teacher_library)

        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_teacher_lib)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_teacher_lib)
        if (blurHeader != null && blurTarget != null) {
            GabAIUtils.setupBlurView(blurHeader, blurTarget)
        }

        val headerInner = findViewById<View>(R.id.lib_header)
        val scrollContent = findViewById<View>(R.id.scroll_teacher_library)
        GabAIUtils.applyHeaderAndScrollInsets(headerInner ?: blurHeader, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) { finish() }

        val btnCreate = findViewById<View>(R.id.btn_create_folder)
        GabAIUtils.addSpringPressEffect(btnCreate) { showFolderDialog(null, "") }

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
        val emptyCard = findViewById<View>(R.id.card_empty_subjects)

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
                    emptyCard?.visibility = View.VISIBLE
                    return@addSnapshotListener
                } else {
                    emptyCard?.visibility = View.GONE
                }

                // Sort locally for instant UI updates
                val sortedDocs = snapshots.documents.sortedBy { it.getLong("timestamp") ?: 0L }
                val rowViews = mutableListOf<View>()

                for (doc in sortedDocs) {
                    val subjectName = doc.getString("name") ?: "Unnamed Subject"
                    val teacherName = doc.getString("teacherName") ?: "Teacher"
                    val row = layoutInflater.inflate(R.layout.item_folder, container, false)
                    row.findViewById<TextView>(R.id.tv_folder_name).text = subjectName

                    val tvTeacher = row.findViewById<TextView>(R.id.tv_folder_teacher)
                    if (tvTeacher != null) tvTeacher.text = "By: $teacherName"

                    // EDIT
                    val btnEdit = row.findViewById<ImageButton>(R.id.btn_edit_folder)
                    btnEdit?.setOnClickListener {
                        showFolderDialog(doc.id, subjectName)
                    }

                    // DELETE
                    val btnDelete = row.findViewById<ImageButton>(R.id.btn_delete_folder)
                    btnDelete?.setOnClickListener {
                        confirmDelete(doc.id, subjectName)
                    }

                    // OPEN FOLDER
                    GabAIUtils.addSpringPressEffect(row) {
                        val intent = Intent(this, SubjectDetailActivity::class.java)
                        intent.putExtra("SUBJECT_ID", doc.id)
                        intent.putExtra("SUBJECT_NAME", subjectName)
                        startActivity(intent)
                    }

                    container.addView(row)
                    rowViews.add(row)
                }

                GabAIUtils.animateCascade(rowViews, 30L)
            }
    }

    private fun showFolderDialog(docId: String?, currentName: String) {
        GabAIDialogs.showInputDialog(
            this,
            title = if (docId == null) "New Subject" else "Rename Subject",
            subtitle = "Enter a name for this subject folder:",
            hint = "e.g. Mathematics",
            initialText = currentName,
            confirmText = "Save",
            badgeIcon = "📁"
        ) { newName ->
            if (newName.isNotEmpty()) {
                if (docId == null) {
                    val data = hashMapOf("name" to newName, "teacherId" to uid, "teacherName" to teacherFullName, "timestamp" to System.currentTimeMillis())
                    db.collection("library_subjects").add(data)
                } else {
                    db.collection("library_subjects").document(docId).update("name", newName)
                }
            }
        }
    }

    // 1. UPDATED DELETION CONFIRMATION
    private fun confirmDelete(subjectId: String, subjectName: String) {
        GabAIDialogs.showConfirmDialog(
            this,
            title = "Delete $subjectName?",
            message = "This will permanently delete this folder AND all the PDFs inside it from both the app and your Google Drive. Are you sure?",
            confirmText = "Delete",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "🗑️"
        ) {
            deleteSubjectAndContents(subjectId)
        }
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