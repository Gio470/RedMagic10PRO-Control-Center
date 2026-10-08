package com.elitedarkkaiser.redmagic

import android.content.Context

/** Saved state for GMS Optimizer. Legacy removed-mod flags are no longer read. */
object PerfTweaksPrefs {
    private const val PREFS = "perf_tweaks"

    const val KEY_GMS_OPTIMIZER = "gms_optimizer_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getBoolean(context: Context, key: String): Boolean =
        prefs(context).getBoolean(key, false)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

}
