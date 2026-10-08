package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/** Cached "last known" fan power state — corrected against the live sysfs read every refresh
 *  cycle, same as the root-access cache, so it stays a fresh mirror rather than a one-way ratchet. */
fun isFanPowerEnabledStorage(context: Context): Boolean {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(AppPrefs.FAN_POWER_ENABLED, false)
}

fun saveFanPowerEnabledStorage(context: Context, enabled: Boolean) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.FAN_POWER_ENABLED, enabled)
        .apply()
}
