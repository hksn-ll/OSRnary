package com.example.gabai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class SubjectDetailActivity : AppCompatActivity() {

    private lateinit var subjectId: String
    private lateinit var subjectName: String
    private lateinit var uploadProgress: ProgressBar
    private lateinit var tvUploadStatus: TextView
    private lateinit var uploadStatusContainer: LinearLayout
    private lateinit var btnUploadPdf: Button
    private val db = FirebaseFirestore.getInstance()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private var teacherFullName: String = "Teacher"

    // --- PASTE YOUR GOOGLE APPS SCRIPT WEB APP URL HERE ---
    private val driveApiUrl = "https://script.google.com/macros/s/AKfycbxmlWtZXkpYqbgQU8wZ6Qdga9ImIHhlP5kMUSdujH8y2Db9SdP_DLswqoTO1-FDcf9CaQ/exec"

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) promptForPdfTitle(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subject_detail)

        subjectId = intent.getStringExtra("SUBJECT_ID") ?: return finish()
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""

        findViewById<TextView>(R.id.tv_subject_title).text = subjectName
        val header = findViewById<View>(R.id.subject_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        uploadProgress = findViewById(R.id.upload_progress)
        tvUploadStatus = findViewById(R.id.tv_upload_status)
        uploadStatusContainer = findViewById(R.id.upload_status_container)
        btnUploadPdf = findViewById(R.id.btn_upload_pdf)

        btnUploadPdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }

        loadPdfs()
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                teacherFullName = "${doc.getString("firstName")} ${doc.getString("lastName")}"
            }
        }
    }

    // 1. AUTO-FILL PDF TITLE LOGIC
    private fun promptForPdfTitle(fileUri: Uri) {
        var originalName = ""

        // Extract the actual filename from the Android system
        contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                originalName = cursor.getString(nameIndex) ?: ""
            }
        }

        // Clean up the name (remove .pdf extension)
        originalName = originalName.removeSuffix(".pdf").replace("_", " ")

        val input = EditText(this).apply {
            setText(originalName) // Auto-fill the suggested name!
            hint = "Enter Document Title"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Upload PDF")
            .setView(input)
            .setPositiveButton("Upload") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) uploadPdfToDrive(fileUri, title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadPdfToDrive(fileUri: Uri, title: String) {
        // 1. Gray out the button so they can't double-click
        btnUploadPdf.isEnabled = false
        btnUploadPdf.setBackgroundColor(Color.LTGRAY)
        btnUploadPdf.text = "Uploading..."

        // 2. Show the progress container
        uploadStatusContainer.visibility = View.VISIBLE
        uploadProgress.progress = 5
        tvUploadStatus.text = "5% - Generating Thumbnail..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val thumbnailBase64 = generateThumbnail(fileUri)

                withContext(Dispatchers.Main) {
                    uploadProgress.progress = 15
                    tvUploadStatus.text = "15% - Reading PDF File..."
                }

                val inputStream = contentResolver.openInputStream(fileUri)
                val bytes = inputStream?.readBytes() ?: throw Exception("Could not read file.")
                val base64File = Base64.encodeToString(bytes, Base64.DEFAULT)
                inputStream.close()

                withContext(Dispatchers.Main) {
                    uploadProgress.progress = 30
                    tvUploadStatus.text = "30% - Uploading to Google Drive (Please wait...)"
                }

                val cleanTitle = title.replace(Regex("[^A-Za-z0-9]"), "")
                val systematicFilename = "GabAI_${subjectName}_${cleanTitle}_${System.currentTimeMillis()}.pdf"

                // MASSIVE 5-MINUTE TIMEOUT FOR SLOW SCHOOL INTERNET
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val formBody = FormBody.Builder()
                    .add("action", "upload")
                    .add("fileName", systematicFilename)
                    .add("mimeType", "application/pdf")
                    .add("fileData", base64File)
                    .build()

                val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                val response = client.newCall(request).execute()
                val responseData = response.body?.string()

                withContext(Dispatchers.Main) {
                    uploadProgress.progress = 85
                    tvUploadStatus.text = "85% - Processing Response..."
                }

                if (response.isSuccessful && responseData != null) {
                    val json = JSONObject(responseData)
                    if (json.getString("status") == "success") {
                        val downloadUrl = json.getString("url")
                        val fileId = json.getString("fileId")

                        withContext(Dispatchers.Main) {
                            uploadProgress.progress = 95
                            tvUploadStatus.text = "95% - Saving to Database..."
                            saveToFirestore(title, downloadUrl, thumbnailBase64, fileId)
                        }
                    } else {
                        throw Exception(json.getString("message"))
                    }
                } else {
                    throw Exception("Google Error Code: ${response.code}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resetUploadUI()
                    Toast.makeText(this@SubjectDetailActivity, "Upload Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToFirestore(title: String, pdfUrl: String, thumbBase64: String, fileId: String) {
        val materialData = hashMapOf(
            "title" to title,
            "subjectId" to subjectId,
            "pdfUrl" to pdfUrl,
            "driveFileId" to fileId,
            "thumbnail" to thumbBase64,
            "teacherId" to uid,
            "uploaderName" to teacherFullName,
            "assignedSections" to listOf<String>(),
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("library_materials").add(materialData).addOnSuccessListener {
            uploadProgress.progress = 100
            tvUploadStatus.text = "100% - Complete!"
            Toast.makeText(this, "Uploaded successfully!", Toast.LENGTH_SHORT).show()

            // Wait 1 second so they can read "100% - Complete!" before hiding it
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                resetUploadUI()
            }, 1000)
        }.addOnFailureListener { e ->
            resetUploadUI()
            Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // HELPER FUNCTION: Un-grays the button and hides the progress bar
    private fun resetUploadUI() {
        uploadStatusContainer.visibility = View.GONE
        btnUploadPdf.isEnabled = true
        btnUploadPdf.setBackgroundColor(Color.parseColor("#6C5CE7"))
        btnUploadPdf.text = "+ Upload PDF Here"
    }

    private fun loadPdfs() {
        val container = findViewById<LinearLayout>(R.id.pdf_list_container)
        db.collection("library_materials")
            .whereEqualTo("subjectId", subjectId)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                container.removeAllViews()

                if (snapshots.isEmpty) {
                    container.addView(TextView(this).apply { text = "No PDFs uploaded yet." })
                    return@addSnapshotListener
                }

                for (doc in snapshots) {
                    val title = doc.getString("title") ?: "Document"
                    val pdfUrl = doc.getString("pdfUrl") ?: ""
                    val fileId = doc.getString("driveFileId") ?: ""
                    val thumbStr = doc.getString("thumbnail") ?: ""


                    val row = layoutInflater.inflate(R.layout.item_pdf_document, container, false)
                    row.findViewById<TextView>(R.id.tv_pdf_title).text = title

                    val uploader = doc.getString("uploaderName") ?: "Unknown"
                    row.findViewById<TextView>(R.id.tv_uploader_name)?.text = "Uploaded by: $uploader"

                    // NEW: FETCH CURRENTLY ASSIGNED SECTIONS
                    val assignedSections = doc.get("assignedSections") as? List<String> ?: listOf()

                    // MANAGE ACCESS LOGIC (Assign/Revoke)
                    row.findViewById<ImageButton>(R.id.btn_assign_pdf).setOnClickListener {
                        managePdfAccess(doc.id, title, assignedSections)
                    }

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

                    row.findViewById<ImageButton>(R.id.btn_open_pdf).setOnClickListener {
                        val intent = Intent(this, PdfViewerActivity::class.java)
                        intent.putExtra("PDF_URL", pdfUrl)
                        intent.putExtra("PDF_TITLE", title)
                        startActivity(intent)
                    }

                    row.findViewById<ImageButton>(R.id.btn_edit_pdf).setOnClickListener {
                        val input = EditText(this).apply { setText(title); setPadding(50, 40, 50, 40) }
                        MaterialAlertDialogBuilder(this).setTitle("Rename PDF").setView(input)
                            .setPositiveButton("Save") { _, _ ->
                                val newTitle = input.text.toString().trim()
                                if (newTitle.isNotEmpty() && newTitle != title) {
                                    // Trigger the new sync function!
                                    renamePdfInDriveAndDatabase(doc.id, fileId, newTitle)
                                }
                            }.setNegativeButton("Cancel", null).show()
                    }

                    // 4. TRUE DELETION LOGIC
                    row.findViewById<ImageButton>(R.id.btn_delete_pdf).setOnClickListener {
                        MaterialAlertDialogBuilder(this).setTitle("Delete PDF?")
                            .setMessage("This will permanently delete the file from Google Drive and remove it from the library.")
                            .setPositiveButton("Delete") { _, _ ->
                                deleteFileFromDriveAndDatabase(doc.id, fileId)
                            }
                            .setNegativeButton("Cancel", null).show()
                    }
                    container.addView(row)
                }
            }
    }

    private fun deleteFileFromDriveAndDatabase(docId: String, fileId: String) {
        uploadProgress.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (fileId.isNotEmpty()) {
                    val client = OkHttpClient()
                    val formBody = FormBody.Builder()
                        .add("action", "delete") // Tell script to trash the file
                        .add("fileId", fileId)
                        .build()
                    val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                    client.newCall(request).execute()
                }

                // Delete from database after Drive deletion succeeds (or if there was no Drive ID)
                withContext(Dispatchers.Main) {
                    db.collection("library_materials").document(docId).delete()
                    uploadProgress.visibility = View.GONE
                    Toast.makeText(this@SubjectDetailActivity, "PDF Deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadProgress.visibility = View.GONE
                    Toast.makeText(this@SubjectDetailActivity, "Failed to delete from Drive: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun generateThumbnail(uri: Uri): String {
        try {
            val fd = contentResolver.openFileDescriptor(uri, "r") ?: return ""
            val renderer = PdfRenderer(fd)
            val page = renderer.openPage(0)

            val width = 300
            val height = (300f * page.height / page.width).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val base64Thumb = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

            page.close()
            renderer.close()
            fd.close()

            return base64Thumb
        } catch (e: Exception) {
            return ""
        }

    }
    private fun renamePdfInDriveAndDatabase(docId: String, fileId: String, newTitle: String) {
        uploadProgress.visibility = View.VISIBLE
        uploadProgress.isIndeterminate = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Rename physically in Google Drive
                if (fileId.isNotEmpty()) {
                    val client = OkHttpClient()
                    val formBody = FormBody.Builder()
                        .add("action", "rename") // Tell script to rename the file
                        .add("fileId", fileId)
                        .add("newName", newTitle) // Send the new name
                        .build()
                    val request = Request.Builder().url(driveApiUrl).post(formBody).build()
                    client.newCall(request).execute()
                }

                // 2. Rename in the Database so students see it instantly
                withContext(Dispatchers.Main) {
                    db.collection("library_materials").document(docId).update("title", newTitle)
                    uploadProgress.visibility = View.GONE
                    Toast.makeText(this@SubjectDetailActivity, "Renamed in Drive & App!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uploadProgress.visibility = View.GONE
                    Toast.makeText(this@SubjectDetailActivity, "Failed to rename: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun managePdfAccess(materialId: String, title: String, currentlyAssigned: List<String>) {
        if (uid == null) return
        db.collection("classes").whereArrayContains("teacherIds", uid).get()
            .addOnSuccessListener { snapshots ->
                if (snapshots.isEmpty) {
                    Toast.makeText(this, "You need to create a Class Section first!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Extract unique section names
                val sectionNames = snapshots.documents.mapNotNull { it.getString("section") }.distinct().toTypedArray()

                // Create an array of booleans to check off sections that ALREADY have access
                val checkedItems = BooleanArray(sectionNames.size) { i ->
                    currentlyAssigned.contains(sectionNames[i])
                }

                // Track changes locally while the teacher is tapping checkboxes
                val selectedSections = currentlyAssigned.toMutableList()

                MaterialAlertDialogBuilder(this)
                    .setTitle("Manage Access: '$title'")
                    .setMultiChoiceItems(sectionNames, checkedItems) { _, which, isChecked ->
                        val section = sectionNames[which]
                        if (isChecked) {
                            if (!selectedSections.contains(section)) selectedSections.add(section)
                        } else {
                            // If unchecked, REVOKE access!
                            selectedSections.remove(section)
                        }
                    }
                    .setPositiveButton("Save") { _, _ ->
                        uploadProgress.visibility = View.VISIBLE

                        // Overwrite the array in Firestore with the new selection
                        db.collection("library_materials").document(materialId)
                            .update("assignedSections", selectedSections)
                            .addOnSuccessListener {
                                uploadProgress.visibility = View.GONE
                                Toast.makeText(this, "Access updated successfully!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                uploadProgress.visibility = View.GONE
                                Toast.makeText(this, "Failed to update access.", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
    }
}