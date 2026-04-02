package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gabai.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedRole: String? = null
    private var isLoginMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Role Selection Logic
        binding.btnSelectStudent.setOnClickListener {
            selectedRole = "student"
            showAuthForm(binding)
        }

        binding.btnSelectTeacher.setOnClickListener {
            selectedRole = "teacher"
            showAuthForm(binding)
        }

        // 2. Toggle Login/Register
        binding.tvSwitchMode.setOnClickListener {
            isLoginMode = !isLoginMode
            binding.btnAuthSubmit.text = if (isLoginMode) "Login" else "Register"
            binding.tvSwitchMode.text = if (isLoginMode)
                "Need an account? Register" else "Already have an account? Login"
        }

        // 3. Submit Logic
        binding.btnAuthSubmit.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isLoginMode) loginUser(email, password) else registerUser(email, password)
        }
    }

    private fun showAuthForm(binding: ActivityAuthBinding) {
        binding.roleSelectionContainer.visibility = View.GONE
        binding.authFormContainer.visibility = View.VISIBLE
    }
    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = auth.currentUser?.uid

                // Create a map to store in Database D1
                val userMap = hashMapOf(
                    "email" to email,
                    "role" to selectedRole, // "student" or "teacher"
                    "createdAt" to System.currentTimeMillis()
                )

                userId?.let {
                    // Save to Firestore 'users' collection
                    db.collection("users").document(it).set(userMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Welcome to GabAI!", Toast.LENGTH_SHORT).show()
                            navigateToDashboard(selectedRole)
                        }
                }
            } else {
                Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun navigateToDashboard(role: String?) {
        val intent = when (role) {
            "teacher" -> {
                // For now, Teachers go to a placeholder or a specific TeacherActivity
                // You will create TeacherMainActivity in the next phase
                Intent(this, MainActivity::class.java).apply {
                    putExtra("USER_ROLE", "teacher")
                }
            }
            else -> {
                // Students go to the main reading assistant
                Intent(this, MainActivity::class.java).apply {
                    putExtra("USER_ROLE", "student")
                }
            }
        }
        startActivity(intent)
        finish()
    }
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val userId = auth.currentUser?.uid
                userId?.let { id ->
                    // Fetch the role from Database D1 (User Database)
                    db.collection("users").document(id).get()
                        .addOnSuccessListener { document ->
                            val role = document.getString("role")
                            navigateToDashboard(role)
                        }
                }
            } else {
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}