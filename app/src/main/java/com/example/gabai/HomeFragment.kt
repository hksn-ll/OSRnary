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
    private var currentCompletedQuests: List<String> = emptyList()

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
            val triggerUid = FirebaseAuth.getInstance().currentUser?.uid
            if (triggerUid != null) {
                FirebaseFirestore.getInstance().collection("users").document(triggerUid)
                    .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("bubble"))
            }
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

        if (!FloatingControlService.isRunning) {
            prefs.edit().putBoolean("bubble_enabled", false).apply()
        }
        // 1. Get the saved state
        val isEnabled = prefs.getBoolean("bubble_enabled", false)

        // 2. CLEAR the listener before setting the state to prevent a loop
        binding.bubbleSwitch.setOnCheckedChangeListener(null)
        binding.bubbleSwitch.isChecked = isEnabled
        // --- ADD THIS LINE INSIDE setupDashboard() ---
        binding.btnOpenLibrary.setOnClickListener {
            // Check if the bubble is actually turned on
            val isBubbleActive = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("bubble_enabled", false)

            if (isBubbleActive) {
                startActivity(Intent(requireContext(), LibraryActivity::class.java))
            } else {
                // Block them and show a message!
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Companion Required 🧚‍♂️")
                    .setMessage("You must turn on the GabAI Floating Bubble before entering the Digital Library so I can assist you with your reading!")
                    .setPositiveButton("Understood", null)
                    .show()
            }
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
        // --- ADD THIS TO LAUNCH THE READER ---
        binding.root.findViewById<android.widget.Button>(R.id.btn_quest_details)?.setOnClickListener {
            showQuestDetailsDialog()
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Real-time listener for user progress (Requirement 7.3)
        // Real-time listener for user progress & onboarding
        db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
            val currentXP = snapshot.getLong("current_xp")?.toInt() ?: 0
            val maxXP = 100

            // Onboarding gamification checks
            val isOnboarded = snapshot.getBoolean("is_onboarded") ?: false
            val completedQuests = snapshot.get("quests_completed") as? List<String> ?: listOf()
            requireContext().getSharedPreferences("OSRnary_XP", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("is_onboarded", isOnboarded).apply()
            currentCompletedQuests = completedQuests // Save for the dialog

            // --- THE GRAND UNLOCK LOGIC ---
            // --- THE GRAND UNLOCK LOGIC ---
            // --- THE GRAND UNLOCK LOGIC (NOW REQUIRES 8 QUESTS) ---
            val requiredQuests = listOf("bubble", "read", "test", "scan", "save", "history", "detail", "library")
            if (!isOnboarded && completedQuests.containsAll(requiredQuests)) {
                // Permanently unlock their account in the database
                db.collection("users").document(uid).update("is_onboarded", true)

                // Show the massive engaging cheer!
                showGrandUnlockCelebration()
                return@addSnapshotListener
            }

            if (isOnboarded) {
                // UI: UNLOCKED STATE
                binding.root.findViewById<View>(R.id.locked_xp_view).visibility = View.GONE
                binding.root.findViewById<View>(R.id.unlocked_xp_view).visibility = View.VISIBLE
                binding.root.findViewById<View>(R.id.quest_board_container).visibility = View.GONE

                binding.tvLevelLabel.text = "LEVEL $currentLevel"
                binding.tvXpLabel.text = "$currentXP / $maxXP XP"
                binding.xpProgressBar.progress = currentXP
            } else {
                // UI: LOCKED STATE (Apprentice Mode)
                binding.root.findViewById<View>(R.id.locked_xp_view).visibility = View.VISIBLE
                binding.root.findViewById<View>(R.id.unlocked_xp_view).visibility = View.INVISIBLE
                binding.root.findViewById<View>(R.id.quest_board_container).visibility = View.VISIBLE

                // Find all 8 text views
                val qBubble = binding.root.findViewById<android.widget.TextView>(R.id.quest_bubble)
                val qRead = binding.root.findViewById<android.widget.TextView>(R.id.quest_read)
                val qTest = binding.root.findViewById<android.widget.TextView>(R.id.quest_test)
                val qScan = binding.root.findViewById<android.widget.TextView>(R.id.quest_scan)
                val qSave = binding.root.findViewById<android.widget.TextView>(R.id.quest_save)
                val qHistory = binding.root.findViewById<android.widget.TextView>(R.id.quest_history)
                val qDetail = binding.root.findViewById<android.widget.TextView>(R.id.quest_detail)
                val qLibrary = binding.root.findViewById<android.widget.TextView>(R.id.quest_library)

                // Update text to show checkmarks
                qBubble.text = if (completedQuests.contains("bubble")) "✅ 1. Activate the Floating Bubble" else "❌ 1. Activate the Floating Bubble"
                qRead.text = if (completedQuests.contains("read")) "✅ 2. Read the 4 Required Materials" else "❌ 2. Read the 4 Required Materials"
                qTest.text = if (completedQuests.contains("test")) "✅ 3. Pass the Initiation Test" else "❌ 3. Pass the Initiation Test"
                qScan.text = if (completedQuests.contains("scan")) "✅ 4. Scan a text with the Camera" else "❌ 4. Scan a text with the Camera"
                qSave.text = if (completedQuests.contains("save")) "✅ 5. Save a word to Favorites" else "❌ 5. Save a word to Favorites"
                qHistory.text = if (completedQuests.contains("history")) "✅ 6. Check your Scan History" else "❌ 6. Check your Scan History"
                qDetail.text = if (completedQuests.contains("detail")) "✅ 7. View Saved Content" else "❌ 7. View Saved Content"
                qLibrary.text = if (completedQuests.contains("library")) "✅ 8. Open the Digital Library" else "❌ 8. Open the Digital Library"
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun showQuestDetailsDialog() {
        // Launch the epic full-screen Quest Details Activity!
        startActivity(Intent(requireContext(), QuestDetailsActivity::class.java))
    }
    private fun showGrandUnlockCelebration() {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(60, 80, 60, 80)
        }

        val icon = android.widget.TextView(requireContext()).apply {
            text = "🎉🏆✨"
            textSize = 50f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val title = android.widget.TextView(requireContext()).apply {
            text = "RANK UNLOCKED!"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#6C5CE7"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val desc = android.widget.TextView(requireContext()).apply {
            text = "Incredible work! You have mastered all the basic tools and passed the trials. Your Leveling System is now permanently unlocked.\n\nGo forth and start earning XP!"
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            gravity = android.view.Gravity.CENTER
            textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(0f, 1.2f)
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(layout)
            .setCancelable(false) // Forces them to click the button to dismiss
            .setPositiveButton("Accept Rank & Enter GabAI") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}