package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.ActivityMainBinding
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

        // FIX: Check if role was passed, otherwise fetch from database
        val userRole = intent.getStringExtra("USER_ROLE")
        if (userRole != null) {
            loadDashboard(userRole)
        } else {
            // FETCH FROM FIRESTORE if we don't know the role (e.g., app restart)
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get().addOnSuccessListener { doc ->
                    val role = doc.getString("role") ?: "student"
                    loadDashboard(role)
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