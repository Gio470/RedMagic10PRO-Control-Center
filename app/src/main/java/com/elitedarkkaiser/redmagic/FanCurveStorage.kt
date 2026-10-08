package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

fun selectedCurveStorage(context: Context): String {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(AppPrefs.SELECTED_CURVE, "balanced") ?: "balanced"
}

fun saveSelectedCurveStorage(context: Context, value: String) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(AppPrefs.SELECTED_CURVE, value)
        .apply()
}

fun isAutoFanEnabledStorage(context: Context): Boolean {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(AppPrefs.AUTO_FAN_ENABLED, false)
}

fun saveAutoFanEnabledStorage(context: Context, enabled: Boolean) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.AUTO_FAN_ENABLED, enabled)
        .apply()
}

fun isRealTimePreviewEnabledStorage(context: Context): Boolean {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(AppPrefs.REALTIME_PREVIEW_ENABLED, false)
}

fun saveRealTimePreviewEnabledStorage(context: Context, enabled: Boolean) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.REALTIME_PREVIEW_ENABLED, enabled)
        .apply()
}
