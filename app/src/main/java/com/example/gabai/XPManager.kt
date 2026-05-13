package com.example.gabai

import android.content.Context

object XPManager {
    private const val PREFS_NAME = "OSRnary_XP"
    private const val KEY_XP = "current_xp"
    private const val KEY_LEVEL = "current_level"
    private const val KEY_ONBOARDED = "is_onboarded"

    fun canEarnXP(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)
    }

    // 🟢 NEW: Dynamic XP Scaling (Level 1 = 100, Level 2 = 200, Level 3 = 300...)
    fun getMaxXPForLevel(level: Int): Int {
        return level * 100
    }

    fun addXP(context: Context, amount: Int): Boolean {
        if (!canEarnXP(context)) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var xp = prefs.getInt(KEY_XP, 0)
        var level = prefs.getInt(KEY_LEVEL, 1)
        val startingLevel = level

        xp += amount

        // 🟢 UPDATED: Use dynamic Max XP
        var currentMaxXP = getMaxXPForLevel(level)
        while (xp >= currentMaxXP) {
            xp -= currentMaxXP
            level += 1
            currentMaxXP = getMaxXPForLevel(level) // Recalculate for the next level up!
        }

        prefs.edit().putInt(KEY_XP, xp).putInt(KEY_LEVEL, level).apply()

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(uid).update(
                "current_xp", xp,
                "level", level
            )
        }

        return level > startingLevel
    }

    fun getXP(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_XP, 0)
    fun getLevel(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LEVEL, 1)
}