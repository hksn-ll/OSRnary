package com.example.gabai

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.sin

/**
 * High-performance hardware-accelerated animated logo view for the GabAI splash screen.
 * Uses a 600x600 virtual coordinate space with 50px outer margins to guarantee zero circle cropping.
 * Symmetrical diagonal glass orbs (160, 440) and (440, 160) bloom harmoniously right after the book unfolds.
 */
class GabAiAnimatedLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var squircleProgress: Float = 0f
        set(value) { field = value; invalidate() }

    var bookProgress: Float = 0f
        set(value) { field = value; invalidate() }

    var circlesProgress: Float = 0f
        set(value) { field = value; invalidate() }

    var sparkProgress: Float = 0f
        set(value) { field = value; invalidate() }

    var idleFloatY: Float = 0f
        set(value) { field = value; invalidate() }

    var idleGlowPulse: Float = 1f
        set(value) { field = value; invalidate() }

    private var breathingAnimator: ValueAnimator? = null
    private var mainAnimatorSet: AnimatorSet? = null

    // 600x600 Virtual Coordinate Space (Center: 300, 300)
    // Squircle: Rect(100f, 100f, 500f, 500f), size 400x400, corner radius 100f
    private val squirclePath = Path().apply {
        addRoundRect(RectF(100f, 100f, 500f, 500f), 100f, 100f, Path.Direction.CW)
    }

    private val innerBorderPath = Path().apply {
        addRoundRect(RectF(102f, 102f, 498f, 498f), 98f, 98f, Path.Direction.CW)
    }

    private val sheenPath: Path = PathParser.createPathFromPathData(
        "M 100,200 C 100,145 145,100 200,100 L 400,100 C 455,100 500,145 500,200 C 500,240 450,268 300,268 C 150,268 100,240 100,200 Z"
    )

    // Book paths in 600x600 coordinate space
    private val spineCoverPath: Path = PathParser.createPathFromPathData(
        "M 300,358 C 267,345 212,345 164,362 L 164,254 C 212,238 267,238 300,252 C 333,238 388,238 436,254 L 436,362 C 388,345 333,345 300,358 Z"
    )

    private val leftPagePath: Path = PathParser.createPathFromPathData(
        "M 294,351 C 262,338 212,338 170,353 C 166,354 162,351 162,347 L 162,241 C 162,237 166,233 170,232 C 212,218 262,218 294,232 Z"
    )

    private val rightPagePath: Path = PathParser.createPathFromPathData(
        "M 306,351 C 338,338 388,338 430,353 C 434,354 438,351 438,347 L 438,241 C 438,237 434,233 430,232 C 388,218 338,218 306,232 Z"
    )

    private val sparkMainGlowPath: Path = PathParser.createPathFromPathData(
        "M 300,142 Q 300,180 338,180 Q 300,180 300,218 Q 300,180 262,180 Q 300,180 300,142 Z"
    )

    private val sparkMainCorePath: Path = PathParser.createPathFromPathData(
        "M 300,151 Q 300,180 329,180 Q 300,180 300,209 Q 300,180 271,180 Q 300,180 300,151 Z"
    )

    private val sparkPinkGlowPath: Path = PathParser.createPathFromPathData(
        "M 378,166 Q 378,182 394,182 Q 378,182 378,198 Q 378,182 362,182 Q 378,182 378,166 Z"
    )

    private val sparkPinkCorePath: Path = PathParser.createPathFromPathData(
        "M 378,171 Q 378,182 389,182 Q 378,182 378,193 Q 378,182 367,182 Q 378,182 378,171 Z"
    )

    // Pre-configured Paints
    private val squirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            100f, 100f, 500f, 500f,
            intArrayOf(
                Color.parseColor("#705CF6"),
                Color.parseColor("#5341CD"),
                Color.parseColor("#3B28B5")
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    private val squircleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B28B5")
        alpha = 85
        maskFilter = BlurMaskFilter(26f, BlurMaskFilter.Blur.NORMAL)
    }

    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        shader = LinearGradient(
            102f, 102f, 498f, 418f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(35, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0.0f, 0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            300f, 100f, 300f, 268f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(30, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    // =========================================================================
    // Symmetrical Diagonal Frosted Glass Orbs (Center: 300, 300 | Radius: 110)
    // Top-Right: (440, 160) | Bottom-Left: (160, 440) -> Symmetrical & Uncropped!
    // =========================================================================

    // Top-Right Frosted Glass Rose-Pink Lens (Center 440, 160, Radius 110)
    private val pinkGlassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            370f, 90f, 510f, 230f,
            Color.argb(165, 255, 140, 210),
            Color.argb(130, 255, 95, 195),
            Shader.TileMode.CLAMP
        )
    }

    private val pinkGlassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(165, 255, 255, 255)
    }

    private val pinkGlassSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(195, 255, 255, 255)
    }

    // Bottom-Left Frosted Mint/Teal Glass Lens (Center 160, 440, Radius 110)
    private val tealGlassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            90f, 370f, 230f, 510f,
            Color.argb(165, 80, 235, 230),
            Color.argb(135, 0, 230, 195),
            Shader.TileMode.CLAMP
        )
    }

    private val tealGlassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(165, 255, 255, 255)
    }

    private val tealGlassSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(195, 255, 255, 255)
    }

    // Book & Spark Paints
    private val shadowOvalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1463")
        alpha = 90
    }

    private val spineCoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E1C91")
        alpha = 130
    }

    private val leftPagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            162f, 290f, 294f, 290f,
            Color.parseColor("#E8E2FF"),
            Color.parseColor("#FFFFFF"),
            Shader.TileMode.CLAMP
        )
    }

    private val rightPagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            306f, 290f, 438f, 290f,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#D9CEFF"),
            Shader.TileMode.CLAMP
        )
    }

    private val spineLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5341CD")
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val readingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5341CD")
        alpha = 56
    }

    private val sparkMainGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F5D4")
    }

    private val sparkWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val sparkPinkGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB4E6")
        alpha = 230
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Map cleanly into 600x600 virtual coordinate space
        val scale = minOf(w, h) / 600f
        val dx = (w - 600f * scale) / 2f
        val dy = (h - 600f * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // =====================================================================
        // 1. SQUIRCLE LAYER (Pops in dynamically with spring bounce, 0ms start)
        // =====================================================================
        if (squircleProgress > 0f) {
            canvas.save()
            val sScale = squircleProgress
            val sRot = (1f - squircleProgress) * -4f
            canvas.translate(300f, 300f)
            canvas.scale(sScale, sScale)
            canvas.rotate(sRot)
            canvas.translate(-300f, -300f)

            // Outer Soft Drop Shadow
            canvas.drawRoundRect(RectF(100f, 110f, 500f, 510f), 100f, 100f, squircleShadowPaint)

            // Main Gradient Squircle Body
            canvas.drawPath(squirclePath, squirclePaint)

            // Glass Gloss Sheen
            canvas.save()
            canvas.clipPath(squirclePath)
            canvas.drawPath(sheenPath, sheenPaint)
            canvas.restore()

            // Inner Gloss Stroke Highlight
            canvas.drawPath(innerBorderPath, innerBorderPaint)

            canvas.restore()
        }

        // =====================================================================
        // 2. FROSTED GLASS ORBS (Symmetrical diagonal balance, zero cropping!)
        // Top-Right: (440, 160) | Bottom-Left: (160, 440) -> Blooms AFTER Book!
        // =====================================================================
        if (circlesProgress > 0f) {
            val cScale = 0.4f + 0.6f * circlesProgress

            // --- Top-Right Pink Frosted Glass Orb (Center 440, 160, Radius 110) ---
            canvas.save()
            canvas.translate(440f, 160f)
            canvas.scale(cScale, cScale)
            canvas.translate(-440f, -160f)

            pinkGlassFillPaint.alpha = (120 * circlesProgress).toInt()
            pinkGlassRimPaint.alpha = (145 * circlesProgress).toInt()
            pinkGlassSpecularPaint.alpha = (165 * circlesProgress).toInt()

            canvas.drawCircle(440f, 160f, 110f, pinkGlassFillPaint)
            canvas.drawCircle(440f, 160f, 110f, pinkGlassRimPaint)
            // Specular crescent arc along top-left curve (195° to 285°)
            canvas.drawArc(
                RectF(330f, 50f, 550f, 270f),
                195f, 90f, false, pinkGlassSpecularPaint
            )
            canvas.restore()

            // --- Bottom-Left Mint/Teal Frosted Glass Orb (Center 160, 440, Radius 110) ---
            canvas.save()
            canvas.translate(160f, 440f)
            canvas.scale(cScale, cScale)
            canvas.translate(-160f, -440f)

            tealGlassFillPaint.alpha = (120 * circlesProgress).toInt()
            tealGlassRimPaint.alpha = (145 * circlesProgress).toInt()
            tealGlassSpecularPaint.alpha = (165 * circlesProgress).toInt()

            canvas.drawCircle(160f, 440f, 110f, tealGlassFillPaint)
            canvas.drawCircle(160f, 440f, 110f, tealGlassRimPaint)
            // Specular crescent arc along bottom-left curve (105° to 195°)
            canvas.drawArc(
                RectF(50f, 330f, 270f, 550f),
                105f, 90f, false, tealGlassSpecularPaint
            )
            canvas.restore()
        }

        // =====================================================================
        // 3. OPEN BOOK LAYER (Unfolding from center spine 300, 351)
        // =====================================================================
        if (bookProgress > 0f) {
            canvas.save()
            val bAlpha = (bookProgress * 255f).toInt().coerceIn(0, 255)
            val bScale = 0.5f + 0.5f * bookProgress

            canvas.translate(300f, 320f)
            canvas.scale(bScale, bScale)
            canvas.translate(-300f, -320f)

            // Base Shadow Ellipse
            shadowOvalPaint.alpha = (90 * bookProgress).toInt()
            canvas.drawOval(RectF(170f, 365f, 430f, 395f), shadowOvalPaint)

            // Spine & Dark Cover Base
            spineCoverPaint.alpha = (130 * bookProgress).toInt()
            canvas.drawPath(spineCoverPath, spineCoverPaint)

            // Left Page Wing (Unfolding rotation / scale)
            canvas.save()
            canvas.translate(294f, 351f)
            val leftScaleX = 0.3f + 0.7f * bookProgress
            val leftAngle = (1f - bookProgress) * 14f
            canvas.scale(leftScaleX, 1f)
            canvas.rotate(leftAngle)
            canvas.translate(-294f, -351f)
            leftPagePaint.alpha = bAlpha
            canvas.drawPath(leftPagePath, leftPagePaint)

            // Left Reading Lines
            if (bookProgress > 0.4f) {
                val linesProgress = ((bookProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                readingLinePaint.alpha = (56 * linesProgress).toInt()
                canvas.drawRoundRect(RectF(190f, 256f, 190f + 68f * linesProgress, 262f), 3f, 3f, readingLinePaint)
                canvas.drawRoundRect(RectF(190f, 274f, 190f + 76f * linesProgress, 280f), 3f, 3f, readingLinePaint)
                canvas.drawRoundRect(RectF(190f, 292f, 190f + 54f * linesProgress, 298f), 3f, 3f, readingLinePaint)
            }
            canvas.restore()

            // Right Page Wing (Unfolding rotation / scale)
            canvas.save()
            canvas.translate(306f, 351f)
            val rightScaleX = 0.3f + 0.7f * bookProgress
            val rightAngle = (1f - bookProgress) * -14f
            canvas.scale(rightScaleX, 1f)
            canvas.rotate(rightAngle)
            canvas.translate(-306f, -351f)
            rightPagePaint.alpha = bAlpha
            canvas.drawPath(rightPagePath, rightPagePaint)

            // Right Reading Lines
            if (bookProgress > 0.4f) {
                val linesProgress = ((bookProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                readingLinePaint.alpha = (56 * linesProgress).toInt()
                canvas.drawRoundRect(RectF(334f, 256f, 334f + 76f * linesProgress, 262f), 3f, 3f, readingLinePaint)
                canvas.drawRoundRect(RectF(334f, 274f, 334f + 64f * linesProgress, 280f), 3f, 3f, readingLinePaint)
                canvas.drawRoundRect(RectF(334f, 292f, 334f + 72f * linesProgress, 298f), 3f, 3f, readingLinePaint)
            }
            canvas.restore()

            // Center Binding Spine Line
            spineLinePaint.alpha = bAlpha
            canvas.drawLine(300f, 226f, 300f, 352f, spineLinePaint)

            canvas.restore() // End Book Group
        }

        // =====================================================================
        // 4. AI WISDOM SPARKS (Lifting from spine with spring rotation & idle float)
        // =====================================================================
        if (sparkProgress > 0f) {
            canvas.save()
            canvas.translate(0f, idleFloatY)

            // --- Primary 4-Point Teal AI Spark ---
            canvas.save()
            val spScale = sparkProgress * idleGlowPulse
            val spRot = (1f - sparkProgress) * -25f
            val spRiseY = (1f - sparkProgress) * 35f

            canvas.translate(300f, 180f + spRiseY)
            canvas.scale(spScale, spScale)
            canvas.rotate(spRot)
            canvas.translate(-300f, -180f)

            sparkMainGlowPaint.alpha = (255 * sparkProgress).toInt().coerceIn(0, 255)
            canvas.drawPath(sparkMainGlowPath, sparkMainGlowPaint)

            sparkWhitePaint.alpha = (255 * sparkProgress).toInt().coerceIn(0, 255)
            canvas.drawPath(sparkMainCorePath, sparkWhitePaint)
            canvas.restore()

            // --- Secondary Pink AI Spark (Top-Right) ---
            if (sparkProgress > 0.25f) {
                val pinkProgress = ((sparkProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                canvas.save()
                canvas.translate(378f, 182f)
                canvas.scale(pinkProgress, pinkProgress)
                canvas.translate(-378f, -182f)

                sparkPinkGlowPaint.alpha = (230 * pinkProgress).toInt()
                canvas.drawPath(sparkPinkGlowPath, sparkPinkGlowPaint)

                sparkWhitePaint.alpha = (255 * pinkProgress).toInt()
                canvas.drawPath(sparkPinkCorePath, sparkWhitePaint)
                canvas.restore()
            }

            // --- Tertiary Cyan Floating Pip (Left) ---
            if (sparkProgress > 0.4f) {
                val pipProgress = ((sparkProgress - 0.4f) / 0.6f).coerceIn(0f, 1f)
                canvas.save()
                canvas.translate(225f, 188f)
                canvas.scale(pipProgress, pipProgress)

                sparkMainGlowPaint.alpha = (255 * pipProgress).toInt()
                canvas.drawCircle(0f, 0f, 5.5f, sparkMainGlowPaint)

                sparkWhitePaint.alpha = (255 * pipProgress).toInt()
                canvas.drawCircle(0f, 0f, 3f, sparkWhitePaint)
                canvas.restore()
            }

            canvas.restore() // End Sparks Group
        }

        canvas.restore() // End Root Virtual Canvas
    }

    /**
     * Start the multi-stage studio spring animation choreography.
     * Sequence:
     * 1. Squircle Pop (0ms &ndash; 550ms, spring scale 0 -> 1 for immediate dynamic motion)
     * 2. Book Wings Unfolding (200ms &ndash; 850ms)
     * 3. Symmetrical Frosted Glass Orbs Bloom (800ms &ndash; 1500ms, starts right after book unfolds!)
     * 4. AI Wisdom Sparks Ascent & Twinkle (1050ms &ndash; 1700ms)
     */
    fun startChoreography(onComplete: (() -> Unit)? = null) {
        stopAllAnimations()

        // 1. Squircle spring entry (pops in dynamically on frame 0)
        val squircleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            interpolator = OvershootInterpolator(1.35f)
            addUpdateListener { squircleProgress = it.animatedValue as Float }
        }

        // 2. Open Book Wings Unfolding (unfolding from center spine)
        val bookAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            startDelay = 200
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { bookProgress = it.animatedValue as Float }
        }

        // 3. Frosted Glass Orbs Bloom (Begins at 800ms, right as book completes unfolding!)
        val circlesAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            startDelay = 800
            interpolator = OvershootInterpolator(1.25f)
            addUpdateListener { circlesProgress = it.animatedValue as Float }
        }

        // 4. AI Wisdom Sparks Ascent & Twinkle
        val sparkAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            startDelay = 1050
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { sparkProgress = it.animatedValue as Float }
        }

        mainAnimatorSet = AnimatorSet().apply {
            playTogether(squircleAnim, bookAnim, circlesAnim, sparkAnim)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    startBreathingLoop()
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    fun startBreathingLoop() {
        if (breathingAnimator?.isRunning == true) return

        breathingAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val phase = it.animatedValue as Float
                idleFloatY = sin(phase.toDouble()).toFloat() * -3f
                idleGlowPulse = 1f + sin(phase.toDouble()).toFloat() * 0.05f
            }
            start()
        }
    }

    fun stopAllAnimations() {
        mainAnimatorSet?.cancel()
        breathingAnimator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
    }
}
