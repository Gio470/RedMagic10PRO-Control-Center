package com.elitedarkkaiser.redmagic.ui

import android.content.Context

/**
 * How the glass bottom bars are drawn: the numbers behind the blur and the refraction, saved so
 * the Glass playground can be the screen that sets them.
 *
 * These used to be constants scattered through
 * [com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBar] -- `blur(4.dp)`, `lens(24.dp, 24.dp)`,
 * a `0.55f` surface tint on the docked bar -- and the playground, which is the one screen in the
 * app that exists to show what those numbers do, could not touch them. It edits these instead.
 *
 * Stored as floats in dp rather than as fractions of anything, because the two bars are different
 * sizes and a fraction would mean something different on each. A dp of refraction is a dp of
 * refraction wherever it is drawn.
 */
object GlassPrefs {

    /**
     * One set of effect parameters.
     *
     * The defaults are the values the bars were hard-coded to, with two exceptions, both of them
     * the answer to "the docked bar does not look like liquid glass": the docked bar had no lens
     * at all (so it was frosted, not glass -- blur alone is a ground-glass pane, refraction at the
     * rim is what makes it read as something with thickness), and its surface tint was 0.55, which
     * is opaque enough to bury whatever is behind it. Both bars share one tint now, at the
     * floating bar's own lighter value.
     */
    data class Effects(
        /** Frosting. High values look soft but erase the detail refraction needs to be visible. */
        val blurRadiusDp: Float = 4f,
        /** How far in from the rim the refraction reaches -- the apparent thickness of the glass. */
        val refractionHeightDp: Float = 24f,
        /** How hard the rim bends what is behind it. */
        val refractionAmountDp: Float = 24f,
        /**
         * The colour wash over the material -- how much of [GlassTint]'s wash lands on the pane.
         * Not an opacity directly: each kind of pane maps this onto its own range, because a bar
         * can go all the way to clear and a sheet carrying body text cannot.
         */
        val tintAlpha: Float = 0.22f,
        /** Splits the refracted edge into red and blue fringes, like a real lens. */
        val dispersion: Boolean = false,
        /** Shading across the pane, so it reads as a slab rather than a hole. */
        val depthEffect: Boolean = false,
        /** The docked bar's top corners. The floating bar is a capsule and has no say in this. */
        val dockedCornerDp: Float = 22f
    )

    /**
     * The three materials worth having as one tap, and what separates them is which effect carries
     * the look.
     *
     * [LIQUID_GLASS] leans on refraction: a thick rim that bends what is behind it, barely any
     * blur, barely any tint -- you can read the content through it, distorted, which is the whole
     * point of the thing. [FROSTED] is the opposite trade: heavy blur, no meaningful rim, a solid
     * tint. It is the material most apps mean by "glass", and it is the most legible of the three
     * over busy content. [CLEAR] takes the blur and most of the tint away and leaves only the
     * refraction and the edge light, which over a photo is striking and over a plain surface is
     * nearly invisible.
     *
     * Blur and refraction pull against each other -- blur destroys the detail refraction needs to
     * show -- which is why none of the three has a lot of both.
     */
    val LIQUID_GLASS = Effects(
        blurRadiusDp = 4f,
        refractionHeightDp = 24f,
        refractionAmountDp = 24f,
        tintAlpha = 0.22f,
        dispersion = false,
        depthEffect = true
    )

    val FROSTED = Effects(
        blurRadiusDp = 20f,
        refractionHeightDp = 6f,
        refractionAmountDp = 8f,
        tintAlpha = 0.42f,
        dispersion = false,
        depthEffect = false
    )

    val CLEAR = Effects(
        blurRadiusDp = 0f,
        refractionHeightDp = 28f,
        refractionAmountDp = 34f,
        tintAlpha = 0.08f,
        dispersion = true,
        depthEffect = true
    )

    /** What the bars are drawn with until someone changes it. */
    val DEFAULT = LIQUID_GLASS

    /** Name to preset, in the order the playground offers them. */
    val PRESETS: List<Pair<String, Effects>> = listOf(
        "Liquid glass" to LIQUID_GLASS,
        "Frosted" to FROSTED,
        "Clear" to CLEAR
    )

    /**
     * Which preset a set of values is, or null for something hand-tuned. Compared on the effect
     * fields only: the docked bar's corner is a shape, not a material, so changing it should not
     * take the preset's name off the chip that is lit.
     */
    fun presetName(effects: Effects): String? =
        PRESETS.firstOrNull { (_, preset) ->
            preset.copy(dockedCornerDp = effects.dockedCornerDp) == effects
        }?.first

    /** Slider ranges, kept here so the playground and any future editor agree on them. */
    const val MAX_BLUR_DP = 24f
    const val MAX_REFRACTION_HEIGHT_DP = 48f
    const val MAX_REFRACTION_AMOUNT_DP = 64f
    const val MAX_TINT = 0.6f
    const val MAX_DOCKED_CORNER_DP = 40f

    private const val PREFS = "glass"
    private const val KEY_BLUR = "blur_dp"
    private const val KEY_REFRACTION_HEIGHT = "refraction_height_dp"
    private const val KEY_REFRACTION_AMOUNT = "refraction_amount_dp"
    private const val KEY_TINT = "tint_alpha"
    private const val KEY_DISPERSION = "dispersion"
    private const val KEY_DEPTH = "depth_effect"
    private const val KEY_DOCKED_CORNER = "docked_corner_dp"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun effects(context: Context): Effects {
        val p = prefs(context)
        return Effects(
            blurRadiusDp = p.getFloat(KEY_BLUR, DEFAULT.blurRadiusDp),
            refractionHeightDp = p.getFloat(KEY_REFRACTION_HEIGHT, DEFAULT.refractionHeightDp),
            refractionAmountDp = p.getFloat(KEY_REFRACTION_AMOUNT, DEFAULT.refractionAmountDp),
            tintAlpha = p.getFloat(KEY_TINT, DEFAULT.tintAlpha),
            dispersion = p.getBoolean(KEY_DISPERSION, DEFAULT.dispersion),
            depthEffect = p.getBoolean(KEY_DEPTH, DEFAULT.depthEffect),
            dockedCornerDp = p.getFloat(KEY_DOCKED_CORNER, DEFAULT.dockedCornerDp)
        )
    }

    fun setEffects(context: Context, effects: Effects) {
        prefs(context).edit()
            .putFloat(KEY_BLUR, effects.blurRadiusDp)
            .putFloat(KEY_REFRACTION_HEIGHT, effects.refractionHeightDp)
            .putFloat(KEY_REFRACTION_AMOUNT, effects.refractionAmountDp)
            .putFloat(KEY_TINT, effects.tintAlpha)
            .putBoolean(KEY_DISPERSION, effects.dispersion)
            .putBoolean(KEY_DEPTH, effects.depthEffect)
            .putFloat(KEY_DOCKED_CORNER, effects.dockedCornerDp)
            .apply()
    }

    /** Shares the Playground's saved material with windows; closing releases the listener. */
    fun observe(context: Context, changed: (Effects) -> Unit): AutoCloseable {
        val app = context.applicationContext
        val preferences = prefs(app)
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        var previous: Effects? = null
        fun publish() {
            if (!active.get()) return
            val current = effects(app)
            if (current != previous) {
                previous = current
                changed(current)
            }
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        publish()
        // SharedPreferences holds listeners weakly; retain it until the window releases this handle.
        return AutoCloseable {
            active.set(false)
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
