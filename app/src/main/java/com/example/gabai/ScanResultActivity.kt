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
    private var currentSurroundingSentence: String = ""

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

        startHudAnimations()
        startLaserSweep()

        // --- QUEST TRIGGER: SCAN ---
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", FieldValue.arrayUnion("scan"))
        }
    }

    private var laserAnimator: android.animation.ValueAnimator? = null

    private fun startLaserSweep() {
        val laser = findViewById<android.view.View>(R.id.scanner_laser_sweep) ?: return
        val container = findViewById<android.view.View>(R.id.scanner_laser_container) ?: return

        container.post {
            val totalHeight = container.height.toFloat()
            if (totalHeight <= 0f) return@post

            laser.visibility = android.view.View.VISIBLE
            laser.alpha = 0.9f

            laserAnimator?.cancel()
            val startY = -60f * resources.displayMetrics.density
            val endY = totalHeight

            laserAnimator = android.animation.ValueAnimator.ofFloat(startY, endY).apply {
                duration = 2600
                repeatMode = android.animation.ValueAnimator.RESTART
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    laser.translationY = anim.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun stopOrFadeLaserSweep() {
        val laser = findViewById<android.view.View>(R.id.scanner_laser_sweep) ?: return
        laser.animate().alpha(0f).setDuration(240).withEndAction {
            laserAnimator?.cancel()
            laser.visibility = android.view.View.GONE
        }.start()
    }

    private fun startHudAnimations() {
        val hudCard = findViewById<android.view.View>(R.id.ll_instruction_hud)
        val bounceArrow = findViewById<android.view.View>(R.id.iv_hud_bounce_arrow)

        hudCard?.let { view ->
            val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.03f).apply {
                duration = 1100
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
            }
            val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.03f).apply {
                duration = 1100
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
            }
            android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                start()
            }
        }

        bounceArrow?.let { view ->
            android.animation.ObjectAnimator.ofFloat(view, "translationY", 0f, 10f).apply {
                duration = 750
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun hideInstructionHud() {
        val hud = findViewById<android.view.View>(R.id.instruction_overlay) ?: return
        if (hud.visibility == android.view.View.VISIBLE) {
            hud.animate()
                .alpha(0f)
                .translationY(-40f)
                .setDuration(220)
                .withEndAction {
                    hud.visibility = android.view.View.GONE
                }
                .start()
        }
    }

    private fun runScanner(bitmap: Bitmap, overlay: TextOverlayView, imageView: ImageView) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                imageView.post {
                    overlay.setTextResult(visionText, bitmap.width, bitmap.height, imageView.width, imageView.height)
                }

                // Hide instruction HUD and stop laser sweep as soon as user touches the screen
                overlay.setOnTouchStartListener {
                    hideInstructionHud()
                    stopOrFadeLaserSweep()
                }

                // Handle word/phrase selection
                overlay.setOnSelectionListener { selectedText, surroundingSentence ->
                    stopOrFadeLaserSweep()
                    updateBottomCard(selectedText, surroundingSentence)
                }
            }
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.result_card)
        makeDraggable(card)
    }

    private fun updateBottomCard(text: String, sentence: String) {
        hideInstructionHud()
        stopOrFadeLaserSweep()

        currentSelectedText = text
        currentSurroundingSentence = sentence
        val card = findViewById<androidx.cardview.widget.CardView>(R.id.result_card)
        val title = findViewById<android.widget.TextView>(R.id.card_title)
        val body = findViewById<android.widget.TextView>(R.id.card_body)
        val badge = findViewById<android.widget.TextView>(R.id.tv_selection_badge)
        val btnAnalyze = findViewById<android.view.View>(R.id.btn_analyze_action)

        // Classify selection mode
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1) {
            badge?.text = "PHRASE SELECTED (${words.size} WORDS)"
        } else {
            badge?.text = "TARGET WORD"
        }

        title.text = text
        body.text = if (sentence.isNotBlank() && sentence != text) {
            "\"$sentence\""
        } else {
            "Tap below for AI Explanation..."
        }

        btnAnalyze?.setOnClickListener {
            openOverviewScreen()
        }

        // Animated reveal for result card
        if (card.visibility != android.view.View.VISIBLE) {
            card.visibility = android.view.View.VISIBLE
            card.alpha = 0f
            card.translationY = 160f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.7f))
                .setDuration(280)
                .start()
        } else {
            card.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(80)
                .withEndAction {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                }
                .start()
        }
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
            intent.putExtra("SURROUNDING_SENTENCE", currentSurroundingSentence)
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

    override fun onDestroy() {
        super.onDestroy()
        laserAnimator?.cancel()
        laserAnimator = null
    }
}