package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

fun isUseFahrenheitStorage(context: Context): Boolean {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(AppPrefs.TEMP_UNIT_FAHRENHEIT, true)
}

fun saveUseFahrenheitStorage(context: Context, useFahrenheit: Boolean) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(AppPrefs.TEMP_UNIT_FAHRENHEIT, useFahrenheit)
        .apply()
}
