package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import com.elitedarkkaiser.redmagic.RootShell

/**
 * RedMagic/MiFavor launcher's "Home screen settings > App icons > Home mono icons" switch.
 *
 * `content update --uri ... --bind boolean_value:b:$enabled` is the exact
 * `ContentResolver.update()` call the native switch performs itself against
 * `com.zte.mifavor.launcher`'s `LauncherCustomizationProvider` (`GridCustomizationsProxy`) --
 * reverse-engineered via Frida, since it is routed through `update()` rather than `call()` and
 * requires the `BIND_WALLPAPER` permission the shell doesn't have, bypassed here with root.
 *
 * No hooking involved and nothing on-device to read the current value back from, so unlike the
 * rest of this package there is no Xposed hook and no config file the app pushes: the switch just
 * remembers the last value it set, in ordinary SharedPreferences.
 */
object MonoIconsHelper {
    private const val MONO_ICONS_URI =
        "content://com.zte.mifavor.launcher.grid_control/set_icon_themed"
    private const val LAUNCHER_PACKAGE = "com.zte.mifavor.launcher"
    private const val PREFS_NAME = "mono_icons"
    private const val KEY_ENABLED = "enabled"

    fun getEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /**
     * Sets mono-icon mode and force-stops the launcher so the app drawer picks it up immediately
     * -- the provider call by itself only live-refreshes the home screen.
     *
     * Call off the main thread: it spawns two root shells.
     */
    fun apply(context: Context, enabled: Boolean): Boolean {
        val applied = RootShell.exec(
            "content update --uri $MONO_ICONS_URI --bind boolean_value:b:$enabled"
        )
        if (applied) {
            RootShell.exec("am force-stop $LAUNCHER_PACKAGE")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
        return applied
    }
}
