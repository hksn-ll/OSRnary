package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import com.example.gabai.R
import androidx.camera.core.AspectRatio
import android.widget.TextView

class CameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        startCamera()

        findViewById<ImageButton>(R.id.btn_capture).setOnClickListener {
            takePhoto()
        }
    }

    private fun startCamera() {
        // 1. Create the Future variable properly
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // ADD THE RATIO HERE
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            // AND ADD IT HERE TOO
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "camera_scan.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        findViewById<TextView>(R.id.tv_camera_instruction).text = "Capturing..."

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // PASS TO YOUR EXISTING SCAN RESULT SCREEN
                val intent = Intent(this@CameraActivity, ScanResultActivity::class.java)
                intent.putExtra("IMG_PATH", photoFile.absolutePath)
                startActivity(intent)
                finish()
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(baseContext, "Capture failed", Toast.LENGTH_SHORT).show()
            }
        })
    }
}