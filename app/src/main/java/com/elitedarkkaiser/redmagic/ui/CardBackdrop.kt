package com.elitedarkkaiser.redmagic.ui

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import java.lang.ref.WeakReference

/** Records only the animated background, never the cards or their foreground content. */
object CardBackdrop {
    private var owner: GraphicsLayer? = null
    private var host = WeakReference<View>(null)
    private var node: RenderNode? = null
    private var appliedRadius = -1f
    private val location = IntArray(2)
    private val hostLocation = IntArray(2)
    private val scope = CanvasDrawScope()

    fun update(layer: GraphicsLayer, view: View, source: DrawScope) {
        if (Build.VERSION.SDK_INT < 31) return
        owner = layer
        host = WeakReference(view)
        record(layer, source)
    }

    @RequiresApi(31)
    private fun record(layer: GraphicsLayer, source: DrawScope) {
        if (CardStyle.blur <= 0f) {
            node = null
            appliedRadius = -1f
            return
        }
        val target = node ?: RenderNode("Card background").also { node = it }
        target.setPosition(0, 0, source.size.width.toInt(), source.size.height.toInt())
        val radius = 32f * source.density * CardStyle.blur
        if (radius != appliedRadius) {
            target.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            appliedRadius = radius
        }
        val canvas = target.beginRecording()
        try {
            scope.draw(
                source, source.layoutDirection,
                androidx.compose.ui.graphics.Canvas(canvas), source.size
            ) { drawLayer(layer) }
        } finally {
            target.endRecording()
        }
        BackdropFill.refreshAll()
    }

    fun clear(layer: GraphicsLayer) {
        if (owner !== layer) return
        owner = null
        host.clear()
        node = null
        appliedRadius = -1f
    }

    /** The drawable's local canvas is already clipped to its card shape by the caller. */
    fun draw(canvas: Canvas, view: View): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated ||
            CardStyle.blur <= 0f) return false
        val target = node ?: return false
        val root = host.get() ?: return false
        view.getLocationInWindow(location)
        root.getLocationInWindow(hostLocation)
        val save = canvas.save()
        canvas.translate(
            (hostLocation[0] - location[0]).toFloat(),
            (hostLocation[1] - location[1]).toFloat()
        )
        canvas.drawRenderNode(target)
        canvas.restoreToCount(save)
        return true
    }
}
