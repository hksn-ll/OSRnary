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
    // Map school names to the IDs required by your database structure
    private val schoolMap = mapOf(
        "Caruhatan National High School - 305445" to "305445",
        "Sitero Francisco Memorial National High School - 305446" to "305446",
        "Punturin Senior High School - 305565" to "305565",
        "Justice Eliezer R. De Los Santos High School - 305566" to "305566",
        "Lingunan National High School - 305567" to "305567",
        "Paso De Blas National High School - 305568" to "305568",
        "Ugong Senior High School - 305576" to "305576",
        "Disiplina Village-Bignay National High School - 305705" to "305705",
        "Malanday National High School - 305706" to "305706",
        "Veinte Reales National High School - 305707" to "305707",
        "Lingunan Senior High School - 305708" to "305708",
        "Valenzuela City School of Mathematics and Science - 320401" to "320401",
        "Vicente Trinidad National High School (Punturin NHS) - 320402" to "320402",
        "Mapulang Lupa National High School - 320403" to "320403",
        "Bignay National High School - 320404" to "320404",
        "Arkong Bato National High School - 320405" to "320405",
        "Canumay East National High School - 320406" to "320406",
        "Wawang Pulo National High School - 320407" to "320407",
        "Bagbaguin National High School - 320408" to "320408",
        "Paso de Blas Senior High School - 340729" to "340729",
        "Polo National High School - 305436" to "305436",
        "Dalandanan National High School - 305437" to "305437",
        "Malinta National High School - 305438" to "305438",
        "Canumay West National High School - 305439" to "305439",
        "Lawang Bato National High School - 305440" to "305440",
        "Valenzuela National High School - 305441" to "305441",
        "Parada National High School - 305442" to "305442",
        "Gen. Tiburcio de Leon National High School - 305443" to "305443",
        "Maysan National High School - 305444" to "305444"
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
        binding.btnBackToRoles.setOnClickListener { showRoleSelection() }
        // Action Buttons
        binding.btnRegisterSubmit.setOnClickListener { performRegistration() }
        binding.btnLoginSubmit.setOnClickListener { performLogin() }
        showRoleSelection()
    }

    private fun setupGradeDropdown() {
        // 🟢 ADDED GRADES 7 TO 10
        val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grades)
        binding.spinnerGrade.setAdapter(adapter)

        // Auto-select Grade 10 as default
        binding.spinnerGrade.setText(grades[3], false)
    }
    private fun updateRoleUI(roleTitle: String) {
        binding.tvRoleIndicator.text = "$roleTitle Access"

        if (roleTitle == "Teacher") {
            binding.tvAuthGreeting.text = "Hello, Educator! 🍎"
            binding.rbTeacher.isChecked = true
            binding.tilRegSection.visibility = View.GONE
            binding.tilRegGrade.visibility = View.GONE
            binding.tvGoToRegister.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.VISIBLE

            // Update Login Hint
            binding.etLoginEmail.parent.let {
                if (it is com.google.android.material.textfield.TextInputLayout) it.hint = "Email"
            }
            binding.etLoginEmail.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        } else {
            binding.tvAuthGreeting.text = "Hello, Learner! 👋"
            binding.rbStudent.isChecked = true
            binding.tilRegSection.visibility = View.VISIBLE
            binding.tilRegGrade.visibility = View.VISIBLE
            binding.tvGoToRegister.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.GONE
            // Update Login Hint
            binding.etLoginEmail.parent.let {
                if (it is com.google.android.material.textfield.TextInputLayout) it.hint = "Username"
            }
            binding.etLoginEmail.inputType = android.text.InputType.TYPE_CLASS_TEXT
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
                    com.example.gabai.GabAIUtils.showSnackbar(this, "Please enter an email address")
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
        val role = "teacher"

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
                val joinCode = java.util.UUID.randomUUID().toString().substring(0, 6).uppercase()
                // Save to Database D1 (User Database)
                // Save to Database D1 (User Database)
                // Save to Database D1 (User Database) - STRICTLY TEACHER
                val userProfile = hashMapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email,
                    "schoolId" to schoolId,
                    "role" to role,
                    "joinCode" to joinCode,// Hardcoded to "teacher"
                    "isApproved" to false,
                    "emailVerified" to false,
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
        val inputId = binding.etLoginEmail.text.toString().trim() // Can be Email OR Username
        val pass = binding.etLoginPassword.text.toString().trim()

        if (inputId.isEmpty() || pass.isEmpty()) {
            showErrorDialog("Login Error", "Credentials cannot be blank.")
            return
        }
        toggleLoading(true)

        if (selectedRole == "teacher") {
            // --- TEACHER LOGIN (Standard Email) ---
            auth.signInWithEmailAndPassword(inputId, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // ==========================================
                    // 🟢 THE FIX: SECURITY CHECK FOR TEACHERS 🟢
                    // ==========================================
                    if (user != null) {
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    // Account is valid! Proceed.
                                    db.collection("users").document(user.uid).update("emailVerified", true)
                                    navigateToMain("teacher")
                                } else {
                                    // Account was deleted from the database!
                                    user.delete() // Clean up the orphaned Auth account
                                    auth.signOut()
                                    toggleLoading(false)
                                    showErrorDialog("Access Denied", "Your teacher account has been removed from the system.")
                                    showLogin()
                                }
                            }
                            .addOnFailureListener {
                                auth.signOut()
                                toggleLoading(false)
                                showErrorDialog("System Error", "Could not verify your account status.")
                                showLogin()
                            }
                    }
                } else {
                    toggleLoading(false)
                    showErrorDialog("Login Failed", task.exception?.localizedMessage ?: "Invalid credentials")
                    showLogin()
                }
            }
        } else {
            // --- STUDENT LOGIN (Account Claiming Flow) ---
            val fakeEmail = "$inputId@gabai.app"

            auth.signInWithEmailAndPassword(fakeEmail, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // NEW SECURITY CHECK: Verify they still exist in the database!
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    // Account is valid! Proceed.
                                    navigateToMain("student")
                                } else {
                                    // Account was deleted by the Adviser!
                                    user.delete() // Clean up the orphaned Auth account
                                    auth.signOut()
                                    toggleLoading(false)
                                    showErrorDialog("Access Denied", "Your account has been removed from the class section by your adviser.")
                                    showLogin()
                                }
                            }
                            .addOnFailureListener {
                                auth.signOut()
                                toggleLoading(false)
                                showErrorDialog("System Error", "Could not verify your account status.")
                                showLogin()
                            }
                    }
                } else {
                    // Failed: Might be their FIRST login.
                    // SECURE FETCH: Try to get the specific document from pending_students
                    db.collection("pending_students").document(inputId).get()
                        .addOnSuccessListener { doc ->
                            // Verify the document exists AND the password matches locally
                            if (doc.exists() && doc.getString("password") == pass) {

                                val studentData = doc.data ?: return@addOnSuccessListener

                                auth.createUserWithEmailAndPassword(fakeEmail, pass).addOnCompleteListener { claimTask ->
                                    if (claimTask.isSuccessful) {
                                        val newUid = auth.currentUser?.uid ?: return@addOnCompleteListener

                                        // 1. Move data to the secure 'users' collection
                                        db.collection("users").document(newUid).set(studentData).addOnSuccessListener {
                                            // 2. Delete the temporary document so it can't be claimed again
                                            doc.reference.delete()
                                            toggleLoading(false)
                                            navigateToMain("student")
                                        }
                                    } else {
                                        toggleLoading(false)
                                        showErrorDialog("System Error", "Could not initialize your account.")
                                        showLogin()
                                    }
                                }
                            } else {
                                toggleLoading(false)
                                showErrorDialog("Login Failed", "Invalid Username or Password.")
                                showLogin()
                            }
                        }.addOnFailureListener {
                            toggleLoading(false)
                            showErrorDialog("Database Error", "Could not verify credentials.")
                            showLogin()
                        }
                }
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
            // Hide all forms to clear the screen
            binding.containerLogin.visibility = View.GONE
            binding.containerRegister.visibility = View.GONE
            binding.containerRole.visibility = View.GONE
            binding.containerGreeting.visibility = View.GONE

            // Show the new Global Transparent Spinner!
            GabAIUtils.showGlobalLoading(this)
        } else {
            // Hide the Global Spinner
            GabAIUtils.hideGlobalLoading(this)
        }
    }
    private fun showRoleSelection() {
        // Hide all other forms
        binding.containerLogin.visibility = View.GONE
        binding.containerRegister.visibility = View.GONE
        binding.containerGreeting.visibility = View.GONE

        // Show ONLY the role selection buttons
        binding.containerRole.visibility = View.VISIBLE
    }

}




