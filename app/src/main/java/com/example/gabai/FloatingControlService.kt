package com.example.gabai

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        }
    }

    private fun captureAndScan() {
        try {
            val image = imageReader?.acquireLatestImage()

            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                saveBitmapAndOpenResult(bitmap)
            } else {
                GabAIUtils.showSnackbar(this, "Screen not ready yet, try again...")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanText(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                showResultDialog(visionText.text)
            }
            .addOnFailureListener { e ->
                GabAIUtils.showSnackbar(this, "Scan Failed: ${e.message}")
            }
    }

    private fun showResultDialog(text: String) {
        Handler(Looper.getMainLooper()).post {
            val dialog = AlertDialog.Builder(applicationContext)
                .setTitle("Scanned Text")
                .setMessage(text.ifEmpty { "No text found on screen." })
                .setPositiveButton("Copy") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Scanned Text", text)
                    clipboard.setPrimaryClip(clip)
                    GabAIUtils.showSnackbar(this, "Copied!")
                }
                .setNegativeButton("Close", null)
                .create()

            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
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
            val metrics = resources.displayMetrics
            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {}, null)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            if (::floatingView.isInitialized) {
                floatingView.visibility = View.VISIBLE
            }
        }
        if (intent != null) {
            when (intent.action) {
                "ACTION_HIDE" -> {
                    floatingView.visibility = View.GONE
                }
                "ACTION_SHOW" -> {
                    floatingView.visibility = View.VISIBLE
                }
                else -> {
                    // Default start
                }
            }
        }
        return START_STICKY
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
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        try {
            if (dismissView?.isAttachedToWindow == true) {
                windowManager.removeView(dismissView)
            }
        } catch (_: Exception) {}
        dismissView = null

        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    private fun saveBitmapAndOpenResult(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val filename = "screenshot_temp.jpg"
                val file = java.io.File(cacheDir, filename)
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }

                withContext(Dispatchers.Main) {
                    val intent = Intent(this@FloatingControlService, ScanResultActivity::class.java).apply {
                        putExtra("IMG_PATH", file.absolutePath)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
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
        val dParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (56 * resources.displayMetrics.density).toInt()
        }
        dView.alpha = 0f
        dView.scaleX = 0.8f
        dView.scaleY = 0.8f
        try {
            windowManager.addView(dView, dParams)
            dismissView = dView
            dismissParams = dParams
            dView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideDismissDock() {
        val dView = dismissView ?: return
        dView.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction {
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
    }

    private fun setupDragBehavior(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
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

                        if (Math.abs(dX) > 10 || Math.abs(dY) > 10) {
                            if (isClick) {
                                isClick = false
                                showDismissDock()
                            }
                        }

                        // Proximity detection for bottom dismiss dock
                        val metrics = resources.displayMetrics
                        val screenHeight = metrics.heightPixels
                        val screenWidth = metrics.widthPixels
                        val density = metrics.density
                        val dismissZoneTop = screenHeight - (160 * density)
                        val dismissZoneLeft = (screenWidth / 2) - (110 * density)
                        val dismissZoneRight = (screenWidth / 2) + (110 * density)

                        val inDismissZone = !isClick && (event.rawY >= dismissZoneTop) &&
                                           (event.rawX in dismissZoneLeft..dismissZoneRight)

                        if (inDismissZone && !isOverDismiss) {
                            isOverDismiss = true
                            dismissView?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(120)?.start()
                            v.animate().scaleX(0.75f).scaleY(0.75f).alpha(0.6f).setDuration(120).start()
                            GabAIUtils.performHaptic(v, android.view.HapticFeedbackConstants.CLOCK_TICK)
                        } else if (!inDismissZone && isOverDismiss) {
                            isOverDismiss = false
                            dismissView?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(120)?.start()
                            v.animate().scaleX(0.92f).scaleY(0.92f).alpha(1.0f).setDuration(120).start()
                        }

                        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        hideDismissDock()

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

                        if (isClick) {
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