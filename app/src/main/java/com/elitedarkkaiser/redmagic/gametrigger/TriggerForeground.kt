package com.elitedarkkaiser.redmagic.gametrigger

import android.os.Handler
import android.os.SystemClock

/** Game engines may produce no accessibility events; sample the real top-resumed activity as root. */
class TriggerForeground(private val main: Handler, private val changed: (String) -> Unit) {
    private var process: Process? = null
    private var reader: Thread? = null
    private var lastSampleAt = 0L
    private var latest: String? = null
    private var retryAfter = 0L

    fun current(): String? = latest?.takeIf {
        process?.isAlive == true && SystemClock.elapsedRealtime() - lastSampleAt in 0L..2_500L
    }

    fun start() {
        if (process?.isAlive == true || SystemClock.elapsedRealtime() < retryAfter) return
        stop()
        retryAfter = SystemClock.elapsedRealtime() + 5_000L
        val child = runCatching { ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true).start() }.getOrNull() ?: return
        process = child
        reader = Thread({
            runCatching {
                child.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        parse(line)?.let { pkg -> main.post {
                            if (process === child) {
                                latest = pkg
                                lastSampleAt = SystemClock.elapsedRealtime()
                                changed(pkg)
                            }
                        } }
                    }
                }
            }
            main.post { if (process === child) stop() }
        }, "trigger-foreground").apply { isDaemon = true; start() }
    }

    fun stop() {
        val old = process
        process = null
        latest = null
        lastSampleAt = 0L
        reader?.interrupt()
        reader = null
        old?.let { runCatching { it.destroy() } }
    }

    companion object {
        private val packageName = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        internal fun parse(line: String): String? = line.trim().takeIf { it.startsWith("TGK_FOREGROUND ") }
            ?.removePrefix("TGK_FOREGROUND ")?.takeIf { packageName.matches(it) }

        // Same authoritative top-resumed source used by the Toolbox's gameplay runtime.
        private val COMMAND = """
            while true; do
                dumpsys activity activities 2>/dev/null | sed -n 's/.*topResumedActivity=.* u[0-9][0-9]* \([^/ ]*\)\/.*/TGK_FOREGROUND \1/p'
                sleep 1
            done
        """.trimIndent()

        data class AppWindow(val packageName: String, val focused: Boolean, val active: Boolean, val layer: Int)

        /** An active system overlay must not hide the focused application underneath it. */
        internal fun fromWindows(windows: List<AppWindow>, eventPackage: String?): String? {
            val apps = windows.filter { packageName.matches(it.packageName) }.sortedByDescending { it.layer }
            return (apps.firstOrNull { it.focused } ?: apps.firstOrNull { it.active } ?: apps.firstOrNull())?.packageName
                ?: eventPackage?.takeIf { packageName.matches(it) }
        }
    }
}
