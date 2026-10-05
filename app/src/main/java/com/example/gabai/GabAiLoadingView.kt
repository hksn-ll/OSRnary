package com.example.gabai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.*

/**
 * High-performance, hardware-accelerated micro-loading animation view for GabAI.
 * Renders the authentic GabAI logo with:
 * - Natural flexible book page turn (seamless left-page "=" reading line morph)
 * - Balanced start-and-finish lighting (100% boundary continuity, zero flicker)
 * - Moving and breathing frosted glass orbs with specular crescent arcs
 * - AI stars and lights that twinkle in place (do not move)
 */
class GabAiLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var cycleProgress: Float = 0f
    private var animator: ValueAnimator? = null
    var cycleDurationMs: Long = 1400L
        set(value) {
            field = value
            if (animator?.isRunning == true) {
                stopLoading()
                startLoading()
            }
        }

    private var isManualScrubbing = false

    // 600x600 Virtual Coordinate Space (Center: 300, 300)
    private val squirclePath = Path().apply {
        addRoundRect(RectF(100f, 100f, 500f, 500f), 100f, 100f, Path.Direction.CW)
    }

    private val innerBorderPath = Path().apply {
        addRoundRect(RectF(102f, 102f, 498f, 498f), 98f, 98f, Path.Direction.CW)
    }

    private val sheenPath: Path = PathParser.createPathFromPathData(
        "M 100,200 C 100,145 145,100 200,100 L 400,100 C 455,100 500,145 500,200 C 500,240 450,268 300,268 C 150,268 100,240 100,200 Z"
    )

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

    // Pre-allocated Paints
    private val squirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            100f, 100f, 500f, 500f,
            intArrayOf(Color.parseColor("#705CF6"), Color.parseColor("#5341CD"), Color.parseColor("#3B28B5")),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    private val squircleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B28B5")
        alpha = 85
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        shader = LinearGradient(
            102f, 102f, 498f, 418f,
            intArrayOf(Color.argb(115, 255, 255, 255), Color.argb(35, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0.0f, 0.4f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    // Frosted Glass Paints
    private val pinkGlassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinkGlassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(165, 255, 255, 255)
    }
    private val pinkGlassSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(215, 255, 255, 255)
    }

    private val tealGlassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tealGlassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(165, 255, 255, 255)
    }
    private val tealGlassSpecularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(215, 255, 255, 255)
    }

    // Book Paints
    private val baseShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1463")
        alpha = 90
    }
    private val spineCoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E1C91")
        alpha = 135
    }
    private val stackEdgeBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C7BAFD")
    }
    private val stackEdgeMiddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDD6FE")
    }
    private val leftBasePagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(162f, 290f, 294f, 290f, Color.parseColor("#E8E2FF"), Color.parseColor("#FFFFFF"), Shader.TileMode.CLAMP)
    }
    private val rightBasePagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(306f, 290f, 438f, 290f, Color.parseColor("#FFFFFF"), Color.parseColor("#D9CEFF"), Shader.TileMode.CLAMP)
    }
    private val readingLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5341CD")
        alpha = 70
    }
    private val spineLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5341CD")
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val dynamicCastShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1463")
    }
    private val turningPagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Sparks Paints
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
        startLoading()
    }

    fun startLoading() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleDurationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (!isManualScrubbing) {
                    cycleProgress = it.animatedValue as Float
                    invalidate()
                }
            }
            start()
        }
    }

    fun stopLoading() {
        animator?.cancel()
        animator = null
    }

    fun isLoading(): Boolean = animator?.isRunning == true

    /** Manual scrubbing for developer lab / testing */
    fun setProgressManual(progress: Float) {
        isManualScrubbing = true
        cycleProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    fun resumeAutoAnimation() {
        isManualScrubbing = false
        startLoading()
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val scale = minOf(w, h) / 600f
        val dx = (w - 600f * scale) / 2f
        val dy = (h - 600f * scale) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        val t = cycleProgress.coerceIn(0f, 1f)
        val u = easeInOutCubic(t)

        // Strict periodic harmonic variables guaranteeing f(0) === f(1)
        val omega = (2 * Math.PI * t).toFloat()
        val sinOmega = sin(omega.toDouble()).toFloat()
        val cosOmega = cos(omega.toDouble()).toFloat()
        val halfOmega = (Math.PI * u).toFloat()
        val sinHalf = sin(halfOmega.toDouble()).toFloat()
        val cosHalf = cos(halfOmega.toDouble()).toFloat()

        // =====================================================================
        // 1. SQUIRCLE WITH DROP SHADOW, GLOSS SHEEN & INNER BORDER
        // =====================================================================
        canvas.drawRoundRect(RectF(100f, 108f, 500f, 508f), 100f, 100f, squircleShadowPaint)
        canvas.drawPath(squirclePath, squirclePaint)

        // Top Gloss Sheen (Strictly equal alpha at t=0 and t=1)
        canvas.save()
        canvas.clipPath(squirclePath)
        val sheenAlpha = (100 + sinHalf * 25).toInt()
        sheenPaint.shader = LinearGradient(
            300f, 100f, 300f, 268f,
            intArrayOf(Color.argb(sheenAlpha, 255, 255, 255), Color.argb((sheenAlpha * 0.3f).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(sheenPath, sheenPaint)
        canvas.restore()

        canvas.drawPath(innerBorderPath, innerBorderPaint)

        // =====================================================================
        // 2. ANIMATED FROSTED GLASS ORBS (Moving & Exactly at Origin at t=0, 1)
        // =====================================================================
        // Top-Right Rose Pink Glass Orb (Center: 440, 160 | Radius: 110)
        val pinkDriftX = sinOmega * 8f
        val pinkDriftY = -sinOmega * 7f
        val pinkScale = 1.0f + sinOmega * 0.05f

        canvas.save()
        canvas.translate(440f + pinkDriftX, 160f + pinkDriftY)
        canvas.scale(pinkScale, pinkScale)
        canvas.translate(-440f, -160f)

        pinkGlassFillPaint.shader = LinearGradient(
            370f, 90f, 510f, 230f,
            Color.argb(165, 255, 140, 210),
            Color.argb(130, 255, 95, 195),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(440f, 160f, 110f, pinkGlassFillPaint)
        canvas.drawCircle(440f, 160f, 110f, pinkGlassRimPaint)

        // Specular crescent arc (angle shifts smoothly, 0° shift at t=0 and t=1)
        val pinkArcShift = sinOmega * 15f
        canvas.drawArc(
            RectF(330f, 50f, 550f, 270f),
            195f + pinkArcShift, 90f, false, pinkGlassSpecularPaint
        )
        canvas.restore()

        // Bottom-Left Mint Teal Glass Orb (Center: 160, 440 | Radius: 110)
        val tealDriftX = -sinOmega * 8f
        val tealDriftY = sinOmega * 7f
        val tealScale = 1.0f - sinOmega * 0.05f

        canvas.save()
        canvas.translate(160f + tealDriftX, 440f + tealDriftY)
        canvas.scale(tealScale, tealScale)
        canvas.translate(-160f, -440f)

        tealGlassFillPaint.shader = LinearGradient(
            90f, 370f, 230f, 510f,
            Color.argb(165, 80, 235, 230),
            Color.argb(135, 0, 230, 195),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(160f, 440f, 110f, tealGlassFillPaint)
        canvas.drawCircle(160f, 440f, 110f, tealGlassRimPaint)

        val tealArcShift = -sinOmega * 15f
        canvas.drawArc(
            RectF(50f, 330f, 270f, 550f),
            105f + tealArcShift, 90f, false, tealGlassSpecularPaint
        )
        canvas.restore()

        // =====================================================================
        // 3. BASE BOOK COVER & SHADOW
        // =====================================================================
        canvas.drawOval(RectF(170f, 365f, 430f, 395f), baseShadowPaint)
        canvas.drawPath(spineCoverPath, spineCoverPaint)

        // 4. UNDERLYING LAYERED PAPER DECKS
        canvas.save()
        canvas.translate(-4f, 3f); canvas.drawPath(leftPagePath, stackEdgeBottomPaint)
        canvas.translate(2f, -1.5f); canvas.drawPath(leftPagePath, stackEdgeMiddlePaint)
        canvas.restore()

        canvas.save()
        canvas.translate(4f, 3f); canvas.drawPath(rightPagePath, stackEdgeBottomPaint)
        canvas.translate(-2f, -1.5f); canvas.drawPath(rightPagePath, stackEdgeMiddlePaint)
        canvas.restore()

        // 5. BASE STATIONARY WINGS (With authentic reading lines)
        canvas.drawPath(leftPagePath, leftBasePagePaint)
        canvas.drawRoundRect(RectF(190f, 256f, 258f, 261f), 2.5f, 2.5f, readingLinePaint)
        canvas.drawRoundRect(RectF(190f, 274f, 266f, 279f), 2.5f, 2.5f, readingLinePaint)
        canvas.drawRoundRect(RectF(190f, 292f, 244f, 297f), 2.5f, 2.5f, readingLinePaint)

        canvas.drawPath(rightPagePath, rightBasePagePaint)
        canvas.drawRoundRect(RectF(334f, 256f, 410f, 261f), 2.5f, 2.5f, readingLinePaint)
        canvas.drawRoundRect(RectF(334f, 274f, 398f, 279f), 2.5f, 2.5f, readingLinePaint)
        canvas.drawRoundRect(RectF(334f, 292f, 406f, 297f), 2.5f, 2.5f, readingLinePaint)

        canvas.drawLine(300f, 226f, 300f, 352f, spineLinePaint)

        // =====================================================================
        // 6. PERFECTED NATURAL PAGE TURN (Equal Lighting at 0° and 180°)
        // =====================================================================
        val liftY = -sinHalf * 32f
        val curlAngleRad = (1.0f - u * 2.0f) * (sinHalf * 0.16f)

        // Dynamic Traveling Drop Shadow (Strictly 0 alpha at t=0 and t=1)
        if (sinHalf > 0.005f) {
            dynamicCastShadowPaint.alpha = (sinHalf * 72f).toInt()
            val shadowX = 300f + cosHalf * 65f
            val shadowRect = RectF(shadowX - (55f * abs(cosHalf) + 18f) / 2f, 350f, shadowX + (55f * abs(cosHalf) + 18f) / 2f, 362f)
            canvas.drawOval(shadowRect, dynamicCastShadowPaint)
        }

        // Active Turning Page
        canvas.save()
        if (cosHalf >= 0f) {
            // PHASE A: Lifting from Right (0° to 90°)
            canvas.translate(300f, 289f + liftY)
            canvas.rotate((curlAngleRad * 180f / Math.PI).toFloat())
            canvas.scale(cosHalf, 1.0f + sinHalf * 0.12f)
            canvas.translate(-300f, -289f)

            val frontShade = 1.0f - sinHalf * 0.12f
            turningPagePaint.shader = LinearGradient(
                306f, 290f, 438f, 290f,
                Color.argb((255 * frontShade).toInt(), 255, 255, 255),
                Color.argb((255 * frontShade).toInt(), 217, 206, 255),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(rightPagePath, turningPagePaint)

            // Right Reading Lines Pattern
            readingLinePaint.alpha = (70 * frontShade).toInt()
            canvas.drawRoundRect(RectF(334f, 256f, 410f, 261f), 2.5f, 2.5f, readingLinePaint)
            canvas.drawRoundRect(RectF(334f, 274f, 398f, 279f), 2.5f, 2.5f, readingLinePaint)
            canvas.drawRoundRect(RectF(334f, 292f, 406f, 297f), 2.5f, 2.5f, readingLinePaint)

        } else {
            // PHASE B: Descending to Left (90° to 180°)
            val leftScaleX = -cosHalf // 0 at 90° -> 1.0 at 180°!
            canvas.translate(300f, 289f + liftY)
            canvas.rotate((curlAngleRad * 180f / Math.PI).toFloat())
            canvas.scale(leftScaleX, 1.0f + sinHalf * 0.12f)
            canvas.translate(-300f, -289f)

            val backShade = 1.0f - sinHalf * 0.10f
            turningPagePaint.shader = LinearGradient(
                162f, 290f, 294f, 290f,
                Color.argb((255 * backShade).toInt(), 232, 226, 255),
                Color.argb((255 * backShade).toInt(), 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(leftPagePath, turningPagePaint)

            // Left Reading Lines Pattern (= pattern)
            readingLinePaint.alpha = (70 * backShade).toInt()
            canvas.drawRoundRect(RectF(190f, 256f, 258f, 261f), 2.5f, 2.5f, readingLinePaint)
            canvas.drawRoundRect(RectF(190f, 274f, 266f, 279f), 2.5f, 2.5f, readingLinePaint)
            canvas.drawRoundRect(RectF(190f, 292f, 244f, 297f), 2.5f, 2.5f, readingLinePaint)
        }
        canvas.restore()

        // =====================================================================
        // 7. AI STAR & LIGHTS BESIDE IT (TWINKLE ONLY VIA LUMINANCE - ZERO MOVEMENT)
        // Stars are astronomically fixed: no rotation, no scale, no translation!
        // =====================================================================
        // Pure twinkling scintillation factor (smooth periodic brightness shimmer)
        val starScintillation = 0.50f + 0.50f * abs(sinOmega)
        val pinkScintillation = 0.45f + 0.55f * abs(cosOmega)
        val pipScintillation  = 0.40f + 0.60f * abs(sin((omega + Math.PI / 3).toDouble()).toFloat())

        // Primary Teal Star at (300, 180): Fixed in space, twinkles in brightness only!
        sparkMainGlowPaint.alpha = (255 * starScintillation).toInt().coerceIn(100, 255)
        sparkWhitePaint.alpha = (255 * (0.75f + 0.25f * starScintillation)).toInt()
        canvas.drawPath(sparkMainGlowPath, sparkMainGlowPaint)
        canvas.drawPath(sparkMainCorePath, sparkWhitePaint)

        // Secondary Rose AI Spark at (378, 182): Fixed in space, twinkles in brightness only!
        sparkPinkGlowPaint.alpha = (230 * pinkScintillation).toInt().coerceIn(80, 230)
        canvas.drawPath(sparkPinkGlowPath, sparkPinkGlowPaint)
        canvas.drawPath(sparkPinkCorePath, sparkWhitePaint)

        // Tertiary Cyan Floating Pip at (225, 188): Fixed in space, twinkles in brightness only!
        sparkMainGlowPaint.alpha = (255 * pipScintillation).toInt().coerceIn(80, 255)
        canvas.drawCircle(225f, 188f, 5.5f, sparkMainGlowPaint)
        canvas.drawCircle(225f, 188f, 3.0f, sparkWhitePaint)

        canvas.restore() // End 600x600 space
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            if (!isManualScrubbing) startLoading()
        } else {
            stopLoading()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE && !isManualScrubbing) {
            startLoading()
        }
    }

    fun playEntranceAnimation() {
        alpha = 0f
        scaleX = 0.55f
        scaleY = 0.55f
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.35f))
            .start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoading()
    }
}
