package com.example.gabai

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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.provider.Settings

class FloatingControlService : Service() {
    companion object {
        var isRunning = false
    }
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var floatingView: View
    private lateinit var serviceNotification: Notification // ADD THIS
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startMyOwnForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Save params as a class variable so we can update them later
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)

        // CHECK PERMISSION FIRST BEFORE ADDING TO SCREEN
        if (Settings.canDrawOverlays(this)) {
            windowManager.addView(floatingView, params)
            val button = floatingView.findViewById<ImageView>(R.id.widget_button)

            // ADD DRAG LISTENER
            setupDragBehavior(button)
        }
    }

    private fun captureAndScan() {
        try {
            // 1. Grab the latest image from the screen recorder
            val image = imageReader?.acquireLatestImage()

            if (image != null) {
                // 2. Convert to Bitmap
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

                // NEW CODE: Save to file and open Activity
                saveBitmapAndOpenResult(bitmap)// Important: Release the image buffer!

                // 3. Send to Google ML Kit

            } else {
                Toast.makeText(this, "Screen not ready yet, try again...", Toast.LENGTH_SHORT).show()
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
                // 4. SHOW RESULT DIALOG
                showResultDialog(visionText.text)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Scan Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResultDialog(text: String) {
        // We need to run this on the main UI thread
        Handler(Looper.getMainLooper()).post {
            val dialog = AlertDialog.Builder(applicationContext) // Use application context for system alerts
                .setTitle("Scanned Text")
                .setMessage(text.ifEmpty { "No text found on screen." })
                .setPositiveButton("Copy") { _, _ ->
                    // Copy to clipboard
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Scanned Text", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null)
                .create()

            // Essential for showing dialogs from a Service
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

                // Use the class property here:
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
                    // Standard startup logic
                    val resultCode = intent.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED)
                    val data = intent.getParcelableExtra<Intent>("DATA")
                    // ... (keep your existing media projection setup code here if you have any)
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

        // Inside startMyOwnForeground()
        val notification = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title)) // Updated
            .setContentText(getString(R.string.notification_text))   // Updated
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        serviceNotification = notification // ADD THIS LINE
// Find the 'if (Build.VERSION.SDK_INT >= ...)' block at the end of the function
        // Find this block at the end of startMyOwnForeground()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // START WITH ONLY SPECIAL_USE (This prevents the crash)
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        // Only remove if it was actually attached to the screen
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        virtualDisplay?.release()
        mediaProjection?.stop()
    }
    private fun saveBitmapAndOpenResult(bitmap: Bitmap) {
        try {
            // 1. Save bitmap to cache directory
            val filename = "screenshot_temp.png"
            val file = java.io.File(cacheDir, filename)
            val out = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            // 2. Start the ResultActivity
            val intent = Intent(this, ScanResultActivity::class.java)
            intent.putExtra("IMG_PATH", file.absolutePath)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required when starting activity from Service
            startActivity(intent)

        } catch (e: Exception) {
            e.printStackTrace()
        }
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

                        // Visual Feedback: Scale UP when touched
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        // Visual Feedback: Scale DOWN when released
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()

                        // If the user barely moved their finger, treat it as a CLICK
                        if (isClick) {
                            captureAndScan()
                        }
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Calculate new position
                        val dX = (event.rawX - initialTouchX).toInt()
                        val dY = (event.rawY - initialTouchY).toInt()

                        params.x = initialX + dX
                        params.y = initialY + dY

                        // Update the window position immediately
                        windowManager.updateViewLayout(floatingView, params)

                        // If moved more than 10 pixels, it is a DRAG, not a CLICK
                        if (Math.abs(dX) > 10 || Math.abs(dY) > 10) {
                            isClick = false
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
}