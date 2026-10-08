package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.content.SharedPreferences
import com.elitedarkkaiser.redmagic.RootShell
import java.io.File

/**
 * App side of the GameAssist/GameSpace tweak settings, ported from khanhnguyen9872/NubiaToolkit.
 *
 * Same split as [WindowModSettings]: ordinary private SharedPreferences keep the screen correct
 * with no root, [pushToDevice] mirrors the current values into [GameAssistConfig.CONFIG_PATH] as
 * root for the hooks to read.
 *
 * The upstream module gates its force-stop behind a "Force Stop on Apply" switch that defaults
 * off, leaving most users to close and reopen GameAssist/GameSpace by hand for a toggle to take
 * effect. This app always force-stops the affected app right after a push -- there is no switch
 * for it -- because a tweak that silently doesn't apply until the user figures out to relaunch the
 * app themselves is a worse default than a one-second restart they didn't have to ask for.
 */
object GameAssistSettings {
    private const val PREFS_NAME = "gameassist_mods"

    const val PKG_GAME_ASSIST = "cn.nubia.gameassist"
    const val PKG_GAME_LAUNCHER = "cn.nubia.gamelauncher"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabled(context: Context): Boolean =
        prefs(context).getBoolean(GameAssistConfig.KEY_ENABLED, GameAssistConfig.DEFAULT_ENABLED)

    fun setEnabled(context: Context, value: Boolean) {
        com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(context, "gameassist")
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_ENABLED, value).apply()
    }

    fun getNoKill(context: Context): Boolean =
        prefs(context).getBoolean(GameAssistConfig.KEY_NO_KILL, GameAssistConfig.DEFAULT_NO_KILL)

    fun setNoKill(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_NO_KILL, value).apply()
    }

    fun getGlobalGameMode(context: Context): Boolean =
        prefs(context).getBoolean(
            GameAssistConfig.KEY_GLOBAL_GAME_MODE, GameAssistConfig.DEFAULT_GLOBAL_GAME_MODE
        )

    fun setGlobalGameMode(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_GLOBAL_GAME_MODE, value).apply()
    }

    fun getHideEnergyCube(context: Context): Boolean =
        prefs(context).getBoolean(
            GameAssistConfig.KEY_HIDE_ENERGY_CUBE, GameAssistConfig.DEFAULT_HIDE_ENERGY_CUBE
        )

    fun setHideEnergyCube(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_HIDE_ENERGY_CUBE, value).apply()
    }

    fun getSuperResolution(context: Context): Boolean =
        prefs(context).getBoolean(
            GameAssistConfig.KEY_SUPER_RESOLUTION, GameAssistConfig.DEFAULT_SUPER_RESOLUTION
        )

    fun setSuperResolution(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_SUPER_RESOLUTION, value).apply()
    }

    fun getWatermarkLength(context: Context): Boolean =
        prefs(context).getBoolean(
            GameAssistConfig.KEY_WATERMARK_LENGTH, GameAssistConfig.DEFAULT_WATERMARK_LENGTH
        )

    fun setWatermarkLength(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_WATERMARK_LENGTH, value).apply()
    }

    fun getSmallWindow(context: Context): Boolean =
        prefs(context).getBoolean(
            GameAssistConfig.KEY_SMALL_WINDOW, GameAssistConfig.DEFAULT_SMALL_WINDOW
        )

    fun setSmallWindow(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(GameAssistConfig.KEY_SMALL_WINDOW, value).apply()
    }

    /**
     * Writes the current settings where the hooks can read them. Staged through the app's cache
     * directory and copied with root rather than written by the shell directly, so the file's
     * content never has to survive shell quoting.
     *
     * Call off the main thread: it spawns a root shell.
     */
    fun pushToDevice(context: Context): Boolean {
        val text = GameAssistConfig.serialize(
            enabled = getEnabled(context),
            noKill = getNoKill(context),
            globalGameMode = getGlobalGameMode(context),
            hideEnergyCube = getHideEnergyCube(context),
            superResolution = getSuperResolution(context),
            watermarkLength = getWatermarkLength(context),
            smallWindow = getSmallWindow(context)
        )
        val staged = File(context.cacheDir, "gameassist_mods.conf")
        return runCatching {
            staged.writeText(text)
            RootShell.exec(
                "cp '${staged.absolutePath}' '${GameAssistConfig.CONFIG_PATH}'; " +
                    "chmod 644 '${GameAssistConfig.CONFIG_PATH}'"
            )
        }.getOrDefault(false)
    }

    /**
     * Force-stops [packages] so a hook that only takes effect on next launch (most of these do --
     * Xposed cannot re-run a method that already returned) applies immediately instead of waiting
     * for the user to notice and relaunch by hand. Built in, not a setting: see the class doc.
     *
     * Call off the main thread: it spawns a root shell per package.
     */
    fun forceStop(vararg packages: String): Boolean =
        packages.fold(true) { ok, pkg -> RootShell.exec("am force-stop $pkg") && ok }

    /** What the module reported at boot, or null when it has never run. Needs root. */
    fun readModuleStatus(): Map<String, String>? {
        val out = RootShell.execForOutput("cat '${GameAssistConfig.STATUS_PATH}' 2>/dev/null")
        if (out.isNullOrBlank()) return null
        val parsed = GameAssistConfig.parse(out)
        return parsed.ifEmpty { null }
    }
}
