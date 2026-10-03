package com.example.gabai

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        setupLanguageDropdown()
        loadUserData()
        loadLifetimeStats()

        binding.tvAppVersionLabel.text = "v${BuildConfig.VERSION_NAME}"
        binding.rowCheckUpdate.setOnClickListener {
            val act = activity ?: return@setOnClickListener
            GitHubUpdateHelper.checkUpdate(act, forceShow = true) {}
        }

        binding.btnLogout.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of GabAI?")
                .setPositiveButton("Sign Out") { _, _ ->
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
                .setNegativeButton("Cancel", null)
                .show()
        }

        return binding.root
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
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

                    if (role == "teacher") {
                        binding.tvProfileSection.visibility = View.GONE
                        val joinCode = doc.getString("joinCode") ?: "N/A"
                        binding.tvProfileGrade.text = "Join Code: $joinCode"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#5341CD"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        binding.tvProfileSection.visibility = View.VISIBLE
                        binding.tvProfileSection.text = "Section: ${doc.getString("section") ?: "N/A"}"
                        binding.tvProfileGrade.text = "Grade: ${doc.getString("grade") ?: "N/A"}"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#334155"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.BOLD)
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

    private fun loadLifetimeStats() {
        val ctx = context ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Study Streak
        val streak = QuestManager.getStreak(ctx)
        binding.tvStatStreak.text = "${streak}d"

        // 2. Words Mastered (spaced repetition interval >= 4)
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("history")
            .whereGreaterThanOrEqualTo("interval", 4)
            .get()
            .addOnSuccessListener { qs ->
                if (_binding != null && isAdded) {
                    binding.tvStatWordsMastered.text = qs.size().toString()
                }
            }

        // 3. Quizzes Taken & Overall Accuracy
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("quiz_history")
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
        val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
        val languageOptions = arrayOf("English", "Taglish", "Tagalog")
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, languageOptions)
        val autoText = binding.root.findViewById<AutoCompleteTextView>(R.id.actv_language)
        autoText.setAdapter(adapter)

        val currentLang = prefs.getString("ai_language_pref", "English") ?: "English"
        autoText.setText(currentLang, false)

        autoText.setOnItemClickListener { parent: AdapterView<*>, _, position: Int, _ ->
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