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

    // Selection Drag Mode
    private enum class DragMode { NONE, SELECTION, DRAG_START_HANDLE, DRAG_END_HANDLE }
    private var currentDragMode = DragMode.NONE

    // Idle Animation State
    private var idlePulseProgress = 0.35f
    private var idleAnimator: android.animation.ValueAnimator? = null

    // Scaling
    private var scaleX = 1f
    private var scaleY = 1f

    private val density: Float
        get() = resources.displayMetrics.density

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

        // Flatten the complex ML Kit data into words
        for (block in text.textBlocks) {
            val rawBlockText = block.text
            for (line in block.lines) {
                val rawLineText = line.text
                for (element in line.elements) {
                    element.boundingBox?.let { box ->
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

        // Sort into geometric natural reading order (top-to-bottom, left-to-right)
        allWords.sortWith { a, b ->
            val yDiff = a.rect.centerY() - b.rect.centerY()
            val lineHeight = min(a.rect.height(), b.rect.height())
            if (kotlin.math.abs(yDiff) < lineHeight * 0.5f) {
                a.rect.left.compareTo(b.rect.left)
            } else {
                a.rect.centerY().compareTo(b.rect.centerY())
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

    private fun performHapticTick() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE)
            } else {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        } catch (_: Exception) {}
    }

    // 4. Handle Touch (Ultra-Snappy & Draggable Handles)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchStarted?.invoke()

                // 1. Check if user grabbed the existing start or end handles
                if (startIndex != -1 && endIndex != -1 && allWords.isNotEmpty()) {
                    val first = min(startIndex, endIndex)
                    val last = max(startIndex, endIndex)
                    val startBox = allWords[first].rect
                    val endBox = allWords[last].rect
                    val handleRadius = 36f * density

                    val startHandleX = startBox.left
                    val startHandleY = startBox.bottom + (10f * density)
                    if (kotlin.math.hypot(x - startHandleX, y - startHandleY) <= handleRadius) {
                        currentDragMode = DragMode.DRAG_START_HANDLE
                        performHapticTick()
                        return true
                    }

                    val endHandleX = endBox.right
                    val endHandleY = endBox.bottom + (10f * density)
                    if (kotlin.math.hypot(x - endHandleX, y - endHandleY) <= handleRadius) {
                        currentDragMode = DragMode.DRAG_END_HANDLE
                        performHapticTick()
                        return true
                    }
                }

                // 2. Direct hit or nearest word snapping (no missed touches in word gaps)
                val index = findNearestWordIndex(x, y, maxDistance = 44f * density)
                if (index != -1) {
                    startIndex = index
                    endIndex = index
                    currentDragMode = DragMode.SELECTION
                    performHapticTick()
                    invalidate()
                    return true
                }

                // Tapped far outside any text -> clear selection
                startIndex = -1
                endIndex = -1
                currentDragMode = DragMode.NONE
                invalidate()
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentDragMode == DragMode.NONE || allWords.isEmpty()) return true

                val nearest = findNearestWordIndex(x, y, maxDistance = 90f * density)
                if (nearest != -1) {
                    when (currentDragMode) {
                        DragMode.DRAG_START_HANDLE -> {
                            if (nearest != startIndex) {
                                startIndex = nearest
                                performHapticTick()
                                invalidate()
                            }
                        }
                        DragMode.DRAG_END_HANDLE -> {
                            if (nearest != endIndex) {
                                endIndex = nearest
                                performHapticTick()
                                invalidate()
                            }
                        }
                        DragMode.SELECTION -> {
                            if (nearest != endIndex) {
                                endIndex = nearest
                                performHapticTick()
                                invalidate()
                            }
                        }
                        DragMode.NONE -> {}
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (startIndex != -1 && endIndex != -1 && allWords.isNotEmpty()) {
                    val first = min(startIndex, endIndex)
                    val last = max(startIndex, endIndex)
                    val selectedText = buildSelectedString()
                    val sentence = extractSurroundingSentence(first, selectedText)
                    onSelectionFinished?.invoke(selectedText, sentence)
                }
                currentDragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Helper: Extract enclosing complete sentence bounded by . ? ! without arbitrary truncation
    private fun extractSurroundingSentence(wordIndex: Int, selectedText: String): String {
        if (wordIndex !in allWords.indices) return selectedText
        val block = allWords[wordIndex].blockText
        if (block.isBlank()) return selectedText

        // Split into candidate sentences using punctuation lookbehind
        val sentences = block.split(Regex("(?<=[.?!\\n])\\s+"))
        for (candidate in sentences) {
            val clean = candidate.trim().replace("\n", " ")
            if (clean.contains(selectedText, ignoreCase = true)) {
                return clean // Return complete, un-severed sentence
            }
        }

        val line = allWords[wordIndex].lineText.trim().replace("\n", " ")
        if (line.contains(selectedText, ignoreCase = true)) {
            return line
        }

        return block.trim().replace("\n", " ")
    }

    // 5. Drawing (The Visuals)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (startIndex != -1 && endIndex != -1 && allWords.isNotEmpty()) {
            val first = min(startIndex, endIndex)
            val last = max(startIndex, endIndex)

            // Draw highlight for every word in range
            for (i in first..last) {
                val box = allWords[i].rect
                canvas.drawRoundRect(box, 10f * density, 10f * density, boxPaint)
                canvas.drawRoundRect(box, 10f * density, 10f * density, boxStrokePaint)
            }

            // Draw interactive grab handles at start and end
            val startBox = allWords[first].rect
            val endBox = allWords[last].rect
            val handleOffsetY = 8f * density
            val handleRadius = 11f * density

            // Start Handle (Left side)
            canvas.drawCircle(startBox.left, startBox.bottom + handleOffsetY, handleRadius, handlePaint)
            // End Handle (Right side)
            canvas.drawCircle(endBox.right, endBox.bottom + handleOffsetY, handleRadius, handlePaint)

        } else if (allWords.isNotEmpty()) {
            // Idle State: Subtle pulsating detected-word pills
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

    // Helper: Find nearest word with line-aware projection
    private fun findNearestWordIndex(x: Float, y: Float, maxDistance: Float = 60f * density): Int {
        if (allWords.isEmpty()) return -1

        val touchPadding = 24f * density

        // 1. Direct hit test with generous touch padding
        for (i in allWords.indices) {
            val r = allWords[i].rect
            if (x >= r.left - touchPadding && x <= r.right + touchPadding &&
                y >= r.top - touchPadding && y <= r.bottom + touchPadding) {
                return i
            }
        }

        // 2. Nearest word projection (weighted to prefer words on the same horizontal line)
        var bestIndex = -1
        var minScore = Float.MAX_VALUE

        for (i in allWords.indices) {
            val r = allWords[i].rect

            val dx = when {
                x < r.left -> r.left - x
                x > r.right -> x - r.right
                else -> 0f
            }

            val dy = when {
                y < r.top -> r.top - y
                y > r.bottom -> y - r.bottom
                else -> 0f
            }

            // Heavy penalty for vertical deviation so dragging stays locked to the line
            val score = kotlin.math.sqrt((dx * dx) + (dy * dy * 3.5f))
            if (score < minScore) {
                minScore = score
                bestIndex = i
            }
        }

        return if (minScore <= maxDistance) bestIndex else -1
    }

    // Helper: Combine all selected words into one string
    private fun buildSelectedString(): String {
        val sb = StringBuilder()
        val first = min(startIndex, endIndex)
        val last = max(startIndex, endIndex)

        for (i in first..last) {
            sb.append(allWords[i].text)
            if (i < last) sb.append(" ")
        }
        return sb.toString()
    }
}