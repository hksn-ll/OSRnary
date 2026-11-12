package com.example.gabai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    private var preloadedRole: String? = null
    private var isUpdateCheckDone = false
    private var isAuthCheckDone = false
    private var hasProceeded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoView = findViewById<GabAiAnimatedLogoView>(R.id.animated_logo)
        val tvTitle = findViewById<TextView>(R.id.tv_app_title)
        val tvSub = findViewById<TextView>(R.id.tv_app_subtitle)

        // Launch the multi-stage physics logo choreography immediately on layout
        logoView?.post {
            logoView.startChoreography()
        }

        var splashDevTaps = 0
        logoView?.setOnClickListener {
            splashDevTaps++
            if (splashDevTaps >= 5) {
                splashDevTaps = 0
                hasProceeded = true
                android.widget.Toast.makeText(this, "🛠️ Opening Developer Component Lab!", android.widget.Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DevEasterEggActivity::class.java))
                finish()
            }
        }

        // Staggered typography entrance coordinated with logo choreography
        tvTitle?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(550)
            ?.setDuration(600)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        tvSub?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(680)
            ?.setDuration(600)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()

        val startTime = System.currentTimeMillis()
        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        preloadedRole = prefs.getString("cached_user_role", null)

        val currentUser = FirebaseAuth.getInstance().currentUser

        // Task A: Pre-warm Firestore user role & profile during the 2400ms splash animation
        // so MainActivity does NOT pop up a secondary "Verifying Account..." loading screen!
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val rawRole = doc.getString("role") ?: "student"
                        val isStudentWithTeacherRole = rawRole == "teacher" &&
                            (doc.contains("section") || doc.contains("grade") || doc.contains("username") || doc.contains("lrn")) &&
                            !doc.contains("teachingSubjects")

                        val role = if (isStudentWithTeacherRole) {
                            doc.reference.update(
                                mapOf(
                                    "role" to "student",
                                    "isApproved" to com.google.firebase.firestore.FieldValue.delete()
                                )
                            )
                            "student"
                        } else {
                            rawRole
                        }

                        val firstName = doc.getString("firstName") ?: doc.getString("first_name") ?: ""
                        val lastName = doc.getString("lastName") ?: doc.getString("last_name") ?: ""
                        val explicitName = doc.getString("name") ?: doc.getString("fullName") ?: ""
                        val fullName = when {
                            firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                            firstName.isNotBlank() -> firstName
                            explicitName.isNotBlank() -> explicitName
                            else -> ""
                        }
                        val gender = doc.getString("gender") ?: doc.getString("sex") ?: "unspecified"
                        preloadedRole = role
                        prefs.edit()
                            .putString("cached_user_role", role)
                            .putString("cached_first_name", firstName)
                            .putString("cached_last_name", lastName)
                            .putString("cached_full_name", fullName)
                            .putString("cached_user_gender", gender)
                            .apply()

                        if (role == "student") {
                            val rawIsOnboarded = doc.getBoolean("is_onboarded") ?: false
                            val questsCompleted = (doc.get("quests_completed") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList<String>()
                            val hasAllQuests = questsCompleted.contains(ApprenticeQuestHelper.QUEST_BUBBLE) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_BUBBLE_SCAN) &&
                                (questsCompleted.contains(ApprenticeQuestHelper.QUEST_TEST) || questsCompleted.contains("read")) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_SCAN) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_SAVE) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_HISTORY) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_DETAIL) &&
                                questsCompleted.contains(ApprenticeQuestHelper.QUEST_LIBRARY)

                            if (rawIsOnboarded && !hasAllQuests) {
                                doc.reference.update("is_onboarded", false)
                                XPManager.setOnboarded(this, false)
                            } else {
                                XPManager.syncFromFirestore(this, doc.getLong("current_xp")?.toInt() ?: 0, doc.getLong("level")?.toInt() ?: 1, rawIsOnboarded)
                            }
                        }
                    } else {
                        // User account deleted or uninitialized
                        FirebaseAuth.getInstance().signOut()
                        prefs.edit()
                            .remove("cached_user_role")
                            .remove("cached_first_name")
                            .remove("cached_last_name")
                            .remove("cached_full_name")
                            .remove("cached_user_gender")
                            .apply()
                        preloadedRole = null
                    }
                    isAuthCheckDone = true
                    checkReadyToProceed(startTime)
                }
                .addOnFailureListener {
                    isAuthCheckDone = true
                    checkReadyToProceed(startTime)
                }
        } else {
            isAuthCheckDone = true
        }

        // Task B: GitHub version check
        GitHubUpdateHelper.checkUpdate(this) {
            isUpdateCheckDone = true
            checkReadyToProceed(startTime)
        }

        // Safety fallback (ensures splash never hangs indefinitely on poor connection)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!hasProceeded) {
                isUpdateCheckDone = true
                isAuthCheckDone = true
                proceedToNextScreen()
            }
        }, 3200)
    }

    private fun checkReadyToProceed(startTime: Long) {
        if (isUpdateCheckDone && isAuthCheckDone && !hasProceeded) {
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingDelay = (2400 - elapsedTime).coerceAtLeast(0)

            Handler(Looper.getMainLooper()).postDelayed({
                proceedToNextScreen()
            }, remainingDelay)
        }
    }

    private fun proceedToNextScreen() {
        if (hasProceeded || isFinishing || isDestroyed) return
        hasProceeded = true

        val currentUser = FirebaseAuth.getInstance().currentUser
        val targetIntent = if (currentUser == null) {
            Intent(this, AuthActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java).apply {
                val role = preloadedRole ?: getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                    .getString("cached_user_role", "student")
                putExtra("USER_ROLE", role)
            }
        }
        startActivity(targetIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}