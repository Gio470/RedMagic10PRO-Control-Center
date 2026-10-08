package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager

data class TriggerDisplayState(val width: Int, val height: Int, val rotation: Int) {
    val orientation: NativeTgkOrientation
        get() = if (width >= height) NativeTgkOrientation.LANDSCAPE else NativeTgkOrientation.PORTRAIT
    fun isValid() = width > 1 && height > 1
}

/** An overlay window context follows the display, independently of the control app's activity. */
object TriggerDisplay {
    private var overlayContext: Context? = null

    @Synchronized fun windowContext(context: Context): Context {
        overlayContext?.let { return it }
        val app = context.applicationContext
        val display = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val window = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && display != null) {
            app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else app
        overlayContext = window
        return window
    }

    fun read(context: Context): TriggerDisplayState {
        val window = windowContext(context)
        val manager = window.getSystemService(WindowManager::class.java) ?: return TriggerDisplayState(0, 0, 0)
        @Suppress("DEPRECATION") val rotation = manager.defaultDisplay.rotation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // currentWindowMetrics can describe the small editor/control window attached to
            // this context. Targets belong to the entire display, regardless of overlay size.
            val bounds = manager.maximumWindowMetrics.bounds
            return TriggerDisplayState(bounds.width(), bounds.height(), rotation)
        }
        @Suppress("DEPRECATION") val metrics = DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
        return TriggerDisplayState(metrics.widthPixels, metrics.heightPixels, rotation)
    }
}
