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
    // 1. Setup Paints (Colors)
    private val boxPaint = Paint().apply {
        color = Color.argb(80, 0, 150, 255) // Light Blue highlight (Transparent)
        style = Paint.Style.FILL
        // REMOVED: cornerRadius = 10f (This caused the error)
    }

    private val handlePaint = Paint().apply {
        color = Color.rgb(0, 150, 255) // Solid Blue for handles
        style = Paint.Style.FILL
    }

    // 2. Data Holders
    private data class WordBox(val text: String, val rect: RectF)
    private val allWords = mutableListOf<WordBox>() // We flatten the ML result into a simple list of words

    // Selection State
    private var startIndex = -1
    private var endIndex = -1
    private var onTouchStarted: (() -> Unit)? = null // Add this line
    private var onSelectionFinished: ((String) -> Unit)? = null

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
            for (line in block.lines) {
                for (element in line.elements) {
                    element.boundingBox?.let { box ->
                        // Convert image rect to screen rect
                        val screenRect = RectF(
                            (box.left * scale) + offsetX,
                            (box.top * scale) + offsetY,
                            (box.right * scale) + offsetX,
                            (box.bottom * scale) + offsetY
                        )
                        allWords.add(WordBox(element.text, screenRect))
                    }
                }
            }
        }
        invalidate()
    }

    fun setOnSelectionListener(action: (String) -> Unit) {
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
                // User let go. Send the selected text.
                if (startIndex != -1 && endIndex != -1) {
                    val selectedText = buildSelectedString()
                    onSelectionFinished?.invoke(selectedText)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
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
                canvas.drawRoundRect(box, 12f, 12f, boxPaint) // Draw Blue Box
            }

            // Draw "Teardrop" handles (Circles) at start and end
            val startBox = allWords[first].rect
            val endBox = allWords[last].rect

            // Start Handle (Left side)
            canvas.drawCircle(startBox.left, startBox.bottom + 10, 15f, handlePaint)
            // End Handle (Right side)
            canvas.drawCircle(endBox.right, endBox.bottom + 10, 15f, handlePaint)
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