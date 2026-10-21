package com.example.gabai

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

object OfflineLockoutHelper {
    private const val TAG = "OfflineLockoutHelper"
    private var isInitialized = false
    private var currentActivityRef: WeakReference<Activity>? = null
    private var activeDialog: Dialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCurrentlyOnline = true

    fun init(app: Application) {
        if (isInitialized) return
        isInitialized = true

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)
                if (!isTransientActivity(activity)) {
                    if (isNetworkConnected(activity)) {
                        if (!isCurrentlyOnline) {
                            isCurrentlyOnline = true
                            dismissOfflineDialog(false)
                        }
                    } else {
                        isCurrentlyOnline = false
                        showOfflineDialog(activity)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activeDialog?.ownerActivity == activity) {
                    try {
                        activeDialog?.dismiss()
                    } catch (_: Exception) {
                    }
                    activeDialog = null
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })

        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        isCurrentlyOnline = isNetworkConnected(app)

        try {
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post {
                        val connected = isNetworkConnected(app)
                        if (connected) {
                            if (!isCurrentlyOnline) {
                                isCurrentlyOnline = true
                                dismissOfflineDialog(true)
                            }
                        } else {
                            // Interface is up (e.g. Wi-Fi connected), but validation might still be probing.
                            // Run a quick active probe to avoid delayed unlock if validation is fast.
                            thread(start = true, name = "GabAI-AvailCheck") {
                                val actuallyOnline = checkRealInternetAccess(1500)
                                mainHandler.post {
                                    if (actuallyOnline && !isCurrentlyOnline) {
                                        isCurrentlyOnline = true
                                        dismissOfflineDialog(true)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onLost(network: Network) {
                    mainHandler.post {
                        val connected = isNetworkConnected(app)
                        if (!connected) {
                            isCurrentlyOnline = false
                            currentActivityRef?.get()?.let { act ->
                                if (!isTransientActivity(act)) {
                                    showOfflineDialog(act)
                                }
                            }
                        }
                    }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasValidatedInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                                               !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

                    mainHandler.post {
                        if (hasValidatedInternet) {
                            if (!isCurrentlyOnline) {
                                isCurrentlyOnline = true
                                dismissOfflineDialog(true)
                            }
                        } else {
                            // Connected to Wi-Fi/Cellular, but WITHOUT verified internet connectivity
                            if (isCurrentlyOnline) {
                                // Confirm via active probe to prevent false-alarm flickers during brief IP handoffs
                                thread(start = true, name = "GabAI-CapCheck") {
                                    val actuallyOnline = checkRealInternetAccess(1500)
                                    mainHandler.post {
                                        if (!actuallyOnline) {
                                            isCurrentlyOnline = false
                                            currentActivityRef?.get()?.let { act ->
                                                if (!isTransientActivity(act)) {
                                                    showOfflineDialog(act)
                                                }
                                            }
                                        } else {
                                            isCurrentlyOnline = true
                                        }
                                    }
                                }
                            } else {
                                currentActivityRef?.get()?.let { act ->
                                    if (!isTransientActivity(act)) {
                                        showOfflineDialog(act)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
               !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }

    /**
     * Actively tests real internet reachability via raw TCP socket and HTTP 204 fallback.
     * Prevents false online status when connected to Wi-Fi/cellular without internet or data plan.
     */
    fun checkRealInternetAccess(timeoutMs: Int = 2000): Boolean {
        // 1. Primary check: Raw TCP socket probe to Google DNS (8.8.8.8:53) or Cloudflare DNS (1.1.1.1:53)
        // Does not rely on Android OS DNS resolver (which blocks/hangs when offline)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("8.8.8.8", 53), timeoutMs)
                return true
            }
        } catch (_: Exception) {
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 53), timeoutMs)
                return true
            }
        } catch (_: Exception) {
        }

        // 2. Secondary check: HTTP generate_204 probe (detects captive portals / HTTP-only proxies)
        return try {
            val url = URL("https://clients3.google.com/generate_204")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
            }
            val code = connection.responseCode
            connection.disconnect()
            code == 204 || code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun isTransientActivity(activity: Activity?): Boolean {
        if (activity == null) return true
        return activity.javaClass.simpleName == "SplashActivity"
    }

    fun showOfflineDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activeDialog?.isShowing == true) return

        val density = activity.resources.displayMetrics.density
        val fontJakarta = try {
            ResourcesCompat.getFont(activity, R.font.font_plus_jakarta_sans)
        } catch (_: Exception) {
            null
        }
        val fontNunito = try {
            ResourcesCompat.getFont(activity, R.font.font_nunito_sans)
        } catch (_: Exception) {
            null
        }

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOwnerActivity(activity)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (24f * density).toInt(),
                (26f * density).toInt(),
                (24f * density).toInt(),
                (20f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 28f * density
                setStroke((1.2f * density).toInt(), Color.parseColor("#E2E8F0"))
            }
        }

        val iconSize = (56f * density).toInt()
        val iconBadge = TextView(activity).apply {
            text = "📡"
            textSize = 28f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                bottomMargin = (14f * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EEF2FF"))
                cornerRadius = 18f * density
                setStroke((1f * density).toInt(), Color.parseColor("#C7D2FE"))
            }
        }
        root.addView(iconBadge)

        val titleView = TextView(activity).apply {
            text = "No Internet Connection"
            textSize = 19f
            setTextColor(Color.parseColor("#0F172A"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6f * density).toInt()
            }
        }
        root.addView(titleView)

        val messageView = TextView(activity).apply {
            text = "GabAI requires an internet connection to load lessons, AI explanations, and quizzes."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            typeface = fontNunito ?: Typeface.DEFAULT
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (18f * density).toInt()
            }
        }
        root.addView(messageView)

        val statusNotice = TextView(activity).apply {
            text = "Still offline. Please check your Wi-Fi or cellular data."
            textSize = 11.5f
            setTextColor(Color.parseColor("#B45309"))
            typeface = fontNunito ?: Typeface.DEFAULT
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(
                (10f * density).toInt(),
                (6f * density).toInt(),
                (10f * density).toInt(),
                (6f * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF3C7"))
                cornerRadius = 10f * density
                setStroke((1f * density).toInt(), Color.parseColor("#FDE68A"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (12f * density).toInt()
            }
        }
        root.addView(statusNotice)

        var isRetrying = false
        val btnRetry = Button(activity).apply {
            text = "Retry Connection 🔄"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4F46E5"))
                cornerRadius = 16f * density
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48f * density).toInt()
            ).apply {
                bottomMargin = (8f * density).toInt()
            }
            setOnClickListener {
                if (isRetrying) return@setOnClickListener
                isRetrying = true
                GabAIUtils.performHaptic(this, HapticFeedbackConstants.CONFIRM)
                text = "Checking Connection... 🔄"
                alpha = 0.75f
                statusNotice.visibility = View.GONE

                thread(start = true, name = "GabAI-RetryCheck") {
                    val hasValidatedCaps = isNetworkConnected(activity)
                    val actuallyReachable = if (hasValidatedCaps) {
                        checkRealInternetAccess(1500)
                    } else {
                        checkRealInternetAccess(2500)
                    }

                    mainHandler.post {
                        isRetrying = false
                        text = "Retry Connection 🔄"
                        alpha = 1.0f

                        if (actuallyReachable) {
                            isCurrentlyOnline = true
                            dismissOfflineDialog(true)
                        } else {
                            isCurrentlyOnline = false
                            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            val activeNet = cm?.activeNetwork
                            val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
                            val isAttached = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                            statusNotice.text = if (isAttached) {
                                "Connected to network, but no internet detected. Please check your Wi-Fi or data balance."
                            } else {
                                "Still offline. Please check your Wi-Fi or cellular data."
                            }
                            statusNotice.visibility = View.VISIBLE
                            mainHandler.removeCallbacksAndMessages(null)
                            mainHandler.postDelayed({
                                if (activeDialog?.isShowing == true) {
                                    statusNotice.visibility = View.GONE
                                }
                            }, 4500L)
                        }
                    }
                }
            }
        }
        root.addView(btnRetry)

        val btnSettings = Button(activity).apply {
            text = "Open Network Settings ⚙️"
            textSize = 13f
            setTextColor(Color.parseColor("#334155"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = 16f * density
                setStroke((1f * density).toInt(), Color.parseColor("#CBD5E1"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44f * density).toInt()
            ).apply {
                bottomMargin = (8f * density).toInt()
            }
            setOnClickListener {
                GabAIUtils.performHaptic(this, HapticFeedbackConstants.VIRTUAL_KEY)
                try {
                    activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                } catch (_: Exception) {
                    try {
                        activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    } catch (_: Exception) {
                    }
                }
            }
        }
        root.addView(btnSettings)

        val btnExit = Button(activity).apply {
            text = "Exit App 🚪"
            textSize = 12.5f
            setTextColor(Color.parseColor("#64748B"))
            typeface = fontJakarta ?: Typeface.DEFAULT_BOLD
            isAllCaps = false
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (38f * density).toInt()
            )
            setOnClickListener {
                GabAIUtils.performHaptic(this, HapticFeedbackConstants.VIRTUAL_KEY)
                try {
                    dialog.dismiss()
                } catch (_: Exception) {
                }
                activity.finishAffinity()
            }
        }
        root.addView(btnExit)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.4f)
        }

        try {
            dialog.show()
            activeDialog = dialog
        } catch (_: Exception) {
        }
    }

    fun dismissOfflineDialog(showToast: Boolean = false) {
        val dialog = activeDialog ?: return
        if (dialog.isShowing) {
            try {
                dialog.dismiss()
            } catch (_: Exception) {
            }
        }
        activeDialog = null
        if (showToast) {
            val activity = currentActivityRef?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                GabAIUtils.showSnackbar(activity, "Connection Restored! 🟢")
            }
        }
    }

    /**
     * Manual trigger to verify connectivity and immediately surface the offline dialog if unreachable.
     */
    fun checkConnectivity(activity: Activity? = null) {
        val targetActivity = activity ?: currentActivityRef?.get() ?: return
        if (isTransientActivity(targetActivity)) return
        if (!isNetworkConnected(targetActivity)) {
            isCurrentlyOnline = false
            showOfflineDialog(targetActivity)
        }
    }
}
