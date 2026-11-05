package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class OrbState {
        IDLE,
        LISTENING,
        SPEAKING,
        THINKING,
        ACTIVE
    }

    private var currentState = OrbState.IDLE

    // Animation values
    private var pulseScale = 1.0f
    private var glowAlpha = 160
    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var thinkingAngle = 0f
    private var currentAmplitude = 0f

    // Animators
    private var pulseAnimator: ValueAnimator? = null
    private var rotationAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private var thinkingAnimator: ValueAnimator? = null

    // Paints
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val thinkingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringRect = RectF()
    private val thinkingRect = RectF()
    private val wavePath = Path()

    // 12 orbiting particles
    private val particleAngles = FloatArray(12) { (it * 30f) }
    private val particleDistances = FloatArray(12) { 1.15f + (it % 3) * 0.1f }

    init {
        initAnimators()
    }

    private fun initAnimators() {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                glowAlpha = (120 + ((pulseScale - 1f) / 0.15f) * 100).toInt().coerceIn(120, 220)
                invalidate()
            }
            start()
        }

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                waveOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        thinkingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                thinkingAngle = it.animatedValue as Float
                if (currentState == OrbState.THINKING) {
                    invalidate()
                }
            }
            start()
        }
    }

    fun setState(state: OrbState) {
        if (currentState != state) {
            currentState = state
            invalidate()
        }
    }

    fun getState(): OrbState = currentState

    fun setAmplitude(rms: Float) {
        currentAmplitude = rms.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 10f || h <= 10f) return

        val centerX = w / 2f
        val centerY = h / 2f
        val baseRadius = (minOf(w, h) / 2f) * 0.48f
        if (baseRadius <= 5f) return

        val (colorStart, colorEnd) = when (currentState) {
            OrbState.IDLE -> Pair(Color.parseColor("#B71C1C"), Color.parseColor("#880E4F"))
            OrbState.LISTENING, OrbState.ACTIVE -> Pair(Color.parseColor("#FF1744"), Color.parseColor("#D500F9"))
            OrbState.SPEAKING -> Pair(Color.parseColor("#E040FB"), Color.parseColor("#FF1744"))
            OrbState.THINKING -> Pair(Color.parseColor("#40C4FF"), Color.parseColor("#00B0FF"))
        }

        val dynamicRadius = if (currentState == OrbState.IDLE) {
            baseRadius * pulseScale
        } else {
            baseRadius * (1f + currentAmplitude * 0.25f)
        }
        if (dynamicRadius <= 5f) return

        // Layer 1: Radial Glow (1.6x radius)
        val glowRadius = maxOf(10f, dynamicRadius * 1.6f)
        val glowColorWithAlpha = Color.argb(glowAlpha, Color.red(colorStart), Color.green(colorStart), Color.blue(colorStart))
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(glowColorWithAlpha, Color.TRANSPARENT),
            floatArrayOf(0.3f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // Layer 2: Core Orb
        val coreRadius = maxOf(10f, dynamicRadius * 1.2f)
        orbPaint.shader = RadialGradient(
            centerX - dynamicRadius * 0.25f,
            centerY - dynamicRadius * 0.25f,
            coreRadius,
            intArrayOf(colorStart, colorEnd, Color.parseColor("#120005")),
            floatArrayOf(0.1f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, dynamicRadius, orbPaint)

        // Layer 3: 3 Rotating Rings (dashed arcs)
        if (currentState != OrbState.IDLE) {
            ringPaint.color = colorStart
            ringPaint.alpha = 180
            ringPaint.strokeWidth = 3f

            for (i in 0 until 3) {
                val ringRad = dynamicRadius * (1.15f + i * 0.16f)
                val dir = if (i % 2 == 0) 1f else -1f
                val ringAngle = (rotationAngle * dir * (1f + i * 0.3f)) % 360f

                ringRect.set(centerX - ringRad, centerY - ringRad, centerX + ringRad, centerY + ringRad)
                ringPaint.pathEffect = DashPathEffect(floatArrayOf(30f, 25f), 0f)
                canvas.drawArc(ringRect, ringAngle, 100f, false, ringPaint)
                canvas.drawArc(ringRect, ringAngle + 180f, 70f, false, ringPaint)
            }
        }

        // Layer 4: Wave Rings (Sine waves, amplitude reactive)
        if (currentState == OrbState.LISTENING || currentState == OrbState.SPEAKING || currentState == OrbState.ACTIVE) {
            wavePaint.color = colorEnd
            wavePaint.strokeWidth = 2.5f
            wavePaint.alpha = (140 + currentAmplitude * 115).toInt().coerceIn(140, 255)

            val waveBaseRadius = dynamicRadius * 1.08f
            val waveAmp = (4f + currentAmplitude * 18f)
            val segments = 72
            wavePath.reset()

            for (i in 0..segments) {
                val theta = (i.toFloat() / segments) * (2 * Math.PI).toFloat()
                val freq = if (currentState == OrbState.SPEAKING) 6.0 else 4.0
                val r = waveBaseRadius + sin(theta * freq + waveOffset) * waveAmp
                val x = centerX + (r * cos(theta)).toFloat()
                val y = centerY + (r * sin(theta)).toFloat()

                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            wavePath.close()
            canvas.drawPath(wavePath, wavePaint)
        }

        // Layer 5: Thinking Arc
        if (currentState == OrbState.THINKING) {
            thinkingPaint.color = Color.parseColor("#40C4FF")
            thinkingPaint.strokeWidth = 6f
            val thinkRad = dynamicRadius * 1.25f
            thinkingRect.set(centerX - thinkRad, centerY - thinkRad, centerX + thinkRad, centerY + thinkRad)
            canvas.drawArc(thinkingRect, thinkingAngle, 80f, false, thinkingPaint)
            canvas.drawArc(thinkingRect, thinkingAngle + 180f, 80f, false, thinkingPaint)
        }

        // Layer 6: Particles
        if (currentState == OrbState.SPEAKING || currentState == OrbState.ACTIVE || currentState == OrbState.LISTENING) {
            particlePaint.color = colorEnd
            for (i in 0 until 12) {
                val angleRad = Math.toRadians((particleAngles[i] + rotationAngle * 1.5f).toDouble())
                val dist = dynamicRadius * (particleDistances[i] + currentAmplitude * 0.3f)
                val px = centerX + (dist * cos(angleRad)).toFloat()
                val py = centerY + (dist * sin(angleRad)).toFloat()
                val pRadius = 3f + (i % 3) * 1.5f
                particlePaint.alpha = (160 + (i % 4) * 20).coerceIn(100, 255)
                canvas.drawCircle(px, py, pRadius, particlePaint)
            }
        }

        // Layer 7: Inner Highlight
        val hlRadius = maxOf(5f, dynamicRadius * 0.45f)
        highlightPaint.shader = RadialGradient(
            centerX - dynamicRadius * 0.35f,
            centerY - dynamicRadius * 0.35f,
            hlRadius,
            intArrayOf(Color.argb(160, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX - dynamicRadius * 0.35f, centerY - dynamicRadius * 0.35f, hlRadius, highlightPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        rotationAnimator?.cancel()
        waveAnimator?.cancel()
        thinkingAnimator?.cancel()
    }
}
