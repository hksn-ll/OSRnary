package com.example.gabai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DailyQuestsActivity : AppCompatActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startActivity(Intent(this, CameraActivity::class.java))
        } else {
            GabAIUtils.showSnackbar(this, "Camera permission needed to scan words")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GabAIUtils.applyHardwareMaxRefreshRate(this)
        setContentView(R.layout.activity_daily_quests)

        // Setup Frosted Header Backdrop Blur
        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_quests)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_quests)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        // Insets handling for header and scroll content
        val header = findViewById<View>(R.id.quests_header)
        val scrollContent = findViewById<View>(R.id.scroll_quests)
        GabAIUtils.applyHeaderAndScrollInsets(header, scrollContent, extraBufferDp = 16)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack?.setOnClickListener {
            GabAIUtils.performHaptic(it, android.view.HapticFeedbackConstants.CLOCK_TICK)
            finish()
        }

        setupActions()
        loadQuests()

        // Cascade entrance animation
        val cardStreak = findViewById<View>(R.id.card_streak)
        val cardScan = findViewById<View>(R.id.card_quest_scan)
        val cardQuiz = findViewById<View>(R.id.card_quest_quiz)
        val cardRead = findViewById<View>(R.id.card_quest_read)
        GabAIUtils.animateCascade(listOf(cardStreak, cardScan, cardQuiz, cardRead), 40L)
    }

    override fun onResume() {
        super.onResume()
        loadQuests()
    }

    private fun setupActions() {
        val btnActionScan = findViewById<MaterialButton>(R.id.btn_action_scan)
        GabAIUtils.addSpringPressEffect(btnActionScan) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                startActivity(Intent(this, CameraActivity::class.java))
            }
        }

        val btnActionQuiz = findViewById<MaterialButton>(R.id.btn_action_quiz)
        GabAIUtils.addSpringPressEffect(btnActionQuiz) {
            startActivity(Intent(this, QuizActivity::class.java))
        }

        val btnActionRead = findViewById<MaterialButton>(R.id.btn_action_read)
        GabAIUtils.addSpringPressEffect(btnActionRead) {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
    }

    private fun loadQuests() {
        // Streak Card
        val currentStreak = QuestManager.getStreak(this)
        findViewById<TextView>(R.id.tv_streak_count).text = "$currentStreak Day Streak!"

        // Scan Quest
        val qScanProg = QuestManager.getProgress(this, QuestManager.QUEST_SCAN)
        val qScanMax = QuestManager.questTargets[QuestManager.QUEST_SCAN] ?: 1
        findViewById<TextView>(R.id.tv_quest_scan_prog).text = "$qScanProg / $qScanMax"
        val progScan = findViewById<ProgressBar>(R.id.prog_quest_scan)
        progScan.max = qScanMax
        GabAIUtils.animateProgress(progScan, qScanProg)

        val cardScan = findViewById<MaterialCardView>(R.id.card_quest_scan)
        val btnActionScan = findViewById<MaterialButton>(R.id.btn_action_scan)
        applyQuestState(cardScan, btnActionScan, qScanProg >= qScanMax, "Open Camera ➔")

        // Quiz Quest
        val qQuizProg = QuestManager.getProgress(this, QuestManager.QUEST_QUIZ)
        val qQuizMax = QuestManager.questTargets[QuestManager.QUEST_QUIZ] ?: 1
        findViewById<TextView>(R.id.tv_quest_quiz_prog).text = "$qQuizProg / $qQuizMax"
        val progQuiz = findViewById<ProgressBar>(R.id.prog_quest_quiz)
        progQuiz.max = qQuizMax
        GabAIUtils.animateProgress(progQuiz, qQuizProg)

        val cardQuiz = findViewById<MaterialCardView>(R.id.card_quest_quiz)
        val btnActionQuiz = findViewById<MaterialButton>(R.id.btn_action_quiz)
        applyQuestState(cardQuiz, btnActionQuiz, qQuizProg >= qQuizMax, "Start Quiz ➔")

        // Read Material Quest
        val qReadProg = QuestManager.getProgress(this, QuestManager.QUEST_READ)
        val qReadMax = QuestManager.questTargets[QuestManager.QUEST_READ] ?: 1
        findViewById<TextView>(R.id.tv_quest_read_prog).text = "$qReadProg / $qReadMax"
        val progRead = findViewById<ProgressBar>(R.id.prog_quest_read)
        progRead.max = qReadMax
        GabAIUtils.animateProgress(progRead, qReadProg)

        val cardRead = findViewById<MaterialCardView>(R.id.card_quest_read)
        val btnActionRead = findViewById<MaterialButton>(R.id.btn_action_read)
        applyQuestState(cardRead, btnActionRead, qReadProg >= qReadMax, "Open Library ➔")

        val completedCount = (if (qScanProg >= qScanMax) 1 else 0) +
            (if (qQuizProg >= qQuizMax) 1 else 0) +
            (if (qReadProg >= qReadMax) 1 else 0)
        findViewById<TextView>(R.id.tv_daily_completion_summary)?.text = "$completedCount of 3 Done"
    }

    private fun applyQuestState(
        card: MaterialCardView?,
        button: MaterialButton?,
        isCompleted: Boolean,
        actionText: String
    ) {
        if (button == null) return
        if (isCompleted) {
            button.text = "Completed ✓"
            button.isEnabled = false
            button.alpha = 1.0f
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0FDF4"))
            button.setTextColor(Color.parseColor("#15803D"))
            button.strokeColor = ColorStateList.valueOf(Color.parseColor("#BBF7D0"))
            button.strokeWidth = (1.0f * resources.displayMetrics.density).toInt()
            card?.strokeColor = Color.parseColor("#BBF7D0")
        } else {
            button.text = actionText
            button.isEnabled = true
            button.alpha = 1.0f
            button.strokeWidth = 0
            card?.strokeColor = Color.parseColor("#E2E8F0")
        }
    }
}