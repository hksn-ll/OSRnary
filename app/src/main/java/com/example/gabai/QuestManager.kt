package com.example.gabai

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object QuestManager {
    private const val BASE_PREFS_NAME = "GabAI_Daily_Quests"
    private const val KEY_LAST_DATE = "last_active_date"

    // Streak Tracking Keys
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

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val prefsName = if (!uid.isNullOrEmpty()) "${BASE_PREFS_NAME}_$uid" else BASE_PREFS_NAME
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // Seamless migration: copy legacy data if user-scoped is fresh
        if (!uid.isNullOrEmpty() && !prefs.contains(KEY_LAST_DATE) && !prefs.contains(KEY_STREAK)) {
            val legacyPrefs = context.getSharedPreferences(BASE_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.contains(KEY_STREAK) || legacyPrefs.contains(KEY_LAST_DATE)) {
                prefs.edit()
                    .putString(KEY_LAST_DATE, legacyPrefs.getString(KEY_LAST_DATE, ""))
                    .putInt(KEY_STREAK, legacyPrefs.getInt(KEY_STREAK, 0))
                    .putString(KEY_LAST_STREAK_DATE, legacyPrefs.getString(KEY_LAST_STREAK_DATE, ""))
                    .putInt(QUEST_QUIZ, legacyPrefs.getInt(QUEST_QUIZ, 0))
                    .putInt(QUEST_SAVE, legacyPrefs.getInt(QUEST_SAVE, 0))
                    .putInt(QUEST_READ, legacyPrefs.getInt(QUEST_READ, 0))
                    .apply()
            }
        }
        return prefs
    }

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
        } catch (_: Exception) {
            0
        }
    }

    fun syncStreakFromFirestore(context: Context, cloudStreak: Int) {
        val prefs = getPrefs(context)
        val localStreak = prefs.getInt(KEY_STREAK, 0)
        if (cloudStreak > localStreak) {
            prefs.edit().putInt(KEY_STREAK, cloudStreak).apply()
        }
    }

    private fun checkAndResetDaily(context: Context) {
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (today != lastDate) {
            // It's a new day: reset daily tasks to 0
            val currentStreak = prefs.getInt(KEY_STREAK, 0)
            val lastStreakDate = prefs.getString(KEY_LAST_STREAK_DATE, "")

            // STREAK PENALTY LOGIC: Reset to 0 if gap > 1 day
            val newStreak = if (!lastStreakDate.isNullOrEmpty()) {
                val daysMissed = getDaysDifference(today, lastStreakDate)
                if (daysMissed > 1) 0 else currentStreak
            } else {
                currentStreak
            }

            prefs.edit()
                .putInt(QUEST_QUIZ, 0)
                .putInt(QUEST_SAVE, 0)
                .putInt(QUEST_READ, 0)
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_STREAK, newStreak)
                .putString(KEY_LAST_STREAK_DATE, lastStreakDate ?: "")
                .apply()
        }
    }

    fun addProgress(context: Context, questKey: String) {
        if (!XPManager.canEarnXP(context)) return // Locked until initiation is done

        checkAndResetDaily(context)

        val prefs = getPrefs(context)
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
                // First task of the day extends streak
                val currentStreak = prefs.getInt(KEY_STREAK, 0)
                val newStreak = currentStreak + 1

                prefs.edit()
                    .putInt(KEY_STREAK, newStreak)
                    .putString(KEY_LAST_STREAK_DATE, today)
                    .apply()

                // Sync streak to Firestore
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid).update("current_streak", newStreak)
                }

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    GabAIUtils.showSnackbar(context, "🔥 Streak extended! You're on a $newStreak-day streak!")
                }, 2000)
            }
        }
    }

    fun getProgress(context: Context, questKey: String): Int {
        checkAndResetDaily(context)
        return getPrefs(context).getInt(questKey, 0)
    }

    fun getStreak(context: Context): Int {
        checkAndResetDaily(context)
        return getPrefs(context).getInt(KEY_STREAK, 0)
    }
}