package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.pm.PackageManager

/**
 * ZTE's device-management stack, which is what delivers system updates on this phone.
 *
 * ZDM is ZTE's FOTA client: it checks for updates, downloads them in the background and nags to
 * install them. Switching it off is the usual reason a rooted RedMagic owner reaches for a debloat
 * tool at all -- an OTA that lands replaces the boot image, which takes root with it.
 *
 * Disabled for the user with `pm disable-user`, and reversible the same way. Nothing is
 * deleted: switching this back off re-enables all three and updates resume.
 */
object OtaUpdates {

    /**
     * The three that make up the stack. zdm is the client, zdmdaemon the background service that
     * polls, and zdmdaemon.install the part that applies a downloaded package -- leaving any one
     * of them on leaves something to restart the others.
     */
    val PACKAGES = listOf(
        "com.zte.zdm",
        "com.zte.zdmdaemon",
        "com.zte.zdmdaemon.install"
    )

    /** Which of [PACKAGES] this phone actually has. A ROM may not ship all three. */
    fun present(context: Context): List<String> = PACKAGES.filter { packageName ->
        runCatching {
            context.packageManager
                .getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.isSuccess
    }

    /**
     * True when every one of them that exists is off.
     *
     * Read from the package manager rather than from a saved preference, so the switch still tells
     * the truth after something else -- a factory reset, another tool -- has changed one of them
     * behind its back.
     */
    fun blocked(context: Context): Boolean {
        val installed = present(context)
        if (installed.isEmpty()) return false
        return installed.none { isEnabled(context, it) }
    }

    private fun isEnabled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager
            .getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            .enabled
    }.getOrDefault(true)

    /**
     * Switches the whole stack off, or back on. Blocking.
     *
     * One root shell for all three: three `su` invocations to flip one switch is three prompts'
     * worth of latency for no reason.
     */
    fun setBlocked(context: Context, blocked: Boolean): Boolean {
        val installed = present(context)
        if (installed.isEmpty()) return false

        val verb = if (blocked) "pm disable-user --user 0" else "pm enable"
        RootShell.exec(installed.joinToString("; ") { "$verb $it" })
        return blocked(context) == blocked
    }
}
