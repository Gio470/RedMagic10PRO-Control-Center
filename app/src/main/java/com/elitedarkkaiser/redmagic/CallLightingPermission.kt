package com.elitedarkkaiser.redmagic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * READ_PHONE_STATE, which call lighting cannot work without.
 *
 * It was declared in the manifest and never asked for. That is not enough for a dangerous
 * permission: TelephonyManager.listen throws SecurityException without the runtime grant, and it
 * was being called straight from CallLightingService.onCreate -- so the service died taking the app
 * with it the moment call lighting was switched on.
 *
 * Granted over root first, which this app has and which costs the user no dialog. The runtime
 * prompt is the fallback for when that doesn't take.
 */
object CallLightingPermission {

    const val PERMISSION: String = Manifest.permission.READ_PHONE_STATE

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Grants it with `pm grant`. Blocking -- call it off the main thread. Returns whether the
     * permission is held afterwards, which is what the caller actually needs to know.
     */
    fun grantWithRoot(context: Context): Boolean {
        if (granted(context)) return true
        RootShell.exec("pm grant ${context.packageName} $PERMISSION")
        return granted(context)
    }
}
