package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentTeacherHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TeacherHomeFragment : Fragment() {
    private var _binding: FragmentTeacherHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTeacherHomeBinding.inflate(inflater, container, false)
        binding.btnCreateClassQuick.visibility = View.GONE

        setupButtons()
        loadEducatorProfileAndMetrics()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadEducatorProfileAndMetrics()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playEntranceAnimation()
    }

    fun playEntranceAnimation() {
        if (_binding == null || !isAdded) return
        val viewsToAnimate = listOfNotNull(
            binding.headerEducator,
            binding.cardHeroJoinCode,
            binding.headerEducatorOverview,
            binding.btnMetricClasses,
            binding.btnMetricStudents,
            binding.btnMetricMaterials,
            binding.btnMetricQuizzes,
            binding.headerTeachingWorkspace,
            binding.btnManageClasses,
            binding.btnAssignMaterials,
            binding.btnViewPerformance,
            binding.btnCreateClassQuick
        )
        GabAIUtils.animateCascade(viewsToAnimate, baseDelay = 30L, startDelayOffset = 180L)
    }

    private fun loadEducatorProfileAndMetrics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Load Teacher User Document (Name, School, Join Code)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded && doc.exists()) {
                    val firstName = doc.getString("firstName") ?: doc.getString("first_name") ?: ""
                    val sId = doc.getString("schoolId")
                    val schoolName = SchoolRepository.getSchoolName(sId)
                    val joinCode = doc.getString("joinCode") ?: "N/A"

                    binding.tvTeacherWelcome.text = if (firstName.isNotEmpty()) "Hello, Teacher $firstName!" else "Hello, Teacher!"
                    binding.tvTeacherSchool.text = if (schoolName.isNotEmpty()) "$schoolName • Educator Console" else "EDUCATOR CONSOLE"
                    binding.tvTeacherJoinCode.text = joinCode

                    GabAIUtils.addSpringPressEffect(binding.btnShowQrCode) {
                        val ctx = context ?: return@addSpringPressEffect
                        if (joinCode != "N/A") {
                            GabAIDialogs.showQrCodeDialog(
                                ctx,
                                title = "Your Teacher QR Code",
                                subtitle = "Share this with learners to enroll into your classes.",
                                qrContent = joinCode,
                                displayCode = joinCode
                            )
                        }
                    }
                }
            }

        // 2. Classes & Total Enrolled Learners
        db.collection("classes")
            .whereArrayContains("teacherIds", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    val classCount = snapshots.size()
                    binding.tvMetricClasses.text = classCount.toString()

                    val uniqueStudentIds = mutableSetOf<String>()
                    var hasAdvisoryClass = false
                    val advisorySections = mutableListOf<Pair<String, String>>() // (grade, section)

                    for (doc in snapshots.documents) {
                        val joined = doc.get("joinedStudents") as? List<*>
                        joined?.filterIsInstance<String>()?.forEach { uniqueStudentIds.add(it) }

                        val isAdviserDoc = doc.getBoolean("isAdviser") ?: true
                        val teacherId = doc.getString("teacherId") ?: ""
                        if (isAdviserDoc && (teacherId == uid || teacherId.isEmpty())) {
                            hasAdvisoryClass = true
                            val sec = doc.getString("section") ?: ""
                            val grd = doc.getString("grade") ?: ""
                            if (sec.isNotEmpty()) {
                                advisorySections.add(grd to sec)
                            }
                        }
                    }

                    val shouldShow = !hasAdvisoryClass
                    if (binding.btnCreateClassQuick.visibility != (if (shouldShow) View.VISIBLE else View.GONE)) {
                        binding.btnCreateClassQuick.visibility = if (shouldShow) View.VISIBLE else View.GONE
                        if (shouldShow) {
                            GabAIUtils.animateCascade(listOf(binding.btnCreateClassQuick), baseDelay = 0L, startDelayOffset = 0L)
                        }
                    }

                    if (advisorySections.isNotEmpty()) {
                        // Also include automatic advisory homeroom learners from the school roster
                        db.collection("users").document(uid).get().addOnSuccessListener { teacherDoc ->
                            val schoolId = teacherDoc.getString("schoolId") ?: ""
                            db.collection("users")
                                .whereEqualTo("role", "student")
                                .whereEqualTo("schoolId", schoolId)
                                .get()
                                .addOnSuccessListener { userSnaps ->
                                    if (_binding == null || !isAdded) return@addOnSuccessListener
                                    for (uDoc in userSnaps.documents) {
                                        val uSec = uDoc.getString("section") ?: ""
                                        val uGrd = uDoc.getString("grade") ?: ""
                                        if (advisorySections.any { (grd, sec) -> sec.equals(uSec, ignoreCase = true) && (grd.isEmpty() || grd.equals(uGrd, ignoreCase = true)) }) {
                                            uniqueStudentIds.add(uDoc.id)
                                        }
                                    }
                                    binding.tvMetricStudents.text = uniqueStudentIds.size.toString()
                                }
                        }
                    } else {
                        binding.tvMetricStudents.text = uniqueStudentIds.size.toString()
                    }
                }
            }

        // 3. Materials Uploaded/Assigned
        db.collection("library_materials")
            .whereEqualTo("uploadedBy", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvMetricMaterials.text = snapshots.size().toString()
                }
            }

        // 4. Quizzes Authored
        db.collection("quizzes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvMetricQuizzes.text = snapshots.size().toString()
                }
            }
    }

    private fun setupButtons() {
        // Class Sections & Rosters
        GabAIUtils.addSpringPressEffect(binding.btnManageClasses) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        // Curriculum & Library Vault
        GabAIUtils.addSpringPressEffect(binding.btnAssignMaterials) {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }

        // Learner Analytics & Performance
        GabAIUtils.addSpringPressEffect(binding.btnViewPerformance) {
            openClassPerformance()
        }

        // Quick Create Class Button
        GabAIUtils.addSpringPressEffect(binding.btnCreateClassQuick) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        // Metric Card Tap Shortcuts
        GabAIUtils.addSpringPressEffect(binding.btnMetricClasses) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnMetricStudents) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnMetricMaterials) {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnMetricQuizzes) {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }
    }

    private fun openClassPerformance() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val act = activity ?: return

        GabAIUtils.showGlobalLoading(act)

        db.collection("classes")
            .whereArrayContains("teacherIds", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                val currentAct = activity ?: return@addOnSuccessListener
                GabAIUtils.hideGlobalLoading(currentAct)
                val currentCtx = context ?: return@addOnSuccessListener

                if (snapshots.isEmpty) {
                    GabAIUtils.showSnackbar(currentCtx, "No classes found to view performance.")
                    return@addOnSuccessListener
                }

                if (snapshots.size() == 1) {
                    val doc = snapshots.documents[0]
                    launchPerformanceForClass(doc.id, doc.getString("className") ?: "", doc.getString("sectionName") ?: "", doc.getString("schoolId") ?: "", doc.getString("grade") ?: "")
                } else {
                    val classNames = snapshots.documents.map {
                        val cName = it.getString("className") ?: "Class"
                        val grade = it.getString("grade") ?: ""
                        if (grade.isNotEmpty() && !cName.contains(grade)) "$grade - $cName" else cName
                    }

                    GabAIDialogs.showSelectionDialog(
                        context = currentCtx,
                        title = "Select Class to View Performance",
                        subtitle = "Choose which section metrics you want to inspect.",
                        items = classNames,
                        badgeIcon = "📊",
                        onSelected = { which, _ ->
                            val doc = snapshots.documents[which]
                            launchPerformanceForClass(doc.id, doc.getString("className") ?: "", doc.getString("sectionName") ?: "", doc.getString("schoolId") ?: "", doc.getString("grade") ?: "")
                        }
                    )
                }
            }
            .addOnFailureListener { e ->
                activity?.let { GabAIUtils.hideGlobalLoading(it) }
                context?.let { GabAIUtils.showSnackbar(it, "Failed to load classes: ${e.message}") }
            }
    }

    private fun launchPerformanceForClass(classId: String, className: String, sectionName: String, schoolId: String, grade: String) {
        val ctx = context ?: return
        val perfIntent = Intent(ctx, TeacherPerformanceActivity::class.java).apply {
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