package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * One glyph of the app's icon font as a [Drawable], for the places a View takes an icon as a
 * drawable rather than as a child -- a text field's leading icon, say -- so those draw from the
 * same icon set as every chip and mark in the app.
 *
 * Centred in a [boxDp] square, which is the icon's layout size (M3's is 24dp); the glyph itself is
 * set at [sizeSp] inside it.
 */
class GlyphDrawable(
    context: Context,
    private val glyph: String,
    color: Int,
    boxDp: Int,
    sizeSp: Float
) : Drawable() {

    private val box = (boxDp * context.resources.displayMetrics.density).roundToInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Icons.typeface(context)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics
        )
        this.color = color
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val metrics = paint.fontMetrics
        val baseline = bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph, bounds.exactCenterX(), baseline, paint)
    }

    override fun getIntrinsicWidth(): Int = box

    override fun getIntrinsicHeight(): Int = box

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
