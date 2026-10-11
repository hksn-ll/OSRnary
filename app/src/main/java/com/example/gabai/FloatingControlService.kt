package com.example.gabai

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingControlService : Service() {
    companion object {
        var isRunning = false
    }
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var floatingView: View
    private lateinit var serviceNotification: Notification
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isTemporarilyHidden = false
    private var isCapturing = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Keep projection alive if possible; refresh on wake
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    updateBubbleVisibility()
                    syncVirtualDisplayMetrics()
                }
            }
        }
    }

    // Bottom dismiss dock
    private var dismissView: View? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var isOverDismiss = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("bubble_enabled", true).apply()
        startMyOwnForeground()
        sendBroadcast(Intent("com.example.gabai.ACTION_BUBBLE_STATE_CHANGED").apply {
            setPackage(packageName)
            putExtra("is_enabled", true)
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (12 * resources.displayMetrics.density).toInt()
        params.y = 120

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)

        if (Settings.canDrawOverlays(this)) {
            windowManager.addView(floatingView, params)
            val button = floatingView.findViewById<ImageView>(R.id.widget_button)
            setupDragBehavior(button)

            button.scaleX = 0f
            button.scaleY = 0f
            button.alpha = 0f
            button.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                .start()

            updateBubbleVisibility()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBubbleVisibility()
        syncVirtualDisplayMetrics()
        clampBubbleToScreen()
    }

    private fun getRealDisplayMetrics(): Triple<Int, Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), resources.configuration.densityDpi)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    private fun syncVirtualDisplayMetrics(): Boolean {
        val proj = mediaProjection ?: return false
        val (realWidth, realHeight, densityDpi) = getRealDisplayMetrics()
        val currentReader = imageReader
        if (currentReader != null && currentReader.width == realWidth && currentReader.height == realHeight) {
            return false
        }
        if (currentReader != null) {
            try {
                currentReader.close()
            } catch (_: Exception) {}
        }
        val newReader = ImageReader.newInstance(realWidth, realHeight, PixelFormat.RGBA_8888, 2)
        imageReader = newReader
        val vDisplay = virtualDisplay
        if (vDisplay == null) {
            virtualDisplay = proj.createVirtualDisplay(
                "ScreenCapture",
                realWidth, realHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                newReader.surface, null, null
            )
        } else {
            vDisplay.resize(realWidth, realHeight, densityDpi)
            vDisplay.setSurface(newReader.surface)
        }
        return true
    }

    private fun clampBubbleToScreen() {
        if (!::floatingView.isInitialized || !floatingView.isAttachedToWindow) return
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val bubbleSize = (58 * metrics.density).toInt()
        params.x = params.x.coerceIn(0, (screenWidth - bubbleSize).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenHeight - bubbleSize).coerceAtLeast(0))
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
    }

    private fun updateBubbleVisibility() {
        if (!::floatingView.isInitialized) return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val isEnabled = getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE).getBoolean("bubble_enabled", false)

        if (isLandscape || isTemporarilyHidden || !isEnabled) {
            floatingView.visibility = View.GONE
        } else {
            floatingView.visibility = View.VISIBLE
        }
    }

    private fun captureAndScan() {
        if (isCapturing) return
        isCapturing = true

        val accessibilityService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            GabAIAccessibilityService.getInstance()
        } else {
            null
        }

        if (accessibilityService != null) {
            captureViaAccessibility(accessibilityService)
            return
        }

        if (mediaProjection == null) {
            isCapturing = false
            Toast.makeText(
                applicationContext,
                "Screen capture session expired. Re-open GabAI to reconnect.",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            return
        }

        captureViaMediaProjection()
    }

    private fun captureViaAccessibility(service: GabAIAccessibilityService) {
        if (::floatingView.isInitialized) {
            floatingView.visibility = View.INVISIBLE
        }

        val handler = Handler(Looper.getMainLooper())
        var isHandled = false

        val timeoutRunnable = Runnable {
            if (!isHandled) {
                isHandled = true
                updateBubbleVisibility()
                isCapturing = false
                Toast.makeText(
                    applicationContext,
                    "Screen capture timed out. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        handler.postDelayed(timeoutRunnable, 2500L)

        handler.postDelayed({
            if (isHandled) return@postDelayed
            service.captureScreen { bitmap, errorCode ->
                handler.post {
                    if (!isHandled) {
                        isHandled = true
                        handler.removeCallbacks(timeoutRunnable)
                        updateBubbleVisibility()
                        isCapturing = false
                        if (bitmap != null) {
                            saveBitmapAndOpenResult(bitmap)
                        } else if (mediaProjection != null) {
                            Log.w("GabAI", "Accessibility capture failed, falling back to MediaProjection.")
                            captureViaMediaProjection()
                        } else {
                            val msg = if (errorCode == 5) {
                                "Cannot capture secure or protected content."
                            } else {
                                "Failed to capture screen. Please try again."
                            }
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }, 60L)
    }

    private fun captureViaMediaProjection() {
        syncVirtualDisplayMetrics()
        val reader = imageReader
        if (reader == null) {
            isCapturing = false
            Toast.makeText(applicationContext, "Screen capture not ready, please try again.", Toast.LENGTH_SHORT).show()
            return
        }

        var immediateImg: Image? = null
        try {
            immediateImg = reader.acquireLatestImage()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (immediateImg != null) {
            processAndOpenImage(immediateImg)
            isCapturing = false
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var frameHandled = false
        val timeoutRunnable = Runnable {
            if (!frameHandled) {
                frameHandled = true
                try {
                    reader.setOnImageAvailableListener(null, null)
                } catch (_: Exception) {}
                isCapturing = false
                Toast.makeText(applicationContext, "Screen not ready yet, try again...", Toast.LENGTH_SHORT).show()
            }
        }
        handler.postDelayed(timeoutRunnable, 450L)

        try {
            reader.setOnImageAvailableListener({ r ->
                if (!frameHandled) {
                    frameHandled = true
                    handler.removeCallbacks(timeoutRunnable)
                    var delayedImg: Image? = null
                    try {
                        delayedImg = r.acquireLatestImage() ?: r.acquireNextImage()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        r.setOnImageAvailableListener(null, null)
                    } catch (_: Exception) {}

                    if (delayedImg == null) {
                        Toast.makeText(applicationContext, "Screen not ready yet, try again...", Toast.LENGTH_SHORT).show()
                    } else {
                        processAndOpenImage(delayedImg)
                    }
                    isCapturing = false
                }
            }, handler)
        } catch (e: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            isCapturing = false
            e.printStackTrace()
            Toast.makeText(applicationContext, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processAndOpenImage(image: Image) {
        var cleanBitmap: Bitmap? = null
        try {
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val rawBitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)
                val screenWidth = image.width
                val screenHeight = image.height

                // Crop off any hardware row padding so text aspect ratio is never squished/skewed
                val cropped = if (rowPadding == 0) {
                    rawBitmap
                } else {
                    val c = Bitmap.createBitmap(rawBitmap, 0, 0, screenWidth, screenHeight)
                    rawBitmap.recycle()
                    c
                }
                cleanBitmap = cropped
                try {
                    image.close()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (cleanBitmap == null) {
                Toast.makeText(applicationContext, "Failed to capture screen image.", Toast.LENGTH_SHORT).show()
            } else {
                saveBitmapAndOpenResult(cleanBitmap)
            }
        } finally {
            try {
                image.close()
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("DATA")

        if (resultCode == Activity.RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(2, serviceNotification, serviceTypes)
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    mediaProjection = null
                    try {
                        virtualDisplay?.release()
                    } catch (_: Exception) {}
                    virtualDisplay = null
                }
            }, null)

            syncVirtualDisplayMetrics()
            updateBubbleVisibility()
        }
        if (intent != null) {
            when (intent.action) {
                "ACTION_HIDE" -> {
                    isTemporarilyHidden = true
                    updateBubbleVisibility()
                }
                "ACTION_SHOW" -> {
                    isTemporarilyHidden = false
                    updateBubbleVisibility()
                }
                else -> {
                    updateBubbleVisibility()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startMyOwnForeground() {
        val channelId = "com.example.osrnary.floating"
        val channelName = "Floating Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        serviceNotification = notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("bubble_enabled", false).apply()
        sendBroadcast(Intent("com.example.gabai.ACTION_BUBBLE_STATE_CHANGED").apply {
            setPackage(packageName)
            putExtra("is_enabled", false)
        })
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        try {
            if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                windowManager.removeView(floatingView)
            }
        } catch (_: Exception) {}
        try {
            if (dismissView?.isAttachedToWindow == true) {
                windowManager.removeView(dismissView)
            }
        } catch (_: Exception) {}
        dismissView = null

        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
    }

    private fun saveBitmapAndOpenResult(bitmap: Bitmap) {
        // Pass uncompressed bitmap in-memory for instant launch & 100% pixel-perfect OCR clarity
        ScanImageHolder.currentBitmap = bitmap

        val intent = Intent(this@FloatingControlService, ScanResultActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("SCAN_SOURCE", ApprenticeQuestHelper.QUEST_BUBBLE)
        }
        startActivity(intent)

        // Asynchronously save lossless PNG cache file as fallback
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = java.io.File(cacheDir, "screenshot_temp.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showDismissDock() {
        if (dismissView != null) return
        val inflater = LayoutInflater.from(this)
        val dView = inflater.inflate(R.layout.floating_dismiss_dock, null)
        val density = resources.displayMetrics.density
        val dParams = WindowManager.LayoutParams(
            (120 * density).toInt(),
            (120 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (36 * density).toInt()
        }
        val container = dView.findViewById<View>(R.id.dismiss_dock_container)
        container?.alpha = 0f
        container?.scaleX = 0.8f
        container?.scaleY = 0.8f
        try {
            windowManager.addView(dView, dParams)
            dismissView = dView
            dismissParams = dParams
            container?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideDismissDock() {
        val dView = dismissView ?: return
        val container = dView.findViewById<View>(R.id.dismiss_dock_container)
        if (container != null) {
            container.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction {
                try {
                    if (dView.isAttachedToWindow) {
                        windowManager.removeView(dView)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                dismissView = null
                dismissParams = null
            }.start()
        } else {
            try {
                if (dView.isAttachedToWindow) {
                    windowManager.removeView(dView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            dismissView = null
            dismissParams = null
        }
    }

    private fun setupDragBehavior(view: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchDownTime = 0L
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchDownTime = System.currentTimeMillis()
                        isClick = true
                        isOverDismiss = false

                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dX = (event.rawX - initialTouchX).toInt()
                        val dY = (event.rawY - initialTouchY).toInt()

                        params.x = initialX + dX
                        params.y = initialY + dY

                        val distFromOriginSq = (event.rawX - initialTouchX) * (event.rawX - initialTouchX) +
                                (event.rawY - initialTouchY) * (event.rawY - initialTouchY)
                        if (distFromOriginSq > touchSlop * touchSlop) {
                            if (isClick) {
                                isClick = false
                                showDismissDock()
                            }
                        }

                        // Circular proximity detection for bottom dismiss dock
                        val (screenWidth, screenHeight, _) = getRealDisplayMetrics()
                        val density = resources.displayMetrics.density
                        val dockCenterX = screenWidth / 2f
                        val dockCenterY = screenHeight - (96f * density)
                        val distToDock = Math.hypot((event.rawX - dockCenterX).toDouble(), (event.rawY - dockCenterY).toDouble())

                        val inDismissZone = !isClick && distToDock <= (80f * density)

                        if (inDismissZone && !isOverDismiss) {
                            isOverDismiss = true
                            val container = dismissView?.findViewById<View>(R.id.dismiss_dock_container)
                            container?.setBackgroundResource(R.drawable.bg_dismiss_dock_active)
                            dismissView?.findViewById<ImageView>(R.id.iv_dismiss_icon)?.setColorFilter(Color.WHITE)
                            container?.animate()?.scaleX(1.22f)?.scaleY(1.22f)?.setDuration(120)?.start()
                            v.animate().scaleX(0.4f).scaleY(0.4f).alpha(0.25f).setDuration(120).start()
                            GabAIUtils.performHaptic(v, android.view.HapticFeedbackConstants.CLOCK_TICK)
                        } else if (!inDismissZone && isOverDismiss) {
                            isOverDismiss = false
                            val container = dismissView?.findViewById<View>(R.id.dismiss_dock_container)
                            container?.setBackgroundResource(R.drawable.bg_dismiss_dock_default)
                            dismissView?.findViewById<ImageView>(R.id.iv_dismiss_icon)?.setColorFilter(Color.parseColor("#EF4444"))
                            container?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(120)?.start()
                            v.animate().scaleX(0.92f).scaleY(0.92f).alpha(1.0f).setDuration(120).start()
                        }

                        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        hideDismissDock()
                        val elapsed = System.currentTimeMillis() - touchDownTime
                        val distFromOriginSq = (event.rawX - initialTouchX) * (event.rawX - initialTouchX) +
                                (event.rawY - initialTouchY) * (event.rawY - initialTouchY)
                        val consideredClick = isClick || (elapsed < 400 && distFromOriginSq < touchSlop * touchSlop)

                        if (isOverDismiss) {
                            GabAIUtils.performHaptic(v, android.view.HapticFeedbackConstants.CONFIRM)
                            isRunning = false
                            getSharedPreferences("GabAI_Prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("bubble_enabled", false).apply()
                            sendBroadcast(Intent("com.example.gabai.ACTION_BUBBLE_STATE_CHANGED").apply {
                                setPackage(packageName)
                                putExtra("is_enabled", false)
                            })
                            v.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(160).withEndAction {
                                stopSelf()
                            }.start()
                            return true
                        }

                        v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120).start()

                        if (consideredClick) {
                            v.performClick()
                            v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(90).withEndAction {
                                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90).withEndAction {
                                    captureAndScan()
                                }.start()
                            }.start()
                        } else {
                            snapToNearestEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapToNearestEdge() {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val margin = (12 * metrics.density).toInt()
        val bubbleWidth = if (floatingView.width > 0) floatingView.width else (58 * metrics.density).toInt()
        val bubbleCenterX = params.x + bubbleWidth / 2

        val targetX = if (bubbleCenterX < screenWidth / 2) {
            margin
        } else {
            screenWidth - bubbleWidth - margin
        }

        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 200
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { anim ->
            if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                params.x = anim.animatedValue as Int
                windowManager.updateViewLayout(floatingView, params)
            }
        }
        animator.start()
    }
}