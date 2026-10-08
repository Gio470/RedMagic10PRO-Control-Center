package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf

/**
 * How the app's cards and rows are filled, after ReSukiSU's theme settings.
 *
 * Two independent settings:
 * - Card blur ([blur]): blurs the animated background behind a tinted card surface.
 *   Zero keeps the original solid surface. Text and controls are never blurred.
 * - Connect cards ([connected]): joins each group of rows into one continuous card -- no gap and
 *   square corners where rows meet (see M3.connectRows) -- instead of a stack of separate pieces.
 *   It changes only the shape of the cards; it does not blur or otherwise touch the background.
 *
 * Cached in fields: every card reads these on every draw, and that should not be a
 * SharedPreferences lookup.
 */
object CardStyle {

    private const val PREFS = "appearance"
    private const val KEY_ALPHA = "card_alpha"
    private const val KEY_BLUR = "card_blur"
    private const val KEY_CONNECTED = "card_connected"

    /** Normalized blur strength; 1 is a 32dp blur radius. */
    var blur by mutableFloatStateOf(0.25f)
        private set

    val blurSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 31

    // Keep existing shadow callers working. Frosted cards use a stable tint, not faded content.
    val alpha: Float get() = if (blurSupported && blur > 0f) 0.35f else 1f

    /** Whether rows of one group are drawn as a single connected card. */
    @Volatile var connected: Boolean = true
        private set

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the saved values into the cache. Called once as the Activity starts. */
    fun load(context: Context) {
        val p = prefs(context)
        val defaultBlur = if (p.contains(KEY_ALPHA)) 1f - p.getFloat(KEY_ALPHA, 1f) else 0.25f
        blur = p.getFloat(KEY_BLUR, defaultBlur).coerceIn(0f, 1f)
        connected = p.getBoolean(KEY_CONNECTED, true)
    }

    fun connectedEnabled(context: Context): Boolean {
        load(context)
        return connected
    }

    fun setBlur(context: Context, value: Float) {
        blur = value.coerceIn(0f, 1f)
        prefs(context).edit().putFloat(KEY_BLUR, blur).apply()
        runCatching { BackdropFill.refreshAll() }
    }

    /** Joins the cards into one, or splits them back. Only the card shapes change. */
    fun setConnected(context: Context, value: Boolean) {
        connected = value
        prefs(context).edit().putBoolean(KEY_CONNECTED, value).apply()
        runCatching { BackdropFill.refreshAll() }
    }

    /** [color] with the card transparency applied on top of whatever alpha it already has. */
    fun tint(color: Int, minimumAlpha: Float = 0f): Int =
        if (alpha >= 1f) {
            color
        } else {
            ColorUtils.setAlphaComponent(color, (android.graphics.Color.alpha(color) * alpha.coerceAtLeast(minimumAlpha)).toInt())
        }
}
