package com.elitedarkkaiser.redmagic.xposed

import java.io.File

/**
 * The config the GameAssist/GameSpace hooks read, and where it lives.
 *
 * Ported from khanhnguyen9872/NubiaToolkit, which reads its settings through a ContentProvider
 * (its hooks and its UI are the same app, so the provider is just IPC to itself). Here the hooks
 * run inside `cn.nubia.gameassist` and `cn.nubia.gamelauncher` -- both a different app than this
 * one -- so a provider call would need a manifest-declared cross-app query the target apps have no
 * reason to grant. A world-readable file under /data/local/tmp sidesteps that the same way
 * [DisplayDensity]'s watchdog script does: any app process can open a path it already knows, no
 * provider or permission grant required.
 */
object GameAssistConfig {
    const val CONFIG_PATH = "/data/local/tmp/rmcc_gameassist_mods.conf"
    const val STATUS_PATH = "/data/local/tmp/rmcc_gameassist_mods.status"

    /** Master switch: off means every tweak below stands down and GameAssist behaves as stock. */
    const val KEY_ENABLED = "enabled"
    const val KEY_NO_KILL = "no_kill"
    const val KEY_GLOBAL_GAME_MODE = "global_game_mode"
    const val KEY_HIDE_ENERGY_CUBE = "hide_energy_cube"
    const val KEY_SUPER_RESOLUTION = "super_resolution"
    const val KEY_WATERMARK_LENGTH = "watermark_length"
    const val KEY_SMALL_WINDOW = "small_window"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_NO_KILL = false
    const val DEFAULT_GLOBAL_GAME_MODE = false
    const val DEFAULT_HIDE_ENERGY_CUBE = false
    const val DEFAULT_SUPER_RESOLUTION = false
    const val DEFAULT_WATERMARK_LENGTH = false
    const val DEFAULT_SMALL_WINDOW = false

    fun serialize(
        enabled: Boolean,
        noKill: Boolean,
        globalGameMode: Boolean,
        hideEnergyCube: Boolean,
        superResolution: Boolean,
        watermarkLength: Boolean,
        smallWindow: Boolean
    ): String =
        buildString {
            append("# Written by RedMagic Control Center. Read by its Xposed hooks.\n")
            append("$KEY_ENABLED=$enabled\n")
            append("$KEY_NO_KILL=$noKill\n")
            append("$KEY_GLOBAL_GAME_MODE=$globalGameMode\n")
            append("$KEY_HIDE_ENERGY_CUBE=$hideEnergyCube\n")
            append("$KEY_SUPER_RESOLUTION=$superResolution\n")
            append("$KEY_WATERMARK_LENGTH=$watermarkLength\n")
            append("$KEY_SMALL_WINDOW=$smallWindow\n")
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
     * costs a stat rather than a parse. Every failure path falls back to the caller's default: a
     * hook must not throw, or Xposed drops the patch for that call.
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
    }
}
