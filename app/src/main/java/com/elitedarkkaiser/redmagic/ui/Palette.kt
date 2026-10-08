package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.R.attr as MaterialAttr
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/**
 * The app's colour scheme, generated in process from one seed colour.
 *
 * This used to come off the Activity theme, which Material had recoloured at creation -- and that
 * is why changing the palette meant recreating the Activity: a theme is resolved once, on the way
 * in, and there is no supported way to take an applied overlay back off again. Toggling dynamic
 * colour therefore looked like the app restarting.
 *
 * Material's own generator is public ([SchemeTonalSpot] is the scheme a wallpaper palette is built
 * with, [MaterialDynamicColors] is every role derived off it), so the same palette can be computed
 * here instead. Changing the seed is then a variable and a redraw rather than a new Activity, which
 * is what lets the appearance settings apply in place.
 *
 * It also means a picked colour works below Android 12, where Material's own override does not
 * exist -- only the *dynamic* source needs the system, because only the system knows the wallpaper.
 */
object Palette {

    /**
     * The palette the app ships with: the primary from Theme.RedMagicRootControl, so "no dynamic
     * colour, nothing picked" looks exactly as the app always has.
     */
    const val DEFAULT_SEED = 0xFF3F5A7D.toInt()

    /**
     * How the seed becomes a palette.
     *
     * Material ships several of these and they differ far more than the name "style" suggests --
     * the seed is a hue, and each scheme decides for itself what chroma and tone to rebuild the
     * roles at. [TONAL_SPOT], the system's own, fixes primary at chroma 36, which is why a vivid
     * seed comes back muted. [VIBRANT] holds the saturation. [EXPRESSIVE] and [FRUIT_SALAD] rotate
     * the hue outright, so a yellow seed can come back pink -- surprising from a colour picker,
     * which is why neither is the default, but both are here because they are genuinely the nicest
     * of the set to look at.
     *
     * Fidelity and Content are deliberately absent: both drive primary toward the seed's own tone,
     * and a light seed (that yellow again) then lands on white, which is not a usable accent.
     */
    enum class Style {
        /** Tonal spot for the wallpaper and the built-in seed, vibrant for a hand-picked colour. */
        AUTO,
        TONAL_SPOT,
        VIBRANT,
        EXPRESSIVE,
        RAINBOW,
        FRUIT_SALAD,
        NEUTRAL,
        MONOCHROME;

        val label: String
            get() = when (this) {
                AUTO -> "Auto"
                TONAL_SPOT -> "Tonal"
                VIBRANT -> "Vibrant"
                EXPRESSIVE -> "Expressive"
                RAINBOW -> "Rainbow"
                FRUIT_SALAD -> "Fruit"
                NEUTRAL -> "Neutral"
                MONOCHROME -> "Mono"
            }

        companion object {
            fun of(name: String?): Style =
                entries.firstOrNull { it.name == name } ?: AUTO
        }
    }

    private var cached: DynamicScheme? = null
    private var cachedSeed = 0
    private var cachedDark: Boolean? = null
    private var cachedStyle: Style? = null

    private val roles = MaterialDynamicColors()

    /** Drops the computed scheme, so the next read rebuilds it from the current settings. */
    @Synchronized
    fun invalidate() {
        cached = null
        cachedDark = null
        cachedStyle = null
    }

    /**
     * What the whole palette is generated from.
     *
     * Dynamic colour seeds from the system's own accent, which is the wallpaper-derived colour
     * Android publishes as a resource -- read directly rather than through a theme overlay, so it
     * can be switched to and from without a new Activity. Below Android 12 there is no such
     * resource and the setting is disabled anyway.
     */
    fun seed(context: Context): Int {
        if (ThemePrefs.useDynamicColor(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val system = runCatching {
                ContextCompat.getColor(context, android.R.color.system_accent1_500)
            }.getOrNull()
            if (system != null && Color.alpha(system) != 0) return system
        }
        return ThemePrefs.customPrimaryColor(context) ?: DEFAULT_SEED
    }

    private fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * The style actually used, with [Style.AUTO] resolved.
     *
     * A hand-picked colour is a statement about what the accent should be, so it is held at its
     * own saturation; a wallpaper colour is not, so it goes through the same muting the system
     * applies. Dynamic colour wins the tie because the wallpaper is then the seed regardless of
     * what is saved in the picker.
     */
    fun style(context: Context): Style {
        val saved = Style.of(ThemePrefs.paletteStyle(context))
        if (saved != Style.AUTO) return saved
        val dynamic = ThemePrefs.useDynamicColor(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val picked = ThemePrefs.customPrimaryColor(context) != null
        return if (!dynamic && picked) Style.VIBRANT else Style.TONAL_SPOT
    }

    @Synchronized
    private fun scheme(context: Context): DynamicScheme {
        val seed = seed(context)
        val dark = isDark(context)
        val style = style(context)
        val current = cached
        if (current != null && cachedSeed == seed && cachedDark == dark && cachedStyle == style) {
            return current
        }

        val built = build(seed, dark, style)
        cached = built
        cachedSeed = seed
        cachedDark = dark
        cachedStyle = style
        return built
    }

    /**
     * Contrast 0.0 is Material's own default; the system contrast setting is not read here because
     * the theme path never read it either, and doing so would change how the app looks for anyone
     * who has nudged it.
     */
    fun build(seed: Int, dark: Boolean, style: Style): DynamicScheme {
        val hct = Hct.fromInt(seed)
        return when (style) {
            Style.VIBRANT -> SchemeVibrant(hct, dark, 0.0)
            Style.EXPRESSIVE -> SchemeExpressive(hct, dark, 0.0)
            Style.RAINBOW -> SchemeRainbow(hct, dark, 0.0)
            Style.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, 0.0)
            Style.NEUTRAL -> SchemeNeutral(hct, dark, 0.0)
            Style.MONOCHROME -> SchemeMonochrome(hct, dark, 0.0)
            Style.AUTO, Style.TONAL_SPOT -> SchemeTonalSpot(hct, dark, 0.0)
        }
    }

    /** The accent a seed would produce under a style, for previewing a choice before taking it. */
    fun previewPrimary(context: Context, seed: Int, style: Style): Int {
        val resolved = if (style == Style.AUTO) Style.VIBRANT else style
        return roles.primary().getArgb(build(seed, isDark(context), resolved))
    }

    /**
     * The colour for one Material theme attribute, or null for an attribute this does not model --
     * in which case [M3] falls back to reading the theme as it always did.
     */
    fun color(context: Context, attr: Int): Int? {
        val role = when (attr) {
            MaterialAttr.colorPrimary -> roles.primary()
            MaterialAttr.colorOnPrimary -> roles.onPrimary()
            MaterialAttr.colorPrimaryContainer -> roles.primaryContainer()
            MaterialAttr.colorOnPrimaryContainer -> roles.onPrimaryContainer()
            MaterialAttr.colorSecondary -> roles.secondary()
            MaterialAttr.colorOnSecondary -> roles.onSecondary()
            MaterialAttr.colorSecondaryContainer -> roles.secondaryContainer()
            MaterialAttr.colorOnSecondaryContainer -> roles.onSecondaryContainer()
            MaterialAttr.colorTertiary -> roles.tertiary()
            MaterialAttr.colorOnTertiary -> roles.onTertiary()
            MaterialAttr.colorTertiaryContainer -> roles.tertiaryContainer()
            MaterialAttr.colorOnTertiaryContainer -> roles.onTertiaryContainer()
            MaterialAttr.colorError -> roles.error()
            MaterialAttr.colorOnError -> roles.onError()
            MaterialAttr.colorErrorContainer -> roles.errorContainer()
            MaterialAttr.colorOnErrorContainer -> roles.onErrorContainer()
            MaterialAttr.colorSurface -> roles.surface()
            // surfaceBright is what the cards are tinted with while blur is on (ReSukiSU's
            // choice); unmapped, it fell back to the theme's untinted grey, and the more solid the
            // card transparency made the cards the greyer they looked.
            MaterialAttr.colorSurfaceBright -> roles.surfaceBright()
            MaterialAttr.colorSurfaceDim -> roles.surfaceDim()
            MaterialAttr.colorSurfaceContainerLowest -> roles.surfaceContainerLowest()
            MaterialAttr.colorSurfaceContainerLow -> roles.surfaceContainerLow()
            MaterialAttr.colorSurfaceContainer -> roles.surfaceContainer()
            MaterialAttr.colorSurfaceContainerHigh -> roles.surfaceContainerHigh()
            MaterialAttr.colorSurfaceContainerHighest -> roles.surfaceContainerHighest()
            MaterialAttr.colorSurfaceVariant -> roles.surfaceVariant()
            MaterialAttr.colorOnSurface -> roles.onSurface()
            MaterialAttr.colorOnSurfaceVariant -> roles.onSurfaceVariant()
            MaterialAttr.colorOutline -> roles.outline()
            MaterialAttr.colorOutlineVariant -> roles.outlineVariant()
            else -> null
        } ?: return null

        return runCatching { role.getArgb(scheme(context)) }.getOrNull()
    }
}
