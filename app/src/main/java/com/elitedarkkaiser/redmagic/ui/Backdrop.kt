package com.elitedarkkaiser.redmagic.ui

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Collections
import java.util.WeakHashMap

/** A tinted card surface over the shared blurred animated background. */
class BackdropFill(private val minimumTintAlpha: Float = 0f) : GradientDrawable() {

    private var base = 0
    private var applied = 0
    private val clip = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val view = owningView()
        val save = canvas.save()
        rect.set(bounds)
        clip.reset()
        val radii = cornerRadii
        if (radii != null) clip.addRoundRect(rect, radii, Path.Direction.CW)
        else clip.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clip)
        val blurred = view != null && CardBackdrop.draw(canvas, view)
        // Solid fallback for old Android, software canvases, or the first unrecorded frame.
        val fill = if (blurred) CardStyle.tint(base, minimumTintAlpha) else base
        if (fill != applied) {
            applied = fill
            super.setColor(fill)
        }
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    /** RippleDrawable, InsetDrawable and LayerDrawable forward callbacks to their owner. */
    internal fun owningView(): View? {
        var owner = callback
        repeat(16) {
            when (val current = owner) {
                is View -> return current
                is Drawable -> owner = current.callback
                else -> return null
            }
        }
        return null
    }

    init {
        live.add(this)
    }

    override fun setColor(argb: Int) {
        base = argb
        applied = argb
        super.setColor(applied)
    }

    /** Redraws with the current blur strength. */
    fun refresh() {
        invalidateSelf()
    }

    companion object {
        private val live: MutableSet<BackdropFill> =
            Collections.newSetFromMap(WeakHashMap<BackdropFill, Boolean>())

        /** Applies a changed [CardStyle] to every card already on screen. */
        fun refreshAll() {
            live.toList().forEach { runCatching { it.refresh() } }
        }
    }
}

