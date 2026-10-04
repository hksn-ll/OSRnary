package com.example.gabai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        val cachedRole = requireContext().getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
            .getString("cached_user_role", "student") ?: "student"
        applyRoleVisibility(cachedRole)

        setupButtons()
        loadUserData()

        binding.tvAppVersionLabel.text = "v${BuildConfig.VERSION_NAME}"
        binding.rowCheckUpdate.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            GitHubUpdateHelper.checkUpdate(act, forceShow = true) {}
        }

        binding.btnLogout.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            GabAIDialogs.showConfirmDialog(
                ctx,
                title = "Sign Out",
                message = "Are you sure you want to sign out of GabAI?",
                confirmText = "Sign Out",
                isDestructive = true,
                badgeIcon = "🚪"
            ) {
                // Stop the bubble service and reset toggle
                requireContext().stopService(Intent(requireContext(), FloatingControlService::class.java))
                requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", false).apply()

                FirebaseAuth.getInstance().signOut()
                val intent = Intent(requireContext(), AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playEntranceAnimation()
    }

    fun playEntranceAnimation() {
        if (_binding == null || !isAdded) return
        val isTeacher = binding.llTeacherStatsContainer.visibility == View.VISIBLE

        val viewsToAnimate = if (isTeacher) {
            listOfNotNull(
                binding.headerProfile,
                binding.cardUserIdentity,
                binding.headerProfileStats,
                binding.cardTeacherStatClasses,
                binding.cardTeacherStatStudents,
                binding.cardTeacherStatMaterials,
                binding.cardTeacherStatQuizzes,
                binding.headerTeacherShortcuts,
                binding.btnTeacherProfileClasses,
                binding.btnTeacherProfileLibrary,
                binding.headerPreferences,
                binding.cardPreferences
            )
        } else {
            listOfNotNull(
                binding.headerProfile,
                binding.cardUserIdentity,
                binding.headerProfileStats,
                binding.cardStatWordsMastered,
                binding.cardStatAccuracy,
                binding.cardStatQuizzesTaken,
                binding.cardStatStreak,
                binding.headerStudentHubs,
                binding.btnProfileProgress,
                binding.btnProfileBadges,
                binding.btnProfileLeaderboard,
                binding.btnProfileFavorites,
                binding.headerPreferences,
                binding.cardPreferences
            )
        }
        GabAIUtils.animateCascade(viewsToAnimate, baseDelay = 35L, startDelayOffset = 180L)
    }

    private fun applyRoleVisibility(role: String) {
        if (role == "teacher") {
            binding.tvProfileSubtitle.text = "Educator credentials & account settings"
            binding.llStudentGradeSectionRow.visibility = View.GONE
            binding.llTeacherCodeRow.visibility = View.VISIBLE
            binding.tvStatsHeader.text = "TEACHING STATS"
            binding.llStudentStatsContainer.visibility = View.GONE
            binding.llTeacherStatsContainer.visibility = View.VISIBLE
            binding.llStudentHubsContainer.visibility = View.GONE
            binding.llTeacherShortcutsContainer.visibility = View.VISIBLE
            binding.llAiLanguageContainer.visibility = View.GONE
        } else {
            binding.tvProfileSubtitle.text = "Personal learning identity & account settings"
            binding.llStudentGradeSectionRow.visibility = View.VISIBLE
            binding.llTeacherCodeRow.visibility = View.GONE
            binding.tvStatsHeader.text = "LEARNING STATS"
            binding.llStudentStatsContainer.visibility = View.VISIBLE
            binding.llTeacherStatsContainer.visibility = View.GONE
            binding.llStudentHubsContainer.visibility = View.VISIBLE
            binding.llTeacherShortcutsContainer.visibility = View.GONE
            binding.llAiLanguageContainer.visibility = View.VISIBLE
        }
    }

    private fun setupButtons() {
        // Student Learning Hub Shortcuts
        GabAIUtils.addSpringPressEffect(binding.btnProfileProgress) {
            startActivity(Intent(requireContext(), ProgressDashboardActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnProfileBadges) {
            startActivity(Intent(requireContext(), AchievementsActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnProfileLeaderboard) {
            startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnProfileFavorites) {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }

        // Teacher Shortcuts
        GabAIUtils.addSpringPressEffect(binding.btnTeacherProfileClasses) {
            startActivity(Intent(requireContext(), ManageClassesActivity::class.java))
        }

        GabAIUtils.addSpringPressEffect(binding.btnTeacherProfileLibrary) {
            startActivity(Intent(requireContext(), TeacherLibraryActivity::class.java))
        }
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && _binding != null && isAdded) {
                    val firstName = doc.getString("firstName") ?: ""
                    val lastName = doc.getString("lastName") ?: ""
                    val role = doc.getString("role") ?: "student"
                    val email = doc.getString("email") ?: FirebaseAuth.getInstance().currentUser?.email ?: ""
                    val sId = doc.getString("schoolId")

                    val initial = firstName.firstOrNull()?.uppercaseChar()?.toString()
                        ?: lastName.firstOrNull()?.uppercaseChar()?.toString()
                        ?: "G"
                    binding.tvAvatarMonogram.text = initial

                    val fullName = "$firstName $lastName".trim()
                    binding.tvProfileName.text = if (fullName.isNotEmpty()) fullName else "Learner"
                    binding.tvProfileEmail.text = email
                    binding.tvProfileRoleBadge.text = role.uppercase()

                    val schoolName = SchoolRepository.getSchoolName(sId)
                    binding.tvProfileSchool.text = "School: $schoolName"

                    applyRoleVisibility(role)

                    if (role == "teacher") {
                        val joinCode = doc.getString("joinCode") ?: "N/A"
                        binding.tvProfileTeacherCode.text = "Join Code: $joinCode"

                        GabAIUtils.addSpringPressEffect(binding.btnTeacherQrPill) {
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

                        // Load Educator Stats
                        loadTeacherStats(uid, db)
                    } else {
                        binding.tvProfileSection.text = "Section: ${doc.getString("section") ?: "N/A"}"
                        binding.tvProfileGrade.text = "Grade: ${doc.getString("grade") ?: "N/A"}"

                        setupLanguageDropdown()

                        // Load Student Stats
                        loadLifetimeStats(uid, db)
                    }
                }
            }
            .addOnFailureListener {
                val safeContext = context
                if (safeContext != null && isAdded) {
                    GabAIUtils.showSnackbar(safeContext, "Failed to load profile data.")
                }
            }
    }

    private fun loadTeacherStats(uid: String, db: FirebaseFirestore) {
        // Classes & Enrolled Students
        db.collection("classes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvTeacherStatClasses.text = snapshots.size().toString()

                    var totalStudents = 0
                    for (doc in snapshots.documents) {
                        val joined = doc.get("joinedStudents") as? List<*>
                        totalStudents += joined?.size ?: 0
                    }
                    binding.tvTeacherStatStudents.text = totalStudents.toString()
                }
            }

        // Materials Uploaded
        db.collection("library_materials")
            .whereEqualTo("uploadedBy", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvTeacherStatMaterials.text = snapshots.size().toString()
                }
            }

        // Quizzes Authored
        db.collection("quizzes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.tvTeacherStatQuizzes.text = snapshots.size().toString()
                }
            }
    }

    private fun loadLifetimeStats(uid: String, db: FirebaseFirestore) {
        val ctx = context ?: return

        // 1. Study Streak
        val streak = QuestManager.getStreak(ctx)
        binding.tvStatStreak.text = "${streak}d"

        // 2. Words Mastered (spaced repetition interval >= 4)
        db.collection("users").document(uid).collection("history")
            .whereGreaterThanOrEqualTo("interval", 4)
            .get()
            .addOnSuccessListener { qs ->
                if (_binding != null && isAdded) {
                    binding.tvStatWordsMastered.text = qs.size().toString()
                }
            }

        // 3. Quizzes Taken & Overall Accuracy
        db.collection("users").document(uid).collection("quiz_history")
            .get()
            .addOnSuccessListener { qs ->
                if (_binding != null && isAdded) {
                    val count = qs.size()
                    binding.tvStatQuizzesTaken.text = count.toString()
                    if (count > 0) {
                        var totalScore = 0.0
                        var totalPossible = 0.0
                        for (d in qs.documents) {
                            val score = d.getDouble("score") ?: 0.0
                            val total = d.getDouble("totalQuestions") ?: d.getDouble("total") ?: 0.0
                            if (total > 0) {
                                totalScore += score
                                totalPossible += total
                            }
                        }
                        val accuracyPct = if (totalPossible > 0) ((totalScore / totalPossible) * 100).toInt() else 0
                        binding.tvStatAccuracy.text = "$accuracyPct%"
                    } else {
                        binding.tvStatAccuracy.text = "0%"
                    }
                }
            }
    }

    private fun setupLanguageDropdown() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
        val languageOptions = arrayOf("English", "Taglish", "Tagalog")
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, languageOptions)
        val autoText = binding.root.findViewById<AutoCompleteTextView>(R.id.actv_language) ?: return
        autoText.setAdapter(adapter)

        val currentLang = prefs.getString("ai_language_pref", "English") ?: "English"
        autoText.setText(currentLang, false)

        autoText.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            prefs.edit().putString("ai_language_pref", selected).apply()
            GabAIUtils.showSnackbar(context, "AI will now explain in $selected")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}