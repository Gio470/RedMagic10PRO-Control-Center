package com.elitedarkkaiser.redmagic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * An HSV colour wheel: hue around it, saturation out from the middle, with a thumb you drag.
 *
 * Upstream (Gio470/Bus-tracker) uses skydoves' Compose colour picker for this. Pulling a Compose
 * picker into a tab drawn entirely in Views would have meant a ComposeView, a theme bridge and a
 * dependency for one dialog, in a dialog whose every other control is a plain View -- so the wheel
 * is drawn here instead, the same way [ArcGauge] and [Sparkline] are.
 *
 * Two shaders composed: a sweep of the hues around the circle, multiplied by a radial white-to-
 * transparent gradient that washes the middle out to grey. Value (brightness) is not on the wheel;
 * it is a slider beside it, because a wheel that dims as well would have no way to show which of
 * the two a dull patch means.
 */
class ColorWheelView(context: Context) : View(context) {

    private val wheel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val m3 = M3(context)

    /** Hue 0..360, saturation 0..1, value 0..1. */
    private var hue = 0f
    private var saturation = 0f
    private var value = 1f

    private var shaderSize = 0

    /** Fired while dragging as well as on release, so a preview can follow the thumb. */
    var onColorChanged: ((Int) -> Unit)? = null

    val color: Int get() = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    /** Moves the thumb to [color] without reporting it back as a change the user made. */
    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        invalidate()
    }

    /** Brightness lives on a slider outside the wheel; the wheel only dims to match. */
    fun setValue(newValue: Float) {
        value = newValue.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Square, to whatever width it is given: an oval wheel would make the hue at the top and
        // the hue at the side sit at different distances for the same saturation.
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = minOf(width, height) / 2f - m3.dpF(2f)
        if (radius <= 0f) return
        val centreX = width / 2f
        val centreY = height / 2f

        if (shaderSize != width) {
            val hues = SweepGradient(centreX, centreY, HUE_STOPS, null)
            val whiteOut = RadialGradient(
                centreX, centreY, radius,
                Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            wheel.shader = ComposeShader(hues, whiteOut, PorterDuff.Mode.SRC_OVER)
            shaderSize = width
        }
        canvas.drawCircle(centreX, centreY, radius, wheel)

        // Brightness, as a black wash over the whole wheel rather than a separate rendering: the
        // slider beside it is what sets this, and the wheel following makes the pair read as one
        // control instead of two that happen to be near each other.
        if (value < 1f) {
            dim.color = Color.argb(((1f - value) * 255).toInt(), 0, 0, 0)
            canvas.drawCircle(centreX, centreY, radius, dim)
        }

        val angle = Math.toRadians(hue.toDouble())
        val thumbX = centreX + (cos(angle) * saturation * radius).toFloat()
        val thumbY = centreY + (sin(angle) * saturation * radius).toFloat()

        thumbFill.color = color
        canvas.drawCircle(thumbX, thumbY, m3.dpF(12f), thumbFill)
        thumbRing.strokeWidth = m3.dpF(3f)
        canvas.drawCircle(thumbX, thumbY, m3.dpF(12f), thumbRing)
    }

    // The wheel is the control; a click listener on it would be meaningless, and performClick is
    // called so accessibility services still see the interaction.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // The sheet this sits in scrolls; without this a drag across the wheel is taken as
                // a scroll of the sheet after the first few pixels.
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFrom(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateFrom(event.x, event.y)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFrom(x: Float, y: Float) {
        val radius = minOf(width, height) / 2f - m3.dpF(2f)
        if (radius <= 0f) return
        val dx = x - width / 2f
        val dy = y - height / 2f

        hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()) + 360f) % 360f
        // Clamped rather than ignored past the edge: a drag that runs off the wheel should pin to
        // full saturation, not stop responding.
        saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)

        invalidate()
        onColorChanged?.invoke(color)
    }

    private companion object {
        /** Red round to red, so the seam is a colour rather than a jump. */
        val HUE_STOPS = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
    }
}
