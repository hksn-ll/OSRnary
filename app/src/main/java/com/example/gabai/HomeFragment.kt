package com.example.gabai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.FragmentHomeBinding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.os.Build

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentCompletedQuests: List<String> = emptyList()
    private var userListener: ListenerRegistration? = null
    private var assessmentListener: ListenerRegistration? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupDashboard()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playEntranceAnimation()
    }

    fun playEntranceAnimation() {
        if (_binding == null || !isAdded) return
        val viewsToAnimate = listOfNotNull(
            binding.containerGreeting,
            binding.cardHeroXp,
            binding.questBoardContainer.takeIf { it.visibility == View.VISIBLE },
            binding.sectionWeeklyAssessments.takeIf { it.visibility == View.VISIBLE },
            binding.headerYourJourney,
            binding.btnLearningProgress,
            binding.btnDailyQuests,
            binding.btnAchievements,
            binding.btnLeaderboard,
            binding.btnJoinClass,
            binding.headerTools,
            binding.btnOpenCamera,
            binding.btnOpenLibrary,
            binding.btnStartQuiz,
            binding.btnFavs,
            binding.btnHistory
        )
        GabAIUtils.animateCascade(viewsToAnimate, baseDelay = 30L, startDelayOffset = 180L)
    }

    override fun onResume() {
        super.onResume()
        loadActiveWeeklyAssessments()
    }

    private fun setupDashboard() {
        val cachedName = requireContext().getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
            .getString("cached_first_name", null)
        if (!cachedName.isNullOrEmpty()) {
            binding.tvGreetingTitle.text = "Hello, $cachedName!"
        }

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

        loadActiveWeeklyAssessments()

        GabAIUtils.addSpringPressEffect(binding.btnLearningProgress) {
            startActivity(Intent(requireContext(), ProgressDashboardActivity::class.java))
        }
        GabAIUtils.addSpringPressEffect(binding.btnAchievements) {
            startActivity(Intent(requireContext(), AchievementsActivity::class.java))
        }
        GabAIUtils.addSpringPressEffect(binding.btnDailyQuests) {
            if (com.example.gabai.XPManager.canEarnXP(requireContext())) {
                startActivity(Intent(requireContext(), DailyQuestsActivity::class.java))
            } else {
                com.example.gabai.GabAIUtils.showSnackbar(requireContext(), "Quests Locked! 🔒 Complete your Apprentice Initiation first.")
            }
        }
        GabAIUtils.addSpringPressEffect(binding.btnLeaderboard) {
            if (com.example.gabai.XPManager.canEarnXP(requireContext())) {
                startActivity(Intent(requireContext(), LeaderboardActivity::class.java))
            } else {
                com.example.gabai.GabAIUtils.showSnackbar(
                    requireContext(),
                    "Leaderboard Locked! 🔒 Complete your Apprentice Quests to unlock the Ranking System."
                )
            }
        }
        GabAIUtils.addSpringPressEffect(binding.btnOpenLibrary) {
            val mainAct = activity as? MainActivity
            if (mainAct?.isBubbleActive() == true) {
                startActivity(Intent(requireContext(), LibraryActivity::class.java))
            } else {
                GabAIDialogs.showConfirmDialog(
                    context = requireContext(),
                    title = "Companion Required",
                    message = "Activate the GabAI Floating Bubble to assist your reading inside the Digital Library.",
                    confirmText = "Turn On & Open",
                    cancelText = "Cancel",
                    isDestructive = false,
                    badgeIcon = "🧚‍♂️",
                    onConfirm = {
                        mainAct?.toggleBubble(enable = true) {
                            startActivity(Intent(requireContext(), LibraryActivity::class.java))
                        }
                    }
                )
            }
        }
        GabAIUtils.addSpringPressEffect(binding.btnOpenCamera) {
            val permission = Manifest.permission.CAMERA
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(requireContext(), CameraActivity::class.java))
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
        GabAIUtils.addSpringPressEffect(binding.btnStartQuiz) { startActivity(Intent(requireContext(), QuizActivity::class.java)) }
        GabAIUtils.addSpringPressEffect(binding.btnFavs) { startActivity(Intent(requireContext(), FavoritesActivity::class.java)) }
        GabAIUtils.addSpringPressEffect(binding.btnHistory) { startActivity(Intent(requireContext(), HistoryActivity::class.java)) }

        val joinBtnId = resources.getIdentifier("btn_join_class", "id", requireContext().packageName)
        if (joinBtnId != 0) {
            binding.root.findViewById<android.widget.Button>(joinBtnId)?.let { btn ->
                GabAIUtils.addSpringPressEffect(btn) { showJoinClassDialog() }
            }
        }
        binding.root.findViewById<android.widget.Button>(R.id.btn_quest_details)?.let { btn ->
            GabAIUtils.addSpringPressEffect(btn) { showQuestDetailsDialog() }
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Real-time listener for user progress & onboarding (non-blocking, cached data renders immediately)
        userListener?.remove()
        userListener = db.collection("users").document(uid).addSnapshotListener { snapshot, e ->

            if (e != null || snapshot == null || !snapshot.exists() || _binding == null || !isAdded) return@addSnapshotListener
            val firstName = snapshot.getString("firstName") ?: snapshot.getString("first_name") ?: ""
            if (firstName.isNotEmpty()) {
                binding.tvGreetingTitle.text = "Hello, $firstName!"
            }
            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
            val currentXP = snapshot.getLong("current_xp")?.toInt() ?: 0
            val currentStreak = snapshot.getLong("current_streak")?.toInt() ?: 0
            val maxXP = com.example.gabai.XPManager.getMaxXPForLevel(currentLevel)

            // Onboarding gamification checks
            val isOnboarded = snapshot.getBoolean("is_onboarded") ?: false
            val completedQuests = snapshot.get("quests_completed") as? List<String> ?: listOf()
            XPManager.syncFromFirestore(requireContext(), currentXP, currentLevel, isOnboarded)
            QuestManager.syncStreakFromFirestore(requireContext(), currentStreak)
            currentCompletedQuests = completedQuests // Save for the dialog

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
                setQuestRowState(R.id.row_quest_read, R.id.indicator_quest_read, R.id.quest_read, R.id.badge_quest_read, "Read Required Study Materials", completedQuests.contains("read"))
                setQuestRowState(R.id.row_quest_test, R.id.indicator_quest_test, R.id.quest_test, R.id.badge_quest_test, "Pass the Initiation Test", completedQuests.contains("test"))
                setQuestRowState(R.id.row_quest_scan, R.id.indicator_quest_scan, R.id.quest_scan, R.id.badge_quest_scan, "Scan a text with the Camera", completedQuests.contains("scan"))
                setQuestRowState(R.id.row_quest_save, R.id.indicator_quest_save, R.id.quest_save, R.id.badge_quest_save, "Save a word to Favorites", completedQuests.contains("save"))
                setQuestRowState(R.id.row_quest_history, R.id.indicator_quest_history, R.id.quest_history, R.id.badge_quest_history, "Check your Scan History", completedQuests.contains("history"))
                setQuestRowState(R.id.row_quest_detail, R.id.indicator_quest_detail, R.id.quest_detail, R.id.badge_quest_detail, "View Saved Content", completedQuests.contains("detail"))
                setQuestRowState(R.id.row_quest_library, R.id.indicator_quest_library, R.id.quest_library, R.id.badge_quest_library, "Open the Digital Library", completedQuests.contains("library"))
            }
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

    private fun showQuestDetailsDialog() {
        // Launch the epic full-screen Quest Details Activity!
        startActivity(Intent(requireContext(), QuestDetailsActivity::class.java))
    }
    private fun showGrandUnlockCelebration() {
        GabAIDialogs.showNoticeDialog(
            context = requireContext(),
            title = "RANK UNLOCKED!",
            message = "Incredible work! You have mastered all the basic tools and passed the trials. Your Leveling System is now permanently unlocked.\n\nGo forth and start earning XP!",
            buttonText = "Accept Rank & Enter GabAI",
            badgeIcon = "🏆",
            cancelable = false
        )
    }
    private fun showJoinClassDialog() {
        GabAIDialogs.showJoinClassDialog(
            requireContext(),
            onScanQrClicked = { activeDialog ->
                activeDialog.dismiss()
                startGoogleQrScan()
            },
            onJoinCodeSubmitted = { activeDialog, code ->
                activeDialog.dismiss()
                joinClassWithCode(code)
            }
        )
    }

    private fun startGoogleQrScan() {
        try {
            val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(requireContext(), options)

            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue?.trim() ?: ""
                    val cleanCode = extractCleanJoinCode(rawValue)
                    if (cleanCode.length == 6) {
                        try {
                            GabAIUtils.performHaptic(requireView(), android.view.HapticFeedbackConstants.CONFIRM)
                        } catch (_: Exception) {}
                        joinClassWithCode(cleanCode)
                    } else {
                        GabAIUtils.showSnackbar(requireContext(), "Scanned code '$cleanCode' is not a valid 6-character Teacher Code.")
                    }
                }
                .addOnCanceledListener {
                    // User canceled scanning
                }
                .addOnFailureListener { e ->
                    GabAIUtils.showSnackbar(requireContext(), "Scan failed: ${e.message}")
                }
        } catch (e: Exception) {
            GabAIUtils.showSnackbar(requireContext(), "Could not launch scanner: ${e.message}")
        }
    }

    private fun extractCleanJoinCode(raw: String): String {
        var result = raw.trim()
        if (result.contains("/")) {
            result = result.substringAfterLast("/")
        }
        if (result.contains(":")) {
            result = result.substringAfterLast(":")
        }
        return result.replace(Regex("[^A-Za-z0-9]"), "").uppercase()
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

            assessmentListener?.remove()
            assessmentListener = db.collection("weekly_assessments")
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

    override fun onDestroyView() {
        super.onDestroyView()
        userListener?.remove()
        userListener = null
        assessmentListener?.remove()
        assessmentListener = null
        _binding = null
    }
}