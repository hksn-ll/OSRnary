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
    private var teacherSchoolId: String = ""
    private val driveApiUrl = GabAIApp.DRIVE_API_URL

    private var allFolders = listOf<com.google.firebase.firestore.DocumentSnapshot>()
    private var currentSearchQuery = ""

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

        val btnRecycleVault = findViewById<ImageButton>(R.id.btn_recycle_vault)
        btnRecycleVault?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) {
                GabAIDialogs.showArchiveVaultDialog(
                    context = this,
                    schoolId = teacherSchoolId,
                    initialFilter = "subject"
                ) {
                    loadFolders()
                }
            }
        }

        setupSearch()
        loadFolders()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                teacherFullName = "${doc.getString("firstName")} ${doc.getString("lastName")}"
                teacherSchoolId = doc.getString("schoolId") ?: ""
            }
        }
    }

    private fun setupSearch() {
        val etSearch = findViewById<EditText>(R.id.et_search_folders) ?: return
        val btnClear = findViewById<ImageButton>(R.id.btn_clear_search_folders) ?: return

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClear.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                renderFolderList()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        GabAIUtils.addSpringPressEffect(btnClear) {
            etSearch.setText("")
        }
    }

    private fun loadFolders() {
        if (uid == null) return

        // Query just the teacher's subjects and sort locally to bypass Firebase strict indexes
        db.collection("library_subjects")
            .whereEqualTo("teacherId", uid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    com.example.gabai.GabAIUtils.showSnackbar(this, "Error loading folders: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                // Sort locally for instant UI updates
                allFolders = snapshots.documents.sortedBy { it.getLong("timestamp") ?: 0L }
                renderFolderList()
            }
    }

    private fun renderFolderList() {
        val container = findViewById<LinearLayout>(R.id.folder_list_container) ?: return
        val emptyCard = findViewById<View>(R.id.card_empty_subjects)
        val noResultsCard = findViewById<View>(R.id.card_no_search_results)
        val searchBox = findViewById<View>(R.id.card_search_folders)

        container.removeAllViews()

        if (allFolders.isEmpty()) {
            emptyCard?.visibility = View.VISIBLE
            noResultsCard?.visibility = View.GONE
            searchBox?.visibility = View.GONE
            return
        }

        emptyCard?.visibility = View.GONE
        searchBox?.visibility = View.VISIBLE

        val filtered = if (currentSearchQuery.isEmpty()) {
            allFolders
        } else {
            allFolders.filter { doc ->
                val name = doc.getString("name") ?: ""
                name.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            noResultsCard?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_no_search_subtitle)?.text = "No subjects matching \"$currentSearchQuery\""
            return
        }

        noResultsCard?.visibility = View.GONE
        val rowViews = mutableListOf<View>()

        for (doc in filtered) {
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

        GabAIUtils.animateCascade(rowViews, 25L)
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
                    val data = hashMapOf(
                        "name" to newName,
                        "teacherId" to uid,
                        "teacherName" to teacherFullName,
                        "schoolId" to teacherSchoolId,
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("library_subjects").add(data)
                } else {
                    val updateMap = hashMapOf<String, Any>("name" to newName)
                    if (teacherSchoolId.isNotEmpty()) {
                        updateMap["schoolId"] = teacherSchoolId
                    }
                    db.collection("library_subjects").document(docId).update(updateMap)
                }
            }
        }
    }

    // 1. UPDATED DELETION WITH RECYCLE VAULT ARCHIVE
    private fun confirmDelete(subjectId: String, subjectName: String) {
        GabAIDialogs.showConfirmDialog(
            this,
            title = "Delete $subjectName?",
            message = "This subject folder and all its materials will be safely moved to the Recycle Vault. You can restore them anytime within 30 days.",
            confirmText = "Archive & Delete",
            cancelText = "Cancel",
            isDestructive = true,
            badgeIcon = "🗑️"
        ) {
            GabAIUtils.showGlobalLoading(this, "Moving to Recycle Vault...")
            val currentUid = uid ?: ""
            val currentName = teacherFullName.ifBlank { "Teacher" }

            ArchiveVaultRepository.archiveSubject(
                subjectId = subjectId,
                schoolId = teacherSchoolId,
                operatorUid = currentUid,
                operatorName = currentName,
                operatorRole = "teacher",
                reason = "Curriculum subject deleted: $subjectName",
                onSuccess = { archiveId ->
                    GabAIUtils.hideGlobalLoading(this)
                    loadFolders()

                    val rootLayout = findViewById<View>(android.R.id.content)
                    GabAIUtils.showUndoSnackbar(
                        view = rootLayout,
                        message = "$subjectName moved to Recycle Vault",
                        actionText = "Undo"
                    ) {
                        GabAIUtils.showGlobalLoading(this, "Restoring subject...")
                        ArchiveVaultRepository.restoreEntity(
                            archiveId = archiveId,
                            restoredByUid = currentUid,
                            onSuccess = {
                                GabAIUtils.hideGlobalLoading(this)
                                loadFolders()
                                GabAIUtils.showSnackbar(this@TeacherLibraryActivity, "Subject restored successfully!")
                            },
                            onFailure = { e ->
                                GabAIUtils.hideGlobalLoading(this)
                                GabAIUtils.showSnackbar(this@TeacherLibraryActivity, "Failed to restore: ${e.message}")
                            }
                        )
                    }
                },
                onFailure = { e ->
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this@TeacherLibraryActivity, "Failed to delete: ${e.message}")
                }
            )
        }
    }
}