package com.elitedarkkaiser.redmagic

/**
 * Cleanup for the global `policy_control` setting an earlier build wrote.
 *
 * That was the documented way to hide the status bar everywhere, and it does nothing on this ROM:
 * Google deleted PolicyControl from AOSP in Android 11 and RedMagicOS took the deletion. The switch
 * is driven by an Xposed hook now (see StatusBarHooks), which leaves the old value stranded in the
 * settings database -- read by nobody, but still there, and still what `settings get` would report
 * if anyone went looking.
 *
 * Removed only when it holds exactly what this app wrote, so a value put there by something else is
 * left alone.
 */
object StatusBarHide {

    private const val WROTE = "immersive.status=*"

    /** Blocking. Safe to call on every launch: it does nothing once the key is gone. */
    fun clearLegacySetting() {
        val current = RootShell.execForOutput("settings get global policy_control")?.trim()
        if (current == WROTE) RootShell.exec("settings delete global policy_control")
    }
}
