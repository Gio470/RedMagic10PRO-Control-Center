package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.widget.LinearLayout

/**
 * The last minute or so of a reading, as a line with the area under it filled.
 *
 * A single percentage answers "now" and nothing else, which on a gaming phone is the less
 * interesting half of the question: a processor pinned at 100% for a minute and one that just
 * spiked read identically, and thermal throttling is a shape over time rather than a number. The
 * samples are whatever the poll happens to deliver, so the line is a history of readings rather
 * than of seconds -- changing the interval in Settings changes how much time it covers.
 */
class Sparkline(
    context: Context,
    private val accent: (M3) -> Int = { it.primary }
) : View(context) {

    private val m3 = M3(context)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePath = Path()
    private val fillPath = Path()

    /** Newest last. Values are 0..1. */
    private val samples = ArrayDeque<Float>()

    /** Rebuilt whenever the height or the palette changes, not per draw. */
    private var gradient: LinearGradient? = null
    private var gradientHeight = 0
    private var gradientColor = 0

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            m3.dp(44)
        ).apply { topMargin = m3.dp(M3.Space.sm) }
    }

    fun push(value: Float) {
        samples.addLast(value.coerceIn(0f, 1f))
        while (samples.size > CAPACITY) samples.removeFirst()
        invalidate()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // One point cannot be a line, and drawing it as a dot in the corner says less than nothing.
        if (samples.size < 2) return

        val colour = accent(m3)
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = m3.dpF(2f)
        val usable = h - inset * 2f

        // Pinned to the right: the newest sample sits at the leading edge and the line grows
        // leftwards into the empty space until there is a full window of history.
        val step = w / (CAPACITY - 1).toFloat()
        val startX = w - step * (samples.size - 1)

        linePath.reset()
        fillPath.reset()

        samples.forEachIndexed { index, value ->
            val x = startX + step * index
            val y = inset + usable * (1f - value)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        if (gradient == null || gradientHeight != height || gradientColor != colour) {
            gradient = LinearGradient(
                0f, 0f, 0f, h,
                (colour and 0x00FFFFFF) or (0x66 shl 24),
                colour and 0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
            gradientHeight = height
            gradientColor = colour
        }
        fill.shader = gradient
        canvas.drawPath(fillPath, fill)

        line.color = colour
        line.strokeWidth = m3.dpF(2f)
        canvas.drawPath(linePath, line)
    }

    private companion object {
        /** Roughly a minute at the two-second default, and a screenful of points at any interval. */
        const val CAPACITY = 40
    }
}
