package com.elitedarkkaiser.redmagic

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Upgrade-only cleanup. No foreground detection, profile application or rate renewal remains. */
internal object LegacyHardwareCleanup {
    private val keys = listOf("performance", "performance_owned", "performance_status",
        "refresh", "refresh_owned", "refresh_status", "refresh_last_apply")
    private val profileStores = listOf("performance_mode_profiles", "refresh_rate_profiles")
    private val packagePattern = Regex("""[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+""")
    private data class Owned(val pkg: String, val value: Int, val applied: Boolean,
        val original: Map<String, String?>)

    fun pending(context: Context): Boolean {
        val prefs = context.getSharedPreferences("per_app_hardware", Context.MODE_PRIVATE)
        return keys.any { prefs.contains(it) } || profileStores.any {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).contains("profiles_json")
        }
    }

    fun schedule(context: Context) {
        if (!pending(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork("legacy-per-app-hardware-cleanup",
            ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LegacyHardwareCleanupWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS).build())
    }

    @Synchronized fun run(context: Context): Boolean = runCatching {
        if (!pending(context)) return@runCatching true
        val prefs = context.getSharedPreferences("per_app_hardware", Context.MODE_PRIVATE)
        check(prefs.edit().putBoolean("performance", false).putBoolean("refresh", false).commit())
        val performance = prefs.getString("performance_owned", null)?.let(::readOwned)
        val refresh = prefs.getString("refresh_owned", null)?.let(::readOwned)
        if (performance != null && !restorePerformance(context, performance)) return@runCatching false
        if (refresh != null && !restoreDisplay(context, refresh)) return@runCatching false

        // Older deletions could leave a native-only reset record without an ownership checkpoint.
        val raw = context.getSharedPreferences("refresh_rate_profiles", Context.MODE_PRIVATE)
            .getString("profiles_json", null)
        val profiles = raw?.let { JSONObject(it).optJSONArray("profiles") }
        if (profiles != null) for (index in 0 until profiles.length()) {
            val profile = profiles.optJSONObject(index) ?: continue
            if (profile.optBoolean("pendingReset") && !clearNative(context, profile.getString("packageName")))
                return@runCatching false
        }
        // Retain all checkpoints and profiles until their cleanup has been verified.
        profileStores.forEach {
            check(context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit())
        }
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        check(editor.commit())
        true
    }.onFailure { Log.w("LegacyHardwareCleanup", "Saved hardware cleanup will retry", it) }.getOrDefault(false)

    private fun readOwned(raw: String): Owned {
        val json = JSONObject(raw)
        val pkg = json.getString("package")
        require(packagePattern.matches(pkg))
        val original = json.optJSONObject("original")
        return Owned(pkg, json.getInt("value"), json.optBoolean("applied"),
            original?.let { data -> data.keys().asSequence().associateWith {
                if (data.isNull(it)) null else data.getString(it)
            } } ?: emptyMap())
    }

    private fun restorePerformance(context: Context, owned: Owned): Boolean {
        val current = LegacyHardwareRestorePolicy.performanceKeys.associateWith {
            Settings.Global.getString(context.contentResolver, it)
        }
        val values = LegacyHardwareRestorePolicy.restorePerformance(owned.original, current,
            owned.pkg, owned.value, owned.applied)
        if (!writeSettings(context, "global", values)) return false
        if (values.any { (key, value) -> Settings.Global.getString(context.contentResolver, key) != value }) return false
        if (values.containsKey("performance_mode_value")) runCatching {
            val manager = context.getSystemService(AudioManager::class.java)
            manager?.javaClass?.getMethod("setParameters", String::class.java)?.invoke(manager,
                "performance_mode_value=${values["performance_mode_value"]?.toIntOrNull() ?: 0}")
        }
        return true
    }

    private fun restoreDisplay(context: Context, owned: Owned): Boolean {
        if (owned.original["_native_request"] != "false" && !clearNative(context, owned.pkg)) return false
        val current = LegacyHardwareRestorePolicy.keys.associateWith {
            Settings.System.getString(context.contentResolver, it)
        }
        val values = LegacyHardwareRestorePolicy.restoreValues(owned.original, current, owned.value)
        if (!writeSettings(context, "system", values)) return false
        if (values.any { (key, value) -> Settings.System.getString(context.contentResolver, key) != value }) return false
        if (owned.original.containsKey(LegacyHardwareRestorePolicy.PREFERRED_MODE)) {
            val mode = preferredMode() ?: return false
            val original = LegacyHardwareRestorePolicy.restoreMode(owned.original, mode)
            if (original != null) {
                val args = if (original == "none") "clear-user-preferred-display-mode 0"
                    else "set-user-preferred-display-mode ${LegacyHardwareRestorePolicy.canonicalMode(original) ?: return false} 0"
                if (!RootShell.exec("/system/bin/cmd display $args") ||
                    !LegacyHardwareRestorePolicy.sameMode(preferredMode(), original)) return false
            }
        }
        return true
    }

    private fun preferredMode() = LegacyHardwareRestorePolicy.parsePreferredMode(
        RootShell.execForOutput("/system/bin/cmd display get-user-preferred-display-mode 0").orEmpty())

    private fun clearNative(context: Context, pkg: String): Boolean {
        require(packagePattern.matches(pkg))
        val output = RootShell.execForOutput("CLASSPATH=${quote(context.applicationInfo.sourceDir)} " +
            "/system/bin/app_process /system/bin com.elitedarkkaiser.redmagic.LegacyHardwareCleanupCli ${quote(pkg)}")
        return output?.lineSequence()?.any { it == "LEGACY_NATIVE_CLEARED" } == true
    }

    private fun writeSettings(context: Context, namespace: String, values: Map<String, String?>): Boolean {
        if (values.isEmpty()) return true
        val allowed = if (namespace == "system") LegacyHardwareRestorePolicy.keys
            else LegacyHardwareRestorePolicy.performanceKeys
        return RootShell.exec(values.entries.joinToString(" && ") { (key, value) ->
            require(key in allowed)
            val cmd = "/system/bin/settings --user ${android.os.Process.myUserHandle().hashCode()}"
            if (value == null) "$cmd delete $namespace ${quote(key)}"
            else "$cmd put $namespace ${quote(key)} ${quote(value)}"
        })
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}

class LegacyHardwareCleanupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = if (LegacyHardwareCleanup.run(applicationContext)) Result.success() else Result.retry()
}
