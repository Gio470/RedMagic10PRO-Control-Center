package com.elitedarkkaiser.redmagic

/** Restores only values still owned by the removed per-app hardware features. */
internal object LegacyHardwareRestorePolicy {
    val performanceKeys = listOf("performance_mode_package", "game_chicken_mode_switch", "performance_mode_value")
    fun restorePerformance(original: Map<String, String?>, current: Map<String, String?>,
        pkg: String, mode: Int, applied: Boolean): Map<String, String?> {
        val currentPackage = current["performance_mode_package"]
        if (applied && current["performance_mode_value"] != mode.toString()) return emptyMap()
        if (currentPackage != pkg && (applied || currentPackage != original["performance_mode_package"])) return emptyMap()
        val written = mapOf("performance_mode_package" to pkg, "game_chicken_mode_switch" to "0",
            "performance_mode_value" to mode.toString())
        return performanceKeys.filter { original.containsKey(it) && current[it] == written[it] }
            .associateWith { original[it] }
    }
    const val PREFERRED_MODE = "_preferred_mode"
    const val WRITTEN_MODE = "_written_mode"
    const val PREVIOUS_MODE = "_previous_mode"
    val keys = listOf("min_refresh_rate", "peak_refresh_rate")
    fun matches(value: String?, rate: Int) = value?.toFloatOrNull()?.let {
        it.isFinite() && kotlin.math.abs(it - rate) < 0.5f
    } == true

    // Keep a newer user/Game Space value. Missing originals belong to older native-only builds.
    fun restoreValues(original: Map<String, String?>, current: Map<String, String?>, rate: Int) =
        keys.filter { original.containsKey(it) && (matches(current[it], rate) ||
            original["_previous_rate"]?.toIntOrNull()?.let { previous -> matches(current[it], previous) } == true) }
            .associateWith { original[it] }

    /** Unknown/error output differs from an explicitly unset mode. Never guess its restore value. */
    fun parsePreferredMode(output: String): String? {
        val value = output.lineSequence().firstOrNull { it.startsWith("User preferred display mode:") }
            ?.substringAfter(':')?.trim() ?: return null
        if (value == "null") return "none"
        // RedMagicOS 11 prints the Display.INVALID_* fields instead of the literal null.
        // Only the complete unset sentinel is safe to restore with clear-user-preferred-display-mode.
        val parts = value.split(Regex("\\s+"))
        if (parts.size == 3 && parts[0].toIntOrNull() == -1 && parts[1].toIntOrNull() == -1 &&
            parts[2].toFloatOrNull() == 0f) return "none"
        return canonicalMode(value)
    }
    fun canonicalMode(value: String): String? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.size != 3) return null
        val width = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val height = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val rate = parts[2].toFloatOrNull()?.takeIf { it.isFinite() && it in 30f..360f } ?: return null
        return "$width $height $rate"
    }
    fun sameMode(first: String?, second: String?): Boolean {
        if (first == null || second == null) return false
        if (first == "none" || second == "none") return first == second
        val a = canonicalMode(first)?.split(' ') ?: return false
        val b = canonicalMode(second)?.split(' ') ?: return false
        return a[0] == b[0] && a[1] == b[1] && kotlin.math.abs(a[2].toFloat() - b[2].toFloat()) < 0.5f
    }
    fun restoreMode(original: Map<String, String?>, current: String?): String? =
        original[PREFERRED_MODE]?.takeIf {
            sameMode(current, original[WRITTEN_MODE]) || sameMode(current, original[PREVIOUS_MODE])
        }
}
