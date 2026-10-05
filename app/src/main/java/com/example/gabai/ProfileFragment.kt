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
import androidx.lifecycle.lifecycleScope
import com.example.gabai.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        val prefs = requireContext().getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val cachedRole = prefs.getString("cached_user_role", "student") ?: "student"
        val cachedFullName = prefs.getString("cached_full_name", "") ?: ""
        val cachedFirstName = prefs.getString("cached_first_name", "") ?: ""
        val cachedLastName = prefs.getString("cached_last_name", "") ?: ""

        if (cachedFullName.isNotBlank()) {
            binding.tvProfileName.text = cachedFullName
            val initial = cachedFullName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "G"
            binding.tvAvatarMonogram.text = initial
        } else if (cachedFirstName.isNotBlank()) {
            val constructed = if (cachedLastName.isNotBlank()) "$cachedFirstName $cachedLastName" else cachedFirstName
            binding.tvProfileName.text = constructed
            binding.tvAvatarMonogram.text = cachedFirstName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
        } else {
            val fbName = FirebaseAuth.getInstance().currentUser?.displayName
            if (!fbName.isNullOrBlank()) {
                binding.tvProfileName.text = fbName
                binding.tvAvatarMonogram.text = fbName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
            }
        }

        applyRoleVisibility(cachedRole)

        setupButtons()
        loadUserData()

        binding.tvAppVersionLabel.text = "v${BuildConfig.VERSION_NAME}"

        var devEasterEggTaps = 0
        var lastDevTapTime = 0L
        binding.tvAppVersionLabel.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastDevTapTime > 2000L) {
                devEasterEggTaps = 0
            }
            lastDevTapTime = now
            devEasterEggTaps++

            val ctx = context ?: return@setOnClickListener
            if (devEasterEggTaps >= 5) {
                devEasterEggTaps = 0
                android.widget.Toast.makeText(ctx, "🛠️ Welcome to Developer Component Lab!", android.widget.Toast.LENGTH_SHORT).show()
                startActivity(android.content.Intent(ctx, DevEasterEggActivity::class.java))
            } else if (devEasterEggTaps >= 2) {
                val remaining = 5 - devEasterEggTaps
                android.widget.Toast.makeText(ctx, "$remaining more taps to open Developer Lab", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

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
                binding.cardUserIdentity,
                binding.llTeacherStatsContainer,
                binding.llTeacherShortcutsContainer,
                binding.cardPreferences
            )
        } else {
            listOfNotNull(
                binding.cardUserIdentity,
                binding.llStudentStatsContainer,
                binding.llStudentHubsContainer,
                binding.cardPreferences
            )
        }

        GabAIUtils.animateCascade(viewsToAnimate, baseDelay = 25L, startDelayOffset = 0L)
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

        binding.tvProfileGrade.text = "Grade: ..."
        binding.tvProfileSection.text = "Section: ..."

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && _binding != null && isAdded) {
                    val firstName = doc.getString("firstName")
                        ?: doc.getString("first_name")
                        ?: ""
                    val lastName = doc.getString("lastName")
                        ?: doc.getString("last_name")
                        ?: ""
                    val explicitName = doc.getString("name")
                        ?: doc.getString("fullName")
                        ?: doc.getString("displayName")
                        ?: ""
                    val role = doc.getString("role") ?: "student"
                    val email = doc.getString("email") ?: FirebaseAuth.getInstance().currentUser?.email ?: ""
                    val sId = doc.getString("schoolId")

                    val fullName = when {
                        firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                        firstName.isNotBlank() -> firstName
                        explicitName.isNotBlank() -> explicitName
                        lastName.isNotBlank() -> lastName
                        else -> {
                            val fbName = FirebaseAuth.getInstance().currentUser?.displayName
                            if (!fbName.isNullOrBlank()) {
                                fbName
                            } else if (email.isNotBlank()) {
                                email.substringBefore("@")
                                    .replace(".", " ")
                                    .replace("_", " ")
                                    .split(" ")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                            } else {
                                if (role == "teacher") "Educator" else "Student Scholar"
                            }
                        }
                    }

                    binding.tvProfileName.text = fullName

                    val initial = firstName.firstOrNull()?.uppercaseChar()?.toString()
                        ?: lastName.firstOrNull()?.uppercaseChar()?.toString()
                        ?: fullName.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
                        ?: "G"
                    binding.tvAvatarMonogram.text = initial

                    // Cache for zero-latency instant loading next time
                    requireContext().getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_user_role", role)
                        .putString("cached_first_name", firstName)
                        .putString("cached_last_name", lastName)
                        .putString("cached_full_name", fullName)
                        .apply()

                    val displayEmail = if (email.endsWith("@gabai.app", ignoreCase = true)) {
                        email.substringBefore("@gabai.app")
                    } else {
                        email
                    }
                    binding.tvProfileEmail.text = displayEmail
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
                        val rawSection = doc.getString("section")?.trim() ?: ""
                        binding.tvProfileSection.text = when {
                            rawSection.startsWith("Section", ignoreCase = true) -> rawSection
                            rawSection.isNotBlank() && rawSection != "N/A" -> "Section: $rawSection"
                            else -> "Section: -"
                        }

                        val rawGrade = doc.getString("grade")?.trim() ?: ""
                        binding.tvProfileGrade.text = when {
                            rawGrade.startsWith("Grade", ignoreCase = true) -> rawGrade
                            rawGrade.isNotBlank() && rawGrade != "N/A" -> "Grade $rawGrade"
                            else -> "Grade: -"
                        }

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
                    binding.loadingTeacherStatClasses.visibility = View.GONE
                    binding.loadingTeacherStatStudents.visibility = View.GONE
                    binding.tvTeacherStatClasses.visibility = View.VISIBLE
                    binding.tvTeacherStatStudents.visibility = View.VISIBLE

                    binding.tvTeacherStatClasses.text = snapshots.size().toString()

                    var totalStudents = 0
                    for (doc in snapshots.documents) {
                        val joined = doc.get("joinedStudents") as? List<*>
                        totalStudents += joined?.size ?: 0
                    }
                    binding.tvTeacherStatStudents.text = totalStudents.toString()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    binding.loadingTeacherStatClasses.visibility = View.GONE
                    binding.loadingTeacherStatStudents.visibility = View.GONE
                    binding.tvTeacherStatClasses.visibility = View.VISIBLE
                    binding.tvTeacherStatStudents.visibility = View.VISIBLE
                    binding.tvTeacherStatClasses.text = "0"
                    binding.tvTeacherStatStudents.text = "0"
                }
            }

        // Materials Uploaded
        db.collection("library_materials")
            .whereEqualTo("uploadedBy", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.loadingTeacherStatMaterials.visibility = View.GONE
                    binding.tvTeacherStatMaterials.visibility = View.VISIBLE
                    binding.tvTeacherStatMaterials.text = snapshots.size().toString()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    binding.loadingTeacherStatMaterials.visibility = View.GONE
                    binding.tvTeacherStatMaterials.visibility = View.VISIBLE
                    binding.tvTeacherStatMaterials.text = "0"
                }
            }

        // Quizzes Authored
        db.collection("quizzes")
            .whereEqualTo("teacherId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                if (_binding != null && isAdded) {
                    binding.loadingTeacherStatQuizzes.visibility = View.GONE
                    binding.tvTeacherStatQuizzes.visibility = View.VISIBLE
                    binding.tvTeacherStatQuizzes.text = snapshots.size().toString()
                }
            }
            .addOnFailureListener {
                if (_binding != null && isAdded) {
                    binding.loadingTeacherStatQuizzes.visibility = View.GONE
                    binding.tvTeacherStatQuizzes.visibility = View.VISIBLE
                    binding.tvTeacherStatQuizzes.text = "0"
                }
            }
    }

    private fun loadLifetimeStats(uid: String, db: FirebaseFirestore) {
        val ctx = context ?: return

        // 1. Study Streak (Instant from local SharedPreferences)
        val streak = QuestManager.getStreak(ctx)
        binding.loadingStatStreak.visibility = View.GONE
        binding.tvStatStreak.visibility = View.VISIBLE
        binding.tvStatStreak.text = "${streak}d"

        // Instant render from local cache if available (0ms freeze)
        val prefs = ctx.getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val cachedWords = prefs.getString("cached_words_mastered_$uid", null)
        val cachedQuizzes = prefs.getString("cached_quizzes_taken_$uid", null)
        val cachedAccuracy = prefs.getString("cached_quiz_accuracy_$uid", null)

        if (cachedWords != null) {
            binding.loadingStatWordsMastered.visibility = View.GONE
            binding.tvStatWordsMastered.visibility = View.VISIBLE
            binding.tvStatWordsMastered.text = cachedWords
        }
        if (cachedQuizzes != null && cachedAccuracy != null) {
            binding.loadingStatQuizzesTaken.visibility = View.GONE
            binding.loadingStatAccuracy.visibility = View.GONE
            binding.tvStatQuizzesTaken.visibility = View.VISIBLE
            binding.tvStatAccuracy.visibility = View.VISIBLE
            binding.tvStatQuizzesTaken.text = cachedQuizzes
            binding.tvStatAccuracy.text = cachedAccuracy
        }

        // Run heavy Firestore queries & deserialization strictly on background Dispatchers.IO
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 2. Words Mastered (spaced repetition interval >= 4)
                val wordsCount = try {
                    val qs = db.collection("users").document(uid).collection("history")
                        .whereGreaterThanOrEqualTo("interval", 4)
                        .get().await()
                    qs.size().toString()
                } catch (_: Exception) {
                    cachedWords ?: "0"
                }

                // 3. Quizzes Taken & Overall Accuracy
                val (quizzesCount, accuracyPct) = try {
                    val quizDocs = db.collection("users").document(uid).collection("quiz_history")
                        .get().await()
                    val count = quizDocs.size()
                    if (count > 0) {
                        var totalScore = 0.0
                        var totalPossible = 0.0
                        for (d in quizDocs.documents) {
                            val score = d.getDouble("finalScore")
                                ?: d.getLong("finalScore")?.toDouble()
                                ?: d.getDouble("score")
                                ?: d.getLong("score")?.toDouble()
                                ?: 0.0

                            val total = d.getDouble("totalAttempts")
                                ?: d.getLong("totalAttempts")?.toDouble()
                                ?: d.getDouble("totalQuestions")
                                ?: d.getLong("totalQuestions")?.toDouble()
                                ?: d.getDouble("total")
                                ?: d.getLong("total")?.toDouble()
                                ?: 0.0

                            if (total > 0) {
                                totalScore += score
                                totalPossible += total
                            }
                        }
                        val acc = if (totalPossible > 0) ((totalScore / totalPossible) * 100).toInt() else 0
                        Pair(count.toString(), "$acc%")
                    } else {
                        Pair("0", "0%")
                    }
                } catch (_: Exception) {
                    Pair(cachedQuizzes ?: "0", cachedAccuracy ?: "0%")
                }

                // Update disk cache
                prefs.edit()
                    .putString("cached_words_mastered_$uid", wordsCount)
                    .putString("cached_quizzes_taken_$uid", quizzesCount)
                    .putString("cached_quiz_accuracy_$uid", accuracyPct)
                    .apply()

                // Update UI on Main thread without dropping a frame
                withContext(Dispatchers.Main) {
                    if (_binding != null && isAdded) {
                        binding.loadingStatWordsMastered.visibility = View.GONE
                        binding.tvStatWordsMastered.visibility = View.VISIBLE
                        binding.tvStatWordsMastered.text = wordsCount

                        binding.loadingStatQuizzesTaken.visibility = View.GONE
                        binding.loadingStatAccuracy.visibility = View.GONE
                        binding.tvStatQuizzesTaken.visibility = View.VISIBLE
                        binding.tvStatAccuracy.visibility = View.VISIBLE
                        binding.tvStatQuizzesTaken.text = quizzesCount
                        binding.tvStatAccuracy.text = accuracyPct
                    }
                }
            } catch (_: Exception) {}
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