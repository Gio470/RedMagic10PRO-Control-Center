package com.elitedarkkaiser.redmagic.systemtheme

import android.graphics.Color
import com.google.android.material.color.utilities.ColorUtils
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.TonalPalette

/**
 * The Monet styles Android's own theme picker knows by name.
 *
 * The names are the strings the platform stores in
 * `Settings.Secure.theme_customization_overlay_packages` under
 * `android.theme.customization.theme_style`, which is why they are spelled this way rather than
 * prettily: [SystemThemeManager] writes them there verbatim.
 *
 * [FIDELITY] and [CONTENT] are not in that list -- the platform picker has no such styles -- so
 * they only ever reach the system through the generated overlay, never through the settings key.
 */
enum class MonetStyle(val label: String, val systemName: String?, val description: String) {
    SPRITZ(
        "Neutral", "SPRITZ",
        "Lively and refreshing color palette inspired by the effervescence of spritz beverages."
    ),
    MONOCHROME(
        "Monochrome", "MONOCHROMATIC",
        "Harmonious scheme using variations of a single hue for a sleek and minimalist aesthetic."
    ),
    TONAL_SPOT(
        "Tonal Spot", "TONAL_SPOT",
        "Palette featuring subtle variations in tone, creating a sophisticated and nuanced visual impact."
    ),
    VIBRANT(
        "Vibrant", "VIBRANT",
        "Energetic and dynamic color combination that exudes liveliness and intensity."
    ),
    RAINBOW(
        "Rainbow", "RAINBOW",
        "Diverse spectrum of colors, symbolizing diversity, playfulness, and a wide range of possibilities."
    ),
    EXPRESSIVE(
        "Expressive", "EXPRESSIVE",
        "Bold and expressive mix of colors that conveys emotion and creativity."
    ),
    FIDELITY(
        "Fidelity", null,
        "Faithful representation of colors, ensuring accuracy and authenticity in visual representation."
    ),
    CONTENT(
        "Content", null,
        "Balanced and calming palette, promoting a sense of tranquility and contentment."
    ),
    FRUIT_SALAD(
        "Fruit Salad", "FRUIT_SALAD",
        "Eclectic blend of colors reminiscent of a vibrant assortment of fresh and juicy fruits."
    );

    companion object {
        fun of(name: String?): MonetStyle = entries.firstOrNull { it.name == name } ?: TONAL_SPOT
    }
}

/**
 * Generating the system's colour tables, ported from Mahmud0808/ColorBlendr's ColorSchemeUtil,
 * ColorModifiers and the parts of ColorUtil they lean on.
 *
 * Android publishes its Material You palette as 78 colour resources in the `android` package --
 * six ramps (three accents, two neutrals, one error) of thirteen tones each -- and everything on
 * the phone that looks themed is reading one of them. Overriding those resources is therefore the
 * whole trick: generate the ramps here, hand them to [SystemThemeManager] to write as an overlay,
 * and the system recolours.
 *
 * Upstream ships its own vendored fork of the Material colour utilities. This uses the ones already
 * in the app (the same library, a release behind), so the two differ in one visible way: upstream
 * can offer the 2025 and 2026 colour specs and a CMF style that only exist in its fork, and this
 * cannot. Everything the styles below have in common is computed identically.
 */
object ThemeEngine {

    /** Resource-name suffixes, lightest to darkest. */
    val TONE_NAMES = arrayOf(
        "0", "10", "50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "1000"
    )

    /** The L* each suffix stands for. 49.6 rather than 50 is the platform's own off-by-a-little. */
    private val TONES = intArrayOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

    /** Ramp names, in the order [generate] returns them. */
    val PALETTE_NAMES = arrayOf(
        "system_accent1", "system_accent2", "system_accent3",
        "system_neutral1", "system_neutral2", "system_error"
    )

    private const val ACCENT1 = 0
    private const val NEUTRAL1 = 3
    private const val NEUTRAL2 = 4

    /** The index of the "900" suffix, which is the dark theme's background tone. */
    private val TONE_900 = TONE_NAMES.indexOf("900")

    /** L* per tone as a fraction, for [shiftLightness]. */
    private val TINTS = floatArrayOf(
        1.0f, 0.99f, 0.95f, 0.9f, 0.8f, 0.7f, 0.6f, 0.496f, 0.4f, 0.3f, 0.2f, 0.1f, 0.0f
    )

    /** One full set of ramps: six palettes of thirteen colours, indexed as [PALETTE_NAMES]. */
    fun generate(
        seed: Int,
        style: MonetStyle,
        dark: Boolean,
        accentSaturation: Int = 100,
        backgroundSaturation: Int = 100,
        backgroundLightness: Int = 100,
        pitchBlack: Boolean = false,
        accurateShades: Boolean = true
    ): Array<IntArray> {
        val scheme = scheme(seed, style, dark)
        val palettes = arrayOf(
            scheme.primaryPalette,
            scheme.secondaryPalette,
            scheme.tertiaryPalette,
            scheme.neutralPalette,
            scheme.neutralVariantPalette,
            scheme.errorPalette
        )

        return Array(palettes.size) { index ->
            val ramp = IntArray(TONES.size) { tone -> palettes[index].tone(TONES[tone]) }
            modify(
                ramp = ramp,
                index = index,
                style = style,
                accentSaturation = accentSaturation,
                backgroundSaturation = backgroundSaturation,
                backgroundLightness = backgroundLightness,
                pitchBlack = pitchBlack,
                accurateShades = accurateShades
            )
        }
    }

    /**
     * The three sliders and the two shade switches, applied to one ramp.
     *
     * Which of them apply depends on what the ramp is for, which is upstream's rule and a sound
     * one: saturating a neutral ramp would stop it being neutral, and lightening an accent would
     * break the contrast the tones exist to guarantee. Monochrome opts out of saturation entirely,
     * because a monochrome palette with chroma in it is not monochrome.
     */
    private fun modify(
        ramp: IntArray,
        index: Int,
        style: MonetStyle,
        accentSaturation: Int,
        backgroundSaturation: Int,
        backgroundLightness: Int,
        pitchBlack: Boolean,
        accurateShades: Boolean
    ): IntArray {
        val isAccent = index <= 2 || index == 5
        val isNeutral = index == NEUTRAL1 || index == NEUTRAL2
        val monochrome = style == MonetStyle.MONOCHROME
        val rainbow = style == MonetStyle.RAINBOW

        if (isAccent && accentSaturation != 100 && !monochrome) {
            for (i in ramp.indices) ramp[i] = adjustSaturation(ramp[i], accentSaturation)
        }

        if (isNeutral) {
            if (backgroundLightness != 100 && !monochrome) {
                for (i in ramp.indices) ramp[i] = shiftLightness(ramp[i], backgroundLightness, i)
            }
            if (backgroundSaturation != 100 && !monochrome && !rainbow) {
                for (i in ramp.indices) ramp[i] = adjustSaturation(ramp[i], backgroundSaturation)
            }
            if (pitchBlack) ramp[TONE_900] = Color.BLACK
        }

        if (monochrome) {
            for (i in ramp.indices) ramp[i] = shiftLightness(ramp[i], backgroundLightness, i)
        }

        // Older Pixels published accent _100 as a copy of _300 rather than as its own tone, and a
        // fair amount of third-party theming was drawn against that. Turning accurate shades off
        // puts it back, which is the only reason anyone would want a less accurate ramp.
        if (!accurateShades && index <= 2) {
            ramp[TONE_NAMES.indexOf("100")] = ramp[TONE_NAMES.indexOf("300")]
        }

        return ramp
    }

    private fun scheme(seed: Int, style: MonetStyle, dark: Boolean): DynamicScheme {
        val hct = Hct.fromInt(seed)
        return when (style) {
            MonetStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, 0.0)
            MonetStyle.VIBRANT -> SchemeVibrant(hct, dark, 0.0)
            MonetStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, 0.0)
            MonetStyle.SPRITZ -> SchemeNeutral(hct, dark, 0.0)
            MonetStyle.RAINBOW -> SchemeRainbow(hct, dark, 0.0)
            MonetStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, 0.0)
            MonetStyle.MONOCHROME -> SchemeMonochrome(hct, dark, 0.0)
            MonetStyle.FIDELITY -> SchemeFidelity(hct, dark, 0.0)
            MonetStyle.CONTENT -> SchemeContent(hct, dark, 0.0)
        }
    }

    /**
     * Pushes a colour's chroma toward the most saturated version of itself sRGB can hold at that
     * lightness, or back toward grey. Hue and lightness are both left exactly where they were.
     *
     * This is ColorBlendr's formula, and the lightness part of that sentence is the whole reason it
     * is worth spelling out. An earlier version here let the tone slide -- up to 12 L* -- when the
     * chroma could not go any higher without it, on the reasoning that a slider which cannot move
     * the tones you actually see is not much of a slider. That was a mistake twice over. It broke
     * the contract the tones exist to keep: tone 40 is the light theme's primary and tone 80 the
     * dark theme's *because* those are the lightnesses that contrast correctly against their
     * containers, and once the roles are published by name (see [SystemRoles]) a shifted tone puts
     * the wrong lightness behind a role that promised a particular one. And it made this app
     * disagree with the app it is a port of, for a case that app does not treat as a problem.
     *
     * The ceiling is real and it is not a bug in either app. Measured against a tonal-spot palette,
     * the primary ramp at tone 80 has between 0 and 2 points of chroma left in it for most seeds --
     * it already sits on the edge of what a screen can show. So raising *accent* saturation moves
     * the middle of the ramp and leaves the lightest tones where they are, and lowering it works
     * everywhere. Background saturation is the one with room: a neutral ramp at tone 30 has thirty
     * to seventy points of headroom, which is why that slider is the one you can see working.
     */
    fun adjustSaturation(color: Int, saturation: Int): Int {
        val amount = (saturation - 100) / 100f
        if (amount == 0f) return color

        val hct = Hct.fromInt(color)
        val lstar = ColorUtils.lstarFromArgb(color)
        var chroma = hct.chroma

        chroma += if (amount > 0f) {
            // Asking for chroma 200 is asking for more than sRGB holds, so what comes back is the
            // most this hue can be at this lightness.
            (Hct.from(hct.hue, 200.0, lstar).chroma - chroma) * amount
        } else {
            chroma * amount
        }

        return Hct.from(hct.hue, chroma, lstar).toInt()
    }

    /**
     * Nudges a tone's lightness, in the small amounts a background wants.
     *
     * The shift is a thousandth per slider point rather than a hundredth: a background ramp is the
     * thing every surface in the system is drawn on, and a percent of L* at a time is far too
     * coarse a step for it. The two lightest tones and pure black are pinned -- 0 and 1000 are the
     * ramp's fixed ends and moving them turns white grey.
     */
    fun shiftLightness(color: Int, lightness: Int, index: Int): Int {
        var amount = (lightness - 100) / 1000f
        when (index) {
            0, 12 -> amount = 0f
            1 -> amount /= 10f
            2 -> amount /= 2f
        }
        val hct = Hct.fromInt(color)
        return Hct.from(hct.hue, hct.chroma, (100f * (TINTS[index] + amount)).toDouble()).toInt()
    }

    /** `#AARRGGBB`, which is the form the overlay writer wants. */
    fun hex(color: Int): String = String.format("#%08X", color)
}
