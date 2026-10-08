package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout

/**
 * A rounded fill bar for a 0–100 reading, drawn rather than composed: this sits in the plain-View
 * Home tab beside a stack of TextViews, where a ComposeView per bar would be three extra
 * compositions for something that is two rounded rectangles.
 *
 * Colours come from [M3], so it follows Material You like everything else, and are read on every
 * draw so a Pure Black toggle -- which rebuilds the tab -- lands without a stale palette.
 */
class UsageBar(context: Context, private val accent: (M3) -> Int = { it.primary }) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val m3 = M3(context)

    private var percent: Int = 0

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            m3.dp(8)
        ).apply { topMargin = m3.dp(8) }
    }

    fun setPercent(value: Int) {
        val clamped = value.coerceIn(0, 100)
        if (clamped == percent) return
        percent = clamped
        invalidate()
    }

    /**
     * M3's determinate linear progress indicator: the active indicator in the accent, then a 4dp
     * gap, then the track in secondaryContainer, closed off by the stop indicator -- a 4dp dot in
     * the accent that marks where full is, so the end of the track reads even where its own colour
     * is close to the card's.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        val radius = h / 2f
        val gap = m3.dpF(TRACK_GAP_DP)
        val accentColor = accent(m3)

        // Never narrower than the bar is tall: below that the rounded caps eat the fill entirely
        // and a small-but-real reading draws as nothing at all.
        // Floor first, then cap, rather than coerceIn(h, w): that throws whenever the bar is
        // narrower than it is tall, which a squeezed or not-yet-laid-out bar can be.
        val filled = if (percent <= 0) 0f else (w * percent / 100f).coerceAtLeast(h).coerceAtMost(w)
        val trackStart = if (filled > 0f) filled + gap else 0f

        if (trackStart < w) {
            paint.color = m3.secondaryContainer
            bounds.set(trackStart, 0f, w, h)
            canvas.drawRoundRect(bounds, radius, radius, paint)

            // The stop indicator, centred in the track's rounded end.
            val stop = m3.dpF(STOP_DP) / 2f
            if (w - trackStart >= h) {
                paint.color = accentColor
                canvas.drawCircle(w - radius, radius, stop, paint)
            }
        }

        if (filled > 0f) {
            paint.color = accentColor
            bounds.set(0f, 0f, filled, h)
            canvas.drawRoundRect(bounds, radius, radius, paint)
        }
    }

    private companion object {
        /** The space between the active indicator and the track, and the stop dot's size. */
        const val TRACK_GAP_DP = 4f
        const val STOP_DP = 4f
    }
}
