package com.example.gabai

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gabai.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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

            binding.tvForgotPassword.visibility = View.VISIBLE
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

        val cleanInput = inputId.removePrefix("@").trim()
        val isLikelyEmail = cleanInput.contains("@") && cleanInput.contains(".") && !cleanInput.endsWith("@gabai.app")
        val isLikelyTeacher = (selectedRole == "teacher" && !inputId.startsWith("@")) || isLikelyEmail

        if (isLikelyTeacher) {
            val cleanEmail = cleanInput.lowercase()

            // 1. FAST PATH: Check pending_students first for unauthenticated temporary passkey lookup
            db.collection("pending_students").document(cleanEmail).get()
                .addOnSuccessListener { pDoc ->
                    if (pDoc.exists()) {
                        val docRole = pDoc.getString("role") ?: "student"
                        if (docRole == "student") {
                            // 🟢 CRITICAL SAFETY GUARD: This is a STUDENT account claim slip!
                            // Redirect directly to student claim flow so students never become teachers!
                            selectedRole = "student"
                            selectRole("student", animate = false)
                            processPendingSlip(pDoc, cleanEmail, pass)
                            return@addOnSuccessListener
                        }
                        if (docRole == "teacher") {
                            val expectedPass = pDoc.getString("password") ?: ""
                            val expectedTemp = pDoc.getString("tempPassword") ?: ""
                            val authEmail = pDoc.getString("authEmail") ?: ""
                            val matchesTemp = (expectedPass.isNotBlank() && expectedPass == pass) ||
                                    (expectedTemp.isNotBlank() && expectedTemp == pass)

                            if (matchesTemp) {
                                // 🔑 Immediate Temporary Passkey Login for Educator (NO email verification!)
                                handleTeacherPasskeyReset(cleanEmail, inputId, pass, pDoc)
                                return@addOnSuccessListener
                            } else if (authEmail.isNotBlank()) {
                                // Teacher previously reset with salted authEmail; sign in with their permanent password
                                performTeacherAuthSignIn(authEmail, inputId, pass)
                                return@addOnSuccessListener
                            }
                        }
                    }
                    // 2. STANDARD PATH: Direct Firebase Auth sign in
                    performTeacherAuthSignIn(inputId, inputId, pass)
                }
                .addOnFailureListener {
                    performTeacherAuthSignIn(inputId, inputId, pass)
                }
        } else {
            // --- STUDENT LOGIN (Account Claiming & Active Flow) ---
            val cleanInputId = inputId.removePrefix("@").trim().lowercase()
            val fakeEmail = "$cleanInputId@gabai.app"

            // 1. FAST PATH: Check pending_students first for instant account activation or salted authEmail lookup
            db.collection("pending_students").document(cleanInputId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val authEmail = doc.getString("authEmail") ?: ""
                        val expectedPass = doc.getString("password") ?: ""
                        val expectedTemp = doc.getString("tempPassword") ?: ""
                        val matchesTemp = (expectedPass.isNotBlank() && expectedPass == pass) || (expectedTemp.isNotBlank() && expectedTemp == pass)

                        if (matchesTemp) {
                            processPendingSlip(doc, cleanInputId, pass)
                        } else if (authEmail.isNotBlank()) {
                            performActiveStudentSignIn(cleanInputId, authEmail, pass)
                        } else {
                            processPendingSlip(doc, cleanInputId, pass)
                        }
                    } else {
                        // Check with leading @ if saved that way
                        db.collection("pending_students").document("@$cleanInputId").get()
                            .addOnSuccessListener { atDoc ->
                                if (atDoc.exists()) {
                                    val authEmail = atDoc.getString("authEmail") ?: ""
                                    val expectedPass = atDoc.getString("password") ?: ""
                                    val expectedTemp = atDoc.getString("tempPassword") ?: ""
                                    val matchesTemp = (expectedPass.isNotBlank() && expectedPass == pass) || (expectedTemp.isNotBlank() && expectedTemp == pass)

                                    if (matchesTemp) {
                                        processPendingSlip(atDoc, cleanInputId, pass)
                                    } else if (authEmail.isNotBlank()) {
                                        performActiveStudentSignIn(cleanInputId, authEmail, pass)
                                    } else {
                                        processPendingSlip(atDoc, cleanInputId, pass)
                                    }
                                } else {
                                    // 2. ACTIVE STUDENT PATH: Account was already claimed, sign in with Firebase Auth
                                    performActiveStudentSignIn(cleanInputId, fakeEmail, pass)
                                }
                            }
                            .addOnFailureListener {
                                performActiveStudentSignIn(cleanInputId, fakeEmail, pass)
                            }
                    }
                }
                .addOnFailureListener {
                    // Fallback to active sign in if Firestore pending check fails
                    performActiveStudentSignIn(cleanInputId, fakeEmail, pass)
                }
        }
    }

    private fun processPendingSlip(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        inputId: String,
        pass: String
    ) {
        val cleanInputId = inputId.removePrefix("@").trim().lowercase()
        val fakeEmail = "$cleanInputId@gabai.app"

        if (doc.getString("role") == "teacher") {
            selectedRole = "teacher"
            handleTeacherPasskeyReset(cleanInputId, inputId, pass, doc)
            return
        }
        val expectedPass = doc.getString("password") ?: ""
        val expectedTemp = doc.getString("tempPassword") ?: ""
        if ((expectedPass.isNotBlank() && expectedPass == pass) || (expectedTemp.isNotBlank() && expectedTemp == pass)) {
            val isReset = doc.getBoolean("isReset") == true
            val claimedUid = doc.getString("claimedUid") ?: ""

            if (isReset && claimedUid.isNotBlank()) {
                // Authenticate first with salted email and pass to satisfy Firestore security rules
                val saltedEmail = "${cleanInputId}.${System.currentTimeMillis() % 100000}@gabai.app"
                auth.createUserWithEmailAndPassword(saltedEmail, pass).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val newUid = authTask.result?.user?.uid ?: auth.currentUser?.uid
                        if (newUid == null) {
                            toggleLoading(false)
                            showErrorDialog("Auth Error", "Could not verify identity.")
                            showLogin()
                            return@addOnCompleteListener
                        }

                        // NOW authenticated, safely retrieve existing student profile
                        db.collection("users").document(claimedUid).get().addOnSuccessListener { oldDoc ->
                            val studentData = if (oldDoc.exists()) {
                                oldDoc.data?.toMutableMap() ?: mutableMapOf()
                            } else {
                                doc.data?.toMutableMap() ?: mutableMapOf()
                            }
                            studentData["authEmail"] = saltedEmail
                            studentData["mustChangePassword"] = true
                            studentData["isClaimed"] = true
                            val wasOnboarded = (oldDoc.getBoolean("is_onboarded") ?: doc.getBoolean("is_onboarded") ?: false)
                            val qCompleted = (oldDoc.get("quests_completed") as? List<*>) ?: (doc.get("quests_completed") as? List<*>) ?: emptyList<Any>()
                            val qStrings = qCompleted.mapNotNull { it?.toString() }
                            val hasAllQuests = qStrings.contains(ApprenticeQuestHelper.QUEST_BUBBLE) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_BUBBLE_SCAN) &&
                                (qStrings.contains(ApprenticeQuestHelper.QUEST_TEST) || qStrings.contains("read")) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_SCAN) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_SAVE) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_HISTORY) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_DETAIL) &&
                                qStrings.contains(ApprenticeQuestHelper.QUEST_LIBRARY)
                            val finalOnboarded = wasOnboarded && hasAllQuests
                            studentData["is_onboarded"] = finalOnboarded
                            XPManager.setOnboarded(this, finalOnboarded)
                            studentData["role"] = "student"
                            studentData["migratedAt"] = System.currentTimeMillis()
                            studentData.remove("tempPassword")
                            studentData.remove("password")

                            db.collection("users").document(newUid).set(studentData).addOnSuccessListener {
                                // 1. Migrate quiz history
                                db.collection("users").document(claimedUid).collection("quiz_history").get()
                                    .addOnSuccessListener { qSnaps ->
                                        for (q in qSnaps) {
                                            db.collection("users").document(newUid).collection("quiz_history").document(q.id).set(q.data)
                                            q.reference.delete().addOnFailureListener { /* ignore */ }
                                        }
                                    }.addOnFailureListener { /* ignore */ }

                                // 2. Migrate diagnostic results
                                db.collection("users").document(claimedUid).collection("diagnostic_results").get()
                                    .addOnSuccessListener { dSnaps ->
                                        for (d in dSnaps) {
                                            db.collection("users").document(newUid).collection("diagnostic_results").document(d.id).set(d.data)
                                            d.reference.delete().addOnFailureListener { /* ignore */ }
                                        }
                                    }.addOnFailureListener { /* ignore */ }

                                // 3. Migrate favorites
                                db.collection("users").document(claimedUid).collection("favorites").get()
                                    .addOnSuccessListener { fSnaps ->
                                        for (f in fSnaps) {
                                            db.collection("users").document(newUid).collection("favorites").document(f.id).set(f.data)
                                            f.reference.delete().addOnFailureListener { /* ignore */ }
                                        }
                                    }.addOnFailureListener { /* ignore */ }

                                // 4. Migrate history
                                db.collection("users").document(claimedUid).collection("history").get()
                                    .addOnSuccessListener { hSnaps ->
                                        for (h in hSnaps) {
                                            db.collection("users").document(newUid).collection("history").document(h.id).set(h.data)
                                            h.reference.delete().addOnFailureListener { /* ignore */ }
                                        }
                                    }.addOnFailureListener { /* ignore */ }

                                // 5. Migrate assessment submissions
                                db.collection("assessment_submissions").whereEqualTo("studentUid", claimedUid).get()
                                    .addOnSuccessListener { subSnaps ->
                                        for (sub in subSnaps) {
                                            val subData = sub.data.toMutableMap()
                                            subData["studentUid"] = newUid
                                            subData["studentId"] = newUid
                                            val assessmentId = sub.getString("assessmentId") ?: ""
                                            val newDocId = if (assessmentId.isNotBlank()) "${assessmentId}_${newUid}" else sub.id
                                            db.collection("assessment_submissions").document(newDocId).set(subData)
                                            sub.reference.delete().addOnFailureListener { /* ignore */ }
                                        }
                                    }.addOnFailureListener { /* ignore */ }

                                // 6. Auto-join classes
                                val schoolId = studentData["schoolId"] as? String ?: ""
                                val grade = studentData["grade"] as? String ?: ""
                                val section = studentData["section"] as? String ?: ""
                                ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(newUid, schoolId, grade, section)

                                // 7. Clear caches & synchronize slip & old user
                                getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).edit().clear().apply()
                                if (claimedUid != newUid) {
                                    db.collection("users").document(claimedUid).delete().addOnFailureListener { /* ignore */ }
                                }
                                val studentSlipUpdates = mapOf<String, Any>(
                                    "authEmail" to saltedEmail,
                                    "uid" to newUid,
                                    "claimedUid" to newUid,
                                    "isClaimed" to true,
                                    "isReset" to false,
                                    "mustChangePassword" to true,
                                    "password" to FieldValue.delete(),
                                    "tempPassword" to FieldValue.delete(),
                                    "updatedAt" to System.currentTimeMillis()
                                )
                                db.collection("pending_students").document(cleanInputId).set(studentSlipUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }
                                db.collection("pending_students").document("@$cleanInputId").set(studentSlipUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }

                                toggleLoading(false)
                                val claimedUser = auth.currentUser
                                if (claimedUser != null) {
                                    showForcePasswordChangeDialog(claimedUser, newUid, "student") {
                                        navigateToMain("student")
                                    }
                                } else {
                                    navigateToMain("student")
                                }
                            }.addOnFailureListener { setErr ->
                                toggleLoading(false)
                                showErrorDialog("Reset Sync Error", setErr.localizedMessage ?: "Failed to initialize updated student profile.")
                                showLogin()
                            }
                        }.addOnFailureListener {
                            toggleLoading(false)
                            showErrorDialog("Profile Load Error", "Could not load existing student record.")
                            showLogin()
                        }
                    } else {
                        toggleLoading(false)
                        showErrorDialog("Auth Error", authTask.exception?.localizedMessage ?: "Could not authenticate reset credentials.")
                        showLogin()
                    }
                }
            } else {
                val studentData = doc.data?.toMutableMap()
                if (studentData == null) {
                    toggleLoading(false)
                    showErrorDialog("Data Error", "Student record is missing details.")
                    showLogin()
                    return
                }
                studentData["mustChangePassword"] = true
                studentData["role"] = "student"
                completeStudentAccountClaim(cleanInputId, fakeEmail, pass, studentData, doc.reference)
            }
        } else {
            // Password did not match initial pending slip; check if student was already claimed or has active reset password
            checkActiveStudentFallback(cleanInputId, pass)
        }
    }

    private fun performTeacherAuthSignIn(authEmailToUse: String, displayEmail: String, pass: String) {
        auth.signInWithEmailAndPassword(authEmailToUse, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser

                if (user != null) {
                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                var role = doc.getString("role") ?: "teacher"

                                // 🟢 DEFENSIVE RECOVERY: Self-heal student accounts mistakenly stamped as teacher
                                if (role == "student" || (role == "teacher" && doc.contains("section") && doc.contains("grade") && doc.contains("username") && !doc.contains("teachingSubjects"))) {
                                    if (role != "student") {
                                        doc.reference.update("role", "student")
                                    }
                                    toggleLoading(false)
                                    navigateToMain("student")
                                    return@addOnSuccessListener
                                }

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
                                val mustChange = doc.getBoolean("mustChangePassword") ?: false
                                if (mustChange) {
                                    toggleLoading(false)
                                    showForcePasswordChangeDialog(user, user.uid, "teacher") {
                                        navigateToMain("teacher")
                                    }
                                    return@addOnSuccessListener
                                }
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
                // Secondary fallback: check pending_students for temporary passkey
                val cleanEmail = displayEmail.removePrefix("@").trim().lowercase()
                db.collection("pending_students").document(cleanEmail).get()
                    .addOnSuccessListener { pDoc ->
                        if (pDoc.exists()) {
                            val docRole = pDoc.getString("role") ?: "student"
                            if (docRole == "student") {
                                selectedRole = "student"
                                selectRole("student", animate = false)
                                processPendingSlip(pDoc, cleanEmail, pass)
                                return@addOnSuccessListener
                            }
                            if (docRole == "teacher") {
                                val expectedPass = pDoc.getString("password") ?: ""
                                val expectedTemp = pDoc.getString("tempPassword") ?: ""
                                val matchesTemp = (expectedPass.isNotBlank() && expectedPass == pass) ||
                                        (expectedTemp.isNotBlank() && expectedTemp == pass)
                                if (matchesTemp) {
                                    handleTeacherPasskeyReset(cleanEmail, displayEmail, pass, pDoc)
                                    return@addOnSuccessListener
                                }
                            }
                        }
                        toggleLoading(false)
                        showErrorDialog("Login Failed", task.exception?.localizedMessage ?: "Invalid credentials")
                        showLogin()
                    }
                    .addOnFailureListener {
                        toggleLoading(false)
                        showErrorDialog("Login Failed", task.exception?.localizedMessage ?: "Invalid credentials")
                        showLogin()
                    }
            }
        }
    }

    private fun handleTeacherPasskeyReset(
        cleanEmail: String,
        inputId: String,
        pass: String,
        pDoc: com.google.firebase.firestore.DocumentSnapshot
    ) {
        val docRole = pDoc.getString("role") ?: ""
        if (docRole == "student") {
            android.util.Log.e("AuthActivity", "Security violation: blocked attempt to execute teacher passkey reset on student slip!")
            selectedRole = "student"
            selectRole("student", animate = true)
            processPendingSlip(pDoc, cleanEmail, pass)
            return
        }
        val cleanAlpha = cleanEmail.replace(Regex("[^a-zA-Z0-9]"), "")
        val saltedEmail = "teacher.${cleanAlpha}.${System.currentTimeMillis() % 100000}@gabai.app"
        val claimedUid = pDoc.getString("claimedUid") ?: ""

        auth.createUserWithEmailAndPassword(saltedEmail, pass).addOnCompleteListener { authTask ->
            if (authTask.isSuccessful) {
                val newUid = authTask.result?.user?.uid ?: auth.currentUser?.uid
                if (newUid == null) {
                    toggleLoading(false)
                    showErrorDialog("Auth Error", "Could not verify educator identity.")
                    showLogin()
                    return@addOnCompleteListener
                }

                // Retrieve existing educator profile from users collection (now authenticated!)
                val oldRef = if (claimedUid.isNotBlank()) db.collection("users").document(claimedUid) else null
                val fetchTask = oldRef?.get() ?: com.google.android.gms.tasks.Tasks.forResult(null)

                fetchTask.addOnCompleteListener { loadTask ->
                    val oldDoc = if (loadTask.isSuccessful) loadTask.result else null
                    val teacherData = if (oldDoc != null && oldDoc.exists()) {
                        oldDoc.data?.toMutableMap() ?: mutableMapOf()
                    } else {
                        pDoc.data?.toMutableMap() ?: mutableMapOf()
                    }

                    teacherData["authEmail"] = saltedEmail
                    teacherData["email"] = inputId
                    teacherData["role"] = "teacher"
                    teacherData["isApproved"] = true
                    teacherData["emailVerified"] = true
                    teacherData["mustChangePassword"] = true
                    teacherData["migratedAt"] = System.currentTimeMillis()
                    teacherData.remove("tempPassword")
                    teacherData.remove("password")

                    db.collection("users").document(newUid).set(teacherData).addOnSuccessListener {
                        // 1. Update pending_students slip so subsequent logins locate authEmail and uid
                        val slipUpdates = mapOf<String, Any>(
                            "authEmail" to saltedEmail,
                            "uid" to newUid,
                            "claimedUid" to newUid,
                            "isClaimed" to true,
                            "isReset" to false,
                            "mustChangePassword" to true,
                            "password" to FieldValue.delete(),
                            "tempPassword" to FieldValue.delete(),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        db.collection("pending_students").document(cleanEmail).set(slipUpdates, SetOptions.merge())

                        // 2. Migrate teacher classes and subjects if claimedUid != newUid
                        if (claimedUid.isNotBlank() && claimedUid != newUid) {
                            migrateTeacherOwnership(claimedUid, newUid)
                            db.collection("users").document(claimedUid).delete().addOnFailureListener { /* ignore */ }
                        }

                        // 3. Clear cached prefs
                        getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).edit().clear().apply()

                        toggleLoading(false)
                        val teacherUser = auth.currentUser
                        if (teacherUser != null) {
                            showForcePasswordChangeDialog(teacherUser, newUid, "teacher") {
                                navigateToMain("teacher")
                            }
                        } else {
                            navigateToMain("teacher")
                        }
                    }.addOnFailureListener { setErr ->
                        toggleLoading(false)
                        showErrorDialog("Reset Sync Error", setErr.localizedMessage ?: "Failed to initialize updated educator profile.")
                        showLogin()
                    }
                }
            } else {
                toggleLoading(false)
                showErrorDialog("Auth Error", authTask.exception?.localizedMessage ?: "Could not authenticate temporary passkey.")
                showLogin()
            }
        }
    }

    private fun migrateTeacherOwnership(oldUid: String, newUid: String) {
        // A. Advisory classes where teacherId == oldUid
        db.collection("classes").whereEqualTo("teacherId", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    val updates = mutableMapOf<String, Any>(
                        "teacherId" to newUid,
                        "teacherIds" to FieldValue.arrayUnion(newUid)
                    )
                    @Suppress("UNCHECKED_CAST")
                    val subEnrollments = doc.get("subjectEnrollments") as? Map<String, List<*>>
                    if (subEnrollments != null && subEnrollments.containsKey(oldUid)) {
                        val enrolledList = subEnrollments[oldUid] ?: emptyList<Any>()
                        if (enrolledList.isNotEmpty()) {
                            updates["subjectEnrollments.$newUid"] = FieldValue.arrayUnion(*enrolledList.toTypedArray())
                        }
                        updates["subjectEnrollments.$oldUid"] = FieldValue.delete()
                    }
                    doc.reference.update(updates).addOnSuccessListener {
                        doc.reference.update("teacherIds", FieldValue.arrayRemove(oldUid)).addOnFailureListener { /* ignore */ }
                    }
                }
            }.addOnFailureListener { /* ignore */ }

        // B. Classes where oldUid is in teacherIds
        db.collection("classes").whereArrayContains("teacherIds", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    val updates = mutableMapOf<String, Any>(
                        "teacherIds" to FieldValue.arrayUnion(newUid)
                    )
                    @Suppress("UNCHECKED_CAST")
                    val subEnrollments = doc.get("subjectEnrollments") as? Map<String, List<*>>
                    if (subEnrollments != null && subEnrollments.containsKey(oldUid)) {
                        val enrolledList = subEnrollments[oldUid] ?: emptyList<Any>()
                        if (enrolledList.isNotEmpty()) {
                            updates["subjectEnrollments.$newUid"] = FieldValue.arrayUnion(*enrolledList.toTypedArray())
                        }
                        updates["subjectEnrollments.$oldUid"] = FieldValue.delete()
                    }
                    doc.reference.update(updates).addOnSuccessListener {
                        doc.reference.update("teacherIds", FieldValue.arrayRemove(oldUid)).addOnFailureListener { /* ignore */ }
                    }
                }
            }.addOnFailureListener { /* ignore */ }

        // C. Teacher library subjects
        db.collection("library_subjects").whereEqualTo("teacherId", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    doc.reference.update("teacherId", newUid).addOnFailureListener { /* ignore */ }
                }
            }.addOnFailureListener { /* ignore */ }

        // D. Teacher weekly assessments
        db.collection("weekly_assessments").whereEqualTo("teacherId", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    doc.reference.update("teacherId", newUid).addOnFailureListener { /* ignore */ }
                }
            }.addOnFailureListener { /* ignore */ }

        // E. Teacher quizzes
        db.collection("quizzes").whereEqualTo("teacherId", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    doc.reference.update("teacherId", newUid).addOnFailureListener { /* ignore */ }
                }
            }.addOnFailureListener { /* ignore */ }

        // F. Teacher announcements
        db.collection("announcements").whereEqualTo("authorUid", oldUid).get()
            .addOnSuccessListener { snaps ->
                for (doc in snaps) {
                    doc.reference.update("authorUid", newUid).addOnFailureListener { /* ignore */ }
                }
            }.addOnFailureListener { /* ignore */ }
    }

    private fun performActiveStudentSignIn(cleanInputId: String, fakeEmail: String, pass: String) {
        auth.signInWithEmailAndPassword(fakeEmail, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                if (user != null) {
                    db.collection("users").document(user.uid).get()
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
                                            "isApproved" to FieldValue.delete()
                                        )
                                    )
                                    "student"
                                } else {
                                    rawRole
                                }

                                val gender = doc.getString("gender") ?: doc.getString("sex") ?: "unspecified"
                                getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
                                    .putString("cached_user_gender", gender)
                                    .putString("cached_user_role", role)
                                    .apply()
                                if (role != "student") {
                                    auth.signOut()
                                    toggleLoading(false)
                                    showErrorDialog("Access Denied", "This portal login is exclusively for students.")
                                    showLogin()
                                    return@addOnSuccessListener
                                }

                                val mustChange = doc.getBoolean("mustChangePassword") ?: false
                                if (mustChange) {
                                    toggleLoading(false)
                                    showForcePasswordChangeDialog(user, user.uid, "student") {
                                        navigateToMain("student")
                                    }
                                } else {
                                    toggleLoading(false)
                                    navigateToMain("student")
                                }
                            } else {
                                // Fallback lookup: Link existing student profile if user.uid doc wasn't found directly
                                db.collection("users")
                                    .whereEqualTo("role", "student")
                                    .whereEqualTo("username", cleanInputId)
                                    .limit(2)
                                    .get()
                                    .addOnSuccessListener { snaps ->
                                        if (!snaps.isEmpty) {
                                            val matchedDoc = snaps.documents[0]
                                            val matchedData = matchedDoc.data?.toMutableMap() ?: mutableMapOf()
                                            matchedData["authEmail"] = fakeEmail
                                            matchedData["role"] = "student"
                                            db.collection("users").document(user.uid).set(matchedData)
                                                .addOnSuccessListener {
                                                    toggleLoading(false)
                                                    val mustChange = matchedDoc.getBoolean("mustChangePassword") ?: false
                                                    if (mustChange) {
                                                        showForcePasswordChangeDialog(user, user.uid, "student") {
                                                            navigateToMain("student")
                                                        }
                                                    } else {
                                                        navigateToMain("student")
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    auth.signOut()
                                                    toggleLoading(false)
                                                    showErrorDialog("System Error", "Could not synchronize student profile.")
                                                    showLogin()
                                                }
                                        } else {
                                            auth.signOut()
                                            toggleLoading(false)
                                            showErrorDialog("Access Denied", "Your student record could not be found.")
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
                        }
                        .addOnFailureListener {
                            auth.signOut()
                            toggleLoading(false)
                            showErrorDialog("System Error", "Could not verify your account status.")
                            showLogin()
                        }
                } else {
                    toggleLoading(false)
                    showErrorDialog("Login Error", "Could not verify session.")
                    showLogin()
                }
            } else {
                checkActiveStudentFallback(cleanInputId, pass, defaultTask = task)
            }
        }
    }

    private fun completeStudentAccountClaim(
        inputId: String,
        email: String,
        pass: String,
        studentData: MutableMap<String, Any>,
        pendingDocRef: com.google.firebase.firestore.DocumentReference
    ) {
        val cleanInputId = inputId.removePrefix("@").trim().lowercase()
        auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { claimTask ->
            if (claimTask.isSuccessful) {
                val newUid = claimTask.result?.user?.uid ?: auth.currentUser?.uid
                if (newUid == null) {
                    toggleLoading(false)
                    showErrorDialog("Activation Error", "Could not verify account identity.")
                    showLogin()
                    return@addOnCompleteListener
                }
                studentData["authEmail"] = email
                studentData["isClaimed"] = true
                studentData["is_onboarded"] = false
                studentData["role"] = "student"
                XPManager.setOnboarded(this, false)
                studentData.remove("password")
                studentData.remove("tempPassword")
                val gender = (studentData["gender"] as? String) ?: "unspecified"
                getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
                    .putString("cached_user_gender", gender)
                    .putString("cached_user_role", "student")
                    .apply()
                db.collection("users").document(newUid).set(studentData)
                    .addOnSuccessListener {
                        pendingDocRef.delete().addOnFailureListener { /* ignore */ }
                        db.collection("pending_students").document(cleanInputId).delete().addOnFailureListener { /* ignore */ }
                        db.collection("pending_students").document("@$cleanInputId").delete().addOnFailureListener { /* ignore */ }
                        val schoolId = studentData["schoolId"] as? String ?: ""
                        val grade = studentData["grade"] as? String ?: ""
                        val section = studentData["section"] as? String ?: ""
                        ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(newUid, schoolId, grade, section)
                        toggleLoading(false)
                        val claimedUser = auth.currentUser
                        if (claimedUser != null) {
                            showForcePasswordChangeDialog(claimedUser, newUid, "student") {
                                navigateToMain("student")
                            }
                        } else {
                            navigateToMain("student")
                        }
                    }
                    .addOnFailureListener { setErr ->
                        toggleLoading(false)
                        showErrorDialog("Profile Creation Error", "Failed to initialize student profile: ${setErr.localizedMessage ?: "Unknown error"}")
                        showLogin()
                    }
            } else {
                val ex = claimTask.exception
                if (ex is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                    // Collision fallback: try signing into existing Auth user with pass
                    auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { reLoginTask ->
                        if (reLoginTask.isSuccessful) {
                            val recoveredUid = reLoginTask.result?.user?.uid ?: auth.currentUser?.uid
                            if (recoveredUid == null) {
                                toggleLoading(false)
                                showErrorDialog("Activation Error", "Could not verify existing account identity.")
                                showLogin()
                                return@addOnCompleteListener
                            }
                            studentData["authEmail"] = email
                            studentData["isClaimed"] = true
                            studentData["is_onboarded"] = false
                            studentData["role"] = "student"
                            XPManager.setOnboarded(this, false)
                            studentData.remove("password")
                            studentData.remove("tempPassword")
                            val gender = (studentData["gender"] as? String) ?: "unspecified"
                            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
                                .putString("cached_user_gender", gender)
                                .putString("cached_user_role", "student")
                                .apply()
                            db.collection("users").document(recoveredUid).set(studentData)
                                .addOnSuccessListener {
                                    pendingDocRef.delete().addOnFailureListener { /* ignore */ }
                                    db.collection("pending_students").document(cleanInputId).delete().addOnFailureListener { /* ignore */ }
                                    db.collection("pending_students").document("@$cleanInputId").delete().addOnFailureListener { /* ignore */ }
                                    val schoolId = studentData["schoolId"] as? String ?: ""
                                    val grade = studentData["grade"] as? String ?: ""
                                    val section = studentData["section"] as? String ?: ""
                                    ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(recoveredUid, schoolId, grade, section)
                                    toggleLoading(false)
                                    val claimedUser = auth.currentUser
                                    if (claimedUser != null) {
                                        showForcePasswordChangeDialog(claimedUser, recoveredUid, "student") {
                                            navigateToMain("student")
                                        }
                                    } else {
                                        navigateToMain("student")
                                    }
                                }
                                .addOnFailureListener { recErr ->
                                    toggleLoading(false)
                                    showErrorDialog("Profile Recovery Error", "Failed to link student profile: ${recErr.localizedMessage ?: "Unknown error"}")
                                    showLogin()
                                }
                        } else {
                            // Suffix a unique salt to email to guarantee clean claim
                            val saltedEmail = "${cleanInputId}.${System.currentTimeMillis() % 100000}@gabai.app"
                            auth.createUserWithEmailAndPassword(saltedEmail, pass).addOnCompleteListener { retryTask ->
                                if (retryTask.isSuccessful) {
                                    val retryUid = retryTask.result?.user?.uid ?: auth.currentUser?.uid
                                    if (retryUid == null) {
                                        toggleLoading(false)
                                        showErrorDialog("Activation Error", "Could not verify retry account identity.")
                                        showLogin()
                                        return@addOnCompleteListener
                                    }
                                    studentData["authEmail"] = saltedEmail
                                    studentData["isClaimed"] = true
                                    studentData["is_onboarded"] = false
                                    studentData["role"] = "student"
                                    XPManager.setOnboarded(this, false)
                                    studentData.remove("password")
                                    studentData.remove("tempPassword")
                                    val gender = (studentData["gender"] as? String) ?: "unspecified"
                                    getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
                                        .putString("cached_user_gender", gender)
                                        .putString("cached_user_role", "student")
                                        .apply()
                                    db.collection("users").document(retryUid).set(studentData)
                                        .addOnSuccessListener {
                                            pendingDocRef.delete().addOnFailureListener { /* ignore */ }
                                            db.collection("pending_students").document(cleanInputId).delete().addOnFailureListener { /* ignore */ }
                                            db.collection("pending_students").document("@$cleanInputId").delete().addOnFailureListener { /* ignore */ }
                                            val schoolId = studentData["schoolId"] as? String ?: ""
                                            val grade = studentData["grade"] as? String ?: ""
                                            val section = studentData["section"] as? String ?: ""
                                            ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(retryUid, schoolId, grade, section)
                                            toggleLoading(false)
                                            val claimedUser = auth.currentUser
                                            if (claimedUser != null) {
                                                showForcePasswordChangeDialog(claimedUser, retryUid, "student") {
                                                    navigateToMain("student")
                                                }
                                            } else {
                                                navigateToMain("student")
                                            }
                                        }
                                        .addOnFailureListener { retryErr ->
                                            toggleLoading(false)
                                            showErrorDialog("Profile Salt Error", "Failed to initialize student profile: ${retryErr.localizedMessage ?: "Unknown error"}")
                                            showLogin()
                                        }
                                } else {
                                    toggleLoading(false)
                                    showErrorDialog("Account Conflict", "This student account encountered a credentials conflict. Please contact your adviser to reset your login.")
                                    showLogin()
                                }
                            }
                        }
                    }
                } else {
                    toggleLoading(false)
                    showErrorDialog("System Error", "Could not initialize your account: ${ex?.localizedMessage ?: "Unknown error"}")
                    showLogin()
                }
            }
        }
    }

    private fun checkActiveStudentFallback(
        inputId: String,
        pass: String,
        defaultTask: com.google.android.gms.tasks.Task<com.google.firebase.auth.AuthResult>? = null
    ) {
        val cleanInputId = inputId.removePrefix("@").trim().lowercase()
        val handleMatch: (com.google.firebase.firestore.QuerySnapshot) -> Unit = { snaps ->
            // Prioritize document where pass matches tempPassword or password; otherwise latest migrated or created
            val studentDoc = snaps.documents.firstOrNull { d ->
                val tPass = d.getString("tempPassword") ?: ""
                val pPass = d.getString("password") ?: ""
                (tPass.isNotBlank() && tPass == pass) || (pPass.isNotBlank() && pPass == pass)
            } ?: snaps.documents.maxByOrNull { it.getLong("migratedAt") ?: it.getLong("createdAt") ?: 0L } ?: snaps.documents[0]

            val customEmail = studentDoc.getString("authEmail") ?: ""
            val tempPassword = studentDoc.getString("tempPassword") ?: ""
            val directPassword = studentDoc.getString("password") ?: ""
            val defaultEmail = "$cleanInputId@gabai.app"

            // Case A: Student has a custom/salted authEmail and typed their permanent password
            if (customEmail.isNotBlank() && customEmail != defaultEmail) {
                auth.signInWithEmailAndPassword(customEmail, pass).addOnCompleteListener { altTask ->
                    if (altTask.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            val mustChange = studentDoc.getBoolean("mustChangePassword") ?: false
                            if (mustChange) {
                                toggleLoading(false)
                                showForcePasswordChangeDialog(user, user.uid, "student") {
                                    navigateToMain("student")
                                }
                            } else {
                                toggleLoading(false)
                                navigateToMain("student")
                            }
                        }
                    } else {
                        checkTempPasswordReset(studentDoc, pass, tempPassword, directPassword)
                    }
                }
            } else {
                checkTempPasswordReset(studentDoc, pass, tempPassword, directPassword)
            }
        }

        db.collection("users")
            .whereEqualTo("role", "student")
            .whereEqualTo("username", cleanInputId)
            .limit(2)
            .get()
            .addOnSuccessListener { snaps ->
                if (!snaps.isEmpty) {
                    handleMatch(snaps)
                } else {
                    // Fallback to query with leading @ if needed
                    db.collection("users")
                        .whereEqualTo("role", "student")
                        .whereEqualTo("username", "@$cleanInputId")
                        .limit(2)
                        .get()
                        .addOnSuccessListener { altSnaps ->
                            if (!altSnaps.isEmpty) {
                                handleMatch(altSnaps)
                            } else {
                                toggleLoading(false)
                                showErrorDialog("Login Failed", "Invalid Username or Password.")
                                showLogin()
                            }
                        }
                        .addOnFailureListener {
                            toggleLoading(false)
                            showErrorDialog("Login Failed", defaultTask?.exception?.localizedMessage ?: "Invalid credentials")
                            showLogin()
                        }
                }
            }
            .addOnFailureListener {
                toggleLoading(false)
                showErrorDialog("Login Failed", defaultTask?.exception?.localizedMessage ?: "Invalid credentials")
                showLogin()
            }
    }

    private fun checkTempPasswordReset(
        studentDoc: com.google.firebase.firestore.DocumentSnapshot,
        pass: String,
        tempPassword: String,
        directPassword: String
    ) {
        val matchesTemp = (tempPassword.isNotBlank() && tempPassword == pass) || (directPassword.isNotBlank() && directPassword == pass)
        if (matchesTemp) {
            // Teacher or School Admin has reset student password! Issue fresh credentials and migrate
            val studentData = studentDoc.data?.toMutableMap() ?: mutableMapOf()
            val rawUsername = studentDoc.getString("username") ?: "student"
            val cleanUsername = rawUsername.removePrefix("@").trim().lowercase()
            val saltedEmail = "${cleanUsername}.${System.currentTimeMillis() % 100000}@gabai.app"
            val oldUid = studentDoc.id

            auth.createUserWithEmailAndPassword(saltedEmail, pass).addOnCompleteListener { resetClaimTask ->
                if (resetClaimTask.isSuccessful) {
                    val newUid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    studentData["authEmail"] = saltedEmail
                    studentData["mustChangePassword"] = true
                    studentData["isClaimed"] = true
                    val wasOnboarded = studentDoc.getBoolean("is_onboarded") ?: false
                    val qCompleted = (studentDoc.get("quests_completed") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList<String>()
                    val hasAllQuests = qCompleted.contains(ApprenticeQuestHelper.QUEST_BUBBLE) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_BUBBLE_SCAN) &&
                        (qCompleted.contains(ApprenticeQuestHelper.QUEST_TEST) || qCompleted.contains("read")) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_SCAN) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_SAVE) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_HISTORY) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_DETAIL) &&
                        qCompleted.contains(ApprenticeQuestHelper.QUEST_LIBRARY)
                    val finalOnboarded = wasOnboarded && hasAllQuests
                    studentData["is_onboarded"] = finalOnboarded
                    XPManager.setOnboarded(this, finalOnboarded)
                    studentData["role"] = "student"
                    studentData["migratedAt"] = System.currentTimeMillis()
                    studentData.remove("tempPassword")
                    studentData.remove("password")

                    db.collection("users").document(newUid).set(studentData).addOnSuccessListener {
                        // 1. Migrate quiz history subcollection
                        db.collection("users").document(oldUid).collection("quiz_history").get()
                            .addOnSuccessListener { quizSnaps ->
                                for (q in quizSnaps) {
                                    db.collection("users").document(newUid).collection("quiz_history").document(q.id).set(q.data)
                                    q.reference.delete().addOnFailureListener { /* ignore */ }
                                }
                            }.addOnFailureListener { /* ignore */ }

                        // 2. Migrate diagnostic results subcollection
                        db.collection("users").document(oldUid).collection("diagnostic_results").get()
                            .addOnSuccessListener { diagSnaps ->
                                for (d in diagSnaps) {
                                    db.collection("users").document(newUid).collection("diagnostic_results").document(d.id).set(d.data)
                                    d.reference.delete().addOnFailureListener { /* ignore */ }
                                }
                            }.addOnFailureListener { /* ignore */ }

                        // 3. Migrate favorites subcollection
                        db.collection("users").document(oldUid).collection("favorites").get()
                            .addOnSuccessListener { favSnaps ->
                                for (f in favSnaps) {
                                    db.collection("users").document(newUid).collection("favorites").document(f.id).set(f.data)
                                    f.reference.delete().addOnFailureListener { /* ignore */ }
                                }
                            }.addOnFailureListener { /* ignore */ }

                        // 4. Migrate search/scan history subcollection
                        db.collection("users").document(oldUid).collection("history").get()
                            .addOnSuccessListener { histSnaps ->
                                for (h in histSnaps) {
                                    db.collection("users").document(newUid).collection("history").document(h.id).set(h.data)
                                    h.reference.delete().addOnFailureListener { /* ignore */ }
                                }
                            }.addOnFailureListener { /* ignore */ }

                        // 5. Migrate assessment submissions to newUid
                        db.collection("assessment_submissions")
                            .whereEqualTo("studentUid", oldUid)
                            .get()
                            .addOnSuccessListener { subSnaps ->
                                for (sub in subSnaps) {
                                    val data = sub.data.toMutableMap()
                                    data["studentUid"] = newUid
                                    data["studentId"] = newUid
                                    val assessmentId = sub.getString("assessmentId") ?: ""
                                    val newDocId = if (assessmentId.isNotBlank()) "${assessmentId}_${newUid}" else sub.id
                                    db.collection("assessment_submissions").document(newDocId).set(data)
                                    sub.reference.delete().addOnFailureListener { /* ignore */ }
                                }
                            }.addOnFailureListener { /* ignore */ }

                        // 6. Update classes and auto-join
                        val schoolId = studentData["schoolId"] as? String ?: ""
                        val grade = studentData["grade"] as? String ?: ""
                        val section = studentData["section"] as? String ?: ""
                        ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(newUid, schoolId, grade, section)

                        // 7. Clear stale SharedPreferences caches
                        getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).edit().clear().apply()

                        // 8. Clean up old user doc safely
                        if (oldUid != newUid) {
                            studentDoc.reference.delete().addOnFailureListener { /* ignore if not superadmin */ }
                        }
                        db.collection("pending_students").document(cleanUsername).delete().addOnFailureListener { /* ignore */ }
                        db.collection("pending_students").document("@$cleanUsername").delete().addOnFailureListener { /* ignore */ }

                        toggleLoading(false)
                        val claimedUser = auth.currentUser
                        if (claimedUser != null) {
                            showForcePasswordChangeDialog(claimedUser, newUid, "student") {
                                navigateToMain("student")
                            }
                        } else {
                            navigateToMain("student")
                        }
                    }.addOnFailureListener { setErr ->
                        toggleLoading(false)
                        showErrorDialog("Reset Sync Error", setErr.localizedMessage ?: "Failed to initialize updated student profile.")
                        showLogin()
                    }
                } else {
                    toggleLoading(false)
                    showErrorDialog("Temporary Login Error", resetClaimTask.exception?.localizedMessage ?: "Could not verify temporary password.")
                    showLogin()
                }
            }
        } else {
            toggleLoading(false)
            showErrorDialog("Login Failed", "Invalid Username or Password.")
            showLogin()
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
        if (selectedRole == "student") {
            GabAIDialogs.showNoticeDialog(
                context = this,
                title = "Student Password Recovery",
                message = "Student accounts do not use email resets.\n\nPlease ask your Teacher or Class Adviser to reset your password to a temporary passkey from Class Management.",
                buttonText = "Understood",
                badgeIcon = "👨‍🏫"
            )
            return
        }

        GabAIDialogs.showInputDialog(
            context = this,
            title = "Reset Password",
            subtitle = "We will send a password reset link to your email address.",
            hint = "Enter your registered email",
            confirmText = "Send Link",
            badgeIcon = "🔐",
            onConfirm = { email ->
                sendPasswordReset(email)
            }
        )
    }

    private fun showForcePasswordChangeDialog(
        user: FirebaseUser,
        uid: String,
        targetRole: String? = null,
        onComplete: () -> Unit
    ) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        val etNewPass = EditText(this).apply {
            hint = "Enter new password (min. 6 chars)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#A0AEC0"))
            textSize = 14f
        }

        val etConfirmPass = EditText(this).apply {
            hint = "Confirm new password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundResource(R.drawable.bg_modern_input)
            setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
            setTextColor(Color.parseColor("#2D3436"))
            setHintTextColor(Color.parseColor("#A0AEC0"))
            textSize = 14f
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
            layoutParams = params
        }

        container.addView(etNewPass)
        container.addView(etConfirmPass)

        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val effectiveRole = targetRole ?: selectedRole
        val isStudent = effectiveRole == "student"
        var selectedGender = prefs.getString("cached_user_gender", "unspecified") ?: "unspecified"

        val tvGenderLabel = TextView(this).apply {
            text = "Learner Gender"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, (14 * density).toInt(), 0, (6 * density).toInt())
        }
        val genderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * density).toInt() }
        }

        val btnMale = TextView(this).apply {
            text = "👦 Male (Boy)"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val btnFemale = TextView(this).apply {
            text = "👧 Female (Girl)"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (6 * density).toInt()
            }
        }

        fun updateGenderPills() {
            if (selectedGender == "male") {
                btnMale.setBackgroundResource(R.drawable.bg_pill_boy)
                btnMale.setTextColor(Color.parseColor("#3730A3"))
                btnFemale.setBackgroundResource(R.drawable.bg_pill_white)
                btnFemale.setTextColor(Color.parseColor("#64748B"))
            } else if (selectedGender == "female") {
                btnFemale.setBackgroundResource(R.drawable.bg_pill_girl)
                btnFemale.setTextColor(Color.parseColor("#9D174D"))
                btnMale.setBackgroundResource(R.drawable.bg_pill_white)
                btnMale.setTextColor(Color.parseColor("#64748B"))
            } else {
                btnMale.setBackgroundResource(R.drawable.bg_pill_white)
                btnMale.setTextColor(Color.parseColor("#64748B"))
                btnFemale.setBackgroundResource(R.drawable.bg_pill_white)
                btnFemale.setTextColor(Color.parseColor("#64748B"))
            }
        }
        updateGenderPills()

        btnMale.setOnClickListener {
            selectedGender = "male"
            updateGenderPills()
        }
        btnFemale.setOnClickListener {
            selectedGender = "female"
            updateGenderPills()
        }

        genderRow.addView(btnMale)
        genderRow.addView(btnFemale)

        if (isStudent) {
            container.addView(tvGenderLabel)
            container.addView(genderRow)
        }

        var forceDialog: android.app.Dialog? = null
        forceDialog = GabAIDialogs.showCustomDialog(
            context = this,
            title = "Create New Password",
            subtitle = if (isStudent) "For your account security, you must update your temporary password before accessing GabAI." else "Please choose a permanent password for your educator account.",
            customView = container,
            confirmText = "Update & Continue",
            cancelText = "Sign Out",
            badgeIcon = "🔑",
            onConfirm = { dialog ->
                val newPass = etNewPass.text.toString().trim()
                val confirmPass = etConfirmPass.text.toString().trim()

                if (newPass.length < 6) {
                    GabAIUtils.showSnackbar(this, "Password must be at least 6 characters")
                    return@showCustomDialog
                }
                if (newPass != confirmPass) {
                    GabAIUtils.showSnackbar(this, "Passwords do not match")
                    return@showCustomDialog
                }
                if (isStudent && selectedGender == "unspecified") {
                    GabAIUtils.showSnackbar(this, "Please select whether you are Male (Boy) or Female (Girl)")
                    return@showCustomDialog
                }

                toggleLoading(true)
                user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        val updates = mutableMapOf<String, Any>(
                            "mustChangePassword" to false,
                            "tempPassword" to FieldValue.delete(),
                            "password" to FieldValue.delete()
                        )
                        if (isStudent) {
                            updates["role"] = "student"
                            updates["isClaimed"] = true
                            updates["gender"] = selectedGender
                            prefs.edit()
                                .putString("cached_user_role", "student")
                                .putString("cached_user_gender", selectedGender)
                                .apply()
                        } else {
                            updates["role"] = "teacher"
                            prefs.edit().putString("cached_user_role", "teacher").apply()
                        }
                        db.collection("users").document(uid).update(updates).addOnCompleteListener {
                            if (isStudent) {
                                db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                                    val uName = userDoc.getString("username")?.removePrefix("@")?.trim()?.lowercase() ?: ""
                                    if (uName.isNotBlank()) {
                                        val studentDoneUpdates = mapOf<String, Any>(
                                            "mustChangePassword" to false,
                                            "password" to FieldValue.delete(),
                                            "tempPassword" to FieldValue.delete()
                                        )
                                        db.collection("pending_students").document(uName).set(studentDoneUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }
                                        db.collection("pending_students").document("@$uName").set(studentDoneUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }
                                    }
                                    val schoolId = userDoc.getString("schoolId") ?: ""
                                    val grade = userDoc.getString("grade") ?: ""
                                    val section = userDoc.getString("section") ?: ""
                                    ClassEnrollmentHelper.autoJoinStudentToAdvisoryClass(uid, schoolId, grade, section) { adviserName, className ->
                                        toggleLoading(false)
                                        dialog.dismiss()
                                        GabAIDialogs.showNoticeDialog(
                                            context = this,
                                            title = "Class Joined!",
                                            message = "You have joined $adviserName's class for $className.",
                                            buttonText = "Continue",
                                            badgeIcon = "🎒",
                                            cancelable = false,
                                            onAction = {
                                                onComplete()
                                            }
                                        )
                                    }
                                }.addOnFailureListener {
                                    toggleLoading(false)
                                    dialog.dismiss()
                                    onComplete()
                                }
                            } else {
                                val teacherEmail = user.email?.trim()?.lowercase() ?: ""
                                val teacherSlipUpdates = mapOf<String, Any>(
                                    "mustChangePassword" to false,
                                    "password" to FieldValue.delete(),
                                    "tempPassword" to FieldValue.delete()
                                )
                                if (teacherEmail.isNotBlank()) {
                                    db.collection("pending_students").document(teacherEmail).set(teacherSlipUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }
                                }
                                val rawEmail = binding.etLoginEmail.text.toString().trim().lowercase()
                                if (rawEmail.isNotBlank() && rawEmail != teacherEmail) {
                                    db.collection("pending_students").document(rawEmail).set(teacherSlipUpdates, SetOptions.merge()).addOnFailureListener { /* ignore */ }
                                }
                                toggleLoading(false)
                                dialog.dismiss()
                                onComplete()
                            }
                        }
                    } else {
                        toggleLoading(false)
                        GabAIUtils.showSnackbar(this, updateTask.exception?.localizedMessage ?: "Failed to update password")
                    }
                }
            },
            onCancel = { dialog ->
                auth.signOut()
                dialog.dismiss()
                showLogin()
            }
        )
        forceDialog.setCancelable(false)
        forceDialog.setCanceledOnTouchOutside(false)
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
        val isSuccess = title.contains("Email", ignoreCase = true) || title.contains("Success", ignoreCase = true)
        GabAIDialogs.showNoticeDialog(
            context = this,
            title = title,
            message = message,
            buttonText = "OK",
            badgeIcon = if (isSuccess) "📧" else "⚠️"
        )
    }

    private fun navigateToMain(role: String?) {
        val targetRole = role ?: selectedRole
        getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).edit()
            .putString("cached_user_role", targetRole)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afm = getSystemService(AutofillManager::class.java)
            afm?.commit()
        }
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("USER_ROLE", targetRole)
        startActivity(intent)
        finish()
    }

    private var loadingWatchdogRunnable: Runnable? = null
    private val loadingHandler = Handler(Looper.getMainLooper())

    private fun toggleLoading(isLoading: Boolean) {
        val activeWatchdog = loadingWatchdogRunnable
        if (activeWatchdog != null) {
            loadingHandler.removeCallbacks(activeWatchdog)
        }
        loadingWatchdogRunnable = null

        if (isLoading) {
            GabAIUtils.showGlobalLoading(this)
            val watchdog = Runnable {
                if (!isFinishing && !isDestroyed) {
                    GabAIUtils.hideGlobalLoading(this)
                    showErrorDialog("Connection Timeout", "The server is taking too long to respond. Please check your internet connection and try again.")
                    showLogin()
                }
            }
            loadingWatchdogRunnable = watchdog
            loadingHandler.postDelayed(watchdog, 15000L)
        } else {
            GabAIUtils.hideGlobalLoading(this)
        }
    }
}
