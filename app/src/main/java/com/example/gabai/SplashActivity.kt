package com.example.gabai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
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
        val progressBar = findViewById<ProgressBar>(R.id.splash_progress)

        // Launch the multi-stage physics logo choreography immediately on layout
        logoView?.post {
            logoView.startChoreography()
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

        progressBar?.animate()
            ?.alpha(1f)
            ?.setStartDelay(850)
            ?.setDuration(500)
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
                        val role = doc.getString("role") ?: "student"
                        val firstName = doc.getString("firstName") ?: doc.getString("first_name") ?: ""
                        preloadedRole = role
                        prefs.edit()
                            .putString("cached_user_role", role)
                            .putString("cached_first_name", firstName)
                            .apply()
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