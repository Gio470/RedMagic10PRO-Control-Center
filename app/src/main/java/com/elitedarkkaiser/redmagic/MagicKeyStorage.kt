package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

fun savedMagicKeyAppPackageStorage(context: Context): String? {
    return context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(AppPrefs.MAGIC_KEY_APP_PACKAGE, null)
}

fun saveMagicKeyAppPackageStorage(context: Context, pkg: String?) {
    context
        .getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(AppPrefs.MAGIC_KEY_APP_PACKAGE, pkg)
        .apply()
}
