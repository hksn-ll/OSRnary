package com.example.gabai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File

class CameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var isTorchOn: Boolean = false
    private var laserAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        startCamera()
        startLaserSweep()
        startShutterRingPulse()

        findViewById<ImageButton>(R.id.btn_close_camera).setOnClickListener {
            finish()
        }

        val btnFlash = findViewById<ImageButton>(R.id.btn_flash_toggle)
        btnFlash.setOnClickListener {
            toggleTorch()
        }

        val btnCapture = findViewById<ImageButton>(R.id.btn_capture)
        GabAIUtils.addSpringPressEffect(btnCapture) {
            takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Zero delay capture mode (instant shutter response)
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Smooth crossfade: dismiss loading screen as soon as preview is live
            viewFinder.previewStreamState.observe(this) { state ->
                if (state == PreviewView.StreamState.STREAMING) {
                    val loading = findViewById<View>(R.id.camera_loading_overlay)
                    if (loading?.visibility == View.VISIBLE) {
                        loading.animate()
                            .alpha(0f)
                            .setDuration(260)
                            .withEndAction { loading.visibility = View.GONE }
                            .start()
                    }
                }
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                // Check flash hardware availability
                val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                val btnFlash = findViewById<ImageButton>(R.id.btn_flash_toggle)
                if (!hasFlash) {
                    btnFlash.alpha = 0.4f
                }
            } catch (e: Exception) {
                GabAIUtils.showSnackbar(this, "Camera failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val cam = camera
        if (cam == null) {
            GabAIUtils.showSnackbar(this, "Camera not ready yet")
            return
        }

        if (cam.cameraInfo.hasFlashUnit()) {
            isTorchOn = !isTorchOn
            cam.cameraControl.enableTorch(isTorchOn)

            val btnFlash = findViewById<ImageButton>(R.id.btn_flash_toggle)
            btnFlash.setImageResource(if (isTorchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
            btnFlash.setColorFilter(if (isTorchOn) Color.parseColor("#FBBF24") else Color.WHITE)

            try {
                btnFlash.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Exception) {}
        } else {
            GabAIUtils.showSnackbar(this, "Flashlight not supported on this device")
        }
    }

    private fun startLaserSweep() {
        val laser = findViewById<View>(R.id.camera_laser_sweep) ?: return
        val container = findViewById<View>(R.id.viewfinder_reticle) ?: return

        container.post {
            val totalHeight = container.height.toFloat()
            if (totalHeight <= 0f) return@post

            laserAnimator?.cancel()
            val startY = 0f
            val endY = totalHeight - laser.height.coerceAtLeast(40)

            laserAnimator = ValueAnimator.ofFloat(startY, endY).apply {
                duration = 2400
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    laser.translationY = anim.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun startShutterRingPulse() {
        val ring = findViewById<View>(R.id.shutter_ring) ?: return
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 0.95f, 1.08f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 0.95f, 1.08f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun triggerShutterFeedback() {
        // 1. Fullscreen White Camera Flash
        val flash = findViewById<View>(R.id.shutter_flash_overlay)
        flash?.visibility = View.VISIBLE
        flash?.alpha = 0.85f
        flash?.animate()
            ?.alpha(0f)
            ?.setDuration(160)
            ?.withEndAction { flash.visibility = View.GONE }
            ?.start()

        // 2. Tactile Haptic Snap
        try {
            findViewById<View>(R.id.btn_capture)?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Exception) {}

        // 3. Status text feedback
        findViewById<TextView>(R.id.tv_camera_instruction)?.text = "Scanning text..."
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "camera_scan.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        triggerShutterFeedback()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val intent = Intent(this@CameraActivity, ScanResultActivity::class.java).apply {
                        putExtra("IMG_PATH", photoFile.absolutePath)
                    }
                    startActivity(intent)
                    finish()
                }

                override fun onError(exc: ImageCaptureException) {
                    GabAIUtils.showSnackbar(baseContext, "Capture failed")
                    findViewById<TextView>(R.id.tv_camera_instruction)?.text = "Align text within frame"
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        laserAnimator?.cancel()
        laserAnimator = null
        if (isTorchOn) {
            camera?.cameraControl?.enableTorch(false)
        }
    }
}