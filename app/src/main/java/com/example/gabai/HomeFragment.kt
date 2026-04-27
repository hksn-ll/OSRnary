package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentHomeBinding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.os.Build
import android.media.projection.MediaProjectionConfig

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // This handles the pop-up result
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission allowed! Open the camera
            startActivity(Intent(requireContext(), CameraActivity::class.java))
        } else {
            // Permission denied
            Toast.makeText(requireContext(), "Camera permission is required to scan books.", Toast.LENGTH_LONG).show()
        }
    }


    // This handles the result of the screen capture permission
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val intent = Intent(requireContext(), FloatingControlService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            requireContext().startService(intent)

            // Save state as "ON"
            requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", true).apply()

            Toast.makeText(context, "Bubble Active!", Toast.LENGTH_SHORT).show()
        } else {
            binding.bubbleSwitch.isChecked = false
            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupDashboard()
        return binding.root
    }

    private fun setupDashboard() {
        val mediaProjectionManager = requireContext().getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)

        // 1. Get the saved state
        val isEnabled = prefs.getBoolean("bubble_enabled", false)

        // 2. CLEAR the listener before setting the state to prevent a loop
        binding.bubbleSwitch.setOnCheckedChangeListener(null)
        binding.bubbleSwitch.isChecked = isEnabled
        // --- ADD THIS LINE INSIDE setupDashboard() ---
        binding.btnOpenLibrary.setOnClickListener {
            startActivity(Intent(requireContext(), LibraryActivity::class.java))
        }
        // 3. NOW set the listener for actual user clicks
        binding.bubbleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(requireContext())) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))
                    startActivity(intent)
                    binding.bubbleSwitch.isChecked = false
                } else {
                    // Only launch permission if the user manually turned it on

                    // --- REPLACE THE OLD LAUNCH LINE WITH THIS BLOCK ---
                    // Force "Entire Screen" capture and skip the app selection dialog on Android 14+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val config = MediaProjectionConfig.createConfigForDefaultDisplay()
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent(config))
                    } else {
                        // For older versions, use the standard intent
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                    // ---------------------------------------------------

                }
            } else {
                // STOP the service and save state as OFF
                requireContext().stopService(Intent(requireContext(), FloatingControlService::class.java))
                prefs.edit().putBoolean("bubble_enabled", false).apply()
            }
        }

        // Rest of your buttons (Camera, Quiz, etc.)
        binding.btnOpenCamera.setOnClickListener {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(requireContext(), CameraActivity::class.java))
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
        binding.btnStartQuiz.setOnClickListener { startActivity(Intent(requireContext(), QuizActivity::class.java)) }
        binding.btnFavs.setOnClickListener { startActivity(Intent(requireContext(), FavoritesActivity::class.java)) }
        binding.btnHistory.setOnClickListener { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Real-time listener for user progress (Requirement 7.3)
        db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
            val currentXP = snapshot.getLong("current_xp")?.toInt() ?: 0
            val maxXP = 100 // Based on your XPManager.kt logic

            // Update UI components from fragment_home.xml
            binding.tvLevelLabel.text = "LEVEL $currentLevel"
            binding.tvXpLabel.text = "$currentXP / $maxXP XP"
            binding.xpProgressBar.progress = currentXP
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}