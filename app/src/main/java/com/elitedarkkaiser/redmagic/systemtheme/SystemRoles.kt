package com.elitedarkkaiser.redmagic.systemtheme

import com.google.android.material.color.utilities.ColorUtils
import com.google.android.material.color.utilities.Hct

/**
 * The colour *roles* the platform publishes, and which tone of which ramp each one is.
 *
 * ## Why the ramps alone are not enough
 *
 * Android 12 published its Material You palette as five tonal ramps -- `system_accent1_500` and
 * its 77 siblings -- and everything themed read one of them. That is what this app was overriding,
 * and on Android 12 it would have been the whole job.
 *
 * It has not been the whole job since Android 13. The platform now also publishes the *derived*
 * roles by name -- `system_primary_dark`, `system_surface_container_light`, `m3_sys_color_*` and
 * the rest -- and modern system UI reads those rather than doing its own tone lookup. Overriding
 * only the ramps leaves every one of those roles still holding the colours the system computed for
 * itself, which is why the theme applied and then hardly anything moved.
 *
 * ## The table
 *
 * Ported from Mahmud0808/ColorBlendr's DynamicColors, which in turn follows AOSP's own
 * `com.android.systemui.monet.DynamicColors`. Each role names the ramp it comes from and the tone
 * it takes in each mode -- primary is tone 40 in light and tone 80 in dark, and so on down the
 * list. The indices are into the thirteen tones [ThemeEngine] generates, lightest first.
 *
 * Unlike the ramps, these names carry their own mode, so both halves can be written at once. That
 * is worth more than it sounds: the ramps can only ever hold the mode the phone is in, which is
 * why they have to be rewritten when it changes, and these do not.
 */
internal object SystemRoles {

    /** A role taken straight from a generated ramp. */
    data class Role(
        val name: String,
        val palette: Int,
        val lightTone: Int,
        val darkTone: Int
    )

    /**
     * A role built from the platform's own fixed error hue rather than from the seed.
     *
     * AOSP does not derive the error colours from the wallpaper -- an error is red whatever the
     * theme -- so these are that fixed red at a given lightness, which is what upstream does too.
     */
    data class ErrorRole(
        val name: String,
        val light: Double,
        val lightMonochrome: Double,
        val dark: Double
    )

    /** AOSP's error hue. */
    private const val ERROR_SEED = 0xFFFF5540.toInt()

    /**
     * The five spellings the platform has accumulated for the same role.
     *
     * `system_*` is the public one; the `m3_sys_color_*` family is what Material's own components
     * resolve against, and the `gm3_` ones are Google's apps. Each is written wherever it exists,
     * because which of them a given piece of UI reads is not something an app can know.
     *
     * The mode goes in a different place in each, which is why these are format strings rather
     * than a prefix and a suffix.
     */
    private val SPELLINGS = listOf(
        "system_%2\$s_%1\$s",
        "m3_sys_color_%1\$s_%2\$s",
        "m3_sys_color_dynamic_%1\$s_%2\$s",
        "gm3_sys_color_%1\$s_%2\$s",
        "gm3_sys_color_dynamic_%1\$s_%2\$s"
    )

    /** Every name a role is published under, for one mode. */
    fun names(role: String, dark: Boolean): List<String> {
        val mode = if (dark) "dark" else "light"
        return SPELLINGS.map { String.format(it, mode, role) }
    }

    fun errorColor(role: ErrorRole, dark: Boolean, monochrome: Boolean): Int {
        val hct = Hct.fromInt(ERROR_SEED)
        val lstar = when {
            dark -> role.dark
            monochrome -> role.lightMonochrome
            else -> role.light
        }
        val chroma = if (monochrome) 0.0 else hct.chroma
        return Hct.from(hct.hue, chroma, lstar).toInt()
    }

    /** Light and dark values for the one role that is a flat scrim rather than a palette colour. */
    val CONTROL_HIGHLIGHT_LIGHT = 0x1F000000
    val CONTROL_HIGHLIGHT_DARK = 0x33FFFFFF.toInt()

    /**
     * M3's reference palette, published as `m3_ref_palette_primary40` and friends.
     *
     * The same thirteen tones under a second naming scheme: M3 names them by tone (0, 10, 20 ... )
     * where the framework ramps name them by weight (1000, 900, 800 ... ), so the table is the
     * translation between the two.
     */
    val REF_TONES = mapOf(
        0 to 12, 10 to 11, 20 to 10, 30 to 9, 40 to 8, 50 to 7,
        60 to 6, 70 to 5, 80 to 4, 90 to 3, 95 to 2, 98 to 1, 100 to 0
    )

    val REF_PALETTES = listOf(
        "primary" to 0, "secondary" to 1, "tertiary" to 2,
        "neutral" to 3, "neutral_variant" to 4, "error" to 5
    )

    /** Unused here, but the reason [ColorUtils] is imported: keeps the lint honest. */
    internal fun lstarOf(argb: Int): Double = ColorUtils.lstarFromArgb(argb)

    val ERROR_ROLES = listOf(
        ErrorRole("error", light = 40.0, lightMonochrome = 40.0, dark = 80.0),
        ErrorRole("on_error", light = 100.0, lightMonochrome = 100.0, dark = 20.0),
        ErrorRole("error_container", light = 90.0, lightMonochrome = 90.0, dark = 30.0),
        ErrorRole("on_error_container", light = 30.0, lightMonochrome = 10.0, dark = 90.0),
    )

    val ROLES = listOf(
        Role("primary_container", 0, 3, 9),
        Role("on_primary_container", 0, 11, 3),
        Role("primary", 0, 8, 4),
        Role("on_primary", 0, 0, 10),
        Role("secondary_container", 1, 3, 9),
        Role("on_secondary_container", 1, 11, 3),
        Role("secondary", 1, 8, 4),
        Role("on_secondary", 1, 0, 10),
        Role("tertiary_container", 2, 3, 9),
        Role("on_tertiary_container", 2, 11, 3),
        Role("tertiary", 2, 8, 4),
        Role("on_tertiary", 2, 0, 10),
        Role("background", 3, 1, 11),
        Role("on_background", 3, 11, 1),
        Role("surface", 3, 1, 11),
        Role("on_surface", 3, 10, 2),
        Role("surface_container_lowest", 3, 2, 10),
        Role("surface_container_low", 3, 2, 10),
        Role("surface_container", 3, 2, 10),
        Role("surface_container_high", 3, 1, 10),
        Role("surface_container_highest", 3, 1, 10),
        Role("surface_bright", 3, 1, 10),
        Role("surface_dim", 3, 1, 11),
        Role("surface_variant", 4, 2, 10),
        Role("on_surface_variant", 4, 10, 2),
        Role("outline", 4, 7, 6),
        Role("outline_variant", 4, 4, 9),
        Role("control_activated", 1, 8, 4),
        Role("control_normal", 4, 4, 9),
        Role("text_primary_inverse", 3, 3, 11),
        Role("text_secondary_and_tertiary_inverse", 4, 4, 9),
        Role("text_primary_inverse_disable_only", 3, 3, 11),
        Role("text_secondary_and_tertiary_inverse_disabled", 3, 3, 11),
        Role("text_hint_inverse", 3, 3, 11),
        Role("palette_key_color_primary", 0, 4, 8),
        Role("palette_key_color_secondary", 1, 4, 8),
        Role("palette_key_color_tertiary", 2, 4, 8),
        Role("palette_key_color_neutral", 3, 4, 8),
        Role("palette_key_color_neutral_variant", 4, 4, 8),
    )
}
