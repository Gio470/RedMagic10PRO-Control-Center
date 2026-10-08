package com.elitedarkkaiser.redmagic.xposed

import java.io.File

/**
 * The config the window-fix hooks read, and where it lives.
 *
 * Not XSharedPreferences. The hooks run inside system_server, and SELinux does not let that domain
 * read an app's data directory, so a preferences read from a hook either fails or throws -- and an
 * exception thrown inside a hook callback is swallowed by Xposed, which then runs the original
 * method. The whole patch silently stops applying. That is why the upstream module, whose hooks
 * read no settings at all, works where a settings-driven port does not.
 *
 * /data/system is system_server's own directory, so it can read from there freely. The app writes
 * the file as root (it requires root anyway, as does the Xposed framework hosting these hooks).
 */
object WindowModConfig {
    const val CONFIG_PATH = "/data/system/redmagic_window_mods.conf"
    const val STATUS_PATH = "/data/system/redmagic_window_mods.status"

    /** Master switch: off means every patch below stands down and the ROM behaves as stock. */
    const val KEY_ENABLED = "enabled"
    const val KEY_ALLOW_ANY_APP = "allow_any_app"
    const val KEY_KEEP_MINI_INTERACTIVE = "keep_mini_interactive"
    const val KEY_KEEP_DROP_POSITION = "keep_drop_position"
    /** Lets a dragged window hang off the edge of the screen instead of being pulled back on. */
    const val KEY_ALLOW_OFFSCREEN = "allow_offscreen"
    /** Stops a drag to the screen edge flipping the window into split screen. */
    const val KEY_NO_DRAG_TO_SPLIT = "no_drag_to_split"
    /** Maximum floating windows; 0 means no limit. */
    const val KEY_WINDOW_LIMIT = "window_limit"

    /**
     * The kernel's unique id for the boot the status file was written on.
     *
     * The status file is written once, at boot, by a hook running inside system_server -- so it
     * says what happened at the last boot the module ran, not whether the module is running now.
     * Disable the module in LSPosed and the file is still sitting there reporting every hook as
     * installed, which is exactly what it said when it was true.
     *
     * /proc/sys/kernel/random/boot_id is a fresh UUID for every boot, so the app comparing the one
     * in the file against the one the kernel has now is an exact answer to "did the module run
     * this boot", with no clock arithmetic to be wrong about across a time-zone change or an NTP
     * correction.
     */
    const val KEY_BOOT_ID = "boot_id"

    const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_ALLOW_ANY_APP = false
    const val DEFAULT_KEEP_MINI_INTERACTIVE = false
    const val DEFAULT_KEEP_DROP_POSITION = false

    // Both off by default. Each removes a behaviour the ROM does deliberately, and neither is a
    // fix for something broken the way the three above are -- a window half off the screen and a
    // drag that no longer splits are things to opt into, not to wake up to.
    const val DEFAULT_ALLOW_OFFSCREEN = false
    const val DEFAULT_NO_DRAG_TO_SPLIT = false
    const val DEFAULT_WINDOW_LIMIT = 4

    const val MIN_WINDOW_LIMIT = 1
    const val MAX_WINDOW_LIMIT = 12

    /** Serialises to the flat `key=value` form both sides agree on. */
    fun serialize(
        enabled: Boolean,
        allowAnyApp: Boolean,
        keepMini: Boolean,
        keepDrop: Boolean,
        allowOffscreen: Boolean,
        noDragToSplit: Boolean,
        limit: Int
    ): String =
        buildString {
            append("# Written by RedMagic Control Center. Read by its Xposed hooks.\n")
            append("$KEY_ENABLED=$enabled\n")
            append("$KEY_ALLOW_ANY_APP=$allowAnyApp\n")
            append("$KEY_KEEP_MINI_INTERACTIVE=$keepMini\n")
            append("$KEY_KEEP_DROP_POSITION=$keepDrop\n")
            append("$KEY_ALLOW_OFFSCREEN=$allowOffscreen\n")
            append("$KEY_NO_DRAG_TO_SPLIT=$noDragToSplit\n")
            append("$KEY_WINDOW_LIMIT=$limit\n")
        }

    fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i).trim() to line.substring(i + 1).trim()
            }

    /**
     * Module-side reader, re-reading only when the file's timestamp moves so a per-call lookup
     * costs a stat rather than a parse. Every failure path falls back to the caller's default:
     * a hook must not throw, or Xposed drops the patch for that call.
     */
    class Reader(private val path: String = CONFIG_PATH) {
        private var stamp = -1L
        private var values: Map<String, String> = emptyMap()

        @Synchronized
        private fun current(): Map<String, String> {
            try {
                val file = File(path)
                if (!file.isFile) {
                    values = emptyMap()
                    stamp = -1L
                    return values
                }
                val modified = file.lastModified()
                if (modified != stamp) {
                    values = parse(file.readText())
                    stamp = modified
                }
            } catch (_: Throwable) {
                // Keep whatever was last read; defaults apply if nothing ever was.
            }
            return values
        }

        fun boolean(key: String, default: Boolean): Boolean =
            current()[key]?.toBooleanStrictOrNull() ?: default

        fun int(key: String, default: Int): Int =
            current()[key]?.toIntOrNull() ?: default
    }
}
