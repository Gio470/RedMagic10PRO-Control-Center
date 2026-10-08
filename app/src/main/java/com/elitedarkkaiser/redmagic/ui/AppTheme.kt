package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/**
 * Material You / Material 3 Expressive palette and shape tokens.
 *
 * The app draws its UI by hand (GradientDrawable backgrounds, no XML layouts or Compose), so
 * "theming" means sampling Material 3 color roles off the current Activity theme at runtime
 * via MaterialColors.getColor. On Android 12+, RedMagicApplication has already recolored that
 * theme with wallpaper-derived (Material You) tones; elsewhere the seed palette in
 * Theme.RedMagicRootControl is used. The hex literals below are only the last-resort fallback
 * if a theme attribute is somehow unresolved.
 */
object AppTheme {
    /** Set once from RedMagicApplication.onCreate(); used to resolve theme colors. */
    var appContext: Context? = null

    private fun color(attr: Int, fallbackHex: String): Int {
        val context = appContext ?: return Color.parseColor(fallbackHex)
        // Through the generated palette first, like M3 does. A direct theme read returns the
        // colours baked into the XML now that nothing recolours the theme any more.
        return Palette.color(context, attr)
            ?: MaterialColors.getColor(context, attr, Color.parseColor(fallbackHex))
    }

    val bgColor: Int get() = color(com.google.android.material.R.attr.colorSurface, "#0A0D12")
    val panelColor: Int get() = color(com.google.android.material.R.attr.colorSurfaceContainerLow, "#121720")
    val panelPressed: Int get() = color(com.google.android.material.R.attr.colorSurfaceContainerHigh, "#1A2230")
    val borderColor: Int get() = color(com.google.android.material.R.attr.colorOutlineVariant, "#232C3B")
    val accentColor: Int get() = color(com.google.android.material.R.attr.colorPrimary, "#8FA3BF")
    val chipOnColor: Int get() = color(com.google.android.material.R.attr.colorSurfaceContainerHighest, "#202B38")
    val chipActiveColor: Int get() = color(com.google.android.material.R.attr.colorSecondaryContainer, "#2B3A4F")
    val dangerColor: Int get() = color(com.google.android.material.R.attr.colorErrorContainer, "#5B2C33")
    val textPrimary: Int get() = color(com.google.android.material.R.attr.colorOnSurface, "#E8EEF7")
    val textSecondary: Int get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant, "#9AA8BA")
    val highlightBorder: Int get() = color(com.google.android.material.R.attr.colorOutline, "#7F8EA3")
    val appTypeface: Typeface? = Typeface.SANS_SERIF

    /** The M3 corner scale -- the same values as [M3.Shape], which is where new code should look. */
    object Shape {
        const val small = M3.Shape.small
        const val medium = M3.Shape.medium
        const val large = M3.Shape.large
        const val extraLarge = M3.Shape.extraLarge
        const val pill = M3.Shape.full
    }

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    fun roundedBg(fill: Int, stroke: Int, radiusPx: Float): GradientDrawable {
        return BackdropFill().apply {
            setColor(fill)
            cornerRadius = radiusPx
            setStroke(1, stroke)
        }
    }
}

