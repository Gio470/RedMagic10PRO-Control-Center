package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/**
 * How often each of the app's three background polls re-runs, in milliseconds.
 *
 * Milliseconds rather than the seconds this used to store, because the floor is now half a second
 * and a whole-second Int cannot say that. The old key is still read once, as a migration: a value
 * saved under it is carried across in ms and then ignored.
 *
 * Three separate periods because the polls cost wildly different amounts. Hardware status runs root
 * shells for temperature and fan RPM, so it wants a slow period; the processor and memory readings
 * are ordinary file reads and are the ones worth watching tick over.
 */
object RefreshIntervals {

    /** The floor for all three. Below this the root-shell poll can't finish before the next fires. */
    const val MIN_MS = 500
    const val MAX_MS = 60_000
    /** Slider granularity: half a second, so [MIN_MS] is reachable exactly. */
    const val STEP_MS = 500

    const val DEFAULT_STATUS_MS = 15_000
    const val DEFAULT_CPU_MS = 2_000
    const val DEFAULT_MEMORY_MS = 2_000

    fun status(context: Context): Int =
        read(context, AppPrefs.STATUS_REFRESH_INTERVAL_MS, DEFAULT_STATUS_MS, migrateSeconds = true)

    fun setStatus(context: Context, ms: Int) =
        write(context, AppPrefs.STATUS_REFRESH_INTERVAL_MS, ms)

    fun cpu(context: Context): Int =
        read(context, AppPrefs.CPU_REFRESH_INTERVAL_MS, DEFAULT_CPU_MS)

    fun setCpu(context: Context, ms: Int) =
        write(context, AppPrefs.CPU_REFRESH_INTERVAL_MS, ms)

    fun memory(context: Context): Int =
        read(context, AppPrefs.MEMORY_REFRESH_INTERVAL_MS, DEFAULT_MEMORY_MS)

    fun setMemory(context: Context, ms: Int) =
        write(context, AppPrefs.MEMORY_REFRESH_INTERVAL_MS, ms)

    /** For a slider label: "0.5s", "2s", "15s". */
    fun format(ms: Int): String {
        val seconds = ms / 1000.0
        return if (ms % 1000 == 0) "${ms / 1000}s" else "${seconds}s"
    }

    private fun read(
        context: Context,
        key: String,
        default: Int,
        migrateSeconds: Boolean = false
    ): Int {
        val prefs = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(key) && migrateSeconds) {
            val seconds = prefs.getInt(AppPrefs.STATUS_REFRESH_INTERVAL_SECONDS, 0)
            if (seconds > 0) return (seconds * 1000).coerceIn(MIN_MS, MAX_MS)
        }
        return prefs.getInt(key, default).coerceIn(MIN_MS, MAX_MS)
    }

    private fun write(context: Context, key: String, ms: Int) {
        context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, ms.coerceIn(MIN_MS, MAX_MS))
            .apply()
    }
}
