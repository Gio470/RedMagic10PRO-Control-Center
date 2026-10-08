package com.elitedarkkaiser.redmagic.xposed

import android.app.Application
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class SoftwareControlsTest {
    private lateinit var context: Context
    private val launcher = "example.home"
    private fun home(pkg: String) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME).setComponent(ComponentName(pkg, "$pkg.Home"))
    private fun app(pkg: String) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(pkg, "$pkg.Settings"))
    private class Task(val baseIntent: Intent)
    private class Group(val task1: Any, val task2: Any? = null)
    private class KeyTask(val key: Task)
    private class MethodGroup(private val first: Any, private val second: Any? = null) {
        fun getTasks() = listOfNotNull(first, second)
    }

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(SoftwareControlsSettings.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun newControlsDefaultOff() {
        assertFalse(SoftwareControlsSettings.volumeEnabled(context))
        assertFalse(SoftwareControlsSettings.hideLauncher(context))
        assertEquals(1, SoftwareControlsSettings.volumeStep(context))
        assertFalse(SoftwareControlsConfig.launcherState(context).active)
    }

    @Test fun savedChoicesAndStepBoundsPersist() {
        SoftwareControlsSettings.setVolumeEnabled(context, true)
        SoftwareControlsSettings.setHideLauncher(context, true)
        SoftwareControlsSettings.setLauncherPackage(context, launcher)
        SoftwareControlsSettings.setVolumeStep(context, 100)
        assertTrue(SoftwareControlsSettings.volumeEnabled(context))
        assertTrue(SoftwareControlsSettings.hideLauncher(context))
        assertEquals(launcher, SoftwareControlsSettings.launcherPackage(context))
        assertEquals(10, SoftwareControlsSettings.volumeStep(context))
        SoftwareControlsSettings.setVolumeStep(context, -1)
        assertEquals(1, SoftwareControlsSettings.volumeStep(context))
        SoftwareControlsSettings.setVolumeEnabled(context, false)
        assertFalse(SoftwareControlsSettings.volumeEnabled(context))
    }

    @Test fun mediaStepsClampAtBothLimits() {
        val state = SoftwareControlsConfig.VolumeState(true, 4)
        assertEquals(9, state.target(3, 1, 5, 0, 15))
        assertEquals(1, state.target(3, -1, 5, 0, 15))
        assertEquals(15, state.target(3, 1, 14, 0, 15))
        assertEquals(2, state.target(3, -1, 3, 2, 15))
    }

    @Test fun disabledNonMediaAndMuteEventsStayStock() {
        assertNull(SoftwareControlsConfig.VolumeState(false, 4).target(3, 1, 5, 0, 15))
        val state = SoftwareControlsConfig.VolumeState(true, 4)
        listOf(0, 1, 2, 4, 5, 6, Int.MIN_VALUE).forEach { assertNull(state.target(it, 1, 5, 0, 15)) }
        listOf(0, -100, 100, 101).forEach { assertNull(state.target(3, it, 5, 0, 15)) }
        assertNull(state.target(3, 1, 5, 15, 0))
    }

    @Test fun systemReaderUsesSavedValuesAndFallsBackWhenMissing() {
        val file = File.createTempFile("volume-control", ".conf")
        try {
            file.writeText(SoftwareControlsConfig.serializeVolume(true, 6))
            val reader = SoftwareControlsConfig.VolumeReader(file.absolutePath)
            assertEquals(SoftwareControlsConfig.VolumeState(true, 6), reader.state())
            file.writeText("volume_enabled=invalid\nvolume_step=999\n# another line\n")
            // Ensure a distinct timestamp; /data/system writes are atomic root-shell transactions.
            file.setLastModified(file.lastModified() + 2000)
            assertEquals(SoftwareControlsConfig.VolumeState(false, 10), reader.state())
            file.delete()
            assertEquals(SoftwareControlsConfig.VolumeState(), reader.state())
        } finally { file.delete() }
    }

    @Test fun onlyTheSelectedHomeTaskMatches() {
        assertTrue(LauncherTaskMatcher.matches(Task(home(launcher)), launcher))
        assertFalse(LauncherTaskMatcher.matches(Task(home("other.home")), launcher))
        assertFalse(LauncherTaskMatcher.matches(Task(app(launcher)), launcher))
        assertFalse(LauncherTaskMatcher.matches(Task(app("example.game")), launcher))
        assertFalse(LauncherTaskMatcher.matches(null, launcher))
    }

    @Test fun groupedAndKeyTasksAreRecognizedWithoutHidingOtherApps() {
        assertTrue(LauncherTaskMatcher.matches(Group(Task(home(launcher))), launcher))
        assertTrue(LauncherTaskMatcher.matches(KeyTask(Task(home(launcher))), launcher))
        assertFalse(LauncherTaskMatcher.matches(Group(Task(home(launcher)), Task(app("example.game"))), launcher))
        assertFalse(LauncherTaskMatcher.matches(Group(Task(home(launcher)), Any()), launcher))
    }

    @Test fun stockEmptyAndInvalidLauncherSelectionsNeverActivate() {
        listOf("", SoftwareControlsConfig.STOCK_LAUNCHER, "com.android.settings", "example.home'; reboot #")
            .forEach { pkg ->
                assertFalse(SoftwareControlsConfig.LauncherState(true, pkg).active)
                assertFalse(LauncherTaskMatcher.matches(Task(home(launcher)), pkg))
            }
        assertFalse(SoftwareControlsConfig.LauncherState(false, launcher).active)
        assertTrue(SoftwareControlsConfig.LauncherState(true, launcher).active)
    }

    @Test fun oemRunningTasksMatchHomeComponentWithoutHomeCategory() {
        val component = ComponentName(launcher, "$launcher.Home")
        val running = ActivityManager.RunningTaskInfo().apply {
            baseIntent = Intent(Intent.ACTION_MAIN).setComponent(component)
            baseActivity = component
            topActivity = component
        }
        val selected = component.flattenToShortString()
        assertTrue(LauncherTaskMatcher.matches(running, launcher, selected))
        assertTrue(LauncherTaskMatcher.matches(MethodGroup(running), launcher, selected))
        assertFalse(LauncherTaskMatcher.matches(Task(app(launcher)), launcher, selected))
        assertFalse(LauncherTaskMatcher.matches(MethodGroup(running, Task(app("example.game"))), launcher, selected))
        assertFalse(LauncherTaskMatcher.matches(running, "other.home", selected))
    }

    @Test fun componentOnlyTaskInfoIsRecognizedAndOtherActivitiesArePreserved() {
        val home = ComponentName(launcher, "$launcher.Home")
        val task = ActivityManager.RunningTaskInfo().apply {
            baseIntent = Intent() // OEM task record has no categories or explicit Intent component.
            baseActivity = home
        }
        assertTrue(LauncherTaskMatcher.matches(task, launcher, home.flattenToString()))
        task.baseActivity = ComponentName(launcher, "$launcher.Settings")
        assertFalse(LauncherTaskMatcher.matches(task, launcher, home.flattenToString()))
    }

    @Test fun filteringPreservesCachedListsAndRespondsToDisabledState() {
        val home = Task(home(launcher))
        val game = Task(app("example.game"))
        val cached = arrayListOf(home, game)
        val on = SoftwareControlsConfig.LauncherState(true, launcher)
        assertEquals(listOf(game), LauncherTaskMatcher.filter(cached, on))
        assertEquals(listOf(home, game), cached)
        assertSame(cached, LauncherTaskMatcher.filter(cached, on.copy(enabled = false)))
        assertSame(cached, LauncherTaskMatcher.filter(cached, on.copy(packageName = "other.home")))
    }

    @Test fun rootPublishedLauncherSettingsIncludeHomeComponent() {
        val resolver = context.contentResolver
        android.provider.Settings.Global.putInt(resolver, SoftwareControlsConfig.HIDE_LAUNCHER, 1)
        android.provider.Settings.Global.putString(resolver, SoftwareControlsConfig.LAUNCHER_PACKAGE, launcher)
        val component = ComponentName(launcher, "$launcher.Home").flattenToShortString()
        android.provider.Settings.Global.putString(resolver, SoftwareControlsConfig.LAUNCHER_COMPONENT, component)
        assertEquals(SoftwareControlsConfig.LauncherState(true, launcher, component),
            SoftwareControlsConfig.launcherState(context))
        android.provider.Settings.Global.putInt(resolver, SoftwareControlsConfig.HIDE_LAUNCHER, 0)
        assertFalse(SoftwareControlsConfig.launcherState(context).active)
    }
}
