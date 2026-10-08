package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.Build
import android.app.usage.UsageStatsManager

object DashboardSnapshot {

    private fun read(path: String): String? {
        return RootShell.execForOutput("cat $path")?.trim()?.ifEmpty { null }
    }

    fun readFanEnabled(): String = read("/sys/kernel/fan/fan_enable") ?: "?"
    fun readFanRpm(): String = read("/sys/kernel/fan/fan_speed_count") ?: "?"
    fun readFanLevel(): String = read("/sys/kernel/fan/fan_speed_level") ?: "?"
    fun readPumpEnabled(): String = read("/proc/driver/micropump/enable") ?: "?"
    fun readPumpFreq(): String = read("/proc/driver/micropump/freq") ?: "?"
    fun readPumpSpeed(): String = read("/proc/driver/micropump/speed") ?: "?"

    fun readCpuTempC(): String {
        val candidates = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone65/temp",
            "/sys/class/thermal/thermal_zone80/temp"
        )

        for (path in candidates) {
            val raw = read(path)?.toFloatOrNull() ?: continue
            val c = if (raw > 1000f) raw / 1000f else raw
            if (c in 10f..120f) return String.format("%.1f", c)
        }
        return "?"
    }

    fun readCpuTempF(): String {
        val c = readCpuTempC().toFloatOrNull() ?: return "?"
        return String.format("%.1f", (c * 9f / 5f) + 32f)
    }

    fun currentForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000L,
            now
        )
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    fun buildSummary(context: Context): String {
        val pkg = currentForegroundPackage(context) ?: "Unavailable"
        return buildString {
            append("Model: ${Build.MODEL ?: "Unknown"}\n")
            append("Root: ${if (RootShell.hasRoot()) "Granted" else "Missing"}\n")
            append("CPU Temp: ${readCpuTempF()}°F\n")
            append("Fan: ${readFanEnabled()} • Level ${readFanLevel()} • ${readFanRpm()} RPM\n")
            append("Pump: ${readPumpEnabled()} • Freq ${readPumpFreq()} • Speed ${readPumpSpeed()}\n")
            append("Foreground app: $pkg")
        }
    }
}
