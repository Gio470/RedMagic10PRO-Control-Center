package com.elitedarkkaiser.redmagic.xposed

import android.content.Context
import android.provider.Settings

/** Independent implementations of the two behaviours documented by XPRAMT/RedMagicX. */
object SoftwareControlsConfig {
    const val VOLUME_PATH = "/data/system/redmagic_volume_controls.conf"
    const val VOLUME_ENABLED = "volume_enabled"
    const val VOLUME_STEP = "volume_step"
    const val HIDE_LAUNCHER = "rmcc_hide_launcher_recents"
    const val LAUNCHER_PACKAGE = "rmcc_recents_launcher"
    const val LAUNCHER_COMPONENT = "rmcc_recents_home_component"
    const val STOCK_LAUNCHER = "com.zte.mifavor.launcher"
    const val MIN_STEP = 1
    const val MAX_STEP = 10

    fun clampStep(value: Int): Int = value.coerceIn(MIN_STEP, MAX_STEP)

    fun validLauncher(value: String): String = value.takeIf {
        it != STOCK_LAUNCHER && it != "com.android.settings" &&
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(it)
    }.orEmpty()

    fun serializeVolume(enabled: Boolean, step: Int): String =
        "$VOLUME_ENABLED=$enabled\n$VOLUME_STEP=${clampStep(step)}\n"

    data class VolumeState(val enabled: Boolean = false, val step: Int = MIN_STEP) {
        /** Only media raise/lower events are changed; ring, calls and mute stay stock. */
        fun target(stream: Int, direction: Int, current: Int, minimum: Int, maximum: Int): Int? {
            if (!enabled || stream != 3 || direction !in listOf(-1, 1) || minimum > maximum) return null
            return (current.toLong() + direction * clampStep(step).toLong())
                .coerceIn(minimum.toLong(), maximum.toLong()).toInt()
        }
    }

    class VolumeReader(path: String = VOLUME_PATH) {
        private val reader = WindowModConfig.Reader(path)
        fun state() = VolumeState(
            reader.boolean(VOLUME_ENABLED, false),
            clampStep(reader.int(VOLUME_STEP, MIN_STEP))
        )
    }

    data class LauncherState(val enabled: Boolean = false, val packageName: String = "", val component: String = "") {
        val active: Boolean get() = enabled && validLauncher(packageName).isNotEmpty()
    }

    /** The launcher cannot read /data/system; Global settings are readable in its process. */
    fun launcherState(context: Context): LauncherState = runCatching {
        LauncherState(
            Settings.Global.getInt(context.contentResolver, HIDE_LAUNCHER, 0) == 1,
            validLauncher(Settings.Global.getString(context.contentResolver, LAUNCHER_PACKAGE).orEmpty()),
            Settings.Global.getString(context.contentResolver, LAUNCHER_COMPONENT).orEmpty()
        )
    }.getOrDefault(LauncherState())
}
