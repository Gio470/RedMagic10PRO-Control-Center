package com.elitedarkkaiser.redmagic.gametrigger

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import com.elitedarkkaiser.redmagic.TriggerAccessibilityService
import java.util.concurrent.TimeUnit

/** User-requested setup, run only when the mapping page/editor is opened or setup is retried. */
object TriggerSetup {
    data class State(val overlay: Boolean, val accessibility: Boolean) {
        val ready get() = overlay && accessibility
    }
    data class Result(val state: State, val message: String)
    @Volatile var running = false
        private set
    @Volatile var lastMessage = "Setup has not run yet"
        private set
    private val callbacks = mutableListOf<(Result) -> Unit>()

    fun state(context: Context): State {
        val component = ComponentName(context, TriggerAccessibilityService::class.java)
        val services = Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            services.split(':').any { ComponentName.unflattenFromString(it) == component }
        return State(Settings.canDrawOverlays(context), enabled)
    }

    fun ensure(context: Context, completed: (Result) -> Unit) {
        synchronized(this) {
            callbacks.add(completed)
            if (running) return
            running = true
        }
        val app = context.applicationContext
        Thread({
            val result = runCatching { grant(app) }.getOrElse {
                Result(state(app), "Automatic permissions couldn't be granted. Check root access.")
            }
            lastMessage = result.message
            val listeners = synchronized(this) {
                running = false
                callbacks.toList().also { callbacks.clear() }
            }
            Handler(Looper.getMainLooper()).post { listeners.forEach { it(result) } }
        }, "trigger-setup").start()
    }

    internal fun grant(context: Context, readState: () -> State = { state(context) },
        execute: (String) -> Boolean = ::rootCommand): Result {
        val before = readState()
        if (before.ready) return Result(before, "Draw over apps and Accessibility are ready")
        val component = ComponentName(context, TriggerAccessibilityService::class.java).flattenToString()
        val rooted = execute(commands(context.packageName, component, Process.myUid() / 100_000, before))
        val after = readState()
        val message = when {
            after.ready -> "Draw over apps and Accessibility are ready"
            !rooted -> "Root setup wasn't available. Grant the remaining permissions manually."
            !after.overlay && !after.accessibility -> "Firmware didn't grant the permissions. Check root access."
            !after.overlay -> "Accessibility is ready. Allow Draw over apps to finish setup."
            else -> "Draw over apps is ready. Enable Trigger service in Accessibility to finish setup."
        }
        return Result(after, message)
    }

    /** Read the current service list inside the root command, preserving every other service. */
    internal fun commands(pkg: String, component: String, user: Int, before: State): String {
        require(pkg.matches(Regex("[A-Za-z0-9_.]+")) && user >= 0)
        require(ComponentName.unflattenFromString(component)?.packageName == pkg)
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        return buildString {
            append("[ \"$(id -u)\" = 0 ] || exit 1\n")
            if (!before.overlay) append("cmd appops set --user $user ${quote(pkg)} SYSTEM_ALERT_WINDOW allow\n")
            if (!before.accessibility) {
                append("services=$(settings --user $user get secure enabled_accessibility_services) || exit 1\n")
                append("[ \"\u0024services\" = null ] && services=''\n")
                append("case \":\u0024services:\" in\n")
                append("  *${quote(":" + component + ":")}*) ;;\n")
                append("  *) services=\"\u0024{services:+\u0024services:}\"${quote(component)}\n")
                append("     settings --user $user put secure enabled_accessibility_services \"\u0024services\" ;;\n")
                append("esac\nsettings --user $user put secure accessibility_enabled 1\n")
            }
            append("exit 0\n")
        }
    }

    /** Bound the root-manager wait and drain output without blocking the UI or filling a pipe. */
    private fun rootCommand(command: String): Boolean {
        val child = runCatching { ProcessBuilder("su", "-c", command).redirectErrorStream(true).start() }.getOrNull()
            ?: return false
        val drain = Thread({ runCatching { child.inputStream.use { stream ->
            val buffer = ByteArray(1024)
            while (stream.read(buffer) >= 0) { /* discard command diagnostics; verify settings instead */ }
        } } }, "trigger-setup-output").apply { isDaemon = true; start() }
        return try {
            if (!child.waitFor(15, TimeUnit.SECONDS)) { child.destroyForcibly(); false }
            else child.exitValue() == 0
        } finally {
            if (child.isAlive) child.destroyForcibly()
            drain.join(200)
        }
    }
}
