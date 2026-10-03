package com.example.gabai

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object XPManager {
    private const val BASE_PREFS_NAME = "OSRnary_XP"
    private const val KEY_XP = "current_xp"
    private const val KEY_LEVEL = "current_level"
    private const val KEY_ONBOARDED = "is_onboarded"

    private fun getPrefs(context: Context): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val prefsName = if (!uid.isNullOrEmpty()) "${BASE_PREFS_NAME}_$uid" else BASE_PREFS_NAME
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        // Seamless migration: if user-scoped prefs are empty but legacy exists, migrate
        if (!uid.isNullOrEmpty() && !prefs.contains(KEY_XP) && !prefs.contains(KEY_LEVEL)) {
            val legacyPrefs = context.getSharedPreferences(BASE_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.contains(KEY_XP) || legacyPrefs.contains(KEY_LEVEL)) {
                val legacyXp = legacyPrefs.getInt(KEY_XP, 0)
                val legacyLevel = legacyPrefs.getInt(KEY_LEVEL, 1)
                val legacyOnboarded = legacyPrefs.getBoolean(KEY_ONBOARDED, false)
                prefs.edit()
                    .putInt(KEY_XP, legacyXp)
                    .putInt(KEY_LEVEL, legacyLevel)
                    .putBoolean(KEY_ONBOARDED, legacyOnboarded)
                    .apply()
            }
        }
        return prefs
    }

    fun canEarnXP(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDED, false)
    }

    fun setOnboarded(context: Context, onboarded: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).update("is_onboarded", onboarded)
    }

    fun syncFromFirestore(context: Context, xp: Int, level: Int, onboarded: Boolean? = null) {
        val editor = getPrefs(context).edit()
            .putInt(KEY_XP, xp)
            .putInt(KEY_LEVEL, if (level > 0) level else 1)
        if (onboarded != null) {
            editor.putBoolean(KEY_ONBOARDED, onboarded)
        }
        editor.apply()
    }

    // Dynamic XP Scaling (Level 1 = 100, Level 2 = 200, Level 3 = 300...)
    fun getMaxXPForLevel(level: Int): Int {
        return if (level > 0) level * 100 else 100
    }

    fun addXP(context: Context, amount: Int): Boolean {
        if (!canEarnXP(context)) return false

        val prefs = getPrefs(context)
        var xp = prefs.getInt(KEY_XP, 0)
        var level = prefs.getInt(KEY_LEVEL, 1)
        val startingLevel = level

        xp += amount

        var currentMaxXP = getMaxXPForLevel(level)
        while (xp >= currentMaxXP) {
            xp -= currentMaxXP
            level += 1
            currentMaxXP = getMaxXPForLevel(level)
        }

        prefs.edit().putInt(KEY_XP, xp).putInt(KEY_LEVEL, level).apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).update(
                "current_xp", xp,
                "level", level
            )
        }

        return level > startingLevel
    }

    fun getXP(context: Context): Int = getPrefs(context).getInt(KEY_XP, 0)
    fun getLevel(context: Context): Int = getPrefs(context).getInt(KEY_LEVEL, 1)
}