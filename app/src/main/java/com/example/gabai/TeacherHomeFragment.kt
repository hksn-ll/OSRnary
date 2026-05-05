package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentTeacherHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherHomeFragment : Fragment() {
    private var _binding: FragmentTeacherHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeacherHomeBinding.inflate(inflater, container, false)

        // 1. OPEN NEW CLASS MANAGEMENT SCREEN
        binding.btnManageClasses.setOnClickListener {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        // 2. OPEN NEW LIBRARY UPLOAD SCREEN
        binding.btnAssignMaterials.setOnClickListener {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }

        return binding.root
    }

    private fun showAssignMaterialDialog() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { classSnapshots ->
                if (classSnapshots.isEmpty) {
                    com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Please create a class section first!")
                    return@addOnSuccessListener
                }

                val classNames = classSnapshots.map { it.getString("className") ?: "" }.toTypedArray()
                val classIds = classSnapshots.map { it.id }.toTypedArray()

                val layout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 40, 60, 40)
                }

                val titleInput = EditText(requireContext()).apply { hint = "Reading Title (e.g., The Digital Age)" }
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

                        if (title.isEmpty() || summary.isEmpty() || driveUrl.isEmpty()) {
                            com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Title, Summary, and Link are all required")
                        } else if (!driveUrl.contains("drive.google.com")) {
                            com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Please provide a valid Google Drive link")
                        } else {
                            saveAssignmentToFirestore(title, summary, driveUrl, selectedClassId, selectedClassName)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
    }

    private fun saveAssignmentToFirestore(title: String, summary: String, pdfUrl: String, classId: String, className: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val schoolId = userDoc.getString("schoolId") ?: ""

            val assignmentData = hashMapOf(
                "title" to title,
                "content" to summary,
                "pdfUrl" to pdfUrl,
                "teacherId" to uid,
                "schoolId" to schoolId,
                "targetClassId" to classId,
                "targetClassName" to className,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("library").add(assignmentData)
                .addOnSuccessListener {
                    com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Material assigned to $className!")
                }
                .addOnFailureListener { e ->
                    com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Error: ${e.message}")
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}