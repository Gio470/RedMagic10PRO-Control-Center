package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import com.elitedarkkaiser.redmagic.RootShell
import java.io.File

object SoftwareControlsSettings {
    const val PREFS = "software_controls"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun volumeEnabled(context: Context) = prefs(context).getBoolean(SoftwareControlsConfig.VOLUME_ENABLED, false)
    fun setVolumeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SoftwareControlsConfig.VOLUME_ENABLED, value).apply()
    }
    fun volumeStep(context: Context) = SoftwareControlsConfig.clampStep(
        prefs(context).getInt(SoftwareControlsConfig.VOLUME_STEP, SoftwareControlsConfig.MIN_STEP)
    )
    fun setVolumeStep(context: Context, value: Int) {
        prefs(context).edit().putInt(SoftwareControlsConfig.VOLUME_STEP, SoftwareControlsConfig.clampStep(value)).apply()
    }

    fun hideLauncher(context: Context) = prefs(context).getBoolean(SoftwareControlsConfig.HIDE_LAUNCHER, false)
    fun setHideLauncher(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SoftwareControlsConfig.HIDE_LAUNCHER, value).apply()
    }
    fun launcherPackage(context: Context): String {
        val p = prefs(context)
        if (p.contains(SoftwareControlsConfig.LAUNCHER_PACKAGE)) {
            return SoftwareControlsConfig.validLauncher(p.getString(SoftwareControlsConfig.LAUNCHER_PACKAGE, "").orEmpty())
        }
        // Start with the current HOME when it is third-party, without changing the default HOME.
        return runCatching {
            context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName.orEmpty()
        }.getOrDefault("").let(SoftwareControlsConfig::validLauncher)
    }
    fun setLauncherPackage(context: Context, value: String) {
        prefs(context).edit().putString(SoftwareControlsConfig.LAUNCHER_PACKAGE,
            SoftwareControlsConfig.validLauncher(value)).apply()
    }

    data class Launcher(val packageName: String, val label: String, val component: ComponentName)
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun launchers(context: Context): List<Launcher> = runCatching {
        val pm = context.packageManager
        pm.queryIntentActivities(homeIntent(), 0).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (!activity.enabled || !activity.exported) return@mapNotNull null
            val pkg = SoftwareControlsConfig.validLauncher(activity.packageName)
            if (pkg.isEmpty()) null else Launcher(pkg, info.loadLabel(pm)?.toString() ?: pkg,
                ComponentName(pkg, activity.name))
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    fun defaultHome(context: Context): ComponentName? = runCatching {
        context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
    }.getOrNull()

    /** An explicit launcher choice also applies HOME, as in RedMagicX. */
    @Synchronized
    fun selectLauncher(context: Context, launcher: Launcher): Boolean {
        if (launchers(context).none { it.component == launcher.component }) return false
        val user = android.os.Process.myUid() / 100000
        val component = launcher.component.flattenToShortString().replace("'", "'\\''")
        if (!RootShell.exec("cmd package set-home-activity --user $user '$component'")) return false
        if (defaultHome(context) != launcher.component) return false
        setLauncherPackage(context, launcher.packageName)
        return pushLauncher(context)
    }

    /** Atomic replacement: an audio callback sees either the old complete file or the new one. */
    @Synchronized
    fun pushVolume(context: Context): Boolean = runCatching {
        val staged = File(context.cacheDir, "volume_controls.conf")
        staged.writeText(SoftwareControlsConfig.serializeVolume(volumeEnabled(context), volumeStep(context)))
        val temporary = SoftwareControlsConfig.VOLUME_PATH + ".new"
        RootShell.exec(
            "cp '${staged.absolutePath}' '$temporary' && chmod 644 '$temporary' && " +
                "chown 1000:1000 '$temporary' && restorecon '$temporary' && " +
                "mv -f '$temporary' '${SoftwareControlsConfig.VOLUME_PATH}' && " +
                "restorecon '${SoftwareControlsConfig.VOLUME_PATH}'"
        )
    }.getOrDefault(false)

    @Synchronized
    fun pushLauncher(context: Context): Boolean {
        val pkg = launcherPackage(context)
        val enabled = hideLauncher(context) && pkg.isNotEmpty()
        val home = defaultHome(context)?.takeIf { it.packageName == pkg }
            ?: launchers(context).firstOrNull { it.packageName == pkg }?.component
        val component = home?.flattenToShortString().orEmpty().replace("'", "'\\''")
        // Stand down while the selected package changes. Package names are validated above.
        return RootShell.exec(
            "settings put global ${SoftwareControlsConfig.HIDE_LAUNCHER} 0 && " +
                "settings put global ${SoftwareControlsConfig.LAUNCHER_PACKAGE} '$pkg' && " +
                "settings put global ${SoftwareControlsConfig.LAUNCHER_COMPONENT} '$component' && " +
                "settings put global ${SoftwareControlsConfig.HIDE_LAUNCHER} ${if (enabled) 1 else 0}"
        )
    }
}
