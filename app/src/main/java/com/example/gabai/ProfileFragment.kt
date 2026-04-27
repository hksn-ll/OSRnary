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
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                // NEW: Only update if the binding still exists and fragment is attached
                if (doc.exists() && _binding != null && isAdded) {
                    binding.tvProfileName.text = "${doc.getString("firstName")} ${doc.getString("lastName")}"
                    binding.tvProfileEmail.text = doc.getString("email")
                    binding.tvProfileSection.text = "Section: ${doc.getString("section")}"
                    binding.tvProfileGrade.text = "Grade: ${doc.getString("grade") ?: "N/A"}"
                    val sId = doc.getString("schoolId")
                    binding.tvProfileSchool.text = "School: ${schoolLookup[sId] ?: sId}"

                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}