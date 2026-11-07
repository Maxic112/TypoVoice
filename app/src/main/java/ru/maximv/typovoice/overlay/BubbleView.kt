package ru.maximv.typovoice.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import ru.maximv.typovoice.R
import ru.maximv.typovoice.core.settings.BubbleIcon
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Round dictation button drawn on a canvas (Figma design: blue radial disc in a green gradient rim);
 * the chosen icon inside, a level halo while recording, a spinner while uploading.
 */
class BubbleView(context: Context) : View(context) {

    enum class Visual { IDLE, RECORDING, BUSY, SUCCESS, ERROR }

    var visual: Visual = Visual.IDLE
        set(value) {
            if (field == value) return
            field = value
            if (value == Visual.BUSY) spinner.start() else spinner.cancel()
            updateBreathing()
            invalidate()
        }

    /** Small dot in the corner: LLM post-processing is switched on from the quick menu. */
    var badge: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The floating button is translucent while idle; an in-app copy stays opaque. */
    var translucentIdle: Boolean = true

    var level: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (visual == Visual.RECORDING) invalidate()
        }

    var idleIcon: BubbleIcon = BubbleIcon.MIC
        set(value) {
            if (field == value) return
            field = value
            idleDrawable = drawable(value)
            invalidate()
        }

    /** [BubbleIcon.PULSE] draws an indicator that follows the voice level instead of an icon. */
    var recordingIcon: BubbleIcon = BubbleIcon.MIC
        set(value) {
            if (field == value) return
            field = value
            recordingDrawable = drawable(value)
            updateBreathing()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.WHITE
    }
    private val arc = RectF()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recordingRimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val recordingDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = REC_DISC }
    private var shaderRadius = -1f
    private var idleDrawable = drawable(BubbleIcon.MIC)
    private var recordingDrawable = drawable(BubbleIcon.MIC)
    private val pulseRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }

    /** Slow "breathing" so dynamic indicators stay alive during silence; the voice level adds on top. */
    private var breath = 0f
    private var phase = 0f
    private val breathing = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            breath = (sin(phase) + 1f) / 2f
            invalidate()
        }
    }

    private fun drawable(icon: BubbleIcon) = ContextCompat.getDrawable(context, iconRes(icon))!!.mutate()

    private fun updateBreathing() {
        val dynamic = visual == Visual.RECORDING && recordingIcon.dynamic
        if (dynamic && !breathing.isStarted) breathing.start() else if (!dynamic) breathing.cancel()
    }
    private var spinnerAngle = 0f
    private val spinner = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spinnerAngle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = minOf(cx, cy)
        val base = maxRadius * 0.78f

        if (visual == Visual.RECORDING) {
            fill.color = REC_RIM_TOP
            fill.alpha = 90
            canvas.drawCircle(cx, cy, base + (maxRadius - base) * level, fill)
        }

        // Rim and disc go through one layer so the idle translucency doesn't show the rim through the disc.
        updateShaders(cx, cy, base)
        val disc = base * DISC_RATIO
        canvas.saveLayerAlpha(cx - base, cy - base, cx + base, cy + base, if (visual == Visual.IDLE && translucentIdle) 200 else 255)
        // Recording has its own base (Figma 2001:9): pink rim, solid deep-blue disc.
        val recording = visual == Visual.RECORDING
        canvas.drawCircle(cx, cy, base, if (recording) recordingRimPaint else rimPaint)
        canvas.drawCircle(cx, cy, disc, if (recording) recordingDiscPaint else discPaint)
        // Only a failure flashes the disc; a success just returns to the normal look.
        if (visual == Visual.ERROR) {
            fill.color = COLOR_ERROR
            fill.alpha = 255
            canvas.drawCircle(cx, cy, disc, fill)
        }
        canvas.restore()

        if (visual == Visual.BUSY) {
            val r = disc - ring.strokeWidth * 2
            arc.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(arc, spinnerAngle, 100f, false, ring)
        }

        if (badge) {
            val r = base * 0.26f
            val bx = cx + base * 0.72f
            val by = cy - base * 0.72f
            fill.color = Color.WHITE
            fill.alpha = 255
            canvas.drawCircle(bx, by, r + 2 * density, fill)
            fill.color = COLOR_BADGE
            canvas.drawCircle(bx, by, r, fill)
        }

        // Raw amplitude is mostly small for speech: the square root spreads it over the whole range.
        val voice = sqrt(level)
        if (recording && recordingIcon == BubbleIcon.WAVE) {
            drawWave(canvas, cx, cy, base, voice)
        } else if (recording && recordingIcon == BubbleIcon.PULSE) {
            // Centre dot and ring grow with the voice; breathing keeps them moving in silence.
            val level = voice
            val dot = base * (0.22f + 0.06f * breath + 0.22f * level)
            fill.color = Color.WHITE
            fill.alpha = 255
            canvas.drawCircle(cx, cy, dot, fill)
            pulseRing.alpha = (110 + 145 * level).toInt().coerceAtMost(255)
            canvas.drawCircle(cx, cy, (dot + base * (0.18f + 0.22f * level)).coerceAtMost(base - pulseRing.strokeWidth), pulseRing)
        } else {
            val drawable = if (recording) recordingDrawable else idleDrawable
            val icon = (base * 0.95f).toInt()
            drawable.setBounds((cx - icon / 2).toInt(), (cy - icon / 2).toInt(), (cx + icon / 2).toInt(), (cy + icon / 2).toInt())
            drawable.draw(canvas)
        }
    }

    private fun updateShaders(cx: Float, cy: Float, base: Float) {
        if (base == shaderRadius) return
        shaderRadius = base
        rimPaint.shader = LinearGradient(cx, cy - base * 0.77f, cx, cy + base * 0.77f, RIM_TOP, RIM_BOTTOM, Shader.TileMode.CLAMP)
        recordingRimPaint.shader = LinearGradient(cx, cy - base, cx, cy + base, REC_RIM_TOP, REC_RIM_BOTTOM, Shader.TileMode.CLAMP)
        discPaint.shader = RadialGradient(
            cx, cy, base * DISC_RATIO,
            intArrayOf(DISC_CENTER, DISC_CENTER, DISC_EDGE), floatArrayOf(0f, 0.37f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** Five bars like the wave icon; their height follows the voice, each with its own slight wobble. */
    private fun drawWave(canvas: Canvas, cx: Float, cy: Float, base: Float, voice: Float) {
        val barWidth = base * 0.13f
        val step = base * 0.24f
        fill.color = Color.WHITE
        fill.alpha = 255
        WAVE_SHAPE.forEachIndexed { i, shape ->
            val wobble = 0.75f + 0.25f * sin(phase * 2 + i * 1.3f)
            val half = base * (0.10f + 0.04f * breath + 0.52f * voice * shape * wobble)
            val x = cx + (i - 2) * step
            arc.set(x - barWidth / 2, cy - half, x + barWidth / 2, cy + half)
            canvas.drawRoundRect(arc, barWidth / 2, barWidth / 2, fill)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBreathing()
    }

    override fun onDetachedFromWindow() {
        spinner.cancel()
        breathing.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private val WAVE_SHAPE = floatArrayOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)

        fun iconRes(icon: BubbleIcon) = when (icon) {
            BubbleIcon.MIC -> R.drawable.ic_mic
            BubbleIcon.VOICE -> R.drawable.ic_voice
            BubbleIcon.HEARING -> R.drawable.ic_hearing
            BubbleIcon.MARK_COLOR -> R.drawable.ic_mark_color
            BubbleIcon.MARK_WHITE -> R.drawable.ic_mark_white
            BubbleIcon.WAVE -> R.drawable.ic_wave
            BubbleIcon.PULSE -> R.drawable.ic_pulse
        }

        /** Disc radius relative to the rim (441 / 512 in the design). */
        private const val DISC_RATIO = 441f / 512f
        val RIM_TOP = Color.rgb(0xB5, 0xFA, 0xC9)
        val RIM_BOTTOM = Color.rgb(0x26, 0xF8, 0xA7)
        val DISC_CENTER = Color.rgb(0x2E, 0x00, 0xFF)
        val DISC_EDGE = Color.rgb(0x21, 0x00, 0xAE)
        val REC_RIM_TOP = Color.rgb(0xFF, 0x00, 0x88)
        val REC_RIM_BOTTOM = Color.rgb(0xCA, 0x00, 0x5E)
        val REC_DISC = Color.rgb(0x28, 0x05, 0xB8)
        val COLOR_ERROR = Color.rgb(0xF5, 0x7C, 0x00)
        val COLOR_BADGE = Color.rgb(0x8E, 0x24, 0xAA)
    }
}
