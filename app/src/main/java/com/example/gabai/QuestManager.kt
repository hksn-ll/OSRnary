package com.example.gabai

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object QuestManager {
    private const val PREFS_NAME = "GabAI_Daily_Quests"
    private const val KEY_LAST_DATE = "last_active_date"

    // NEW: Streak Tracking Keys
    private const val KEY_STREAK = "current_streak"
    private const val KEY_LAST_STREAK_DATE = "last_streak_date"

    const val QUEST_QUIZ = "quest_quiz"       // Target: 1 Quiz Session
    const val QUEST_SAVE = "quest_save"       // Target: Save 2 Words
    const val QUEST_READ = "quest_read"       // Target: Open 1 PDF

    val questTargets = mapOf(
        QUEST_QUIZ to 1,
        QUEST_SAVE to 2,
        QUEST_READ to 1
    )

    // Helper to calculate days between two dates
    private fun getDaysDifference(date1: String, date2: String): Long {
        if (date1.isEmpty() || date2.isEmpty()) return 0
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val d1 = format.parse(date1)
            val d2 = format.parse(date2)
            if (d1 != null && d2 != null) {
                TimeUnit.MILLISECONDS.toDays(d1.time - d2.time)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun checkAndResetDaily(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (today != lastDate) {
            // It's a new day! We must reset their daily tasks to 0.
            val currentStreak = prefs.getInt(KEY_STREAK, 0)
            val lastStreakDate = prefs.getString(KEY_LAST_STREAK_DATE, "")

            // STREAK PENALTY LOGIC: Did they miss yesterday?
            val newStreak = if (lastStreakDate != null && lastStreakDate.isNotEmpty()) {
                val daysMissed = getDaysDifference(today, lastStreakDate)
                if (daysMissed > 1) 0 else currentStreak // Reset to 0 if gap is > 1 day
            } else {
                currentStreak
            }

            // Clear tasks, but safely carry over the Streak data
            prefs.edit()
                .clear()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_STREAK, newStreak)
                .putString(KEY_LAST_STREAK_DATE, lastStreakDate)
                .apply()
        }
    }

    fun addProgress(context: Context, questKey: String) {
        if (!XPManager.canEarnXP(context)) return // Locked until initiation is done!

        checkAndResetDaily(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentProgress = prefs.getInt(questKey, 0)
        val target = questTargets[questKey] ?: 1

        if (currentProgress < target) {
            val newProgress = currentProgress + 1
            prefs.edit().putInt(questKey, newProgress).apply()

            // 1. Give XP for completing the specific task
            if (newProgress == target) {
                val leveledUp = XPManager.addXP(context, 50)
                if (leveledUp) {
                    GabAIUtils.showSnackbar(context, "Quest Complete! 🎉 +50 XP and you LEVELED UP!")
                } else {
                    GabAIUtils.showSnackbar(context, "Quest Complete! 🎉 +50 XP")
                }
            }

            // 2. STREAK REWARD LOGIC: Did they extend their streak today?
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val lastStreakDate = prefs.getString(KEY_LAST_STREAK_DATE, "")

            if (today != lastStreakDate) {
                // This is their FIRST task of the day! Bump the streak!
                val currentStreak = prefs.getInt(KEY_STREAK, 0)
                val newStreak = currentStreak + 1

                prefs.edit()
                    .putInt(KEY_STREAK, newStreak)
                    .putString(KEY_LAST_STREAK_DATE, today)
                    .apply()

                // --- NEW: SYNC STREAK TO FIRESTORE ---
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid).update("current_streak", newStreak)
                }

                // Show a bonus popup for the streak!
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    GabAIUtils.showSnackbar(context, "🔥 Streak extended! You're on a $newStreak-day streak!")
                }, 2000)
            }
        }
    }

    fun getProgress(context: Context, questKey: String): Int {
        checkAndResetDaily(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(questKey, 0)
    }

    // New helper to read the streak for the UI
    fun getStreak(context: Context): Int {
        checkAndResetDaily(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_STREAK, 0)
    }
}