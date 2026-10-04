package com.example.gabai

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.gabai.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingBubbleLaunchCallback: (() -> Unit)? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            FloatingControlService.isRunning = true
            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", true).apply()

            val intent = Intent(this, FloatingControlService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            startService(intent)

            updateBubbleUi(true, animate = true)
            GabAIUtils.performHaptic(binding.btnBubbleToggle, android.view.HapticFeedbackConstants.CONFIRM)
            GabAIUtils.showSnackbar(this, "Bubble Active! 🧚‍♂️")

            val triggerUid = FirebaseAuth.getInstance().currentUser?.uid
            if (triggerUid != null) {
                FirebaseFirestore.getInstance().collection("users").document(triggerUid)
                    .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("bubble"))
            }

            pendingBubbleLaunchCallback?.invoke()
            pendingBubbleLaunchCallback = null
        } else {
            pendingBubbleLaunchCallback = null
            FloatingControlService.isRunning = false
            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", false).apply()
            updateBubbleUi(false, animate = true)
            GabAIUtils.showSnackbar(this, "Permission denied")
        }
    }

    private val bubbleStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val enabled = intent?.getBooleanExtra("is_enabled", false) ?: false
            FloatingControlService.isRunning = enabled
            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("bubble_enabled", enabled).apply()
            updateBubbleUi(enabled, animate = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Uncap display refresh rate to hardware maximum (90Hz on Galaxy A24)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val disp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            val maxMode = disp?.supportedModes?.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        val userRole = intent.getStringExtra("USER_ROLE")
        if (userRole != null) {
            loadDashboard(userRole)
            // Background verification of account status to catch deleted accounts or role shifts
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get().addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        showAccountDeletedDialog()
                    }
                }
        } else {
            GabAIUtils.showGlobalLoading(this, "Verifying Account...")

            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .get().addOnSuccessListener { doc ->
                    GabAIUtils.hideGlobalLoading(this)

                    if (!doc.exists()) {
                        showAccountDeletedDialog()
                        return@addOnSuccessListener
                    }

                    val role = doc.getString("role") ?: "student"
                    loadDashboard(role)
                }.addOnFailureListener {
                    GabAIUtils.hideGlobalLoading(this)
                    val cachedRole = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                        .getString("cached_user_role", "student") ?: "student"
                    loadDashboard(cachedRole)
                    GabAIUtils.showSnackbar(this, "Operating offline. Check connection to sync.")
                }
        }

        // Adjust padding for system bars
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { v, insets ->
            val systemBars =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // Clip bottom navigation strictly to 24dp rounded top corners
        binding.blurBottomNav.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                val radius = 24 * view.resources.displayMetrics.density
                outline.setRoundRect(0, 0, view.width, (view.height + radius).toInt(), radius)
            }
        }
        binding.blurBottomNav.clipToOutline = true

        // Hardware-accelerated ultra-low-resolution frosted glass blur (downsampled 12x for zero lag)
        binding.blurHeaderBar.setupWith(
            binding.blurTargetMain,
            downsampleFactor = 12f,
            blurRadius = 2.5f,
            overlayColor = Color.parseColor("#BFFFFFFF")
        )
        binding.blurBottomNav.setupWith(
            binding.blurTargetMain,
            downsampleFactor = 12f,
            blurRadius = 2.5f,
            overlayColor = Color.parseColor("#BFFFFFFF")
        )

        // Wire Option A interactive Material Bubble Button with spring press effect and haptics
        GabAIUtils.addSpringPressEffect(binding.btnBubbleToggle) {
            GabAIUtils.performHaptic(binding.btnBubbleToggle)
            toggleBubble()
        }
        syncBubbleButtonState()

        // Wire elevated center circular scanner button with haptic feedback
        GabAIUtils.addSpringPressEffect(binding.btnCenterScanner) {
            GabAIUtils.performHaptic(binding.btnCenterScanner)
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            GabAIUtils.performHaptic(binding.bottomNavigation)
            val currentRole = binding.root.tag as? String ?: "student"
            when (item.itemId) {
                R.id.nav_home -> {
                    val targetTag = if (currentRole == "teacher") "teacher_home" else "student_home"
                    showFragmentByTag(targetTag)
                    true
                }
                R.id.nav_profile -> {
                    showFragmentByTag("profile")
                    true
                }
                else -> false
            }
        }

        // Double-tap or reselect active tab to scroll to top
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            GabAIUtils.performHaptic(binding.bottomNavigation)
            if (item.itemId == R.id.nav_home) {
                val scrollTarget = activeFragment?.view?.findViewById<android.view.View>(R.id.scroll_content)
                when (scrollTarget) {
                    is androidx.core.widget.NestedScrollView -> scrollTarget.smoothScrollTo(0, 0)
                    is android.widget.ScrollView -> scrollTarget.smoothScrollTo(0, 0)
                    else -> scrollTarget?.scrollTo(0, 0)
                }
            }
        }

        // System Back navigation: If on Profile, navigate back to Home first before exiting
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Ensure bubble toggle button scales inward from right edge without cropping
        binding.btnBubbleToggle.post {
            if (binding.btnBubbleToggle.width > 0) {
                binding.btnBubbleToggle.pivotX = binding.btnBubbleToggle.width.toFloat()
                binding.btnBubbleToggle.pivotY = binding.btnBubbleToggle.height.toFloat() / 2f
            }
        }
    }

    // ==========================================
    // ==========================================
    // 🟢 FORCE LOGOUT DIALOGS 🟢
    // ==========================================
    private fun showAdminWebOnlyDialog() {
        GabAIDialogs.showNoticeDialog(
            context = this,
            title = "Web Administrative Portal Only",
            message = "Super Admin and School Admin accounts must sign in using the GabAI Web Administrative Portal.\n\nThe mobile application is exclusively designed for Teachers and Students.",
            buttonText = "Log Out",
            badgeIcon = "💻",
            cancelable = false,
            onAction = {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
        )
    }

    private fun showAccountDeletedDialog() {
        GabAIDialogs.showNoticeDialog(
            context = this,
            title = "Account Not Found",
            message = "Your account details could not be found. It may have been deleted by your adviser or school administrator.\n\nPlease log out.",
            buttonText = "Log Out",
            badgeIcon = "🚫",
            cancelable = false,
            onAction = {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
            }
        )
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.example.gabai.ACTION_BUBBLE_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bubbleStateReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(bubbleStateReceiver)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        syncBubbleButtonState()
        // Automatically check for updates on foreground resume (debounced)
        GitHubUpdateHelper.checkUpdate(this, isBackground = true) {}
    }

    @Suppress("DEPRECATION")
    fun isBubbleActive(): Boolean {
        if (FloatingControlService.isRunning) return true
        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        val prefEnabled = prefs.getBoolean("bubble_enabled", false)

        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (manager != null) {
            val services = manager.getRunningServices(Int.MAX_VALUE)
            for (service in services) {
                if (FloatingControlService::class.java.name == service.service.className) {
                    FloatingControlService.isRunning = true
                    return true
                }
            }
        }
        return prefEnabled && FloatingControlService.isRunning
    }

    fun syncBubbleButtonState() {
        val currentRole = binding.root.tag as? String ?: "student"
        if (currentRole == "teacher") {
            binding.btnBubbleToggle.visibility = View.GONE
            return
        }
        val active = isBubbleActive()
        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bubble_enabled", active).apply()
        updateBubbleUi(active, animate = false)
    }

    fun updateBubbleUi(isActive: Boolean, animate: Boolean = true) {
        val currentRole = binding.root.tag as? String ?: "student"
        if (currentRole == "teacher") {
            binding.btnBubbleToggle.visibility = View.GONE
            return
        }
        val btn = binding.btnBubbleToggle
        val targetText = if (isActive) "Bubble Active" else "Launch Bubble"
        val targetBgColor = if (isActive) Color.parseColor("#00B894") else Color.parseColor("#F8FAFC")
        val targetStrokeColor = if (isActive) Color.parseColor("#00A383") else Color.parseColor("#CBD5E1")
        val targetTextColor = if (isActive) Color.WHITE else Color.parseColor("#475569")
        val targetIconTint = if (isActive) Color.WHITE else Color.parseColor("#5341CD")
        val targetElevation = if (isActive) 3f * resources.displayMetrics.density else 1f * resources.displayMetrics.density

        if (!animate || btn.text == targetText) {
            btn.text = targetText
            btn.setTextColor(targetTextColor)
            btn.iconTint = ColorStateList.valueOf(targetIconTint)
            btn.backgroundTintList = ColorStateList.valueOf(targetBgColor)
            btn.strokeColor = ColorStateList.valueOf(targetStrokeColor)
            btn.elevation = targetElevation
            return
        }

        // Morphing Color Animator
        val currentBg = btn.backgroundTintList?.defaultColor ?: (if (isActive) Color.parseColor("#F8FAFC") else Color.parseColor("#00B894"))
        val currentStroke = btn.strokeColor?.defaultColor ?: (if (isActive) Color.parseColor("#CBD5E1") else Color.parseColor("#00A383"))
        val currentTextCol = btn.currentTextColor
        val currentIcon = btn.iconTint?.defaultColor ?: (if (isActive) Color.parseColor("#5341CD") else Color.WHITE)

        val evaluator = ArgbEvaluator()
        val colorAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                btn.backgroundTintList = ColorStateList.valueOf(evaluator.evaluate(fraction, currentBg, targetBgColor) as Int)
                btn.strokeColor = ColorStateList.valueOf(evaluator.evaluate(fraction, currentStroke, targetStrokeColor) as Int)
                btn.setTextColor(evaluator.evaluate(fraction, currentTextCol, targetTextColor) as Int)
                btn.iconTint = ColorStateList.valueOf(evaluator.evaluate(fraction, currentIcon, targetIconTint) as Int)
            }
        }

        // Tactile Spring Rebound Animation (Anchored to right edge so expansion goes inward)
        btn.animate().cancel()
        if (btn.width > 0) {
            btn.pivotX = btn.width.toFloat()
            btn.pivotY = btn.height.toFloat() / 2f
        }
        btn.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(100)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                btn.text = targetText
                btn.elevation = targetElevation
                if (btn.width > 0) {
                    btn.pivotX = btn.width.toFloat()
                    btn.pivotY = btn.height.toFloat() / 2f
                }
                btn.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(160)
                    .setInterpolator(OvershootInterpolator(1.8f))
                    .withEndAction {
                        btn.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(90)
                            .start()
                    }
                    .start()
            }
            .start()

        colorAnim.start()
    }

    fun toggleBubble(enable: Boolean? = null, onLaunched: (() -> Unit)? = null) {
        val currentlyActive = isBubbleActive() || binding.btnBubbleToggle.text == "Bubble Active"
        val shouldEnable = enable ?: !currentlyActive
        val prefs = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)

        if (shouldEnable) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                updateBubbleUi(false, animate = false)
            } else {
                pendingBubbleLaunchCallback = onLaunched
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val config = MediaProjectionConfig.createConfigForDefaultDisplay()
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent(config))
                } else {
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            }
        } else {
            stopService(Intent(this, FloatingControlService::class.java))
            FloatingControlService.isRunning = false
            prefs.edit().putBoolean("bubble_enabled", false).apply()
            updateBubbleUi(false, animate = true)
            GabAIUtils.performHaptic(binding.btnBubbleToggle, android.view.HapticFeedbackConstants.REJECT)
            GabAIUtils.showSnackbar(this, "Bubble Deactivated")
        }
    }

    // Caching fragment switcher (preserves state, view hierarchy, and scroll position)
    private var activeFragment: Fragment? = null

    private fun applyRoleUi(role: String) {
        // GabAI frosted glass header is always visible for both Student and Teacher!
        binding.blurHeaderBar.visibility = View.VISIBLE

        if (role == "teacher") {
            // Hide the floating bubble switch in header
            binding.btnBubbleToggle.visibility = View.GONE
            // Hide the elevated circular center scanner button
            binding.btnCenterScanner.visibility = View.GONE
            // Hide placeholder item in bottom nav so Home and Profile space evenly
            binding.bottomNavigation.menu.findItem(R.id.nav_placeholder)?.isVisible = false
        } else {
            // Student: show bubble switch, center scanner, and placeholder
            binding.btnBubbleToggle.visibility = View.VISIBLE
            binding.btnCenterScanner.visibility = View.VISIBLE
            binding.bottomNavigation.menu.findItem(R.id.nav_placeholder)?.isVisible = true
        }
    }

    private fun loadDashboard(role: String) {
        if (role == "super_admin" || role == "school_admin" || role == "admin") {
            showAdminWebOnlyDialog()
            return
        }
        binding.root.tag = role // Save role so the bottom menu knows which one to show
        applyRoleUi(role)
        val targetTag = if (role == "teacher") "teacher_home" else "student_home"
        showFragmentByTag(targetTag)
    }

    private fun showFragmentByTag(tag: String) {
        if (isFinishing || isDestroyed) return

        val currentRole = binding.root.tag as? String ?: "student"
        applyRoleUi(currentRole)

        val fm = supportFragmentManager
        val target = fm.findFragmentByTag(tag) ?: when (tag) {
            "teacher_home" -> TeacherHomeFragment()
            "profile" -> ProfileFragment()
            else -> HomeFragment()
        }

        if (activeFragment === target && target.isAdded && target.isVisible) return

        val currentTag = activeFragment?.tag
        val currentIndex = if (currentTag == "profile") 1 else 0
        val targetIndex = if (tag == "profile") 1 else 0
        val isDirectional = activeFragment != null && activeFragment !== target

        val outgoingFragment = activeFragment
        val outgoingView = outgoingFragment?.view

        // Immediately cancel any in-flight animations
        outgoingView?.animate()?.cancel()

        val transaction = fm.beginTransaction()
        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, tag)
        } else {
            transaction.show(target)
        }
        activeFragment = target
        transaction.commitNowAllowingStateLoss()

        val incomingView = target.view
        if (incomingView != null) {
            incomingView.animate()?.cancel()
            incomingView.bringToFront()

            if (isDirectional && outgoingView != null) {
                val isMovingRight = targetIndex > currentIndex
                val slideOffset = 24f * resources.displayMetrics.density
                val enterStartX = if (isMovingRight) slideOffset else -slideOffset
                val exitEndX = if (isMovingRight) -slideOffset else slideOffset

                // Hold incoming view ready and hidden until outgoing dissolves
                incomingView.translationX = enterStartX
                incomingView.alpha = 0f
                incomingView.visibility = View.INVISIBLE

                // 1. Outgoing view dissolves out cleanly first (130ms) to eliminate double-exposure overlap
                outgoingView.animate()
                    ?.translationX(exitEndX)
                    ?.alpha(0f)
                    ?.setDuration(130)
                    ?.setInterpolator(AccelerateInterpolator(1.5f))
                    ?.withEndAction {
                        if (activeFragment !== outgoingFragment && !isFinishing && !isDestroyed) {
                            outgoingView.visibility = View.GONE
                            if (outgoingFragment.isAdded) {
                                fm.beginTransaction().hide(outgoingFragment).commitNowAllowingStateLoss()
                            }
                            outgoingView.translationX = 0f
                            outgoingView.alpha = 1f
                        }
                    }
                    ?.start()

                // 2. Incoming view glides in gracefully with relaxed, deliberate pacing (260ms)
                incomingView.postDelayed({
                    if (activeFragment === target && !isFinishing && !isDestroyed) {
                        incomingView.visibility = View.VISIBLE
                        incomingView.animate()
                            ?.translationX(0f)
                            ?.alpha(1f)
                            ?.setDuration(260)
                            ?.setInterpolator(DecelerateInterpolator(1.5f))
                            ?.start()
                    }
                }, 100)
            } else {
                incomingView.translationX = 0f
                incomingView.alpha = 1f
                incomingView.visibility = View.VISIBLE
                if (outgoingFragment != null && outgoingFragment !== target && outgoingFragment.isAdded) {
                    fm.beginTransaction().hide(outgoingFragment).commitNowAllowingStateLoss()
                }
            }
        }
    }
}