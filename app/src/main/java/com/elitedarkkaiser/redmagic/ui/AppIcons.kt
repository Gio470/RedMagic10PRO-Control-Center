package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * App icons, kept after the first load.
 *
 * PackageManager.getApplicationIcon opens the other app's APK and decodes a drawable out of it.
 * That is fine once and ruinous in a list adapter, where it runs on the main thread for every row
 * that scrolls into view -- and again for the same row when it scrolls back.
 *
 * Bounded, because the icons for every system app on the device are megabytes of bitmap: the cap is
 * about a screenful either side of where the list has been.
 */
object AppIcons {

    private val cache = object : LruCache<String, Drawable>(192) {}

    /** Null for a package with no icon, or one that has gone away. */
    fun of(context: Context, packageName: String): Drawable? {
        if (packageName.isEmpty()) return null
        cache.get(packageName)?.let { return it }
        val icon = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        cache.put(packageName, icon)
        return icon
    }
}
