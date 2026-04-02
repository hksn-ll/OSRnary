package com.example.gabai

import android.content.Context

object XPManager {
    private const val PREFS_NAME = "OSRnary_XP"
    private const val KEY_XP = "current_xp"
    private const val KEY_LEVEL = "current_level"

    // In XPManager.kt
    fun addXP(context: Context, amount: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var xp = prefs.getInt(KEY_XP, 0)
        var level = prefs.getInt(KEY_LEVEL, 1)
        val startingLevel = level

        xp += amount

        while (xp >= 100) {
            xp -= 100
            level += 1
        }

        prefs.edit().putInt(KEY_XP, xp).putInt(KEY_LEVEL, level).apply()

        // Returns true if the level is now higher than before
        return level > startingLevel
    }
    fun getXP(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_XP, 0)
    fun getLevel(context: Context): Int = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LEVEL, 1)
}