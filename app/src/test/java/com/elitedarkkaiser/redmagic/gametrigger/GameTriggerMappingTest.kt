package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import com.elitedarkkaiser.redmagic.TriggerAccessibilityService
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class GameTriggerMappingTest {
    private lateinit var context: Context
    private val rect = NativeTgkRect(100, 200, 140, 240, 1000, 2000)
    private val mapping = NativeTgkOrientationMapping(rect, rect.copy(left = 700, right = 740))

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        org.robolectric.util.ReflectionHelpers.setStaticField(TriggerDisplay::class.java, "overlayContext", null)
    }

    @Test fun newMappingControlsStartOff() {
        assertFalse(NativeTgkStorage.enabled(context))
        assertTrue(NativeTgkStorage.readProfiles(context).isEmpty())
        val profile = NativeTgkProfile("example.game", "Game")
        assertFalse(profile.enabled)
        assertFalse(profile.hapticsEnabled)
        assertFalse(profile.showSavedTargets)
    }

    @Test fun settingsEditsAfterReturningFromEditorPreserveNewTargets() {
        val game = NativeTgkProfile("example.game", "Game")
        assertTrue(NativeTgkStorage.saveProfile(context, game))
        val checkboxCallback: (NativeTgkProfile) -> NativeTgkProfile = { it.copy(hapticsEnabled = true) }
        assertTrue(NativeTgkStorage.saveMapping(context, game.packageName, NativeTgkOrientation.PORTRAIT, mapping, true))
        assertTrue(NativeTgkStorage.updateProfile(context, game.packageName, checkboxCallback))
        val saved = NativeTgkStorage.getProfile(context, game.packageName)!!
        assertEquals(mapping, saved.mappingFor(NativeTgkOrientation.PORTRAIT))
        assertTrue(saved.enabled && saved.hapticsEnabled && NativeTgkStorage.enabled(context))
        assertFalse(NativeTgkStorage.updateProfile(context, "missing.game", checkboxCallback))
    }

    @Test fun savingTargetsPublishesEnableStatesInTheSamePreferenceTransaction() {
        assertTrue(NativeTgkStorage.saveProfile(context, NativeTgkProfile("example.game", "Game")))
        val prefs = context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE)
        val observed = mutableListOf<Boolean>()
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "profiles_json") observed += NativeTgkStorage.enabled(context) &&
                NativeTgkStorage.getProfile(context, "example.game")!!.hasCompleteMapping(NativeTgkOrientation.PORTRAIT)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            assertTrue(NativeTgkStorage.saveMapping(context, "example.game", NativeTgkOrientation.PORTRAIT, mapping, true))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(true), observed)
        } finally { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Test fun accessibilityTeardownDoesNotClearTheRootForegroundOwner() {
        GameTrigger.onForeground(context, "example.game", "Root top-resumed activity")
        Robolectric.buildService(TriggerAccessibilityService::class.java).create().destroy()
        assertEquals("example.game", GameTrigger.currentForeground())
        GameTrigger.foregroundUnavailable(context)
    }

    @Test fun rootRuntimeIsStickyAndSurvivesAccessibilityTeardown() {
        // API 31's AndroidX receiver fallback uses the app's merged signature permission.
        // Robolectric does not automatically grant that install-time permission.
        org.robolectric.Shadows.shadowOf(context as Application).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit()
            .putBoolean("mapping_enabled", true).commit()
        val runtime = Robolectric.buildService(TriggerRuntimeService::class.java).create()
        try {
            assertTrue(TriggerRuntimeService.isRunning())
            assertEquals(android.app.Service.START_STICKY, runtime.get().onStartCommand(null, 0, 1))
            Robolectric.buildService(TriggerAccessibilityService::class.java).create().destroy()
            assertTrue(TriggerRuntimeService.isRunning())
            assertTrue(NativeTgkStorage.enabled(context))
        } finally { runtime.destroy() }
        assertFalse(TriggerRuntimeService.isRunning())
    }

    @Test
    @Config(shadows = [SmallOverlayWindowManager::class])
    fun smallEditorWindowsDoNotReplaceTheLandscapeCaptureDimensions() {
        val display = TriggerDisplay.read(context)
        assertEquals(2688, display.width)
        assertEquals(1216, display.height)
        assertEquals(NativeTgkOrientation.LANDSCAPE, display.orientation)
        val placed = TriggerPlacement.mapping(display, NativeTgkOrientation.LANDSCAPE,
            1000f, 400f, 1800f, 700f, 18)
        assertEquals(2688, placed.left!!.captureWidth)
        assertTrue(placed.isComplete())
    }

    @org.robolectric.annotation.Implements(className = "android.view.WindowManagerImpl", isInAndroidSdk = false)
    class SmallOverlayWindowManager : org.robolectric.shadows.ShadowWindowManagerImpl() {
        @org.robolectric.annotation.Implementation
        fun getCurrentWindowMetrics() = android.view.WindowMetrics(android.graphics.Rect(0, 0, 60, 60),
            android.view.WindowInsets.Builder().build())
        @org.robolectric.annotation.Implementation
        fun getMaximumWindowMetrics() = android.view.WindowMetrics(android.graphics.Rect(0, 0, 2688, 1216),
            android.view.WindowInsets.Builder().build())
    }

    @Test fun namedLayoutsAndSeparateOrientationsSurviveSave() {
        val profile = NativeTgkProfile("example.game", "Game", enabled = true, portrait = mapping)
        assertTrue(NativeTgkStorage.saveProfile(context, profile))
        val saved = NativeTgkStorage.getProfile(context, profile.packageName)!!
        val alternate = saved.activeLayout()!!.copy(id = "alternate", name = "Sniper", portrait = null, landscape = mapping)
        assertTrue(NativeTgkStorage.saveProfile(context, saved.copy(activeLayoutId = alternate.id, layouts = saved.layouts + alternate)))
        val loaded = NativeTgkStorage.getProfile(context, profile.packageName)!!
        assertEquals("Sniper", loaded.activeLayout()!!.name)
        assertFalse(loaded.hasCompleteMapping(NativeTgkOrientation.PORTRAIT))
        assertTrue(loaded.hasCompleteMapping(NativeTgkOrientation.LANDSCAPE))
        assertEquals(mapping, loaded.copy(activeLayoutId = "default").mappingFor(NativeTgkOrientation.PORTRAIT))
    }

    @Test fun triggerBehaviorChangesStayInTheSelectedLayout() {
        assertTrue(NativeTgkStorage.saveProfile(context, NativeTgkProfile("example.game", "Game", portrait = mapping)))
        val profile = NativeTgkStorage.getProfile(context, "example.game")!!
            .withTriggerBehavior(true, NativeTgkTriggerBehavior.RAPID_FIRE, 5)
            .withTriggerBehavior(false, NativeTgkTriggerBehavior.LONG_PRESS, 0)
        assertTrue(NativeTgkStorage.saveProfile(context, profile))
        val loaded = NativeTgkStorage.getProfile(context, "example.game")!!
        assertEquals(NativeTgkTriggerBehavior.RAPID_FIRE, loaded.effectiveLeftBehavior())
        assertEquals(5, loaded.effectiveLeftRapidFireCount())
        assertEquals(NativeTgkTriggerBehavior.LONG_PRESS, loaded.effectiveRightBehavior())
        assertTrue(NativeTgkStorage.saveProfile(context, loaded.withTriggerBehavior(true, NativeTgkTriggerBehavior.SINGLE_TOUCH, 0)))
        assertEquals(0, NativeTgkStorage.getProfile(context, "example.game")!!.effectiveLeftRapidFireCount())
    }

    @Test fun targetScalingKeepsCoordinatesInsidePhysicalDisplay() {
        assertArrayEquals(intArrayOf(50, 100, 70, 120), rect.scaledTo(500, 1000))
        val edge = NativeTgkRect(999, 1999, 1000, 2000, 1000, 2000).scaledTo(1080, 2400)
        assertTrue(edge[0] < edge[2] && edge[1] < edge[3])
        assertTrue(edge[2] < 1080 && edge[3] < 2400)
        assertFalse(NativeTgkOrientationMapping(rect, null).isComplete())
    }

    @Test fun landscapePlacementUsesTheGameDisplayCoordinates() {
        val display = TriggerDisplayState(2688, 1216, 1)
        val placed = TriggerPlacement.mapping(display, NativeTgkOrientation.LANDSCAPE,
            1000f, 400f, 1800f, 700f, 18)
        val left = placed.left!!
        val right = placed.right!!
        assertEquals(NativeTgkOrientation.LANDSCAPE, display.orientation)
        assertEquals(2688, left.captureWidth)
        assertEquals(1216, left.captureHeight)
        assertArrayEquals(intArrayOf(982, 382, 1018, 418), left.scaledTo(2688, 1216))
        assertArrayEquals(intArrayOf(1782, 682, 1818, 718), right.scaledTo(2688, 1216))
    }

    @Test fun landscapePlacementCannotSavePortraitCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            TriggerPlacement.mapping(TriggerDisplayState(1216, 2688, 0), NativeTgkOrientation.LANDSCAPE,
                100f, 200f, 700f, 800f, 18)
        }
    }

    @Test fun saveAndEnableActivatesTheSelectedGameAndPreservesPortraitTargets() {
        val game = NativeTgkProfile("example.game", "Game", portrait = mapping)
        val other = NativeTgkProfile("other.game", "Other")
        assertTrue(NativeTgkStorage.saveProfile(context, game))
        assertTrue(NativeTgkStorage.saveProfile(context, other))
        val landscape = TriggerPlacement.mapping(TriggerDisplayState(2688, 1216, 1), NativeTgkOrientation.LANDSCAPE,
            1000f, 400f, 1800f, 700f, 18)
        assertTrue(NativeTgkStorage.saveMapping(context, game.packageName, NativeTgkOrientation.LANDSCAPE, landscape, true))
        val saved = NativeTgkStorage.getProfile(context, game.packageName)!!
        assertTrue(NativeTgkStorage.enabled(context))
        assertTrue(saved.enabled)
        assertEquals(mapping, saved.mappingFor(NativeTgkOrientation.PORTRAIT))
        assertEquals(landscape, saved.mappingFor(NativeTgkOrientation.LANDSCAPE))
        assertFalse(NativeTgkStorage.getProfile(context, other.packageName)!!.enabled)
        assertFalse(saved.hapticsEnabled)
        assertFalse(saved.showSavedTargets)
    }

    @Test fun saveOnlyKeepsTheMasterAndProfileOff() {
        val profile = NativeTgkProfile("example.game", "Game")
        assertTrue(NativeTgkStorage.saveProfile(context, profile))
        assertTrue(NativeTgkStorage.saveMapping(context, profile.packageName, NativeTgkOrientation.PORTRAIT, mapping, false))
        assertFalse(NativeTgkStorage.enabled(context))
        val saved = NativeTgkStorage.getProfile(context, profile.packageName)!!
        assertFalse(saved.enabled)
        assertTrue(saved.hasCompleteMapping(NativeTgkOrientation.PORTRAIT))
    }

    @Test fun invalidOrMissingProfilesCannotBeActivated() {
        assertFalse(NativeTgkStorage.saveMapping(context, "missing.game", NativeTgkOrientation.PORTRAIT, mapping, true))
        assertTrue(NativeTgkStorage.saveProfile(context, NativeTgkProfile("example.game", "Game")))
        assertFalse(NativeTgkStorage.saveMapping(context, "example.game", NativeTgkOrientation.PORTRAIT,
            NativeTgkOrientationMapping(rect, null), true))
        assertFalse(NativeTgkStorage.enabled(context))
        assertFalse(NativeTgkStorage.getProfile(context, "example.game")!!.enabled)
    }

    @Test fun focusedGameRemainsForegroundWhenAnOverlayEmitsWindowEvents() {
        val game = TriggerForeground.Companion.AppWindow("example.game", focused = true, active = false, layer = 5)
        val home = TriggerForeground.Companion.AppWindow("example.launcher", focused = false, active = false, layer = 1)
        assertEquals("example.game", TriggerForeground.fromWindows(listOf(game, home), "com.redmagic.control"))
        assertEquals("example.game", TriggerForeground.fromWindows(listOf(game, home), "com.android.systemui"))
        val app = TriggerForeground.Companion.AppWindow("com.redmagic.control", focused = true, active = true, layer = 6)
        assertEquals("com.redmagic.control", TriggerForeground.fromWindows(listOf(game.copy(focused = false), app), null))
    }

    @Test fun rootForegroundSamplesRejectErrorsAndMalformedPackages() {
        assertEquals("com.example.game", TriggerForeground.parse("TGK_FOREGROUND com.example.game"))
        assertEquals("com.example.game", TriggerForeground.parse("  TGK_FOREGROUND com.example.game  "))
        listOf("Permission denied", "com.example.game", "TGK_FOREGROUND com.example.game/MainActivity", "TGK_FOREGROUND ")
            .forEach { assertNull(TriggerForeground.parse(it)) }
    }

    @Test fun getterRepliesDistinguishOffFromUnsupportedAndDenied() {
        assertFalse(TgkParcelReply.boolean("Result: Parcel(00000000 00000000 '........')"))
        assertTrue(TgkParcelReply.boolean("Result: Parcel(00000000 00000001 '........')"))
        assertTrue(TgkParcelReply.boolean("Result: Parcel(\n  0x00000000: 00000000 00000001 '........'\n)"))
        listOf("Parcel(NULL)", "Parcel(fffffffe 00000001)", "Parcel(00000000 00000040)", "Permission Denial")
            .forEach { output -> assertThrows(IllegalStateException::class.java) { TgkParcelReply.boolean(output) } }
    }

    @Test fun rejectedSettersCannotReportSuccess() {
        TgkParcelReply.checkSetter("Result: Parcel(NULL)")
        TgkParcelReply.checkSetter("Result: Parcel(00000000)")
        listOf("Parcel(ffffffff 00000000)", "Exception: SecurityException", "Unknown transaction")
            .forEach { output -> assertThrows(IllegalStateException::class.java) { TgkParcelReply.checkSetter(output) } }
    }

    @Test fun discoveredTransactionNumbersComeFromTheFirmwareReply() {
        val ids = NativeTgkAbi.parse("runtime warning\nTGK_ABI setTgkPoint=93;isGameKeyEnable=82;invalid=-1")
        assertEquals(93, ids["setTgkPoint"])
        assertEquals(82, ids["isGameKeyEnable"])
        assertFalse(ids.containsKey("invalid"))
        assertTrue(NativeTgkAbi.parse("TGK_UNAVAILABLE Unsupported firmware").isEmpty())
    }

    @Test fun binderNamesResolveToBridgeNamesUsingInstalledTransactionNumbers() {
        val ids = NativeTgkAbi.discover(FirmwareInput::class.java, FirmwareStub::class.java)
        assertEquals(931, ids["setTgkPoint"])
        assertEquals(417, ids["setGameKeyEnable"])
        assertEquals(418, ids["isGameKeyEnable"])
        assertEquals(419, ids["setLeftTgkEnable"])
        assertEquals(420, ids["setRightTgkEnable"])
        assertFalse(ids.containsKey("setGlobalKeyEnable"))
        assertEquals(12, ids.size)
    }

    @Test fun optionalMethodsWithIncompatibleSignaturesAreNotCalled() {
        val ids = NativeTgkAbi.discover(FirmwareInput::class.java, FirmwareStub::class.java)
        assertFalse(ids.containsKey("setTgkRapidFireCount"))
        assertFalse(ids.containsKey("setTgkTopEffectEnable"))
        assertFalse(ids.containsKey("setTgkCenterEffectEnable"))
    }

    @Test fun missingRequiredTransactionsFailWithTheActualBinderMethodNames() {
        val failure = assertThrows(IllegalStateException::class.java) {
            NativeTgkAbi.discover(FirmwareInput::class.java, IncompleteStub::class.java)
        }
        assertTrue(failure.message!!.contains("setGlobalKeyEnable"))
        assertTrue(failure.message!!.contains("setLeftGameKeyEnable"))
        assertFalse(failure.message!!.contains("setTgkPoint"))
    }

    @Test fun cachedWindowEventsApplyOnlyTheLatestGame() {
        val jobs = mutableListOf<Runnable>()
        val applied = mutableListOf<String>()
        var cleared = 0
        val queue = LatestMappingQueue<String>(Executor { jobs.add(it) }, { value, _ -> applied.add(value) }, { cleared++ })
        queue.submit("game.a")
        queue.submit("game.a")
        queue.submit("game.b")
        assertEquals(1, jobs.size)
        jobs.removeAt(0).run()
        assertEquals(listOf("game.b"), applied)
        queue.submit(null)
        jobs.removeAt(0).run()
        assertFalse(queue.requested())
        assertEquals(1, cleared)
    }

    @Test fun leavingDuringConfigurationInvalidatesThePendingApply() {
        val jobs = mutableListOf<Runnable>()
        var cleared = false
        lateinit var queue: LatestMappingQueue<String>
        queue = LatestMappingQueue(Executor { jobs.add(it) }, { _, current ->
            assertTrue(current())
            queue.submit(null)
            assertFalse(current())
        }, { cleared = true })
        queue.submit("game.a")
        jobs.removeAt(0).run()
        assertTrue(cleared)
        assertFalse(queue.requested())
        assertTrue(jobs.isEmpty())
    }

    @Test fun leavingBeforeWorkerStartsDoesNotConfigureTheOldGame() {
        val jobs = mutableListOf<Runnable>()
        val queue = LatestMappingQueue<String>(Executor { jobs.add(it) }, { _, _ -> fail("Stale game applied") }, {})
        queue.submit("game.a")
        queue.submit(null)
        jobs.removeAt(0).run()
    }

    @Test fun profileRemovalStopsEligibilityAndPreservesOtherGames() {
        val first = NativeTgkProfile("example.game", "Game", enabled = true, portrait = mapping)
        val other = NativeTgkProfile("other.game", "Other", enabled = true, landscape = mapping)
        assertTrue(NativeTgkStorage.saveProfile(context, first))
        assertTrue(NativeTgkStorage.saveProfile(context, other))
        assertTrue(NativeTgkStorage.hasEnabledProfile(context, first.packageName))
        assertTrue(NativeTgkStorage.removeProfile(context, first.packageName))
        assertFalse(NativeTgkStorage.hasEnabledProfile(context, first.packageName))
        assertTrue(NativeTgkStorage.hasEnabledProfile(context, other.packageName))
    }

    // Model the real AIDL names, deliberately without the public InputManager wrapper names.
    interface FirmwareInput {
        fun setTgkPoint(first: IntArray, second: IntArray, key: Int)
        fun setTgkMode(mode: Int, key: Int)
        fun setTgkVersion(version: Int)
        fun enableTgkDrive(enabled: Boolean)
        fun setConsumeTgkKey(enabled: Boolean)
        fun setTouchHapticFeedbackEnable(enabled: Boolean)
        fun setLeftGameKeyEnable(enabled: Boolean)
        fun setRightGameKeyEnable(enabled: Boolean)
        fun setGlobalKeyEnable(enabled: Boolean)
        fun isGlobalKeyEnable(): Boolean
        fun isLeftGameKeyEnable(): Boolean
        fun isRightGameKeyEnable(): Boolean
        fun setTgkRapidFireCount(count: String, key: Int)
        fun setTgkTopEffectEnable(enabled: Boolean): Int
    }

    class FirmwareStub {
        companion object {
            const val TRANSACTION_setTgkPoint = 931
            const val TRANSACTION_setTgkMode = 932
            const val TRANSACTION_setTgkVersion = 933
            const val TRANSACTION_enableTgkDrive = 934
            const val TRANSACTION_setConsumeTgkKey = 935
            const val TRANSACTION_setTouchHapticFeedbackEnable = 936
            const val TRANSACTION_setGlobalKeyEnable = 417
            const val TRANSACTION_isGlobalKeyEnable = 418
            const val TRANSACTION_setLeftGameKeyEnable = 419
            const val TRANSACTION_setRightGameKeyEnable = 420
            const val TRANSACTION_isLeftGameKeyEnable = 421
            const val TRANSACTION_isRightGameKeyEnable = 422
            const val TRANSACTION_setTgkRapidFireCount = 937
            const val TRANSACTION_setTgkTopEffectEnable = 938
            // A transaction field alone must not make a nonexistent method eligible.
            const val TRANSACTION_isGameKeyEnable = 1
        }
    }

    class IncompleteStub {
        companion object { const val TRANSACTION_setTgkPoint = 931 }
    }
}
