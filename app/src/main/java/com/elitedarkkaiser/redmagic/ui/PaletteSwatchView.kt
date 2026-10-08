package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * ColorBlendr's palette chip, drawn to the same geometry as its own
 * `ColorPreviewCanvas` / `WallColorPreviewCanvas`: a 12dp rounded square, and inside it a circle
 * cut into a top half and two bottom quarters.
 *
 * The split is upstream's and it is not arbitrary. The top half is the accent, which is most of
 * what a theme looks like, so it gets most of the circle; the two quarters underneath are the
 * tertiary and secondary ramps, which are the parts you only notice when they clash. A row of
 * plain colour dots would show the *seed* instead, which is not the question anyone browsing a
 * palette has.
 *
 * Two variants, as upstream has two. The colour grid adds a fixed 10dp disc of the seed itself in
 * the middle and a tick when chosen; the styles list leaves the middle open, since there the seed
 * is a constant and only the arrangement changes.
 */
class PaletteSwatchView(
    context: Context,
    private val square: Int,
    private val halfCircle: Int,
    private val firstQuarter: Int,
    private val secondQuarter: Int,
    /** The seed, drawn as a disc in the middle. Null for the styles list. */
    private val centre: Int? = null,
    private val tick: Int = 0,
    /** Upstream insets the circle by 6dp in the colour grid and 10dp in the styles list. */
    private val insetDp: Float = 10f
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // Upstream's literal 4 pixels, not 4dp: the tick sits inside a disc whose radius is also
        // given in raw units, and scaling one without the other bends it out of shape.
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bounds = RectF()
    private val tickPath = Path()

    /** Drawn when this is the chosen one. */
    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val density = resources.displayMetrics.density
        val corner = 12f * density
        val inset = insetDp * density
        val cx = width / 2f
        val cy = height / 2f

        fill.color = square
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, corner, corner, fill)

        bounds.set(inset, inset, width - inset, height - inset)
        // 0 degrees is at three o'clock and sweeps clockwise, so 180/180 is the top half, 90/90
        // the bottom left and 0/90 the bottom right.
        fill.color = halfCircle
        canvas.drawArc(bounds, 180f, 180f, true, fill)
        fill.color = firstQuarter
        canvas.drawArc(bounds, 90f, 90f, true, fill)
        fill.color = secondQuarter
        canvas.drawArc(bounds, 0f, 90f, true, fill)

        val seed = centre ?: return
        val radius = 10f * density
        // The square colour first, so the seed disc sits on the tile's own background rather than
        // on whichever arc happens to be under it -- a translucent seed would otherwise pick up
        // the accent behind it.
        fill.color = square
        canvas.drawCircle(cx, cy, radius, fill)
        fill.color = seed
        canvas.drawCircle(cx, cy, radius, fill)

        if (checked) {
            tickPath.reset()
            tickPath.moveTo(cx - radius / 2f, cy)
            tickPath.lineTo(cx - radius / 6f, cy + radius / 3f)
            tickPath.lineTo(cx + radius / 2f, cy - radius / 3f)
            stroke.color = tick
            canvas.drawPath(tickPath, stroke)
        }
    }
}

/**
 * One ramp of the palette, drawn as a bar of thirteen tones with rounded ends -- the row that fills
 * ColorBlendr's Theme tab, where the sliders' effect is actually legible.
 */
class ToneRampView(context: Context, private val tones: IntArray) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        if (tones.isEmpty() || width == 0) return
        val radius = height / 2f

        canvas.save()
        clip.reset()
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        canvas.clipPath(clip)

        val step = width.toFloat() / tones.size
        tones.forEachIndexed { index, color ->
            fill.color = color
            // A half-pixel of overlap, or the seams between segments show as light lines on a dark
            // ramp at some densities.
            canvas.drawRect(index * step - 0.5f, 0f, (index + 1) * step + 0.5f, height.toFloat(), fill)
        }
        canvas.restore()
    }
}
