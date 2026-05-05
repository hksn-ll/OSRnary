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

        loadUserData()

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }

        return binding.root
    }

    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // TURN ON SPINNER
        GabAIUtils.showGlobalLoading(context)

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                // SAFELY TURN OFF SPINNER
                val safeContext = context
                if (safeContext != null) GabAIUtils.hideGlobalLoading(safeContext)

                if (doc.exists() && _binding != null && isAdded) {
                    val role = doc.getString("role") ?: "student"

                    binding.tvProfileName.text = "${doc.getString("firstName")} ${doc.getString("lastName")}"
                    binding.tvProfileEmail.text = doc.getString("email")

                    val sId = doc.getString("schoolId")
                    binding.tvProfileSchool.text = "School: ${schoolLookup[sId] ?: sId}"
                    // --- BEAUTIFUL DROPDOWN LOGIC ---
                    val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                    val languageOptions = arrayOf("English", "Taglish", "Tagalog")

// Explicitly tell the adapter it is for Strings
                    val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, languageOptions)

                    val autoText = binding.root.findViewById<AutoCompleteTextView>(R.id.actv_language)
                    autoText.setAdapter(adapter)

// Load the current saved preference (Default to English)
                    val currentLang = prefs.getString("ai_language_pref", "English") ?: "English"
                    autoText.setText(currentLang, false)

// Fix: Use explicit names (parent, view, position, id) instead of 'it' or '_'
                    autoText.setOnItemClickListener { parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long ->
                        val selected = languageOptions[position]
                        prefs.edit().putString("ai_language_pref", selected).apply()
                        com.example.gabai.GabAIUtils.showSnackbar(context, "AI will now explain in $selected")
                    }

                    if (role == "teacher") {
                        // FOR TEACHERS: Hide section/grade, show Join Code
                        binding.tvProfileSection.visibility = View.GONE
                        val joinCode = doc.getString("joinCode") ?: "N/A"
                        binding.tvProfileGrade.text = "Join Code: $joinCode"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#6C5CE7"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.BOLD)
                    } else {
                        // FOR STUDENTS: Show section/grade
                        binding.tvProfileSection.visibility = View.VISIBLE
                        binding.tvProfileSection.text = "Section: ${doc.getString("section")}"
                        binding.tvProfileGrade.text = "Grade: ${doc.getString("grade") ?: "N/A"}"
                        binding.tvProfileGrade.setTextColor(android.graphics.Color.parseColor("#636E72"))
                        binding.tvProfileGrade.setTypeface(null, android.graphics.Typeface.NORMAL)
                    }
                }
            }
            .addOnFailureListener {
                val safeContext = context
                if (safeContext != null) {
                    GabAIUtils.hideGlobalLoading(safeContext)
                    GabAIUtils.showSnackbar(safeContext, "Failed to load profile data.")
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}