package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.content.SharedPreferences
import com.elitedarkkaiser.redmagic.RootShell
import java.io.File

/**
 * App side of the window-fix settings.
 *
 * The UI's own copy lives in ordinary private SharedPreferences; every change is then pushed to
 * [WindowModConfig.CONFIG_PATH] as root, which is the copy the hooks inside system_server actually
 * read. Two stores rather than one because they answer different questions: the prefs keep the
 * screen correct with no root and no reboot, while the file has to sit somewhere system_server is
 * allowed to read.
 */
object WindowModSettings {
    private const val PREFS_NAME = "window_mods"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEnabled(context: Context): Boolean =
        prefs(context).getBoolean(WindowModConfig.KEY_ENABLED, WindowModConfig.DEFAULT_ENABLED)

    fun getAllowAnyApp(context: Context): Boolean =
        prefs(context).getBoolean(
            WindowModConfig.KEY_ALLOW_ANY_APP,
            WindowModConfig.DEFAULT_ALLOW_ANY_APP
        )

    fun setEnabled(context: Context, value: Boolean) {
        com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(context, "floating_windows")
        prefs(context).edit().putBoolean(WindowModConfig.KEY_ENABLED, value).apply()
    }

    fun setAllowAnyApp(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(WindowModConfig.KEY_ALLOW_ANY_APP, value).apply()
    }

    fun getKeepMiniInteractive(context: Context): Boolean =
        prefs(context).getBoolean(
            WindowModConfig.KEY_KEEP_MINI_INTERACTIVE,
            WindowModConfig.DEFAULT_KEEP_MINI_INTERACTIVE
        )

    fun getKeepDropPosition(context: Context): Boolean =
        prefs(context).getBoolean(
            WindowModConfig.KEY_KEEP_DROP_POSITION,
            WindowModConfig.DEFAULT_KEEP_DROP_POSITION
        )

    fun getAllowOffscreen(context: Context): Boolean =
        prefs(context).getBoolean(
            WindowModConfig.KEY_ALLOW_OFFSCREEN,
            WindowModConfig.DEFAULT_ALLOW_OFFSCREEN
        )

    fun setAllowOffscreen(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(WindowModConfig.KEY_ALLOW_OFFSCREEN, value).apply()
    }

    fun getNoDragToSplit(context: Context): Boolean =
        prefs(context).getBoolean(
            WindowModConfig.KEY_NO_DRAG_TO_SPLIT,
            WindowModConfig.DEFAULT_NO_DRAG_TO_SPLIT
        )

    fun setNoDragToSplit(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(WindowModConfig.KEY_NO_DRAG_TO_SPLIT, value).apply()
    }

    fun getWindowLimit(context: Context): Int =
        prefs(context).getInt(
            WindowModConfig.KEY_WINDOW_LIMIT,
            WindowModConfig.DEFAULT_WINDOW_LIMIT
        )

    fun setKeepMiniInteractive(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(WindowModConfig.KEY_KEEP_MINI_INTERACTIVE, value).apply()
    }

    fun setKeepDropPosition(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(WindowModConfig.KEY_KEEP_DROP_POSITION, value).apply()
    }

    fun setWindowLimit(context: Context, value: Int) {
        prefs(context).edit().putInt(WindowModConfig.KEY_WINDOW_LIMIT, value).apply()
    }

    /**
     * Writes the current settings where the hooks can read them. Staged through the app's cache
     * directory and copied with root rather than written by the shell directly, so the file's
     * content never has to survive shell quoting. restorecon puts it back to /data/system's own
     * SELinux label, without which system_server may be denied the read.
     *
     * Call off the main thread: it spawns a root shell.
     */
    fun pushToDevice(context: Context): Boolean {
        val text = WindowModConfig.serialize(
            enabled = getEnabled(context),
            allowAnyApp = getAllowAnyApp(context),
            keepMini = getKeepMiniInteractive(context),
            keepDrop = getKeepDropPosition(context),
            allowOffscreen = getAllowOffscreen(context),
            noDragToSplit = getNoDragToSplit(context),
            limit = getWindowLimit(context)
        )
        val staged = File(context.cacheDir, "window_mods.conf")
        return runCatching {
            staged.writeText(text)
            RootShell.exec(
                "cp '${staged.absolutePath}' '${WindowModConfig.CONFIG_PATH}'; " +
                    "chmod 644 '${WindowModConfig.CONFIG_PATH}'; " +
                    "chown 1000:1000 '${WindowModConfig.CONFIG_PATH}'; " +
                    "restorecon '${WindowModConfig.CONFIG_PATH}'"
            )
        }.getOrDefault(false)
    }

    /** What the module reported at boot, or null when it has never run. Needs root. */
    fun readModuleStatus(): Map<String, String>? {
        // Both in one shell: the report, and the boot the kernel is actually on.
        val out = RootShell.execForOutput(
            "cat '${WindowModConfig.STATUS_PATH}' 2>/dev/null; " +
                "echo '@@BOOT@@'; cat '${WindowModConfig.BOOT_ID_PATH}' 2>/dev/null"
        )
        if (out.isNullOrBlank()) return null

        val parts = out.split("@@BOOT@@")
        val parsed = WindowModConfig.parse(parts.getOrNull(0).orEmpty())
        if (parsed.isEmpty()) return null

        // A report from a previous boot means the module did not load on this one -- it was
        // switched off in LSPosed, or it failed. Either way it is not running, and saying "active"
        // off the back of a file it wrote last week is how the card came to claim a module the
        // user had just disabled.
        val currentBoot = parts.getOrNull(1)?.trim().orEmpty()
        val reportedBoot = parsed[WindowModConfig.KEY_BOOT_ID].orEmpty()
        if (currentBoot.isEmpty()) return parsed
        // An empty reported id is a report from a build before this check existed. That build's
        // module has not run since the update either -- LSPosed needs a reboot to pick up new
        // module code -- so it is stale by the same argument.
        if (reportedBoot != currentBoot) return null

        return parsed
    }
}
