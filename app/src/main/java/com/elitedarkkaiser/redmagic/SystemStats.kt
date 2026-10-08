package com.elitedarkkaiser.redmagic

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.util.Locale

/**
 * Live CPU and memory readings, ported from RohitKushvaha01/TaskManager (its CpuInfoReader and RAM
 * screen) into this app's plain-object, no-coroutines shape.
 *
 * Everything here reads sysfs, /proc or the ActivityManager -- none of it needs root, unlike the
 * rest of [HardwareController]. The file IO is still slow enough that every entry point belongs on
 * a background thread; the Home tab calls them from the status refresh it already runs off the
 * main thread.
 *
 * Where TaskManager gets CPU load from its own root daemon, this computes it from /proc/stat
 * deltas -- the same numbers the daemon reads, and the aggregate line stays world-readable under
 * the hidepid rules that hide other processes' entries.
 */
object SystemStats {

    // ---- CPU ---------------------------------------------------------------------------------

    /** A run of cores that share a maximum frequency: the big/little split, as the kernel has it. */
    data class CpuCluster(
        val cores: Int,
        val maxKHz: Long,
        val currentKHz: Long
    )

    data class CpuInfo(
        val soc: String,
        val abi: String,
        val arch: String,
        val cores: Int,
        val governor: String?,
        val clusters: List<CpuCluster>,
        /** Null until two reads have happened -- load is a delta, so the first one has nothing to
         *  compare against. */
        val usagePercent: Int?
    )

    /**
     * Cluster membership and core count never change while the phone is on, so the directory scan
     * behind them runs once. Only the frequencies are re-read.
     */
    private var cachedCoreDirs: List<File>? = null
    private var cachedClusters: List<List<File>>? = null
    private var cachedSoc: String? = null

    @Volatile private var lastCpuTotal = 0L
    @Volatile private var lastCpuIdle = 0L

    fun readCpu(): CpuInfo {
        if (cachedClusters == null) {
            val dirs = runCatching {
                File("/sys/devices/system/cpu/")
                    .listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) }
                    ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: 0 }
                    ?: emptyList()
            }.getOrDefault(emptyList())

            cachedCoreDirs = dirs
            // Grouped by the ceiling each core reports, highest cluster first -- which is how a
            // big.LITTLE part separates, without needing to know the SoC's own topology.
            cachedClusters = dirs
                .groupBy { core ->
                    readLong("${core.path}/cpufreq/cpuinfo_max_freq")
                        ?: readLong("${core.path}/cpufreq/scaling_max_freq")
                        ?: 0L
                }
                .entries
                .sortedByDescending { it.key }
                .map { it.value }
        }

        val clusters = cachedClusters.orEmpty().map { cores ->
            val core = cores.first()
            CpuCluster(
                cores = cores.size,
                maxKHz = freq(
                    "${core.path}/cpufreq/scaling_max_freq",
                    "${core.path}/cpufreq/cpuinfo_max_freq"
                ),
                currentKHz = freq(
                    "${core.path}/cpufreq/scaling_cur_freq",
                    "${core.path}/cpufreq/cpuinfo_cur_freq"
                )
            )
        }

        return CpuInfo(
            soc = soc(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            arch = System.getProperty("os.arch") ?: "Unknown",
            cores = cachedCoreDirs?.size ?: Runtime.getRuntime().availableProcessors(),
            governor = readText("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"),
            clusters = clusters,
            usagePercent = readCpuUsage()
        )
    }

    /**
     * The SoC's name, read once. [HardwareController.readCpuModel] shells out to getprop, and this
     * is called on every status refresh -- the part number is not going to change between them.
     */
    private fun soc(): String {
        cachedSoc?.let { return it }
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL.isNotBlank()) {
            Build.SOC_MODEL
        } else {
            HardwareController.readCpuModel()
        }
        cachedSoc = name
        return name
    }

    /**
     * Load since the previous call, from /proc/stat's aggregate line.
     *
     * The kernel counts jiffies spent in each state cumulatively, so a single reading says nothing
     * about now -- what matters is how much of the time *since the last look* was not idle. Null on
     * the first call, and on any read where the counters didn't move.
     */
    private fun readCpuUsage(): Int? {
        val line = (readText("/proc/stat") ?: RootShell.execForOutput("cat /proc/stat"))
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("cpu ") }
            ?: return null

        val fields = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 4) return null

        // user nice system idle iowait irq softirq steal ...
        val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
        val total = fields.sum()

        val totalDelta = total - lastCpuTotal
        val idleDelta = idle - lastCpuIdle
        val hadPrevious = lastCpuTotal > 0L

        lastCpuTotal = total
        lastCpuIdle = idle

        if (!hadPrevious || totalDelta <= 0L) return null
        return (((totalDelta - idleDelta) * 100.0) / totalDelta).toInt().coerceIn(0, 100)
    }

    /** First path that reports a plausible frequency; 0 when the core exposes none. */
    private fun freq(vararg paths: String): Long {
        for (path in paths) {
            // Below 10 MHz is a placeholder rather than a real clock -- offline cores report 0,
            // and some kernels leave a stub value behind in the scaling nodes.
            val value = readLong(path)
            if (value != null && value >= 10_000L) return value
        }
        return 0L
    }

    // ---- Memory ------------------------------------------------------------------------------

    data class MemInfo(
        val totalBytes: Long,
        val usedBytes: Long,
        val swapTotalBytes: Long,
        val swapUsedBytes: Long
    ) {
        val usedPercent: Int
            get() = percent(usedBytes, totalBytes)

        val swapUsedPercent: Int
            get() = percent(swapUsedBytes, swapTotalBytes)

        private fun percent(part: Long, whole: Long): Int =
            if (whole <= 0L) 0 else ((part.toDouble() / whole) * 100).toInt().coerceIn(0, 100)
    }

    fun readMemory(context: Context): MemInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)

        // Swap has no ActivityManager equivalent, so it comes from /proc/meminfo, in kB.
        val meminfo = readText("/proc/meminfo") ?: RootShell.execForOutput("cat /proc/meminfo")
        fun kb(key: String): Long = meminfo
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
            ?: 0L

        val swapTotal = kb("SwapTotal") * 1024
        val swapFree = kb("SwapFree") * 1024

        return MemInfo(
            totalBytes = info.totalMem,
            usedBytes = info.totalMem - info.availMem,
            swapTotalBytes = swapTotal,
            swapUsedBytes = (swapTotal - swapFree).coerceAtLeast(0L)
        )
    }

    // ---- Formatting --------------------------------------------------------------------------

    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.ENGLISH, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.ENGLISH, "%.0f MB", bytes / mb)
            else -> String.format(Locale.ENGLISH, "%.0f KB", bytes / kb)
        }
    }

    fun formatFreq(kHz: Long): String = when {
        kHz <= 0L -> "—"
        kHz >= 1_000_000L -> String.format(Locale.ENGLISH, "%.2f GHz", kHz / 1_000_000.0)
        else -> "${kHz / 1000} MHz"
    }

    // ---- File helpers ------------------------------------------------------------------------

    private fun readText(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readLong(path: String): Long? = readText(path)?.toLongOrNull()
}
