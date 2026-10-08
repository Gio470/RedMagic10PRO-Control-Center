package com.elitedarkkaiser.redmagic

import android.content.Context
import java.io.File

/**
 * Display density, over the `wm density` shell command.
 *
 * Android Screener, which this is modelled on, reaches IWindowManager through Shizuku and
 * HiddenApiBypass. This app already has root, for which the shell command is both simpler and
 * steadier across releases than reflecting into hidden framework methods.
 *
 * A wrong density can leave a phone awkward or impossible to drive, so a change is never simply
 * applied: [apply] arms a revert that runs whether or not this app is still alive to run it.
 */
object DisplayDensity {

    /**
     * Set while a change is awaiting confirmation. MainActivity declares density changes as handled
     * so the confirmation dialog survives the one it causes; outside that window it recreates
     * itself as before, so changing display size in Android's own settings still rebuilds the UI at
     * the new scale instead of leaving it drawn for the old one.
     */
    @Volatile
    var confirmInProgress: Boolean = false

    private const val KEEP_FLAG = "/data/local/tmp/rmcc_dpi_keep"
    private const val WATCHDOG = "/data/local/tmp/rmcc_dpi_watchdog.sh"

    /** [current] is what the display is using now; [physical] is the panel's own density. */
    data class State(val physical: Int, val current: Int) {
        val isOverridden: Boolean get() = current != physical
    }

    /**
     * `wm density` prints "Physical density: N" and, only when one is set, "Override density: N".
     * A missing override line means the display is running at its physical density.
     */
    fun read(): State? {
        val out = RootShell.execForOutput("wm density") ?: return null
        val physical = Regex("Physical density:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val override = Regex("Override density:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
        return State(physical = physical, current = override ?: physical)
    }

    /**
     * Applies [dpi] and arms a revert to [previous] in [revertAfterSeconds], unless [keep] is
     * called first.
     *
     * The revert is a detached root shell rather than a timer in this app, because the app is the
     * thing most likely to be lost: changing density is a configuration change, and a density that
     * makes the screen unusable is exactly when the user cannot reach a button to undo it. Written
     * as a script file so nothing depends on quoting surviving a shell round trip.
     */
    fun apply(context: Context, dpi: Int, previous: State, revertAfterSeconds: Int): Boolean {
        val revert = revertCommand(previous)
        val script = """
            #!/system/bin/sh
            sleep $revertAfterSeconds
            [ -f $KEEP_FLAG ] || $revert
            rm -f $KEEP_FLAG
        """.trimIndent()

        val staged = File(context.cacheDir, "rmcc_dpi_watchdog.sh")
        return runCatching {
            staged.writeText(script)
            RootShell.exec(
                "rm -f $KEEP_FLAG; " +
                    "cp '${staged.absolutePath}' $WATCHDOG; chmod 755 $WATCHDOG; " +
                    "wm density $dpi; " +
                    "nohup sh $WATCHDOG >/dev/null 2>&1 &"
            )
        }.getOrDefault(false)
    }

    /** Confirms the change, so the armed revert lapses instead of firing. */
    fun keep(): Boolean = RootShell.exec("touch $KEEP_FLAG")

    /** Reverts now, and disarms the watchdog so it does not apply the same thing again later. */
    fun revert(previous: State): Boolean =
        RootShell.exec("touch $KEEP_FLAG; ${revertCommand(previous)}")

    /** Clears any override outright, back to the panel's own density. */
    fun reset(): Boolean = RootShell.exec("touch $KEEP_FLAG; wm density reset")

    private fun revertCommand(previous: State): String =
        if (previous.isOverridden) "wm density ${previous.current}" else "wm density reset"
}
