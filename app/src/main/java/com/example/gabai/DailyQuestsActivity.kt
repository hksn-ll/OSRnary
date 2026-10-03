package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DailyQuestsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_quests)

        val header = findViewById<View>(R.id.quests_header)
        GabAIUtils.applyFrostedGlass(header, 28f)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        GabAIUtils.addSpringPressEffect(btnBack) {
            finish()
        }

        setupActions()
        loadQuests()

        // Cascade entrance animation
        val cardStreak = findViewById<View>(R.id.card_streak)
        val cardQuiz = findViewById<View>(R.id.card_quest_quiz)
        val cardSave = findViewById<View>(R.id.card_quest_save)
        val cardRead = findViewById<View>(R.id.card_quest_read)
        GabAIUtils.animateCascade(listOf(cardStreak, cardQuiz, cardSave, cardRead), 40L)
    }

    private fun setupActions() {
        val btnActionQuiz = findViewById<MaterialButton>(R.id.btn_action_quiz)
        GabAIUtils.addSpringPressEffect(btnActionQuiz) {
            startActivity(Intent(this, QuizActivity::class.java))
        }

        val btnActionSave = findViewById<MaterialButton>(R.id.btn_action_save)
        GabAIUtils.addSpringPressEffect(btnActionSave) {
            startActivity(Intent(this, FavoritesActivity::class.java))
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

        // Quiz Quest
        val qQuizProg = QuestManager.getProgress(this, QuestManager.QUEST_QUIZ)
        val qQuizMax = QuestManager.questTargets[QuestManager.QUEST_QUIZ] ?: 1
        findViewById<TextView>(R.id.tv_quest_quiz_status).text = "Complete 1 Quiz Session ($qQuizProg/$qQuizMax)"
        val progQuiz = findViewById<ProgressBar>(R.id.prog_quest_quiz)
        progQuiz.max = qQuizMax
        GabAIUtils.animateProgress(progQuiz, qQuizProg)

        val btnActionQuiz = findViewById<MaterialButton>(R.id.btn_action_quiz)
        if (qQuizProg >= qQuizMax) {
            btnActionQuiz.text = "Completed ✓"
            btnActionQuiz.isEnabled = false
            btnActionQuiz.alpha = 0.6f
        }

        // Save Words Quest
        val qSaveProg = QuestManager.getProgress(this, QuestManager.QUEST_SAVE)
        val qSaveMax = QuestManager.questTargets[QuestManager.QUEST_SAVE] ?: 2
        findViewById<TextView>(R.id.tv_quest_save_status).text = "Save 2 Words to Favorites ($qSaveProg/$qSaveMax)"
        val progSave = findViewById<ProgressBar>(R.id.prog_quest_save)
        progSave.max = qSaveMax
        GabAIUtils.animateProgress(progSave, qSaveProg)

        val btnActionSave = findViewById<MaterialButton>(R.id.btn_action_save)
        if (qSaveProg >= qSaveMax) {
            btnActionSave.text = "Completed ✓"
            btnActionSave.isEnabled = false
            btnActionSave.alpha = 0.6f
        }

        // Read Material Quest
        val qReadProg = QuestManager.getProgress(this, QuestManager.QUEST_READ)
        val qReadMax = QuestManager.questTargets[QuestManager.QUEST_READ] ?: 1
        findViewById<TextView>(R.id.tv_quest_read_status).text = "Open 1 Reading Material ($qReadProg/$qReadMax)"
        val progRead = findViewById<ProgressBar>(R.id.prog_quest_read)
        progRead.max = qReadMax
        GabAIUtils.animateProgress(progRead, qReadProg)

        val btnActionRead = findViewById<MaterialButton>(R.id.btn_action_read)
        if (qReadProg >= qReadMax) {
            btnActionRead.text = "Completed ✓"
            btnActionRead.isEnabled = false
            btnActionRead.alpha = 0.6f
        }
    }
}