package com.example.gabai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        val userRole = intent.getStringExtra("USER_ROLE")
        if (userRole != null) {
            loadDashboard(userRole)
        } else {
            // 1. SHOW THE LOADER IMMEDIATELY HERE
            GabAIUtils.showGlobalLoading(this, "Verifying Account...")

            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get().addOnSuccessListener { doc ->
                    GabAIUtils.hideGlobalLoading(this)

                    // ==========================================
                    // 🟢 THE FIX: CHECK IF DOCUMENT EXISTS 🟢
                    // ==========================================
                    if (!doc.exists()) {
                        showAccountDeletedDialog()
                        return@addOnSuccessListener
                    }
                    // ==========================================

                    val role = doc.getString("role") ?: "student"
                    loadDashboard(role)
                }.addOnFailureListener {
                    GabAIUtils.hideGlobalLoading(this)
                    GabAIUtils.showSnackbar(this, "Failed to verify account. Check your connection.")
                }
        }

        // Adjust padding for system bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val systemBars =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // 1. Get the currently selected tab ID
            val currentTab = binding.bottomNavigation.selectedItemId

            // 2. ONLY switch if the user clicked a DIFFERENT tab
            if (item.itemId != currentTab) {
                val currentRole = binding.root.tag as? String ?: "student"
                when (item.itemId) {
                    R.id.nav_home -> {
                        if (currentRole == "teacher") loadFragment(TeacherHomeFragment())
                        else loadFragment(HomeFragment())
                    }

                    R.id.nav_profile -> loadFragment(ProfileFragment())
                }
            }
            true
        }
    }

    // ==========================================
    // 🟢 NEW FUNCTION: FORCE LOGOUT DIALOG 🟢
    // ==========================================
    private fun showAccountDeletedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Account Not Found")
            .setMessage("Your account details could not be found. It may have been deleted by your adviser or school administrator.\n\nPlease log out.")
            .setCancelable(false) // Forces them to click the button
            .setPositiveButton("Log Out") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
            .show()
    }

    // New helper to handle fragment loading and role memory
    private fun loadDashboard(role: String) {
        binding.root.tag = role // Save role so the bottom menu knows which one to show
        if (role == "teacher") loadFragment(TeacherHomeFragment())
        else loadFragment(HomeFragment())
    }

    private fun loadFragment(fragment: Fragment) {
        // Safety check to ensure the Activity is still active
        if (isFinishing || isDestroyed) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            // Use commitAllowingStateLoss for fast navigation
            .commitAllowingStateLoss()
    }
}