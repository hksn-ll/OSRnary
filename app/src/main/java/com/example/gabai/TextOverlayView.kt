package com.example.gabai

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.text.Text
import kotlin.math.max
import kotlin.math.min

class TextOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // 1. Setup Paints (Colors)
    private val boxPaint = Paint().apply {
        color = Color.argb(95, 108, 92, 231) // GabAI Purple (#6C5CE7) transparent
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val boxStrokePaint = Paint().apply {
        color = Color.rgb(108, 92, 231) // Solid GabAI Purple border
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val handlePaint = Paint().apply {
        color = Color.rgb(83, 65, 205) // Deep GabAI Indigo (#5341CD) for handles
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Idle State Paints (Soft Glowing Target Indicators)
    private val idleBoxPaint = Paint().apply {
        color = Color.argb(25, 129, 140, 248)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val idleStrokePaint = Paint().apply {
        color = Color.argb(70, 165, 180, 252)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // 2. Data Holders
    private data class WordBox(
        val text: String,
        val rect: RectF,
        val blockText: String = "",
        val lineText: String = ""
    )
    private val allWords = mutableListOf<WordBox>() // We flatten the ML result into a simple list of words

    // Selection State
    private var startIndex = -1
    private var endIndex = -1
    private var onTouchStarted: (() -> Unit)? = null
    private var onSelectionFinished: ((selectedText: String, surroundingSentence: String) -> Unit)? = null

    // Idle Animation State
    private var idlePulseProgress = 0.35f
    private var idleAnimator: android.animation.ValueAnimator? = null

    // Scaling
    private var scaleX = 1f
    private var scaleY = 1f

    // 3. Receive Data from Activity
    fun setTextResult(text: Text, imgWidth: Int, imgHeight: Int, viewWidth: Int, viewHeight: Int) {
        allWords.clear()

        // Calculate Scale to map image coordinates to screen coordinates
        scaleX = viewWidth.toFloat() / imgWidth.toFloat()
        scaleY = viewHeight.toFloat() / imgHeight.toFloat()
        val scale = min(scaleX, scaleY)
        scaleX = scale
        scaleY = scale

        // Calculate centering offset (because fitCenter puts black bars)
        val offsetX = (viewWidth - (imgWidth * scale)) / 2
        val offsetY = (viewHeight - (imgHeight * scale)) / 2

        // Flatten the complex ML Kit data into a simple list of words
        for (block in text.textBlocks) {
            val rawBlockText = block.text
            for (line in block.lines) {
                val rawLineText = line.text
                for (element in line.elements) {
                    element.boundingBox?.let { box ->
                        // Convert image rect to screen rect
                        val screenRect = RectF(
                            (box.left * scale) + offsetX,
                            (box.top * scale) + offsetY,
                            (box.right * scale) + offsetX,
                            (box.bottom * scale) + offsetY
                        )
                        allWords.add(WordBox(element.text, screenRect, rawBlockText, rawLineText))
                    }
                }
            }
        }
        startIdleAnimation()
        invalidate()
    }

    fun startIdleAnimation() {
        if (idleAnimator?.isRunning == true) return
        idleAnimator = android.animation.ValueAnimator.ofFloat(0.2f, 0.7f).apply {
            duration = 1400
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                idlePulseProgress = anim.animatedValue as Float
                if (startIndex == -1) {
                    invalidate()
                }
            }
            start()
        }
    }

    fun stopIdleAnimation() {
        idleAnimator?.cancel()
        idleAnimator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopIdleAnimation()
    }

    fun setOnSelectionListener(action: (selectedText: String, surroundingSentence: String) -> Unit) {
        onSelectionFinished = action
    }
    fun setOnTouchStartListener(action: () -> Unit) {
        onTouchStarted = action
    }

    // 4. Handle Touch (The Magic)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStarted?.invoke()
                // User started touching. Find which word they touched.
                val index = findWordIndex(x, y)
                if (index != -1) {
                    startIndex = index
                    endIndex = index
                    invalidate() // Redraw to show selection
                    return true
                }
                // If they didn't touch text, clear selection
                startIndex = -1
                endIndex = -1
                invalidate()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                // User is dragging. Update the end word.
                val index = findWordIndex(x, y)
                if (index != -1 && startIndex != -1) {
                    endIndex = index
                    invalidate() // Redraw the new range
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                // User let go. Send the selected text and its surrounding sentence.
                if (startIndex != -1 && endIndex != -1) {
                    val first = min(startIndex, endIndex)
                    val last = max(startIndex, endIndex)
                    val selectedText = buildSelectedString()
                    val sentence = extractSurroundingSentence(first, selectedText)
                    onSelectionFinished?.invoke(selectedText, sentence)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Helper: Extract enclosing sentence bounded by . ? !
    private fun extractSurroundingSentence(wordIndex: Int, selectedText: String): String {
        if (wordIndex !in allWords.indices) return selectedText
        val block = allWords[wordIndex].blockText
        if (block.isBlank()) return selectedText

        // Split into sentences using punctuation lookbehind
        val sentences = block.split(Regex("(?<=[.?!\\n])\\s+"))
        for (candidate in sentences) {
            val clean = candidate.trim().replace("\n", " ")
            if (clean.contains(selectedText, ignoreCase = true)) {
                // Cap word count to prevent runaway input tokens
                val words = clean.split(Regex("\\s+"))
                return if (words.size > 35) {
                    words.take(35).joinToString(" ") + "..."
                } else {
                    clean
                }
            }
        }

        val line = allWords[wordIndex].lineText.trim().replace("\n", " ")
        if (line.contains(selectedText, ignoreCase = true)) {
            return line
        }

        return selectedText
    }

    // 5. Drawing (The Visuals)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (startIndex != -1 && endIndex != -1) {
            // Ensure start is always before end
            val first = min(startIndex, endIndex)
            val last = max(startIndex, endIndex)

            // Draw highlight for every word in the range
            for (i in first..last) {
                val box = allWords[i].rect
                canvas.drawRoundRect(box, 12f, 12f, boxPaint) // Draw Filled Purple Box
                canvas.drawRoundRect(box, 12f, 12f, boxStrokePaint) // Draw Crisp Purple Border
            }

            // Draw "Teardrop" handles (Circles) at start and end
            val startBox = allWords[first].rect
            val endBox = allWords[last].rect

            // Start Handle (Left side)
            canvas.drawCircle(startBox.left, startBox.bottom + 10, 15f, handlePaint)
            // End Handle (Right side)
            canvas.drawCircle(endBox.right, endBox.bottom + 10, 15f, handlePaint)
        } else if (allWords.isNotEmpty()) {
            // Idle State: Draw subtle pulsating detected-word pills across the screen!
            val fillAlpha = (22 * (idlePulseProgress / 0.7f)).toInt().coerceIn(10, 42)
            val strokeAlpha = (70 * (idlePulseProgress / 0.7f)).toInt().coerceIn(25, 90)

            idleBoxPaint.color = Color.argb(fillAlpha, 129, 140, 248)
            idleStrokePaint.color = Color.argb(strokeAlpha, 165, 180, 252)

            for (w in allWords) {
                canvas.drawRoundRect(w.rect, 8f, 8f, idleBoxPaint)
                canvas.drawRoundRect(w.rect, 8f, 8f, idleStrokePaint)
            }
        }
    }

    // Helper: Find which word is at coordinates (x, y)
    private fun findWordIndex(x: Float, y: Float): Int {
        // We expand the touch area slightly (20px) to make it easier to grab small words
        val touchPadding = 20f

        for (i in allWords.indices) {
            val r = allWords[i].rect
            if (x >= r.left - touchPadding && x <= r.right + touchPadding &&
                y >= r.top - touchPadding && y <= r.bottom + touchPadding) {
                return i
            }
        }
        return -1
    }

    // Helper: Combine all selected words into one string
    private fun buildSelectedString(): String {
        val sb = StringBuilder()
        val first = min(startIndex, endIndex)
        val last = max(startIndex, endIndex)

        for (i in first..last) {
            sb.append(allWords[i].text)
            if (i < last) sb.append(" ") // Add space between words
        }
        return sb.toString()
    }
}