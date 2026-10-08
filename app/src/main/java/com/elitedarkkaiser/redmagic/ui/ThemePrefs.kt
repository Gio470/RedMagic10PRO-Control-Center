package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.Configuration

/**
 * Appearance settings, modelled on NPatch's Appearance & Theme card.
 *
 * Read on the way into the Activity ([applyNightMode] runs from attachBaseContext) and when the
 * glass bar is composed, so changing one takes effect on the next Activity — every setter's caller
 * recreates.
 */
object ThemePrefs {
    private const val PREFS = "appearance"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_GLASS_BLUR = "glass_blur"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val KEY_BACKGROUND_ANIMATION = "background_animation"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary_color"
    private const val KEY_DOCKED_BAR = "docked_bar"
    private const val KEY_PALETTE_STYLE = "palette_style"

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(context: Context): Int = prefs(context).getInt(KEY_THEME_MODE, MODE_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    /** Fresh installs follow the wallpaper palette unless the user chooses otherwise. */
    fun useDynamicColor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setUseDynamicColor(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
    }

    /**
     * A seed colour to build the whole palette from, or null for the one baked into
     * Theme.RedMagicRootControl.
     *
     * Only consulted when dynamic colour is off: the two are the same job done from different
     * sources -- the wallpaper, or a colour picked by hand -- and Material takes one seed, not two.
     * Stored as an opaque ARGB int; 0 is the "unset" marker rather than a real colour, since a
     * fully transparent seed is not a thing anyone can pick.
     */
    fun customPrimaryColor(context: Context): Int? =
        prefs(context).getInt(KEY_CUSTOM_PRIMARY, 0).takeIf { it != 0 }

    fun setCustomPrimaryColor(context: Context, color: Int?) {
        prefs(context).edit().putInt(KEY_CUSTOM_PRIMARY, color ?: 0).apply()
    }

    /**
     * How the palette is derived from the seed, as a [Palette.Style] name.
     *
     * The default is AUTO, which is the answer to "I picked a bright yellow and it did not
     * apply": Material's tonal-spot scheme throws the seed's chroma away and rebuilds the accent
     * at a fixed, fairly muted saturation, so a vivid yellow comes back as #D4C871. That is right
     * for a wallpaper colour -- it is what the system itself does, and a wallpaper is a whole
     * image rather than a chosen shade -- and wrong for a colour someone picked on purpose. AUTO
     * draws that line: tonal spot for the wallpaper and the built-in seed, vibrant for a hand-
     * picked one, which holds the chroma (#DBC900 for the same yellow). Any style can still be
     * chosen outright.
     */
    fun paletteStyle(context: Context): String =
        prefs(context).getString(KEY_PALETTE_STYLE, Palette.Style.AUTO.name)
            ?: Palette.Style.AUTO.name

    fun setPaletteStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_PALETTE_STYLE, style).apply()
    }

    /** Fresh installs use the full-width navigation bar with Settings as a tab. */
    fun useDockedBar(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOCKED_BAR, true)

    fun setUseDockedBar(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOCKED_BAR, value).apply()
    }

    fun useGlassBlur(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLASS_BLUR, true)

    fun setUseGlassBlur(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLASS_BLUR, value).apply()
    }

    /** True black surfaces in dark mode, in place of the theme's tonal dark surface ladder. */
    fun usePureBlack(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PURE_BLACK, false)

    fun setUsePureBlack(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PURE_BLACK, value).apply()
    }

    /** Stores a [com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType] by name. */
    fun backgroundAnimation(context: Context): String =
        prefs(context).getString(KEY_BACKGROUND_ANIMATION, "CIRCLES") ?: "CIRCLES"

    fun setBackgroundAnimation(context: Context, name: String) {
        prefs(context).edit().putString(KEY_BACKGROUND_ANIMATION, name).apply()
    }

    /**
     * Forces the light/dark half of the theme for an Activity's base context.
     *
     * AppCompatDelegate.setDefaultNightMode is the usual route, but it only governs AppCompat
     * activities and this one is a plain ComponentActivity. Overriding the night bits of the
     * configuration works for any Activity and needs no change of base class.
     */
    fun applyNightMode(base: Context): Context {
        val mode = themeMode(base)
        if (mode == MODE_SYSTEM) return base

        val night = if (mode == MODE_DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        return base.createConfigurationContext(config)
    }
}
