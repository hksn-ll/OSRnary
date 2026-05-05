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

        // Wait for 2 seconds then decide where to go
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                // Not logged in -> Auth Screen
                startActivity(Intent(this, AuthActivity::class.java))
            } else {
                // Already logged in -> Main Dashboard
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish() // Close the splash screen
        }, 2000)
    }
}