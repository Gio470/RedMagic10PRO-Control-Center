package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File

/**
 * The config the icon pack hooks read, and where it lives.
 *
 * Mostly not a file, unlike [WindowModConfig] and [GameAssistConfig]. These hooks run inside every
 * app whose icons are being replaced -- the launcher, SystemUI, Settings -- and an ordinary app
 * process can read neither /data/system (system_server's directory) nor /data/local/tmp (SELinux
 * stopped letting untrusted_app touch shell_data_file in Android 9). `Settings.Global` is readable
 * by any app without a permission and writable with root, so it carries the settings instead.
 *
 * The exception is the one hook that runs in system_server, which is on the wrong side of both
 * problems: it installs before there is a settings provider to ask, and it sits on a path far too
 * hot to put a Binder call on. The pack name is mirrored into [SYSTEM_CONFIG_PATH] for it, and
 * [SystemReader] is what reads it.
 *
 * Upstream (github.com/RichardLuo0/global-icon-pack-android) uses XSharedPreferences for this,
 * which works only because LSPosed bridges module preferences into hooked processes. This app
 * already requires root for everything else, so it does not need to depend on that.
 */
object IconPackConfig {
    /**
     * Where the chosen pack is mirrored for the one hook that runs in system_server.
     *
     * `Settings.Global` cannot answer there: the hook installs before the settings provider is up,
     * and the callback runs on a path hot enough that a Binder round trip per call is out of the
     * question. Same directory and same reasoning as [WindowModConfig] -- /data/system is
     * system_server's own, and the app writes it as root.
     */
    const val SYSTEM_CONFIG_PATH = "/data/system/redmagic_icon_pack.conf"

    const val KEY_ENABLED = "rmcc_icon_pack_enabled"
    const val KEY_PACK = "rmcc_icon_pack"
    const val KEY_AS_FALLBACK = "rmcc_icon_pack_as_fallback"
    const val KEY_SHORTCUT = "rmcc_icon_pack_shortcut"
    const val KEY_FORCE_MONOCHROME = "rmcc_icon_pack_force_monochrome"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_PACK = ""
    const val DEFAULT_AS_FALLBACK = false
    const val DEFAULT_SHORTCUT = false
    const val DEFAULT_FORCE_MONOCHROME = false

    data class State(
        val enabled: Boolean,
        val pack: String,
        val asFallback: Boolean,
        val shortcut: Boolean,
        val forceMonochrome: Boolean,
    ) {
        /** Nothing to do without a pack to read icons out of, whatever else is set. */
        val active: Boolean get() = enabled && pack.isNotEmpty()
    }

    /**
     * Read once per process, at the point the first icon is asked for -- "local mode" in upstream's
     * terms: every hooked app loads the pack itself on launch and keeps it for that process's life,
     * so a settings change lands when the app is next started.
     *
     * Must not throw: a hook that throws is dropped by Xposed for that call.
     */
    fun read(context: Context): State {
        val resolver = context.contentResolver
        fun bool(key: String, default: Boolean) =
            runCatching { Settings.Global.getInt(resolver, key, if (default) 1 else 0) != 0 }
                .getOrDefault(default)

        return State(
            enabled = bool(KEY_ENABLED, DEFAULT_ENABLED),
            pack = runCatching { Settings.Global.getString(resolver, KEY_PACK) }
                .getOrNull() ?: DEFAULT_PACK,
            asFallback = bool(KEY_AS_FALLBACK, DEFAULT_AS_FALLBACK),
            shortcut = bool(KEY_SHORTCUT, DEFAULT_SHORTCUT),
            forceMonochrome = bool(KEY_FORCE_MONOCHROME, DEFAULT_FORCE_MONOCHROME),
        )
    }

    /**
     * The chosen pack, for [IconPackHooks]' system_server hook.
     *
     * Answers from a field, re-checking the file's timestamp at most once every [REFRESH_MS], so
     * the cost per call on a path system_server walks hundreds of thousands of times at boot is a
     * clock read and a field read. The window is what a settings change waits out before the
     * visibility bypass follows it -- picking a pack force-stops the launcher anyway, and by the
     * time it has restarted this has long since caught up.
     *
     * Must not throw: a hook that throws is dropped by Xposed for that call.
     */
    class SystemReader(private val path: String = SYSTEM_CONFIG_PATH) {
        @Volatile
        private var pack: String = ""

        @Volatile
        private var checkedAt = Long.MIN_VALUE

        @Volatile
        private var stamp = -1L

        fun pack(): String {
            val now = SystemClock.uptimeMillis()
            if (now - checkedAt < REFRESH_MS) return pack
            checkedAt = now
            try {
                val file = File(path)
                if (!file.isFile) {
                    pack = ""
                    stamp = -1L
                    return pack
                }
                val modified = file.lastModified()
                if (modified != stamp) {
                    pack = file.readText().lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("$KEY_PACK=") }
                        ?.substringAfter('=')
                        ?.trim()
                        .orEmpty()
                    stamp = modified
                }
            } catch (_: Throwable) {
                // Keep the last answer; an unreadable file is not a reason to stop theming.
            }
            return pack
        }

        /**
         * True only when the file is there and says no pack is selected -- i.e. when the app has
         * run at least once and the answer is a definite no. A file that is not there yet is not
         * an answer, and the caller installs its hook rather than guessing.
         */
        fun knownEmpty(): Boolean = File(path).isFile && pack().isEmpty()

        private companion object {
            const val REFRESH_MS = 2_000L
        }
    }

    /** The system-side file's contents, written by the app as root. */
    fun serializeForSystem(pack: String): String =
        "# Written by RedMagic Control Center. Read by its Xposed hook in system_server.\n" +
            "$KEY_PACK=$pack\n"
}
