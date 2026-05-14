package com.example.gabai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.media.ExifInterface
import android.graphics.Matrix
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ScanResultActivity : AppCompatActivity() {

    private var currentSelectedText: String = "" // Add this line

    override fun onResume() {
        super.onResume()
        // Always try to hide it when this screen is open
        val intent = android.content.Intent(this, FloatingControlService::class.java)
        intent.action = "ACTION_HIDE"
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        // ONLY show it if the user actually enabled it in the HomeFragment
        val isEnabled = getSharedPreferences("GabAI_Prefs", MODE_PRIVATE).getBoolean("bubble_enabled", false)
        if (isEnabled) {
            val intent = android.content.Intent(this, FloatingControlService::class.java)
            intent.action = "ACTION_SHOW"
            startService(intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        setContentView(R.layout.activity_scan_result)


        val imageView = findViewById<ImageView>(R.id.screenshot_view)
        val overlayView = findViewById<TextOverlayView>(R.id.text_overlay)
        val closeBtn = findViewById<ImageButton>(R.id.close_button)

        // 1. Get the image path passed from the Service
        // 1. Get the image path passed from the Service
        val imagePath = intent.getStringExtra("IMG_PATH")
        if (imagePath != null) {
            // LOAD THE IMAGE
            val originalBitmap = BitmapFactory.decodeFile(imagePath)

            // FIX THE ROTATION BEFORE SHOWING IT
            val correctedBitmap = rotateImageIfRequired(originalBitmap, imagePath)

            imageView.setImageBitmap(correctedBitmap)

            // 2. Run the scanner on the CORRECTED image
            runScanner(correctedBitmap, overlayView, imageView)
        }

        findViewById<ImageButton>(R.id.close_button).setOnClickListener {
            finishAndRemoveTask()
        }

    }

    private fun runScanner(bitmap: Bitmap, overlay: TextOverlayView, imageView: ImageView) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->

                // 🟢 FIX: Trigger quest ONLY if actual text was captured!
                if (visionText.text.isNotEmpty()) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .update("quests_completed", com.google.firebase.firestore.FieldValue.arrayUnion("scan"))
                    }
                }

                // 3. Pass the results to the Overlay to draw boxes
                // We wait for the ImageView to be laid out to get exact size
                imageView.post {
                    overlay.setTextResult(visionText, bitmap.width, bitmap.height, imageView.width, imageView.height)
// In ScanResultActivity.kt, inside imageView.post { ... }

// NEW CODE: Hide overlay when user touches text


                }



                // 4. Handle clicks
                overlay.setOnSelectionListener { selectedText ->
                    // Instead of showBottomSheet, we call our new non-blocking function
                    updateBottomCard(selectedText)

                }
            }
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.result_card)
        makeDraggable(card)

    }

    private fun updateBottomCard(text: String) {
        // 1. NUCLEAR OPTION: Find the text and hide it unconditionally
        val instructionText = findViewById<android.widget.TextView>(R.id.instruction_text)

        // Only run if we actually found the view
        if (instructionText != null) {
            instructionText.clearAnimation() // Stop any fighting animations
            instructionText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    instructionText.visibility = android.view.View.GONE
                }
                .start()
        }

        // 2. Standard Card Update Logic
        currentSelectedText = text
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.result_card)
        val title = findViewById<android.widget.TextView>(R.id.card_title)
        val body = findViewById<android.widget.TextView>(R.id.card_body)

        // Show the result card
        card.visibility = android.view.View.VISIBLE
        title.text = text
        body.text = "Tap for AI Explanation..."
    }
    // This function makes any view follow your finger
    private fun makeDraggable(view: android.view.View) {
        view.setOnTouchListener(object : android.view.View.OnTouchListener {
            var dY = 0f
            var startY = 0f
            var isClick = false

            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        dY = v.y - event.rawY
                        startY = event.rawY
                        isClick = true // Assume it's a click until it moves
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        // If moved more than 10 pixels, it is a DRAG, not a click
                        if (Math.abs(event.rawY - startY) > 10) {
                            isClick = false
                        }

                        v.animate()
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        // If it was a click, open the new screen!
                        if (isClick) {
                            openOverviewScreen()
                        }
                    }
                    else -> return false
                }
                return true
            }
        })
    }

    private fun openOverviewScreen() {
        if (currentSelectedText.isNotEmpty()) {
            val intent = android.content.Intent(this, OverviewActivity::class.java)
            intent.putExtra("SELECTED_TEXT", currentSelectedText)
            startActivity(intent)
        }
    }
    private fun rotateImageIfRequired(img: Bitmap, path: String): Bitmap {
        val ei = ExifInterface(path)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle() // Clean up memory from the old sideways image
        return rotatedImg
    }
}