package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Simple 1.5s delay then route
        Handler(Looper.getMainLooper()).postDelayed({
            handleNavigation()
        }, 1500)
    }

    private fun handleNavigation() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        } else {
            FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val role = doc.getString("role") ?: "student"
                    val intent = Intent(this, MainActivity::class.java)
                    intent.putExtra("USER_ROLE", role)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, AuthActivity::class.java))
                    finish()
                }
        }
    }
}