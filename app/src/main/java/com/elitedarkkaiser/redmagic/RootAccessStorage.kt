package com.elitedarkkaiser.redmagic

import android.content.Context

private const val ROOT_PREFS = "root_access_cache"
private const val ROOT_GRANTED_ONCE = "root_granted_once"

fun hasCachedRootAccessStorage(context: Context): Boolean {
    return context.getSharedPreferences(ROOT_PREFS, Context.MODE_PRIVATE)
        .getBoolean(ROOT_GRANTED_ONCE, false)
}

fun setCachedRootAccessStorage(context: Context, granted: Boolean) {
    context.getSharedPreferences(ROOT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(ROOT_GRANTED_ONCE, granted)
        .apply()
}
