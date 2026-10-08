package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.elitedarkkaiser.redmagic.RootShell

/**
 * App side of the global icon pack settings, ported from
 * github.com/RichardLuo0/global-icon-pack-android.
 *
 * Same split as [WindowModSettings]: private SharedPreferences keep the screen correct with no
 * root, [pushToDevice] mirrors them into the `Settings.Global` keys the hooks read (see
 * [IconPackConfig]). Every push restarts the launcher, since a hooked app reads the settings once
 * at launch.
 */
object IconPackSettings {
    private const val PREFS_NAME = "icon_pack"

    /** The intents an icon pack declares so launchers can find it. */
    private val ICON_PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "org.adw.ActivityStarter.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabled(context: Context): Boolean =
        prefs(context).getBoolean(IconPackConfig.KEY_ENABLED, IconPackConfig.DEFAULT_ENABLED)

    fun setEnabled(context: Context, value: Boolean) {
        com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(context, "icon_pack")
        prefs(context).edit().putBoolean(IconPackConfig.KEY_ENABLED, value).apply()
    }

    fun getPack(context: Context): String =
        prefs(context).getString(IconPackConfig.KEY_PACK, IconPackConfig.DEFAULT_PACK)
            ?: IconPackConfig.DEFAULT_PACK

    fun setPack(context: Context, value: String) {
        prefs(context).edit().putString(IconPackConfig.KEY_PACK, value).apply()
    }

    fun getAsFallback(context: Context): Boolean =
        prefs(context).getBoolean(IconPackConfig.KEY_AS_FALLBACK, IconPackConfig.DEFAULT_AS_FALLBACK)

    fun setAsFallback(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(IconPackConfig.KEY_AS_FALLBACK, value).apply()
    }

    fun getShortcut(context: Context): Boolean =
        prefs(context).getBoolean(IconPackConfig.KEY_SHORTCUT, IconPackConfig.DEFAULT_SHORTCUT)

    fun setShortcut(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(IconPackConfig.KEY_SHORTCUT, value).apply()
    }

    fun getForceMonochrome(context: Context): Boolean =
        prefs(context).getBoolean(
            IconPackConfig.KEY_FORCE_MONOCHROME, IconPackConfig.DEFAULT_FORCE_MONOCHROME
        )

    fun setForceMonochrome(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(IconPackConfig.KEY_FORCE_MONOCHROME, value).apply()
    }

    data class IconPack(val packageName: String, val label: String)

    /**
     * Every installed icon pack, found the way launchers find them: by the theme intents a pack
     * declares. A pack usually declares several, hence the deduplication.
     */
    fun installed(context: Context): List<IconPack> {
        val pm = context.packageManager
        return ICON_PACK_ACTIONS
            .flatMap { action ->
                runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
            }
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { IconPack(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Writes the settings where the hooks can read them and restarts the launcher so the change is
     * visible immediately. Force-stopping is built in rather than a switch, for the same reason it
     * is in [GameAssistSettings]: the settings are read once per process at launch, so without a
     * restart a change appears to do nothing.
     *
     * Call off the main thread: it spawns a root shell.
     */
    fun pushToDevice(context: Context): Boolean {
        fun put(key: String, value: String) = "settings put global $key '$value'"
        fun flag(key: String, value: Boolean) = put(key, if (value) "1" else "0")

        val pack = getPack(context)
        val ok = RootShell.exec(
            listOf(
                flag(IconPackConfig.KEY_ENABLED, getEnabled(context)),
                // Deleted rather than set empty: `settings put` would store the literal "null".
                if (pack.isEmpty()) "settings delete global ${IconPackConfig.KEY_PACK}"
                else put(IconPackConfig.KEY_PACK, pack),
                flag(IconPackConfig.KEY_AS_FALLBACK, getAsFallback(context)),
                flag(IconPackConfig.KEY_SHORTCUT, getShortcut(context)),
                flag(IconPackConfig.KEY_FORCE_MONOCHROME, getForceMonochrome(context)),
            ).joinToString("; ")
        ) && pushSystemCopy(context, pack)
        restartLauncher(context)
        return ok
    }

    /**
     * Mirrors the pack name into /data/system for the one hook that runs in system_server, which
     * cannot reach `Settings.Global` (see [IconPackConfig.SYSTEM_CONFIG_PATH]).
     *
     * [ensureSystemCopy] is the same write, called once per app launch rather than on a settings
     * change: the hook treats a file that is not there as "no answer yet" and installs itself
     * anyway, so the file existing is what lets a device with no icon pack stop paying for it.
     *
     * Staged in the cache directory and copied as root the same way [WindowModSettings.pushToDevice]
     * does it, ownership and SELinux label included -- a file dropped into /data/system without a
     * relabel carries the app's context, and system_server is not allowed to read that.
     */
    fun ensureSystemCopy(context: Context): Boolean = pushSystemCopy(context, getPack(context))

    private fun pushSystemCopy(context: Context, pack: String): Boolean {
        val staged = java.io.File(context.cacheDir, "icon_pack.conf")
        return runCatching {
            staged.writeText(IconPackConfig.serializeForSystem(pack))
            RootShell.exec(
                "cp '${staged.absolutePath}' '${IconPackConfig.SYSTEM_CONFIG_PATH}'; " +
                    "chmod 644 '${IconPackConfig.SYSTEM_CONFIG_PATH}'; " +
                    "chown 1000:1000 '${IconPackConfig.SYSTEM_CONFIG_PATH}'; " +
                    "restorecon '${IconPackConfig.SYSTEM_CONFIG_PATH}'"
            )
        }.getOrDefault(false)
    }

    /** Force-stops the current home app, so it rebuilds its icon cache against the new settings. */
    fun restartLauncher(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = runCatching {
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull() ?: return false
        return RootShell.exec("am force-stop $pkg")
    }
}
