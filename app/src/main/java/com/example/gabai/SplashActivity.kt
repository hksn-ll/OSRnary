package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val startTime = System.currentTimeMillis()

        // Check for required GitHub updates before letting user proceed
        GitHubUpdateHelper.checkUpdate(this) {
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingDelay = (1200 - elapsedTime).coerceAtLeast(0)

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser == null) {
                        startActivity(Intent(this, AuthActivity::class.java))
                    } else {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                    finish()
                }
            }, remainingDelay)
        }
    }
}