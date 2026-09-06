package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import android.widget.Toast
import android.widget.TextView
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

    override fun onResume() {
        super.onResume()
        loadActiveWeeklyAssessments()
    }

    private fun setupDashboard() {
        val mediaProjectionManager = requireContext().getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val prefs = requireContext().getSharedPreferences("GabAI_Prefs", android.content.Context.MODE_PRIVATE)

        if (!FloatingControlService.isRunning) {
            prefs.edit().putBoolean("bubble_enabled", false).apply()
        }
        // 1. Get the saved state
        val isEnabled = prefs.getBoolean("bubble_enabled", false)
        // Check lock status for the Leaderboard and Daily Quests UI
        if (!com.example.gabai.XPManager.canEarnXP(requireContext())) {
            binding.tvLeaderboardTitle.text = "Leaderboard (Locked)"
            binding.tvLeaderboardTitle.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            binding.tvLeaderboardSubtitle.text = "Complete Initiation to unlock"
            binding.ivLeaderboardIcon.setImageResource(R.drawable.ic_lock)
            binding.ivLeaderboardIcon.setColorFilter(android.graphics.Color.parseColor("#94A3B8"))

            binding.tvDailyQuestsTitle.text = "Daily Quests (Locked)"
            binding.tvDailyQuestsTitle.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            binding.tvDailyQuestsSubtitle.text = "Complete Initiation to unlock"
            binding.ivDailyQuestsIcon.setImageResource(R.drawable.ic_lock)
            binding.ivDailyQuestsIcon.setColorFilter(android.graphics.Color.parseColor("#94A3B8"))
        }

        // 2. CLEAR the listener before setting the state to prevent a loop
        binding.bubbleSwitch.setOnCheckedChangeListener(null)
        binding.bubbleSwitch.isChecked = isEnabled
        loadActiveWeeklyAssessments()
        binding.btnLearningProgress.setOnClickListener {
            startActivity(Intent(requireContext(), ProgressDashboardActivity::class.java))
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
            val firstName = snapshot.getString("firstName") ?: snapshot.getString("first_name") ?: ""
            if (firstName.isNotEmpty()) {
                binding.tvGreetingTitle.text = "Hello, $firstName!"
            }
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
                binding.root.findViewById<View>(R.id.unlocked_xp_view).visibility = View.GONE
                binding.root.findViewById<View>(R.id.quest_board_container).visibility = View.VISIBLE

                // Update badge and quest rows with clean Google Stitch styling
                val countDone = completedQuests.size.coerceAtMost(8)
                binding.root.findViewById<android.widget.TextView>(R.id.tv_quest_progress_badge)?.text = "$countDone / 8 Done"

                setQuestRowState(R.id.row_quest_bubble, R.id.indicator_quest_bubble, R.id.quest_bubble, R.id.badge_quest_bubble, "Activate the Floating Bubble", completedQuests.contains("bubble"))
                setQuestRowState(R.id.row_quest_read, R.id.indicator_quest_read, R.id.quest_read, R.id.badge_quest_read, "Read the 4 Required Materials", completedQuests.contains("read"))
                setQuestRowState(R.id.row_quest_test, R.id.indicator_quest_test, R.id.quest_test, R.id.badge_quest_test, "Pass the Initiation Test", completedQuests.contains("test"))
                setQuestRowState(R.id.row_quest_scan, R.id.indicator_quest_scan, R.id.quest_scan, R.id.badge_quest_scan, "Scan a text with the Camera", completedQuests.contains("scan"))
                setQuestRowState(R.id.row_quest_save, R.id.indicator_quest_save, R.id.quest_save, R.id.badge_quest_save, "Save a word to Favorites", completedQuests.contains("save"))
                setQuestRowState(R.id.row_quest_history, R.id.indicator_quest_history, R.id.quest_history, R.id.badge_quest_history, "Check your Scan History", completedQuests.contains("history"))
                setQuestRowState(R.id.row_quest_detail, R.id.indicator_quest_detail, R.id.quest_detail, R.id.badge_quest_detail, "View Saved Content", completedQuests.contains("detail"))
                setQuestRowState(R.id.row_quest_library, R.id.indicator_quest_library, R.id.quest_library, R.id.badge_quest_library, "Open the Digital Library", completedQuests.contains("library"))
            }
            GabAIUtils.hideGlobalLoading(safeContext)
        }
    }

    private fun setQuestRowState(
        containerId: Int,
        indicatorId: Int,
        textId: Int,
        badgeId: Int?,
        title: String,
        isDone: Boolean
    ) {
        val container = binding.root.findViewById<View>(containerId) ?: return
        val indicator = binding.root.findViewById<android.widget.ImageView>(indicatorId)
        val textView = binding.root.findViewById<android.widget.TextView>(textId)
        val badge = if (badgeId != null) binding.root.findViewById<View>(badgeId) else null

        textView?.text = title
        val isAlwaysTealCheck = (badgeId == R.id.badge_quest_bubble || badgeId == R.id.badge_quest_read || 
                                 badgeId == R.id.badge_quest_scan || badgeId == R.id.badge_quest_library)

        if (isDone) {
            container.setBackgroundResource(R.drawable.bg_quest_row_done)
            indicator?.setBackgroundResource(R.drawable.bg_badge_mint)
            indicator?.setImageResource(R.drawable.ic_check_bold)
            indicator?.setColorFilter(android.graphics.Color.parseColor("#006B55"))
            indicator?.setPadding(10, 10, 10, 10)
            textView?.setTextColor(android.graphics.Color.parseColor("#64748B"))
            textView?.paintFlags = (textView?.paintFlags ?: 0) or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            
            if (isAlwaysTealCheck) {
                badge?.visibility = View.VISIBLE
            } else {
                badge?.visibility = View.GONE
            }
        } else {
            container.setBackgroundResource(R.drawable.bg_quest_row_pending)
            indicator?.setBackgroundResource(R.drawable.bg_badge_rose)
            indicator?.setImageResource(R.drawable.ic_close_bold)
            indicator?.setColorFilter(android.graphics.Color.parseColor("#BA1A1A"))
            indicator?.setPadding(10, 10, 10, 10)
            textView?.setTextColor(android.graphics.Color.parseColor("#161D1F"))
            textView?.paintFlags = (textView?.paintFlags ?: 0) and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()

            if (isAlwaysTealCheck) {
                badge?.visibility = View.GONE
            } else {
                badge?.visibility = View.VISIBLE
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

    // =========================================================================
    // 🟢 LOAD ACTIVE WEEKLY ASSESSMENTS FOR STUDENT
    // =========================================================================
    private fun loadActiveWeeklyAssessments() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            if (!isAdded || _binding == null) return@addOnSuccessListener
            val studentSchoolId = userDoc.getString("schoolId") ?: ""
            val studentGrade = userDoc.getString("grade") ?: ""
            val studentSection = userDoc.getString("section") ?: ""

            db.collection("weekly_assessments")
                .whereEqualTo("status", "active")
                .addSnapshotListener { snapshots, error ->
                    if (!isAdded || _binding == null || error != null || snapshots == null) return@addSnapshotListener

                    val matchingDocs = snapshots.documents.filter { doc ->
                        val docSchoolId = doc.getString("schoolId") ?: ""
                        val docGrade = doc.getString("grade") ?: ""
                        val docClassName = doc.getString("className") ?: ""

                        val schoolMatches = docSchoolId.isEmpty() || studentSchoolId.isEmpty() || docSchoolId == studentSchoolId
                        val gradeMatches = docGrade.isEmpty() || studentGrade.isEmpty() || docGrade.contains(studentGrade, ignoreCase = true)
                        val sectionMatches = studentSection.isEmpty() || docClassName.contains(studentSection, ignoreCase = true)

                        schoolMatches && (gradeMatches || sectionMatches)
                    }

                    if (matchingDocs.isEmpty()) {
                        binding.sectionWeeklyAssessments.visibility = View.GONE
                        return@addSnapshotListener
                    }

                    binding.sectionWeeklyAssessments.visibility = View.VISIBLE
                    binding.tvAssessmentActiveBadge.text = "${matchingDocs.size} Active"
                    binding.containerAssessmentCards.removeAllViews()

                    db.collection("assessment_submissions")
                        .whereEqualTo("studentUid", uid)
                        .get()
                        .addOnSuccessListener { subSnaps ->
                            if (!isAdded || _binding == null) return@addOnSuccessListener
                            val submissionsByAssessment = subSnaps.documents.associateBy { it.getString("assessmentId") ?: "" }

                            for (doc in matchingDocs) {
                                val assessmentId = doc.id
                                val title = doc.getString("title") ?: "Weekly Assessment"
                                val subject = doc.getString("subjectName") ?: "Subject"
                                val teacher = doc.getString("teacherName") ?: "Teacher"
                                val className = doc.getString("className") ?: ""
                                val count = doc.getLong("questionCount")?.toInt() ?: 10
                                val dueMillis = doc.getLong("dueDate") ?: 0L

                                val card = layoutInflater.inflate(R.layout.item_weekly_assessment_card, binding.containerAssessmentCards, false)

                                card.findViewById<TextView>(R.id.tv_assessment_subject_badge).text = subject
                                card.findViewById<TextView>(R.id.tv_assessment_item_count).text = "$count Items"
                                card.findViewById<TextView>(R.id.tv_assessment_title).text = title
                                card.findViewById<TextView>(R.id.tv_assessment_meta).text = "Assigned by $teacher • $className"

                                if (dueMillis > 0) {
                                    val sdf = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                                    card.findViewById<TextView>(R.id.tv_assessment_due_date).text = "Due: ${sdf.format(java.util.Date(dueMillis))}"
                                } else {
                                    card.findViewById<TextView>(R.id.tv_assessment_due_date).visibility = View.GONE
                                }

                                val subDoc = submissionsByAssessment[assessmentId]
                                val pill = card.findViewById<TextView>(R.id.tv_assessment_score_pill)
                                val btn = card.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_action_assessment)

                                if (subDoc != null) {
                                    val score = subDoc.getLong("score")?.toInt() ?: 0
                                    val total = subDoc.getLong("totalQuestions")?.toInt() ?: count
                                    val pct = subDoc.getLong("percentage")?.toInt() ?: ((score * 100) / total.coerceAtLeast(1))

                                    pill.visibility = View.VISIBLE
                                    pill.text = "Completed • Score: $score/$total ($pct%) ✓"
                                    btn.text = "View Results ➔"
                                    btn.setBackgroundColor(android.graphics.Color.parseColor("#00B894"))
                                } else {
                                    pill.visibility = View.GONE
                                    btn.text = "Start Assessment ➔"
                                }

                                card.setOnClickListener {
                                    val intent = Intent(requireContext(), WeeklyAssessmentActivity::class.java).apply {
                                        putExtra("ASSESSMENT_ID", assessmentId)
                                    }
                                    startActivity(intent)
                                }

                                btn.setOnClickListener {
                                    val intent = Intent(requireContext(), WeeklyAssessmentActivity::class.java).apply {
                                        putExtra("ASSESSMENT_ID", assessmentId)
                                    }
                                    startActivity(intent)
                                }

                                binding.containerAssessmentCards.addView(card)
                            }
                        }
                }
        }
    }
}