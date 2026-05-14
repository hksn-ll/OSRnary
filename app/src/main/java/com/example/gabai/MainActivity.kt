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
        // 🟢 NEW: Add this permission launcher right here, above onCreate!
         val requestPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                GabAIUtils.showSnackbar(this, "Camera permission is required to scan books.")
            }
        }
        setContentView(binding.root)
        GabAIUtils.checkForCrashes(this)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        // Adjust padding for system bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val systemBars =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        // 🟢 NEW: Set up the center FAB to launch the Text Scanner
        // 🟢 NEW: Set up the Extended FAB to actually request permission
        val fabScanText = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_scan_text)
        fabScanText.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(this, CameraActivity::class.java))
            } else {
                // DIRECTLY ASK FOR PERMISSION HERE
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
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
    // New helper to handle fragment loading and role memory
    private fun loadDashboard(role: String) {
        binding.root.tag = role // Save role so the bottom menu knows which one to show

        // Hide the Scanner FAB for Teachers
        val fabScanText = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_scan_text)
        if (role == "teacher") {
            fabScanText.visibility = android.view.View.GONE
        } else {
            fabScanText.visibility = android.view.View.VISIBLE
        }

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