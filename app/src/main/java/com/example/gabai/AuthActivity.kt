package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gabai.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var selectedRole: String? = null

    // Map school names to the IDs required by your database structure
    private val schoolMap = mapOf(
        "Vicente P. Trinidad National High School" to "320402",
        "Sitero Francisco Memorial National High School" to "305446"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inside onCreate in AuthActivity.kt

        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupSchoolDropdown()
        setupGradeDropdown()
        // --- ADD THIS INSIDE onCreate ---
        binding.rgRegisterRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_teacher) {
                // Hide inputs for Teachers
                binding.tilRegSection.visibility = View.GONE
                binding.tilRegGrade.visibility = View.GONE
            } else {
                // Show inputs for Students
                binding.tilRegSection.visibility = View.VISIBLE
                binding.tilRegGrade.visibility = View.VISIBLE
            }
        }
        // Role Choice
        binding.btnRoleStudent.setOnClickListener {
            selectedRole = "student"
            updateRoleUI("Student")
            showLogin()
        }

        binding.btnRoleTeacher.setOnClickListener {
            selectedRole = "teacher"
            updateRoleUI("Teacher")
            showLogin()
        }

        // Navigation
        binding.tvGoToRegister.setOnClickListener { showRegister() }
        binding.tvGoToLogin.setOnClickListener { showLogin() }
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Action Buttons
        binding.btnRegisterSubmit.setOnClickListener { performRegistration() }
        binding.btnLoginSubmit.setOnClickListener { performLogin() }
    }

    private fun setupGradeDropdown() {
        // Enforcement of Grade 10 as the target demographic
        val grades = arrayOf("Grade 10")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grades)
        binding.spinnerGrade.setAdapter(adapter)

        // Auto-select Grade 10 as it's the priority
        binding.spinnerGrade.setText(grades[0], false)
    }
    private fun updateRoleUI(roleTitle: String) {
        binding.tvRoleIndicator.text = "$roleTitle Access"

        if (roleTitle == "Teacher") {
            binding.tvAuthGreeting.text = "Hello, Educator! 🍎"
            binding.rbTeacher.isChecked = true // Sync radio button
            binding.tilRegSection.visibility = View.GONE
            binding.tilRegGrade.visibility = View.GONE
        } else {
            binding.tvAuthGreeting.text = "Hello, Learner! 👋"
            binding.rbStudent.isChecked = true // Sync radio button
            binding.tilRegSection.visibility = View.VISIBLE
            binding.tilRegGrade.visibility = View.VISIBLE
        }
    }
    private fun showErrorDialog(title: String, message: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showForgotPasswordDialog() {
        val emailInput = android.widget.EditText(this).apply {
            hint = "Enter your registered email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(50, 40, 50, 40)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("We will send a password reset link to your email address.")
            .setView(emailInput)
            .setPositiveButton("Send Link") { _, _ ->

                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendPasswordReset(email)
                } else {
                    Toast.makeText(this, "Please enter an email address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordReset(email: String) {
        toggleLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { _ -> // FIXED: Change 'task' to '_' to remove unused warning
                toggleLoading(false)
                showLogin()
                showErrorDialog(

                    "Check Your Email",
                    "If an account is associated with $email, you will receive a reset link shortly."
                )
            }
    }
    private fun setupSchoolDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, schoolMap.keys.toList())
        binding.spinnerSchool.setAdapter(adapter)
    }

    private fun showLogin() {
        binding.containerRole.visibility = View.GONE
        binding.containerRegister.visibility = View.GONE

        // Show greetings and login form
        binding.containerGreeting.visibility = View.VISIBLE
        binding.containerLogin.visibility = View.VISIBLE
    }

    private fun showRegister() {
        binding.containerLogin.visibility = View.GONE
        binding.containerRegister.visibility = View.VISIBLE
    }

    private fun performRegistration() {
        val email = binding.etRegEmail.text.toString().trim()
        val pass = binding.etRegPassword.text.toString().trim()
        val firstName = binding.etRegFirstname.text.toString().trim()
        val lastName = binding.etRegLastname.text.toString().trim()

        // Capture specific fields for students
        val section = binding.etRegSection.text.toString().trim()
        val grade = binding.spinnerGrade.text.toString().trim()

        val schoolName = binding.spinnerSchool.text.toString().trim()
        val schoolId = schoolMap[schoolName] ?: ""

        // Determine role based on RadioGroup selection
        val role = if (binding.rbTeacher.isChecked) "teacher" else "student"

        // Anti-spam: disable button immediately
        binding.btnRegisterSubmit.isEnabled = false

        // Update your validation to use this new 'role' variable
        if (email.isEmpty() || pass.isEmpty() || schoolId.isEmpty() ||
            (role == "student" && (section.isEmpty() || grade.isEmpty()))) {
            showErrorDialog("Incomplete Form", "Please fill in all details.")
            binding.btnRegisterSubmit.isEnabled = true
            return
        }
        toggleLoading(true)
        // FIXED: Only ONE call to createUserWithEmailAndPassword
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser

                // FIXED: Define and USE actionCodeSettings
                val actionCodeSettings = com.google.firebase.auth.actionCodeSettings {
                    url = "https://gabai-6b004.firebaseapp.com" // Authorized domain
                    handleCodeInApp = true
                    setAndroidPackageName("com.example.gabai", true, "24")
                }

                // Send the verification email using the settings defined above
                user?.sendEmailVerification(actionCodeSettings)?.addOnCompleteListener { emailTask ->
                    if (emailTask.isSuccessful) {
                        showErrorDialog("Verify Your Email", "A link has been sent to $email. Please verify your email before logging in.")
                    }
                }

                // Save to Database D1 (User Database)
                // Save to Database D1 (User Database)
                val userProfile = hashMapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email,
                    "grade" to if (role == "student") grade else "N/A", // Save grade for students
                    "section" to if (role == "student") section else "N/A", // Save section for students
                    "schoolId" to schoolId,
                    "role" to role,
                    "isApproved" to false,
                    "emailVerified" to false,
                    "current_xp" to 0,         // Initiate XP
                    "level" to 1,              // Initiate Level
                    "createdAt" to System.currentTimeMillis()
                )

                user?.uid?.let { uid ->
                    db.collection("users").document(uid).set(userProfile).addOnSuccessListener {
                        auth.signOut()
                        toggleLoading(false) // STOP LOADING ON SUCCESS
                        showLogin()
                    }.addOnFailureListener {
                        toggleLoading(false) // STOP LOADING ON FIRESTORE FAILURE
                        showErrorDialog("Database Error", it.localizedMessage ?: "Failed to save profile")
                    }
                }
            } else {
                toggleLoading(false) // STOP LOADING ON AUTH FAILURE
                showErrorDialog("Registration Failed", task.exception?.localizedMessage ?: "Unknown error")
                binding.btnRegisterSubmit.isEnabled = true
                showRegister()
            }
        }
    }

    private fun performLogin() {
        val email = binding.etLoginEmail.text.toString()
        val pass = binding.etLoginPassword.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            showErrorDialog("Login Error", "Email or Password cannot be blank.")
            return
        }
        toggleLoading(true)


        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser

                // GATE 1: Check Email Verification

                if (user?.isEmailVerified == false) {
                    toggleLoading(false) // STOP LOADING
                    showErrorDialog("Email Not Verified", "Check your inbox and verify your email first.")
                    auth.signOut()
                    showLogin()
                    return@addOnCompleteListener
                }

                // GATE 2: Check Admin Approval in D1
                user?.uid?.let { uid ->
                    db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                        toggleLoading(false) // STOP LOADING
                        val isApproved = doc.getBoolean("isApproved") ?: false
                        val role = doc.getString("role")

                        if (isApproved) {
                            toggleLoading(false)
                            // Mark as verified in DB for Admin Dashboard visibility
                            db.collection("users").document(uid).update("emailVerified", true)
                            navigateToMain(role)
                        }
                            else {
                            toggleLoading(false)
                            showErrorDialog("Account Pending", "Your email is verified, but an Admin must approve your school access.")
                            auth.signOut()
                            showLogin()
                        }
                    }
                }
            } else {
                toggleLoading(false)
                showErrorDialog("Login Failed", task.exception?.localizedMessage ?: "Invalid credentials")
                showLogin()
            }
        }
    }

    private fun navigateToMain(role: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish()
    }
    private fun toggleLoading(isLoading: Boolean) {
        if (isLoading) {
            // Hide all forms and show loading
            binding.containerLogin.visibility = View.GONE
            binding.containerRegister.visibility = View.GONE
            binding.containerRole.visibility = View.GONE
            binding.containerGreeting.visibility = View.GONE
            binding.containerLoading.visibility = View.VISIBLE
        } else {
            // Hide loading (forms will be re-shown by showLogin/showRegister)
            binding.containerLoading.visibility = View.GONE
        }
    }
}