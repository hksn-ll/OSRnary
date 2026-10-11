package com.example.gabai

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

object AchievementManager {
    private const val BASE_PREFS = "GabAI_Achievements"

    const val ACH_COMBO_MONARCH = "combo_monarch"
    const val ACH_EAGLE_EYE = "eagle_eye"
    const val ACH_GLITCH_BUSTER = "glitch_buster"
    const val ACH_LIGHTNING_REFLEXES = "lightning_reflexes"
    const val ACH_BOLD_READER = "bold_reader"
    const val ACH_VOCAB_VAULT = "vocab_vault"

    private const val KEY_STAT_DETECTIVE_CLUES = "stat_detective_clues"
    private const val KEY_STAT_GLITCH_BUSTER = "stat_glitch_buster"
    private const val KEY_STAT_CROSS_OUT = "stat_cross_out"
    private const val KEY_STAT_WORDS_MASTERED = "stat_words_mastered"

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val name = if (!uid.isNullOrEmpty()) "GabAI_Achievements_$uid" else BASE_PREFS
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun isUnlocked(context: Context, id: String): Boolean {
        return getPrefs(context).getBoolean("unlocked_$id", false)
    }

    fun unlock(context: Context, id: String): Boolean {
        val prefs = getPrefs(context)
        if (prefs.getBoolean("unlocked_$id", false)) return false
        prefs.edit().putBoolean("unlocked_$id", true).apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (!uid.isNullOrEmpty()) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("achievements")
                .document(id)
                .set(
                    mapOf(
                        "achievementId" to id,
                        "unlockedAt" to System.currentTimeMillis()
                    )
                )
        }
        return true
    }

    fun recordComboFlawless(context: Context, combo: Int): Boolean {
        if (combo >= 5) {
            return unlock(context, ACH_COMBO_MONARCH)
        }
        return false
    }

    fun recordDetectiveClue(context: Context, clueCount: Int = 1): Boolean {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_STAT_DETECTIVE_CLUES, 0) + clueCount
        prefs.edit().putInt(KEY_STAT_DETECTIVE_CLUES, current).apply()
        if (current >= 10) {
            return unlock(context, ACH_EAGLE_EYE)
        }
        return false
    }

    fun recordGlitchBuster(context: Context): Boolean {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_STAT_GLITCH_BUSTER, 0) + 1
        prefs.edit().putInt(KEY_STAT_GLITCH_BUSTER, current).apply()
        if (current >= 5) {
            return unlock(context, ACH_GLITCH_BUSTER)
        }
        return false
    }

    fun recordCrossOutUse(context: Context): Boolean {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_STAT_CROSS_OUT, 0) + 1
        prefs.edit().putInt(KEY_STAT_CROSS_OUT, current).apply()
        if (current >= 3) {
            return unlock(context, ACH_BOLD_READER)
        }
        return false
    }

    fun recordRapidClear(context: Context, durationMs: Long): Boolean {
        if (durationMs < 8000L) {
            return unlock(context, ACH_LIGHTNING_REFLEXES)
        }
        return false
    }

    fun recordWordMastered(context: Context, word: String): Boolean {
        val prefs = getPrefs(context)
        val existingWords = prefs.getStringSet(KEY_STAT_WORDS_MASTERED, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        existingWords.add(word.trim().lowercase(Locale.ROOT))
        prefs.edit().putStringSet(KEY_STAT_WORDS_MASTERED, existingWords).apply()
        if (existingWords.size >= 20) {
            return unlock(context, ACH_VOCAB_VAULT)
        }
        return false
    }

    fun getUnlockedIds(context: Context): Set<String> {
        val prefs = getPrefs(context)
        val list = mutableSetOf<String>()
        for ((k, v) in prefs.all) {
            if (k.startsWith("unlocked_") && v == true) {
                list.add(k.removePrefix("unlocked_"))
            }
        }
        return list
    }
}
