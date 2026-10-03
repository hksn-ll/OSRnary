package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        setupButtons()
        loadEducatorMetrics()

        // Cascade entrance animation
        GabAIUtils.animateCascade(
            listOf(
                binding.headerEducator,
                binding.llEducatorMetrics,
                binding.btnManageClasses,
                binding.btnAssignMaterials,
                binding.btnViewPerformance
            ),
            35L
        )

        return binding.root
    }

    private fun loadEducatorMetrics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Classes & Total Enrolled Learners
        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    val classCount = snapshots.size()
                    binding.tvMetricClasses.text = classCount.toString()

                    var totalStudents = 0
                    for (doc in snapshots.documents) {
                        val joined = doc.get("joinedStudents") as? List<*>
                        totalStudents += joined?.size ?: 0
                    }
                    binding.tvMetricStudents.text = totalStudents.toString()
                }
            }

        // 2. Materials Uploaded/Assigned
        db.collection("library_materials")
            .whereEqualTo("uploadedBy", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvMetricMaterials.text = snapshots.size().toString()
                }
            }
    }

    private fun setupButtons() {
        // 1. OPEN CLASS MANAGEMENT
        GabAIUtils.addSpringPressEffect(binding.btnManageClasses) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        // 2. OPEN TEACHER LIBRARY
        GabAIUtils.addSpringPressEffect(binding.btnAssignMaterials) {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }

        // 3. DIRECT LEARNER ANALYTICS & PERFORMANCE
        GabAIUtils.addSpringPressEffect(binding.btnViewPerformance) {
            openClassPerformance()
        }
    }

    private fun openClassPerformance() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showGlobalLoading(requireActivity())

        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                GabAIUtils.hideGlobalLoading(requireActivity())

                if (snapshots.isEmpty) {
                    GabAIUtils.showSnackbar(requireContext(), "Please create a class section first!")
                    return@addOnSuccessListener
                }

                if (snapshots.size() == 1) {
                    val doc = snapshots.documents[0]
                    launchPerformanceForClass(doc.id, doc.getString("className") ?: "", doc.getString("sectionName") ?: "", doc.getString("schoolId") ?: "", doc.getString("grade") ?: "")
                } else {
                    val classNames = snapshots.documents.map {
                        val cName = it.getString("className") ?: "Class"
                        val grade = it.getString("grade") ?: ""
                        if (grade.isNotEmpty()) "$grade - $cName" else cName
                    }.toTypedArray()

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Select Class to View Performance")
                        .setItems(classNames) { _, which ->
                            val doc = snapshots.documents[which]
                            launchPerformanceForClass(doc.id, doc.getString("className") ?: "", doc.getString("sectionName") ?: "", doc.getString("schoolId") ?: "", doc.getString("grade") ?: "")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .addOnFailureListener { e ->
                GabAIUtils.hideGlobalLoading(requireActivity())
                GabAIUtils.showSnackbar(requireContext(), "Failed to load classes: ${e.message}")
            }
    }

    private fun launchPerformanceForClass(classId: String, className: String, sectionName: String, schoolId: String, grade: String) {
        val perfIntent = Intent(requireContext(), TeacherPerformanceActivity::class.java).apply {
            putExtra("CLASS_ID", classId)
            putExtra("CLASS_NAME", className)
            putExtra("SECTION_NAME", sectionName)
            putExtra("SCHOOL_ID", schoolId)
            putExtra("GRADE", grade)
        }
        startActivity(perfIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}