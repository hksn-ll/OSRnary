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
            com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Camera permission is required to scan books.")
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

            com.example.gabai.GabAIUtils.showSnackbar(context, "Bubble Active!")
            val triggerUid = FirebaseAuth.getInstance().currentUser?.uid
            if (triggerUid != null) {
                FirebaseFirestore.getInstance().collection("users").document(triggerUid)
                    .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("bubble"))
            }
        } else {
            binding.bubbleSwitch.isChecked = false
            com.example.gabai.GabAIUtils.showSnackbar(context, "Permission denied")
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
        // Check lock status for the Leaderboard UI
        if (!com.example.gabai.XPManager.canEarnXP(requireContext())) {
            // Find the views inside the included card (make sure to assign IDs if you haven't)
            val titleText = binding.btnLeaderboard.getChildAt(0).let { it as? android.widget.LinearLayout }?.getChildAt(0) as? android.widget.TextView
            val subtitleText = binding.btnLeaderboard.getChildAt(0).let { it as? android.widget.LinearLayout }?.getChildAt(1) as? android.widget.TextView
            val icon = binding.btnLeaderboard.getChildAt(1) as? android.widget.ImageView

            // Make it look locked
            titleText?.text = "Leaderboard (Locked)"
            titleText?.setTextColor(android.graphics.Color.parseColor("#B2BEC3")) // Gray out
            subtitleText?.text = "Complete Initiation to unlock"
            icon?.setImageResource(android.R.drawable.ic_secure) // Lock icon
            icon?.setColorFilter(android.graphics.Color.parseColor("#B2BEC3"))
        }
        binding.btnAchievements.setOnClickListener {
            startActivity(Intent(requireContext(), AchievementsActivity::class.java))
        }
        binding.btnDailyQuests.setOnClickListener {
            if (com.example.gabai.XPManager.canEarnXP(requireContext())) {
                startActivity(Intent(requireContext(), DailyQuestsActivity::class.java))
            } else {
                com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Quests Locked! 🔒 Complete your Apprentice Initiation first.")
            }
        }

// Visual Lock (add this with your Leaderboard lock code in onCreateView)
        if (!com.example.gabai.XPManager.canEarnXP(requireContext())) {
            val qTitle = binding.btnDailyQuests.getChildAt(0).let { it as? android.widget.LinearLayout }?.getChildAt(0) as? android.widget.TextView
            val qIcon = binding.btnDailyQuests.getChildAt(1) as? android.widget.ImageView
            qTitle?.text = "Daily Quests (Locked)"
            qTitle?.setTextColor(android.graphics.Color.parseColor("#B2BEC3"))
            qIcon?.setImageResource(android.R.drawable.ic_secure)
            qIcon?.setColorFilter(android.graphics.Color.parseColor("#B2BEC3"))
        }

        // 2. CLEAR the listener before setting the state to prevent a loop
        binding.bubbleSwitch.setOnCheckedChangeListener(null)
        binding.bubbleSwitch.isChecked = isEnabled
        binding.btnLearningProgress.setOnClickListener {
            startActivity(Intent(requireContext(), ProgressDashboardActivity::class.java))
        }
        // Inside your onCreateView in HomeFragment.kt
        binding.btnLeaderboard.setOnClickListener {
            // GATEKEEPER: Check if they have unlocked their rank yet!
            if (com.example.gabai.XPManager.canEarnXP(requireContext())) {
                startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
            } else {
                // Locked state!
                com.example.gabai.GabAIUtils.showSnackbar(
                    requireContext(),
                    "Leaderboard Locked! 🔒 Complete your Apprentice Quests to unlock the Ranking System."
                )
            }
        }
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
        // Dynamically look for the button so the app compiles even if the XML isn't updated yet!
        val joinBtnId = resources.getIdentifier("btn_join_class", "id", requireContext().packageName)
        if (joinBtnId != 0) {
            binding.root.findViewById<android.widget.Button>(joinBtnId)?.setOnClickListener {
                showJoinClassDialog()
            }
        }
        // --- ADD THIS TO LAUNCH THE READER ---
        binding.root.findViewById<android.widget.Button>(R.id.btn_quest_details)?.setOnClickListener {
            showQuestDetailsDialog()
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Real-time listener for user progress (Requirement 7.3)
        // TURN ON SPINNER FOR INITIAL LOAD
        GabAIUtils.showGlobalLoading(context, "Syncing...")

        // Real-time listener for user progress & onboarding
        db.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            // SAFELY TURN OFF SPINNER
            val safeContext = context
            if (safeContext != null) {
                GabAIUtils.hideGlobalLoading(safeContext)
            }

            if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
            val currentXP = snapshot.getLong("current_xp")?.toInt() ?: 0
            val maxXP = com.example.gabai.XPManager.getMaxXPForLevel(currentLevel)

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
                binding.xpProgressBar.max = maxXP
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
            GabAIUtils.hideGlobalLoading(safeContext)
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
    private fun showJoinClassDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Enter 6-character Teacher Code"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(50, 40, 50, 40)
            isAllCaps = true
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Join a Class")
            .setMessage("Enter the Join Code provided by your teacher to access their subject materials.")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val code = input.text.toString().trim().uppercase()
                if (code.length == 6) {
                    joinClassWithCode(code)
                } else {
                    GabAIUtils.showSnackbar(requireContext(), "Invalid Code. Must be 6 characters.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinClassWithCode(joinCode: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        GabAIUtils.showGlobalLoading(requireContext())

        // 1. Find the Teacher with this Join Code
        db.collection("users")
            .whereEqualTo("role", "teacher")
            .whereEqualTo("joinCode", joinCode)
            .get()
            .addOnSuccessListener { userSnaps ->
                if (userSnaps.isEmpty) {
                    GabAIUtils.hideGlobalLoading(requireContext())
                    GabAIUtils.showSnackbar(requireContext(), "No teacher found with that code.")
                    return@addOnSuccessListener
                }

                val teacherDoc = userSnaps.documents[0]
                val teacherId = teacherDoc.id
                val teacherSchoolId = teacherDoc.getString("schoolId") ?: "" // 🟢 NEW: Get Teacher's School ID

                // 2. Fetch the student's data
                db.collection("users").document(uid).get().addOnSuccessListener { studentDoc ->
                    val studentSchoolId = studentDoc.getString("schoolId") ?: ""

                    // ==========================================
                    // 🟢 STRICT CHECK: Enforce School Boundaries
                    // ==========================================
                    if (teacherSchoolId != studentSchoolId) {
                        GabAIUtils.hideGlobalLoading(requireContext())
                        GabAIUtils.showSnackbar(requireContext(), "Invalid Code! This teacher belongs to a different school.")
                        return@addOnSuccessListener
                    }
                    // ==========================================

                    val studentSection = studentDoc.getString("section") ?: ""
                    val studentGrade = studentDoc.getString("grade") ?: ""
                    val fullClassName = "$studentGrade - $studentSection"

                    // Check if this class already exists for this specific teacher
                    db.collection("classes")
                        .whereEqualTo("schoolId", studentSchoolId)
                        .whereEqualTo("className", fullClassName)
                        .whereArrayContains("teacherIds", teacherId)
                        .get()
                        .addOnSuccessListener { classSnaps ->
                            if (classSnaps.isEmpty) {
                                // Create the class link for the teacher AND ADD STUDENT
                                val classData = hashMapOf(
                                    "className" to fullClassName,
                                    "grade" to studentGrade,
                                    "section" to studentSection,
                                    "teacherId" to teacherId, // Owner
                                    "teacherIds" to listOf(teacherId),
                                    "schoolId" to studentSchoolId,
                                    "isAdviser" to false, // Least Privilege Flag!
                                    "joinedStudents" to listOf(uid),
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("classes").add(classData).addOnSuccessListener {
                                    GabAIUtils.hideGlobalLoading(requireContext())
                                    GabAIUtils.showSnackbar(requireContext(), "Successfully joined class!")
                                }
                            } else {
                                // Class exists, just update the array with the student!
                                val classDocId = classSnaps.documents[0].id
                                db.collection("classes").document(classDocId)
                                    .update("joinedStudents", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
                                    .addOnSuccessListener {
                                        GabAIUtils.hideGlobalLoading(requireContext())
                                        GabAIUtils.showSnackbar(requireContext(), "Successfully joined class!")
                                    }
                            }
                        }
                }
            }
            .addOnFailureListener {
                GabAIUtils.hideGlobalLoading(requireContext())
                GabAIUtils.showSnackbar(requireContext(), "Error connecting to server.")
            }
    }

}