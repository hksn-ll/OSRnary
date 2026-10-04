package com.example.gabai

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gabai.databinding.ActivityAuthBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Default to Student role on launch for immediate, friendly access
    private var selectedRole: String = "student"

    // Dynamic school list populated from SchoolRepository
    private var schoolList: List<School> = SchoolRepository.defaultSchools

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupSchoolDropdown()
        setupGradeDropdown()
        setupListeners()
        setupAutofill()

        // Initialize with Student Role
        selectRole("student", animate = false)
    }

    private fun setupAutofill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Explicitly connect username and password fields for system Autofill / Password Managers
            binding.etLoginEmail.setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS)
            binding.etLoginPassword.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            binding.etLoginEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            binding.etLoginPassword.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

            // Registration Form Autofill Mapping
            binding.etRegFirstname.setAutofillHints(View.AUTOFILL_HINT_NAME, "personGivenName")
            binding.etRegLastname.setAutofillHints(View.AUTOFILL_HINT_NAME, "personFamilyName")
            binding.etRegEmail.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
            binding.etRegPassword.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "newPassword")
            binding.etRegConfirmPassword.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "newPassword")
            binding.etRegFirstname.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            binding.etRegLastname.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            binding.etRegEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            binding.etRegPassword.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            binding.etRegConfirmPassword.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
    }

    private fun setupListeners() {
        // Segmented Role Switcher
        binding.btnTabStudent.setOnClickListener {
            if (selectedRole != "student") {
                selectRole("student", animate = true)
            }
        }

        binding.btnTabTeacher.setOnClickListener {
            if (selectedRole != "teacher") {
                selectRole("teacher", animate = true)
            }
        }

        // Navigation between Login & Register
        binding.tvGoToRegister.setOnClickListener { showRegister() }
        binding.tvGoToLogin.setOnClickListener { showLogin() }
        binding.btnRegBackHeader.setOnClickListener { showLogin() }

        // Forgot Password
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Action Buttons
        binding.btnLoginSubmit.setOnClickListener { performLogin() }
        binding.btnRegisterSubmit.setOnClickListener { performRegistration() }

        // Trigger Login on Enter / Done from Password Field
        binding.etLoginPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                performLogin()
                true
            } else {
                false
            }
        }

        // Trigger Register on Enter / Done from Register Confirm Password Field
        binding.etRegConfirmPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                performRegistration()
                true
            } else {
                false
            }
        }

        // Fallback for role radio group if accessed
        binding.rgRegisterRole.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_teacher) {
                binding.tilRegSection.visibility = View.GONE
                binding.tilRegGrade.visibility = View.GONE
            } else {
                binding.tilRegSection.visibility = View.VISIBLE
                binding.tilRegGrade.visibility = View.VISIBLE
            }
        }
    }

    private fun selectRole(role: String, animate: Boolean) {
        if (animate) {
            val transition = AutoTransition().apply {
                duration = 200
            }
            TransitionManager.beginDelayedTransition(binding.cardInnerLayout, transition)
        }

        selectedRole = role
        val primaryColor = ContextCompat.getColor(this, R.color.stitch_primary)
        val mutedColor = ContextCompat.getColor(this, R.color.stitch_text_muted)

        binding.btnTabStudent.stateListAnimator = null
        binding.btnTabTeacher.stateListAnimator = null

        if (role == "teacher") {
            // Educator Tab Active Styling
            binding.btnTabTeacher.setBackgroundResource(R.drawable.bg_role_tab_indicator)
            binding.btnTabTeacher.setTextColor(primaryColor)
            binding.btnTabTeacher.elevation = 2f

            // Student Tab Inactive Styling
            binding.btnTabStudent.setBackgroundResource(android.R.color.transparent)
            binding.btnTabStudent.setTextColor(mutedColor)
            binding.btnTabStudent.elevation = 0f

            // Update UI Copy & Inputs
            binding.tvAuthGreeting.text = "Educator Portal 📖"
            binding.tvRoleIndicator.text = "Sign in to review student literacy journeys."

            binding.tilLoginEmail.hint = "Email or Username"
            binding.etLoginEmail.hint = "Email or Username"
            binding.tilLoginEmail.setStartIconDrawable(R.drawable.ic_mail)
            binding.etLoginEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.etLoginEmail.setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS)
            }

            binding.tvForgotPassword.visibility = View.VISIBLE
            binding.containerStudentNote.visibility = View.GONE
            binding.tvGoToRegister.visibility = View.VISIBLE
            binding.rbTeacher.isChecked = true
        } else {
            // Student Tab Active Styling
            binding.btnTabStudent.setBackgroundResource(R.drawable.bg_role_tab_indicator)
            binding.btnTabStudent.setTextColor(primaryColor)
            binding.btnTabStudent.elevation = 2f

            // Educator Tab Inactive Styling
            binding.btnTabTeacher.setBackgroundResource(android.R.color.transparent)
            binding.btnTabTeacher.setTextColor(mutedColor)
            binding.btnTabTeacher.elevation = 0f

            // Update UI Copy & Inputs
            binding.tvAuthGreeting.text = "Welcome Back! 👋"
            binding.tvRoleIndicator.text = "Enter your credentials to continue reading."

            binding.tilLoginEmail.hint = "Username"
            binding.etLoginEmail.hint = "Username"
            binding.tilLoginEmail.setStartIconDrawable(R.drawable.ic_person)
            binding.etLoginEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.etLoginEmail.setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS)
            }

            binding.tvForgotPassword.visibility = View.GONE
            binding.containerStudentNote.visibility = View.VISIBLE
            binding.tvGoToRegister.visibility = View.GONE
            binding.rbStudent.isChecked = true
        }
    }

    private fun showLogin() {
        val transition = AutoTransition().apply {
            duration = 220
        }
        TransitionManager.beginDelayedTransition(binding.cardInnerLayout, transition)

        binding.containerRegister.visibility = View.GONE
        binding.containerRoleSwitcher.visibility = View.VISIBLE
        binding.containerGreeting.visibility = View.VISIBLE
        binding.containerLogin.visibility = View.VISIBLE

        selectRole(selectedRole, animate = false)
    }

    private fun showRegister() {
        val transition = AutoTransition().apply {
            duration = 220
        }
        TransitionManager.beginDelayedTransition(binding.cardInnerLayout, transition)

        binding.containerLogin.visibility = View.GONE
        binding.containerGreeting.visibility = View.GONE
        binding.containerRoleSwitcher.visibility = View.GONE
        binding.containerRegister.visibility = View.VISIBLE
        binding.tvGoToLogin.text = android.text.Html.fromHtml("Already have an account? <font color='#5341CD'><b>Sign In</b></font>", android.text.Html.FROM_HTML_MODE_LEGACY)
    }

    private fun setupGradeDropdown() {
        val grades = arrayOf("Grade 7", "Grade 8", "Grade 9", "Grade 10")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, grades)
        binding.spinnerGrade.setAdapter(adapter)
        binding.spinnerGrade.setText(grades[3], false)
    }

    private fun setupSchoolDropdown() {
        SchoolRepository.fetchSchools { schools ->
            schoolList = schools
            val schoolDisplayNames = schools.map { it.displayName }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, schoolDisplayNames)
            binding.spinnerSchool.setAdapter(adapter)
        }
    }

    private fun performLogin() {
        val inputId = binding.etLoginEmail.text.toString().trim()
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

                    if (user != null) {
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    val role = doc.getString("role") ?: "teacher"

                                    // 🟢 RESTRICT ADMIN ROLES TO WEB PORTAL
                                    if (role == "super_admin" || role == "school_admin" || role == "admin") {
                                        auth.signOut()
                                        toggleLoading(false)
                                        showErrorDialog(
                                            "Web Administrative Portal Only",
                                            "Super Admin and School Admin accounts must access GabAI using the Web Administrative Portal.\n\nThe mobile application is exclusively designed for Teachers and Students."
                                        )
                                        showLogin()
                                        return@addOnSuccessListener
                                    }

                                    // 🟢 CHECK ADMIN APPROVAL STATUS
                                    val isApproved = doc.getBoolean("isApproved") ?: false

                                    if (!isApproved) {
                                        auth.signOut()
                                        toggleLoading(false)
                                        showErrorDialog(
                                            "Account Pending Approval",
                                            "Your educator account is awaiting verification by the Division/School Administrator.\n\nPlease contact your admin for activation."
                                        )
                                        showLogin()
                                        return@addOnSuccessListener
                                    }

                                    // Account is approved! Proceed.
                                    db.collection("users").document(user.uid).update("emailVerified", true)
                                    navigateToMain("teacher")
                                } else {
                                    // Account was deleted from database
                                    user.delete()
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
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    navigateToMain("student")
                                } else {
                                    // Account was deleted by the Adviser
                                    user.delete()
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
                    // Check pending_students for first-time account activation
                    db.collection("pending_students").document(inputId).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists() && doc.getString("password") == pass) {
                                val studentData = doc.data ?: return@addOnSuccessListener

                                auth.createUserWithEmailAndPassword(fakeEmail, pass).addOnCompleteListener { claimTask ->
                                    if (claimTask.isSuccessful) {
                                        val newUid = auth.currentUser?.uid ?: return@addOnCompleteListener

                                        // 1. Move data to the secure 'users' collection
                                        db.collection("users").document(newUid).set(studentData).addOnSuccessListener {
                                            // 2. Delete temporary document so it cannot be claimed again
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

    private fun performRegistration() {
        val email = binding.etRegEmail.text.toString().trim()
        val pass = binding.etRegPassword.text.toString().trim()
        val confirmPass = binding.etRegConfirmPassword.text.toString().trim()
        val firstName = binding.etRegFirstname.text.toString().trim()
        val lastName = binding.etRegLastname.text.toString().trim()

        val section = binding.etRegSection.text.toString().trim()
        val grade = binding.spinnerGrade.text.toString().trim()

        val schoolName = binding.spinnerSchool.text.toString().trim()
        val schoolId = schoolList.firstOrNull { it.displayName == schoolName || it.schoolName == schoolName }?.schoolId
            ?: schoolList.firstOrNull { schoolName.contains(it.schoolId) }?.schoolId
            ?: ""

        val role = "teacher"

        // Anti-spam disable
        binding.btnRegisterSubmit.isEnabled = false

        if (email.isEmpty() || pass.isEmpty() || confirmPass.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || schoolId.isEmpty() ||
            (role == "student" && (section.isEmpty() || grade.isEmpty()))) {
            showErrorDialog("Incomplete Form", "Please fill in all details.")
            binding.btnRegisterSubmit.isEnabled = true
            return
        }

        if (pass != confirmPass) {
            showErrorDialog("Password Mismatch", "Passwords do not match. Please re-enter your password.")
            binding.btnRegisterSubmit.isEnabled = true
            return
        }

        if (pass.length < 6) {
            showErrorDialog("Weak Password", "Password should be at least 6 characters.")
            binding.btnRegisterSubmit.isEnabled = true
            return
        }

        toggleLoading(true)

        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser

                val actionCodeSettings = com.google.firebase.auth.actionCodeSettings {
                    url = "https://gabai-6b004.firebaseapp.com"
                    handleCodeInApp = true
                    setAndroidPackageName("com.example.gabai", true, "24")
                }

                user?.sendEmailVerification(actionCodeSettings)?.addOnCompleteListener { emailTask ->
                    if (emailTask.isSuccessful) {
                        showErrorDialog("Verify Your Email", "A verification link has been sent to $email. Please verify your email before logging in.")
                    }
                }

                val joinCode = java.util.UUID.randomUUID().toString().substring(0, 6).uppercase()

                val userProfile = hashMapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email,
                    "schoolId" to schoolId,
                    "role" to role,
                    "joinCode" to joinCode,
                    "isApproved" to false,
                    "emailVerified" to false,
                    "createdAt" to System.currentTimeMillis()
                )

                user?.uid?.let { uid ->
                    db.collection("users").document(uid).set(userProfile).addOnSuccessListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            getSystemService(AutofillManager::class.java)?.commit()
                        }
                        auth.signOut()
                        toggleLoading(false)
                        binding.btnRegisterSubmit.isEnabled = true
                        showLogin()
                    }.addOnFailureListener {
                        toggleLoading(false)
                        binding.btnRegisterSubmit.isEnabled = true
                        showErrorDialog("Database Error", it.localizedMessage ?: "Failed to save profile")
                    }
                }
            } else {
                toggleLoading(false)
                binding.btnRegisterSubmit.isEnabled = true
                showErrorDialog("Registration Failed", task.exception?.localizedMessage ?: "Unknown error")
                showRegister()
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val emailInput = android.widget.EditText(this).apply {
            hint = "Enter your registered email"
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("We will send a password reset link to your email address.")
            .setView(emailInput)
            .setPositiveButton("Send Link") { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendPasswordReset(email)
                } else {
                    GabAIUtils.showSnackbar(this, "Please enter an email address")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordReset(email: String) {
        toggleLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener {
                toggleLoading(false)
                showLogin()
                showErrorDialog(
                    "Check Your Email",
                    "If an account is associated with $email, you will receive a reset link shortly."
                )
            }
    }

    private fun showErrorDialog(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(AutofillManager::class.java)?.cancel()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun navigateToMain(role: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = getSystemService(AutofillManager::class.java)
            afm?.commit()
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", role)
        startActivity(intent)
        finish()
    }

    private fun toggleLoading(isLoading: Boolean) {
        if (isLoading) {
            GabAIUtils.showGlobalLoading(this)
        } else {
            GabAIUtils.hideGlobalLoading(this)
        }
    }
}
