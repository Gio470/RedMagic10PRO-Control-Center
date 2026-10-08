package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/**
 * A colour rotation for the fan LED, after the one in the RedMagic RGB Control Center module.
 *
 * The chip has no rotation of its own — it lights one colour of the sixteen it knows until told
 * otherwise — so this is software: [FanLedService] steps through the chosen colours on a timer,
 * writing each one as an ordinary colour under whichever effect is selected. That also makes it
 * the only way to get a colour the palette doesn't hold, which is what "custom" comes to here.
 *
 * Stored beside the rest of the fan LED state, and saved as it is edited rather than on Save: the
 * dialog previews the rotation by letting the service run it, and the service reads it from here.
 */
object FanLedCycle {

    /**
     * Stands in for a colour id in [LedState.color] when the rotation is what's selected. Negative,
     * so it can never collide with a real colour, and anything writing it straight to the hardware
     * falls back to a solid colour rather than failing.
     */
    const val CYCLE_COLOR = -2

    private const val KEY_COLORS = "fan_led_cycle_colors"
    private const val KEY_SPEED_MS = "fan_led_cycle_speed_ms"

    val DEFAULT_COLORS = listOf(1, 3, 4, 5, 6, 7, 8, 9)

    /**
     * Ready-made rotations. Each is drawn as a swatch of its own colours — quartered, sampling four
     * when it holds more — and cycles the distinct ones among them, so what a swatch shows is what
     * it plays.
     *
     * The first seven are the colours the stock patterns are made of, cycled as solids — a
     * different thing from the pattern itself, which lights them all at once and lives in the
     * Color card above.
     */
    val PRESETS: List<List<Int>> = listOf(
        // The colour sets the stock patterns are made of, as rotations of solid colours.
        listOf(9, 3, 7, 5),  // pink, orange, blue, green
        listOf(8, 4, 6, 9),  // purple, yellow, cyan, pink
        listOf(8, 6, 7, 9),  // purple, cyan, blue, pink
        listOf(9, 7, 9, 7),  // pink and blue
        listOf(8, 6, 8, 6),  // purple and cyan
        listOf(6, 3, 6, 3),  // cyan and orange
        listOf(4, 5, 4, 5),  // yellow and green
        // Sets built from the palette itself, the patterns among them.
        listOf(1, 3, 4, 5, 6, 7, 8, 9),             // every solid
        listOf(101, 102, 103, 104, 105, 106, 107),  // every pattern
        listOf(1, 3, 4, 9),                         // warm
        listOf(5, 6, 7, 8),                         // cool
        listOf(1, 3, 4),                            // fire
        listOf(7, 6, 5),                            // ocean
        listOf(6, 8, 9, 7),                         // neon
        listOf(9, 8, 4, 5),                         // candy
    )

    const val DEFAULT_SPEED_MS = 1000

    /**
     * Each step is a root shell, so this is the floor that keeps a fast rotation from spawning
     * them faster than they can finish.
     */
    const val MIN_SPEED_MS = 300
    const val MAX_SPEED_MS = 3000

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    fun colors(context: Context): List<Int> {
        val stored = prefs(context).getString(KEY_COLORS, null) ?: return DEFAULT_COLORS
        val parsed = stored.split(',').mapNotNull { it.trim().toIntOrNull() }
        // A rotation of nothing would leave the LED on whatever it happened to be showing.
        return parsed.ifEmpty { DEFAULT_COLORS }
    }

    fun setColors(context: Context, colors: List<Int>) {
        prefs(context).edit()
            .putString(KEY_COLORS, colors.joinToString(",") { it.toString() })
            .commit()
    }

    fun speedMs(context: Context): Int =
        prefs(context).getInt(KEY_SPEED_MS, DEFAULT_SPEED_MS).coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)

    fun setSpeedMs(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_SPEED_MS, value.coerceIn(MIN_SPEED_MS, MAX_SPEED_MS))
            .commit()
    }
}
