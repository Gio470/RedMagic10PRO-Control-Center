package com.elitedarkkaiser.redmagic.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * A reading drawn as an open arc with its value in the middle -- the shape a dial takes, for the
 * two numbers on this phone that are actually a dial: how hot it is and how hard the fan is working.
 *
 * They were small text in small cards, which is the right treatment for a number you read and a
 * wrong one for a number you glance at. An arc says "where in its range" before the digits are read
 * at all, which is the whole question being asked of a temperature.
 *
 * Drawn rather than composed, for the same reason [UsageBar] is: this sits in a plain-View tab, and
 * a ComposeView per gauge is a composition apiece for what is three arcs and two strings.
 */
class ArcGauge(context: Context) : View(context) {

    private val m3 = M3(context)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    /** Where the value sits in its range, 0..1. Animated, so a reading slides rather than jumps. */
    private var fraction = 0f
    private var animator: ValueAnimator? = null

    private var value: String = "--"
    private var caption: String = ""

    /** Read on every draw, so a Pure Black or Material You change lands without a stale palette. */
    private var accent: (M3) -> Int = { it.primary }

    /**
     * @param fraction where the reading sits in its range; the arc's length.
     * @param value the figure itself, drawn large in the middle.
     * @param caption what it is, drawn small underneath.
     */
    fun set(fraction: Float, value: String, caption: String, accent: (M3) -> Int = this.accent) {
        val target = fraction.coerceIn(0f, 1f)
        this.value = value
        this.caption = caption
        this.accent = accent

        if (target == this.fraction) {
            invalidate()
            return
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(this.fraction, target).apply {
            duration = SWEEP_MS
            interpolator = EMPHASIZED
            addUpdateListener {
                this@ArcGauge.fraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // The Expressive thick indicator: 8dp, twice the standard 4dp, for a dial this size.
        val stroke = m3.dpF(8f)
        arc.strokeWidth = stroke

        // Square, centred: the arc is a circle whichever way the card it sits in is stretched.
        val size = minOf(width, height).toFloat() - stroke
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        bounds.set(left, top, left + size, top + size)

        // M3's determinate circular indicator: the active arc in the accent, the track in
        // secondaryContainer, and a 4dp gap between them. The round caps each reach half a stroke
        // past the end of their arc, so the angle kept clear has to cover the stroke as well as
        // the gap for the gap to come out at 4dp.
        val radius = size / 2f
        val gapDegrees = Math.toDegrees(((m3.dpF(TRACK_GAP_DP) + stroke) / radius).toDouble()).toFloat()
        // A floor of a couple of degrees, or a small-but-real reading draws as nothing: the round
        // cap alone is wider than the arc a 1% value would otherwise get.
        val active = if (fraction > 0f) (SWEEP_DEGREES * fraction).coerceAtLeast(2f) else 0f
        val trackStart = if (active > 0f) active + gapDegrees else 0f
        if (trackStart < SWEEP_DEGREES) {
            arc.color = m3.secondaryContainer
            canvas.drawArc(bounds, START_DEGREES + trackStart, SWEEP_DEGREES - trackStart, false, arc)
        }

        if (active > 0f) {
            arc.color = accent(m3)
            canvas.drawArc(bounds, START_DEGREES, active.coerceAtMost(SWEEP_DEGREES), false, arc)
        }

        val centreX = width / 2f
        val centreY = height / 2f

        // headlineMedium, emphasized (500): the reading is the one thing the dial is for.
        text.typeface = VALUE_FACE
        text.textAlign = Paint.Align.CENTER
        text.color = m3.onSurface
        text.textSize = m3.dpF(VALUE_SP)
        // Centred on the glyphs rather than the baseline: text sits low in its line box, and a
        // value centred by baseline reads as sunk in a gauge whose arc is geometrically centred.
        val metrics = text.fontMetrics
        canvas.drawText(value, centreX, centreY - (metrics.ascent + metrics.descent) / 2f, text)

        if (caption.isNotEmpty()) {
            text.typeface = CAPTION_FACE
            text.color = m3.onSurfaceVariant
            text.textSize = m3.dpF(CAPTION_SP)
            canvas.drawText(caption, centreX, centreY + m3.dpF(CAPTION_OFFSET), text)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Open at the bottom, like a dial: 270 degrees of sweep starting from lower-left. */
        const val START_DEGREES = 135f
        const val SWEEP_DEGREES = 270f

        /** headlineMedium and bodySmall sizes -- in dp, as the dial they sit in is a fixed size. */
        const val VALUE_SP = 28f
        const val CAPTION_SP = 12f
        const val CAPTION_OFFSET = 24f

        const val TRACK_GAP_DP = 4f

        /** Built once rather than on every frame the dial draws. */
        val VALUE_FACE: Typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
        val CAPTION_FACE: Typeface = Typeface.create(Typeface.SANS_SERIF, 400, false)

        /** A value that stays on screen while it changes: emphasized, over long2 (500ms). */
        const val SWEEP_MS = M3.Motion.long2
        val EMPHASIZED get() = M3.Motion.emphasized
    }
}
