package com.elitedarkkaiser.redmagic.systemtheme

import android.content.Context

/**
 * What the system-wide theme is set to. Stored apart from the app's own appearance settings
 * ([com.elitedarkkaiser.redmagic.ui.ThemePrefs]) because the two govern different machines: one
 * recolours this app, the other recolours the phone.
 */
object SystemThemePrefs {

    private const val PREFS = "system_theme"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_SEED = "seed"
    private const val KEY_FOLLOW_WALLPAPER = "follow_wallpaper"
    private const val KEY_STYLE = "style"
    private const val KEY_ACCENT_SATURATION = "accent_saturation"
    private const val KEY_BACKGROUND_SATURATION = "background_saturation"
    private const val KEY_BACKGROUND_LIGHTNESS = "background_lightness"
    private const val KEY_ACCURATE_SHADES = "accurate_shades"
    private const val KEY_PITCH_BLACK = "pitch_black"
    private const val KEY_TINT_TEXT = "tint_text"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The master switch. Off means the app has written nothing to the system. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun followWallpaper(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOLLOW_WALLPAPER, false)

    fun setFollowWallpaper(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_WALLPAPER, value).apply()
    }

    fun seed(context: Context): Int =
        prefs(context).getInt(KEY_SEED, SystemThemeManager.BASIC_COLORS[5])

    fun setSeed(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SEED, value).apply()
    }

    fun style(context: Context): MonetStyle =
        MonetStyle.of(prefs(context).getString(KEY_STYLE, MonetStyle.TONAL_SPOT.name))

    fun setStyle(context: Context, value: MonetStyle) {
        prefs(context).edit().putString(KEY_STYLE, value.name).apply()
    }

    /** All three sliders are percentages where 100 means "as generated". */
    fun accentSaturation(context: Context): Int = prefs(context).getInt(KEY_ACCENT_SATURATION, 100)

    fun setAccentSaturation(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_ACCENT_SATURATION, value).apply()
    }

    fun backgroundSaturation(context: Context): Int =
        prefs(context).getInt(KEY_BACKGROUND_SATURATION, 100)

    fun setBackgroundSaturation(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_BACKGROUND_SATURATION, value).apply()
    }

    fun backgroundLightness(context: Context): Int =
        prefs(context).getInt(KEY_BACKGROUND_LIGHTNESS, 100)

    fun setBackgroundLightness(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_BACKGROUND_LIGHTNESS, value).apply()
    }

    /** Opt-in use of the exact Material tone mapping. */
    fun accurateShades(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACCURATE_SHADES, false)

    fun setAccurateShades(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACCURATE_SHADES, value).apply()
    }

    fun pitchBlack(context: Context): Boolean = prefs(context).getBoolean(KEY_PITCH_BLACK, false)

    fun setPitchBlack(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PITCH_BLACK, value).apply()
    }

    /** Opt-in tinting of text and foreground colors. */
    fun tintText(context: Context): Boolean = prefs(context).getBoolean(KEY_TINT_TEXT, false)

    fun setTintText(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TINT_TEXT, value).apply()
    }

    fun resetTheme(context: Context) {
        prefs(context).edit()
            .putInt(KEY_ACCENT_SATURATION, 100)
            .putInt(KEY_BACKGROUND_SATURATION, 100)
            .putInt(KEY_BACKGROUND_LIGHTNESS, 100)
            .putBoolean(KEY_ACCURATE_SHADES, false)
            .putBoolean(KEY_PITCH_BLACK, false)
            .putBoolean(KEY_TINT_TEXT, false)
            .apply()
    }
}
