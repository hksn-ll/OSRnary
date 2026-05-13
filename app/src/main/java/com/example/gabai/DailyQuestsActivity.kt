package com.example.gabai

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DailyQuestsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_quests)

        // Fix Status Bar
        val header = findViewById<View>(R.id.quests_header)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 20, v.paddingRight, v.paddingBottom)
            insets
        }

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        loadQuests()
    }

    private fun loadQuests() {
        // Quiz Quest
        val currentStreak = QuestManager.getStreak(this)
        findViewById<TextView>(R.id.tv_streak_count).text = "$currentStreak Day Streak!"
        val qQuizProg = QuestManager.getProgress(this, QuestManager.QUEST_QUIZ)
        val qQuizMax = QuestManager.questTargets[QuestManager.QUEST_QUIZ]!!
        findViewById<TextView>(R.id.tv_quest_quiz_status).text = "Complete 1 Quiz Session ($qQuizProg/$qQuizMax)"
        findViewById<ProgressBar>(R.id.prog_quest_quiz).progress = qQuizProg

        // Save Words Quest
        val qSaveProg = QuestManager.getProgress(this, QuestManager.QUEST_SAVE)
        val qSaveMax = QuestManager.questTargets[QuestManager.QUEST_SAVE]!!
        findViewById<TextView>(R.id.tv_quest_save_status).text = "Save 2 Words to Favorites ($qSaveProg/$qSaveMax)"
        findViewById<ProgressBar>(R.id.prog_quest_save).progress = qSaveProg

        // Read PDF Quest
        val qReadProg = QuestManager.getProgress(this, QuestManager.QUEST_READ)
        val qReadMax = QuestManager.questTargets[QuestManager.QUEST_READ]!!
        findViewById<TextView>(R.id.tv_quest_read_status).text = "Open 1 Reading Material ($qReadProg/$qReadMax)"
        findViewById<ProgressBar>(R.id.prog_quest_read).progress = qReadProg
    }
}