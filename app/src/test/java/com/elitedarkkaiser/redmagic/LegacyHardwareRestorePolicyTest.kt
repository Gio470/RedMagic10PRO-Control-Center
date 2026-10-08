package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class LegacyHardwareRestorePolicyTest {
    private val original = mapOf("min_refresh_rate" to null, "peak_refresh_rate" to "90.0")

    @Test fun ownedPerformanceStateRestoresTheSavedValuesIncludingAbsentKeys() {
        val original = mapOf("performance_mode_package" to null, "performance_mode_value" to "0",
            "game_chicken_mode_switch" to "1")
        val current = mapOf("performance_mode_package" to "com.game.one", "performance_mode_value" to "3",
            "game_chicken_mode_switch" to "0")
        assertEquals(original, LegacyHardwareRestorePolicy.restorePerformance(original, current, "com.game.one", 3, true))
    }
    @Test fun newerGameSpacePackageOrModeIsPreserved() {
        val original = mapOf("performance_mode_package" to null, "performance_mode_value" to "0",
            "game_chicken_mode_switch" to "1")
        val current = mapOf("performance_mode_package" to "com.game.one", "performance_mode_value" to "3",
            "game_chicken_mode_switch" to "0")
        assertTrue(LegacyHardwareRestorePolicy.restorePerformance(original,
            current + ("performance_mode_package" to "com.game.two"), "com.game.one", 3, true).isEmpty())
        assertTrue(LegacyHardwareRestorePolicy.restorePerformance(original,
            current + ("performance_mode_value" to "1"), "com.game.one", 3, true).isEmpty())
    }
    @Test fun interruptedPerformanceWritesRestoreOnlyKeysThatWereWritten() {
        val original = mapOf("performance_mode_package" to null, "performance_mode_value" to "2",
            "game_chicken_mode_switch" to "1")
        val current = mapOf("performance_mode_package" to "com.game.one", "performance_mode_value" to "2",
            "game_chicken_mode_switch" to "0")
        assertEquals(original.filterKeys { it != "performance_mode_value" },
            LegacyHardwareRestorePolicy.restorePerformance(original, current, "com.game.one", 3, false))
    }

    @Test fun leavingAppRestoresAbsentAndExplicitSettings() {
        assertEquals(original, LegacyHardwareRestorePolicy.restoreValues(original,
            mapOf("min_refresh_rate" to "120", "peak_refresh_rate" to "120.0"), 120))
    }
    @Test fun anotherControllerChangingOneKeyKeepsItsNewValue() {
        assertEquals(mapOf("min_refresh_rate" to null), LegacyHardwareRestorePolicy.restoreValues(original,
            mapOf("min_refresh_rate" to "120", "peak_refresh_rate" to "60"), 120))
    }
    @Test fun interruptedRateChangeRestoresBothOldAndNewWrites() {
        assertEquals(original, LegacyHardwareRestorePolicy.restoreValues(original + ("_previous_rate" to "60"),
            mapOf("min_refresh_rate" to "120", "peak_refresh_rate" to "60"), 120))
    }
    @Test fun olderNativeOnlyCheckpointDoesNotDeleteUserSettings() {
        assertTrue(LegacyHardwareRestorePolicy.restoreValues(emptyMap(),
            mapOf("min_refresh_rate" to "120", "peak_refresh_rate" to "120"), 120).isEmpty())
    }
    @Test fun malformedValuesAreNotTreatedAsOurOverride() {
        listOf(null, "", "NaN", "Infinity", "60", "-120").forEach {
            assertFalse(it, LegacyHardwareRestorePolicy.matches(it, 120))
        }
        assertTrue(LegacyHardwareRestorePolicy.matches("119.88", 120))
    }
    @Test fun unreadablePreferredModeMustNotBeTreatedAsUnset() {
        assertNull(LegacyHardwareRestorePolicy.parsePreferredMode("Unknown command"))
        assertNull(LegacyHardwareRestorePolicy.parsePreferredMode("User preferred display mode: 1216 2688 NaN"))
        assertEquals("none", LegacyHardwareRestorePolicy.parsePreferredMode("User preferred display mode: null"))
        assertEquals("1216 2688 144.0", LegacyHardwareRestorePolicy.parsePreferredMode("User preferred display mode: 1216 2688 144"))
    }
    @Test fun preferredModeRestoresOnlyOurWriteAndPreservesNewerChoices() {
        val original = mapOf(LegacyHardwareRestorePolicy.PREFERRED_MODE to "none",
            LegacyHardwareRestorePolicy.WRITTEN_MODE to "1216 2688 144.0")
        assertEquals("none", LegacyHardwareRestorePolicy.restoreMode(original, "1216 2688 143.99"))
        assertNull(LegacyHardwareRestorePolicy.restoreMode(original, "1216 2688 90.0"))
        assertNull(LegacyHardwareRestorePolicy.restoreMode(original, null))
    }
    @Test fun redMagicUnsetSentinelCanBeCapturedAndRestoredAfterAnOverride() {
        listOf("-1 -1 0.0", "-1  -1\t0", "-1 -1 0.000").forEach { raw ->
            val baseline = LegacyHardwareRestorePolicy.parsePreferredMode("User preferred display mode: $raw")
            assertEquals("none", baseline)
            val original = mapOf(LegacyHardwareRestorePolicy.PREFERRED_MODE to baseline,
                LegacyHardwareRestorePolicy.WRITTEN_MODE to "1216 2688 144.0")
            assertEquals("none", LegacyHardwareRestorePolicy.restoreMode(original, "1216 2688 144.0"))
            assertNull(LegacyHardwareRestorePolicy.restoreMode(original, "1216 2688 90.0"))
        }
    }
    @Test fun partialOrMalformedPreferredModesRemainUnreadable() {
        listOf("-1 -1 60.0", "-1 2688 0.0", "1216 -1 0.0", "0 0 0.0", "-1 -1 NaN",
            "-1 -1 Infinity", "-1 -1 0.0 extra").forEach { raw ->
            assertNull(raw, LegacyHardwareRestorePolicy.parsePreferredMode("User preferred display mode: $raw"))
        }
    }
    @Test fun interruptedPreferredModeChangeCanRestorePreviousWrite() {
        val original = mapOf(LegacyHardwareRestorePolicy.PREFERRED_MODE to "1216 2688 90.0",
            LegacyHardwareRestorePolicy.WRITTEN_MODE to "1216 2688 144.0",
            LegacyHardwareRestorePolicy.PREVIOUS_MODE to "1216 2688 120.0")
        assertEquals("1216 2688 90.0", LegacyHardwareRestorePolicy.restoreMode(original, "1216 2688 120.0"))
    }
}
