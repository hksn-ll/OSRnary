package com.example.gabai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentTeacherHomeBinding
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.graphics.Color
import com.google.firebase.firestore.Query
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.storage.FirebaseStorage
import android.widget.Button

class TeacherHomeFragment : Fragment() {
    private var _binding: FragmentTeacherHomeBinding? = null
    private val binding get() = _binding!!
    private var selectedPdfUri: Uri? = null

    // This launcher opens the file manager and filters for PDFs
    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            Toast.makeText(requireContext(), "PDF Selected: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeacherHomeBinding.inflate(inflater, container, false)

        // This screen allows teachers to manage classes (Objective 1.2.2)
        binding.tvTeacherWelcome.text = "Educator Dashboard"
        // --- ADD THESE LINES ---
        binding.btnManageClasses.setOnClickListener {
            showCreateClassDialog()
        }

        binding.btnAssignMaterials.setOnClickListener {
            // Trigger the assignment process by first fetching available classes
            showAssignMaterialDialog()
        }
        // -----------------------
// Add this line right before 'return binding.root'
        fetchAndDisplayClasses()
        return binding.root
    }
    // --- ADD THESE NEW FUNCTIONS ---
    private fun showCreateClassDialog() {
        // Create a layout to hold multiple inputs
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // 1. Grade Dropdown (Spinner)
        val gradeLabel = TextView(requireContext()).apply { text = "Select Grade:" }
        val gradeSpinner = Spinner(requireContext()).apply {
            // Dropdown array - keeping only Grade 10 for now as requested
            val grades = arrayOf("Grade 10")
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, grades)
        }

        // 2. Section Name Input (EditText)
        val sectionLabel = TextView(requireContext()).apply {
            text = "Enter Section Name:"
            setPadding(0, 40, 0, 0) // Add some space above
        }
        val sectionInput = EditText(requireContext()).apply {
            hint = "e.g., Rizal"
        }

        // Add everything to the layout
        layout.addView(gradeLabel)
        layout.addView(gradeSpinner)
        layout.addView(sectionLabel)
        layout.addView(sectionInput)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create New Class Section")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val selectedGrade = gradeSpinner.selectedItem.toString()
                val sectionName = sectionInput.text.toString().trim()

                if (sectionName.isNotEmpty()) {
                    saveClassToFirestore(selectedGrade, sectionName)
                } else {
                    Toast.makeText(requireContext(), "Section name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveClassToFirestore(grade: String, sectionName: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val schoolId = doc.getString("schoolId") ?: ""
            val fullClassName = "$grade - $sectionName"

            val classData = hashMapOf(
                "className" to fullClassName, // e.g., "Grade 10 - Rizal"
                "grade" to grade,
                "section" to sectionName,
                "teacherId" to uid,
                "schoolId" to schoolId,
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("classes").add(classData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Class '$fullClassName' created successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    // --------------------------------
    // --- ADD THIS NEW FUNCTION ---
    private fun fetchAndDisplayClasses() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Find the container we just added in the XML
        val container = binding.root.findViewById<LinearLayout>(R.id.classes_container)

        // Listen for real-time updates to this teacher's classes
        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews() // Clear to avoid duplicates on refresh

                if (snapshots.isEmpty) {
                    val emptyText = TextView(requireContext()).apply {
                        text = "No classes created yet. Click 'Manage My Class Sections' to add one."
                        setTextColor(Color.GRAY)
                        setPadding(0, 20, 0, 0)
                    }
                    container.addView(emptyText)
                    return@addSnapshotListener
                }

                // Build a UI Card for each class found
                for (doc in snapshots) {
                    val className = doc.getString("className") ?: "Unknown Class"

                    val classView = TextView(requireContext()).apply {
                        text = className
                        textSize = 18f
                        setPadding(40, 40, 40, 40)
                        setBackgroundResource(R.drawable.bg_card_quiz) // Reusing your existing blue rounded card
                        setTextColor(Color.BLACK)

                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 24) // Add spacing between classes
                        }
                        layoutParams = params

                        // We extract the section to match with students later
                        val sectionName = doc.getString("section") ?: ""

                        setOnClickListener {
                            // --- REPLACE THE OLD TOAST WITH THIS FUNCTION CALL ---
                            showClassRosterDialog(className, sectionName)
                        }
                    }
                    container.addView(classView)
                }
            }
    }
    // --- ADD THIS NEW FUNCTION ---
    private fun showClassRosterDialog(className: String, targetSection: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Create a scrollable container for the student list
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val loadingText = TextView(requireContext()).apply { text = "Loading student roster..." }
        container.addView(loadingText)

        // Wrap the container in a ScrollView in case there are many students
        val scrollView = android.widget.ScrollView(requireContext()).apply { addView(container) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Roster: $className")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()

        // 1. Get the teacher's school ID to ensure we only search within their school
        db.collection("users").document(uid).get().addOnSuccessListener { teacherDoc ->
            val schoolId = teacherDoc.getString("schoolId") ?: ""

            // 2. Query for students in the same school AND same section
            db.collection("users")
                .whereEqualTo("role", "student")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("section", targetSection)
                .get()
                .addOnSuccessListener { snapshots ->
                    container.removeView(loadingText) // Remove loading message

                    if (snapshots.isEmpty) {
                        val emptyText = TextView(requireContext()).apply {
                            text = "No students have registered for section '$targetSection' yet."
                        }
                        container.addView(emptyText)
                        return@addOnSuccessListener
                    }

                    // 3. Build a UI element for each student found
                    for (studentDoc in snapshots) {
                        val fName = studentDoc.getString("firstName") ?: ""
                        val lName = studentDoc.getString("lastName") ?: ""
                        val level = studentDoc.getLong("level")?.toInt() ?: 1
                        val xp = studentDoc.getLong("current_xp")?.toInt() ?: 0

                        val studentView = TextView(requireContext()).apply {
                            // Using simple emoji for UI, displaying Name and Progress
                            text = "👤 $fName $lName\n⭐ Level $level ($xp XP)"
                            textSize = 16f
                            setTextColor(Color.BLACK)
                            setPadding(0, 20, 0, 20)
                        }
                        container.addView(studentView)

                        // Add a subtle gray divider line between students
                        val divider = View(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                                setMargins(0, 10, 0, 10)
                            }
                            setBackgroundColor(Color.LTGRAY)
                        }
                        container.addView(divider)
                    }
                }
                .addOnFailureListener { e ->
                    loadingText.text = "Failed to load roster: ${e.message}"
                }
        }
    }
    // --- ADD THESE NEW FUNCTIONS ---
    private fun showAssignMaterialDialog() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Fetch the teacher's classes to populate a selection dropdown
        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { classSnapshots ->
                if (classSnapshots.isEmpty) {
                    Toast.makeText(requireContext(), "Please create a class first!", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val classNames = classSnapshots.map { it.getString("className") ?: "" }.toTypedArray()
                val classIds = classSnapshots.map { it.id }.toTypedArray()

                // 2. Build the Input UI
                // --- Inside showAssignMaterialDialog() in TeacherHomeFragment.kt ---

                // 2. Build the Input UI
                // --- Inside showAssignMaterialDialog() in TeacherHomeFragment.kt ---

                // 2. Build the Input UI
                val layout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val titleInput = EditText(requireContext()).apply {
                    hint = "Reading Title (e.g., The Digital Age)"
                }

                val linkInput = EditText(requireContext()).apply {
                    hint = "Google Drive Link"
                    inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
                }

                val summaryInput = EditText(requireContext()).apply {
                    hint = "Short Summary of the Material"
                    minLines = 3
                    gravity = android.view.Gravity.TOP
                }

                val classLabel = TextView(requireContext()).apply {
                    text = "Assign to Class:"
                    setPadding(0, 30, 0, 10)
                }
                val classSpinner = Spinner(requireContext()).apply {
                    adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, classNames)
                }

                layout.addView(titleInput)
                layout.addView(linkInput)
                layout.addView(summaryInput)
                layout.addView(classLabel)
                layout.addView(classSpinner)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Assign New Reading Material")
                    .setView(layout)
                    .setPositiveButton("Assign") { _, _ ->
                        val title = titleInput.text.toString().trim()
                        val summary = summaryInput.text.toString().trim()
                        val driveUrl = linkInput.text.toString().trim()
                        val selectedClassId = classIds[classSpinner.selectedItemPosition]
                        val selectedClassName = classNames[classSpinner.selectedItemPosition]

                        // VALIDATION LOGIC
                        if (title.isEmpty() || summary.isEmpty() || driveUrl.isEmpty()) {
                            Toast.makeText(requireContext(), "Title, Summary, and Link are all required", Toast.LENGTH_SHORT).show()
                        } else if (!driveUrl.contains("drive.google.com")) {
                            // Enforce only Google Drive URLs
                            Toast.makeText(requireContext(), "Please provide a valid Google Drive link", Toast.LENGTH_SHORT).show()
                        } else {
                            // Save to Firestore
                            saveAssignmentToFirestore(title, summary, driveUrl, selectedClassId, selectedClassName)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

            }
    }

    // --- UPDATED SIGNATURE TO ACCEPT 5 ARGUMENTS ---
    // ADD pdfUrl: String to the parameters here
    // Now accepting exactly 5 arguments: title, content, pdfUrl, classId, className
    private fun saveAssignmentToFirestore(title: String, summary: String, pdfUrl: String, classId: String, className: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val assignmentData = hashMapOf(
            "title" to title,
            "content" to summary, // This is your summary
            "pdfUrl" to pdfUrl,    // This is your Google Drive link
            "teacherId" to uid,
            "targetClassId" to classId,
            "targetClassName" to className,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("library").add(assignmentData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Material assigned to $className!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    // --- TeacherHomeFragment.kt ---

    private fun uploadPdfToFirebase(title: String, fileUri: Uri, classId: String, className: String) {
        // REPLACE "gabai-6b004.firebasestorage.app" with your exact bucket name if different
        val storage = FirebaseStorage.getInstance("gs://gabai-6b004.firebasestorage.app")
        val storageRef = storage.reference.child("library_pdfs/${System.currentTimeMillis()}.pdf")

        Toast.makeText(requireContext(), "Uploading PDF...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(fileUri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUri = task.result
                saveAssignmentToFirestore(title, "[PDF Document]", downloadUri.toString(), classId, className)
                selectedPdfUri = null
            } else {
                // Log the error detail for debugging
                android.util.Log.e("GabAI_Storage", "Upload failed", task.exception)
                Toast.makeText(requireContext(), "Upload failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // --------------------------------
    // -----------------------------
    // -----------------------------
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}