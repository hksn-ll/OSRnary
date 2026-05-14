package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.AdapterView

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // School Mapping (same as Admin Portal)
    private val schoolLookup = mapOf("320402" to "Vicente P. Trinidad NHS", "305446" to "Sitero Francisco Memorial NHS")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        binding.root.findViewById<android.widget.Button>(R.id.btn_report_bug)?.setOnClickListener {
            GabAIUtils.showReportDialog(requireContext(), "Bug", "General App Bug")
        }
        loadUserData()

        binding.btnLogout.setOnClickListener {
            requireContext().stopService(Intent(requireContext(), FloatingControlService::class.java))
            requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", false).apply()
            requireContext().getSharedPreferences("OSRnary_XP", android.content.Context.MODE_PRIVATE).edit().clear().apply()

            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }

        return binding.root
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // TURN ON SPINNER
        GabAIUtils.showGlobalLoading(requireContext())

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                GabAIUtils.hideGlobalLoading(requireContext())

                if (doc.exists() && _binding != null && isAdded) {
                    val role = doc.getString("role") ?: "student"

                    binding.tvProfileName.text = "${doc.getString("firstName")} ${doc.getString("lastName")}"
                    binding.tvProfileEmail.text = doc.getString("email")

                    val sId = doc.getString("schoolId")
                    binding.tvProfileSchool.text = "School: ${schoolLookup[sId] ?: sId}"

                    // --- DROPDOWN LOGIC ---
                    val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                    val languageOptions = arrayOf("English", "Taglish", "Tagalog")

                    val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, languageOptions)
                    val autoText = binding.root.findViewById<AutoCompleteTextView>(R.id.actv_language)
                    autoText.setAdapter(adapter)

                    val currentLang = prefs.getString("ai_language_pref", "English") ?: "English"
                    autoText.setText(currentLang, false)

                    autoText.setOnItemClickListener { _: AdapterView<*>, _: android.view.View?, position: Int, _: Long ->
                        val selected = languageOptions[position]
                        prefs.edit().putString("ai_language_pref", selected).apply()
                        GabAIUtils.showSnackbar(requireContext(), "AI will now explain in $selected")
                    }

                    if (role == "teacher") {
                        // Update to Teacher PNG
                        binding.profileImage.setImageResource(R.drawable.ic_teacher_avatar)

                        // FOR TEACHERS: Hide section/grade, show Join Code
                        binding.tvProfileSection.visibility = View.GONE
                        val joinCode = doc.getString("joinCode") ?: "N/A"
                        binding.tvProfileGrade.text = "Join Code: $joinCode"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#6C5CE7"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.BOLD)

                        binding.root.findViewById<View>(R.id.container_preferences)?.visibility = View.GONE
                    } else {
                        // Update to Student PNG
                        binding.profileImage.setImageResource(R.drawable.ic_student_avatar)

                        // FOR STUDENTS: Show section/grade
                        binding.tvProfileSection.visibility = View.VISIBLE
                        binding.tvProfileSection.text = "Section: ${doc.getString("section")}"
                        binding.tvProfileGrade.text = "Grade: ${doc.getString("grade") ?: "N/A"}"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#636E72"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.NORMAL)

                        binding.root.findViewById<View>(R.id.container_preferences)?.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener {
                GabAIUtils.hideGlobalLoading(requireContext())
                GabAIUtils.showSnackbar(requireContext(), "Failed to load profile data.")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}