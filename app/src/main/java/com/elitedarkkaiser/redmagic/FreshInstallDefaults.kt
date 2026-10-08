package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/** Initializes device-backed controls only for a new installation, after root/device checks. */
internal object FreshInstallDefaults {
    private const val PREFS = "fresh_install_defaults"
    private const val PREPARED = "prepared"
    private const val PENDING = "pending_controls"
    private val preferenceLock = Any()
    internal val controls = setOf(
        "fan", "triggers", "magic_key", "density", "block_updates",
        "floating_windows", "gameassist", "icon_pack"
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Call before startup writes any preferences. Existing installs keep their device state. */
    @Synchronized
    fun prepare(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(PREPARED, false)) return
        val existing = listOf(
            "appearance", AppPrefs.PREFS_NAME, "triggers", "root_access_cache",
            "window_mods", "gameassist_mods", "icon_pack", "system_theme", "perf_tweaks"
        ).any { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.isNotEmpty() }
        p.edit().putBoolean(PREPARED, true)
            .putStringSet(PENDING, if (existing) emptySet() else controls)
            .commit()
    }

    /** Failed writes remain pending; successful controls are never reset on later launches. */
    @Synchronized
    fun initializeDeviceControls(
        context: Context,
        actions: Map<String, () -> Boolean> = deviceActions(context)
    ) {
        val p = prefs(context)
        val pending = p.getStringSet(PENDING, emptySet()).orEmpty().toSet()
        for (key in pending) {
            if (!p.getStringSet(PENDING, emptySet()).orEmpty().contains(key)) continue
            val action = actions[key] ?: continue
            if (runCatching { action() }.getOrDefault(false)) {
                keepUserChoice(context, key)
            }
        }
    }

    /** A user choice takes priority over any failed first-install reset still awaiting retry. */
    fun keepUserChoice(context: Context, vararg keys: String) = synchronized(preferenceLock) {
        val p = prefs(context)
        val pending = p.getStringSet(PENDING, emptySet()).orEmpty().toMutableSet()
        if (pending.removeAll(keys.toSet())) {
            p.edit().putStringSet(PENDING, pending).apply()
        }
    }

    private fun deviceActions(context: Context): Map<String, () -> Boolean> = mapOf(
        "fan" to {
            HardwareController.enableFan(false).also { ok ->
                if (ok) saveFanPowerEnabledStorage(context, false)
            }
        },
        "triggers" to { HardwareController.disableTriggers() },
        "magic_key" to {
            MasterSwitches.setLastMagicKeyMode(context, MagicKeyActions.readModeLabel())
            HardwareController.disableSliderSystemHandling()
        },
        "density" to {
            DisplayDensity.read()?.let { !it.isOverridden || DisplayDensity.reset() } ?: false
        },
        "block_updates" to {
            !OtaUpdates.blocked(context) || OtaUpdates.setBlocked(context, false)
        },
        "floating_windows" to {
            com.elitedarkkaiser.redmagic.xposed.WindowModSettings.pushToDevice(context)
        },
        "gameassist" to {
            com.elitedarkkaiser.redmagic.xposed.GameAssistSettings.pushToDevice(context)
        },
        "icon_pack" to {
            com.elitedarkkaiser.redmagic.xposed.IconPackSettings.pushToDevice(context)
        }
    )
}
