package com.elitedarkkaiser.redmagic

import android.app.Application
import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class LegacyHardwareCleanupTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        listOf("per_app_hardware", "refresh_rate_profiles", "performance_mode_profiles").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        Settings.System.putString(context.contentResolver, "min_refresh_rate", "90")
        Settings.System.putString(context.contentResolver, "peak_refresh_rate", "90")
    }

    @Test fun freshInstallHasNoCleanupWorkOrDisplayChanges() {
        assertFalse(LegacyHardwareCleanup.pending(context))
        assertTrue(LegacyHardwareCleanup.run(context))
        assertEquals("90", Settings.System.getString(context.contentResolver, "peak_refresh_rate"))
    }
    @Test fun inactiveProfilesAreRemovedWithoutChangingUnownedDisplaySettings() {
        val prefs = context.getSharedPreferences("per_app_hardware", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("refresh", true).putBoolean("performance", true).commit()
        listOf("refresh_rate_profiles", "performance_mode_profiles").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit()
                .putString("profiles_json", "{\"profiles\":[]}").commit()
        }
        assertTrue(LegacyHardwareCleanup.run(context))
        assertFalse(LegacyHardwareCleanup.pending(context))
        assertEquals("90", Settings.System.getString(context.contentResolver, "min_refresh_rate"))
        assertEquals("90", Settings.System.getString(context.contentResolver, "peak_refresh_rate"))
    }
    @Test fun malformedCheckpointsAreRetainedInsteadOfGuessingTheirRestoreValues() {
        val prefs = context.getSharedPreferences("per_app_hardware", Context.MODE_PRIVATE)
        prefs.edit().putString("refresh_owned", "broken checkpoint").putBoolean("refresh", true).commit()
        assertFalse(LegacyHardwareCleanup.run(context))
        assertTrue(LegacyHardwareCleanup.pending(context))
        assertEquals("broken checkpoint", prefs.getString("refresh_owned", null))
        assertFalse(prefs.getBoolean("refresh", true))
    }
    @Test fun newerPerformanceOwnerSurvivesUpgradeAndOldCheckpointIsCleared() {
        Settings.Global.putString(context.contentResolver, "performance_mode_package", "com.game.new")
        Settings.Global.putString(context.contentResolver, "performance_mode_value", "1")
        Settings.Global.putString(context.contentResolver, "game_chicken_mode_switch", "1")
        val checkpoint = JSONObject().put("package", "com.game.old").put("value", 3).put("applied", true)
            .put("original", JSONObject().put("performance_mode_package", JSONObject.NULL)
                .put("performance_mode_value", "0").put("game_chicken_mode_switch", "0"))
        context.getSharedPreferences("per_app_hardware", Context.MODE_PRIVATE).edit()
            .putString("performance_owned", checkpoint.toString()).commit()
        assertTrue(LegacyHardwareCleanup.run(context))
        assertFalse(LegacyHardwareCleanup.pending(context))
        assertEquals("com.game.new", Settings.Global.getString(context.contentResolver, "performance_mode_package"))
        assertEquals("1", Settings.Global.getString(context.contentResolver, "performance_mode_value"))
        assertEquals("1", Settings.Global.getString(context.contentResolver, "game_chicken_mode_switch"))
    }
}
