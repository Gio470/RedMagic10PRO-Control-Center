package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout

/**
 * A many-coloured swatch, drawn the way this app has always drawn one: a circle quartered into
 * four colours, ringed in white when it is the selection.
 *
 * Quartered rather than split evenly across however many colours it is given — a rotation can hold
 * all sixteen, and sixteen slivers read as a smear. Four is what the shape is recognisable as, so
 * more than four are sampled evenly across the set to stand for it.
 */
class CycleSwatch(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val m3 = M3(context)
    private val bounds = RectF()
    private val clip = Path()

    private var colors: List<String> = emptyList()
    private var selected: Boolean = false

    init {
        val size = m3.dp(42)
        layoutParams = LinearLayout.LayoutParams(size, size)
        ringPaint.strokeWidth = m3.dp(3).toFloat()
    }

    fun setSwatch(colors: List<String>, selected: Boolean) {
        this.colors = colors
        this.selected = selected
        invalidate()
    }

    /** Up to four, spread across the set so the swatch stands for all of it rather than its start. */
    private fun quarters(): List<Int> {
        if (colors.isEmpty()) return emptyList()
        val picked = if (colors.size <= 4) colors
        else List(4) { colors[it * colors.size / 4] }
        return picked.map { runCatching { Color.parseColor(it) }.getOrDefault(Color.GRAY) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val quarters = quarters()
        if (quarters.isEmpty()) return

        val pad = ringPaint.strokeWidth
        bounds.set(pad, pad, width - pad, height - pad)

        val saveCount = canvas.save()
        clip.reset()
        clip.addOval(bounds, Path.Direction.CW)
        canvas.clipPath(clip)

        val midX = bounds.centerX()
        val midY = bounds.centerY()
        // Reading order — top left, top right, bottom left, bottom right — which is how this app's
        // preset bubbles have always been laid out, so a quartet drawn here lands where it used to.
        val cells = listOf(
            RectF(bounds.left, bounds.top, midX, midY),
            RectF(midX, bounds.top, bounds.right, midY),
            RectF(bounds.left, midY, midX, bounds.bottom),
            RectF(midX, midY, bounds.right, bounds.bottom),
        )
        cells.forEachIndexed { index, cell ->
            fillPaint.color = quarters[index % quarters.size]
            canvas.drawRect(cell, fillPaint)
        }
        canvas.restoreToCount(saveCount)

        // onSurface rather than white: the ring has to show against the page in either theme.
        ringPaint.color = if (selected) m3.onSurface else Color.TRANSPARENT
        canvas.drawOval(bounds, ringPaint)
    }
}
