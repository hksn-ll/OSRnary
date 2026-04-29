package com.example.gabai

import android.content.Context

object XPManager {
    private const val PREFS_NAME = "OSRnary_XP"
    private const val KEY_XP = "current_xp"
    private const val KEY_LEVEL = "current_level"
    private const val KEY_ONBOARDED = "is_onboarded" // NEW: Track their rank status locally

    // --- NEW: The Gatekeeper Function ---
    fun canEarnXP(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)
    }

    fun addXP(context: Context, amount: Int): Boolean {
        // If they haven't unlocked their rank, block the XP entirely!
        if (!canEarnXP(context)) return false

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

        // Sync this progress to the student's cloud profile
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