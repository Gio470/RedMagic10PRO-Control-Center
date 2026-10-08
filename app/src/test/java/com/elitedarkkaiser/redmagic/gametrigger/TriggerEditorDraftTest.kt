package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class TriggerEditorDraftTest {
    private lateinit var context: Context
    private val landscapeDisplay = TriggerDisplayState(2688, 1216, 1)
    private val portraitDisplay = TriggerDisplayState(1216, 2688, 0)
    private val landscape = TriggerPlacement.mapping(landscapeDisplay, NativeTgkOrientation.LANDSCAPE,
        600f, 500f, 1900f, 700f, 18)
    private val portrait = TriggerPlacement.mapping(portraitDisplay, NativeTgkOrientation.PORTRAIT,
        200f, 1900f, 1000f, 2200f, 18)

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun seed(): NativeTgkProfile {
        assertTrue(NativeTgkStorage.saveProfile(context,
            NativeTgkProfile("example.game", "Game", portrait = portrait, landscape = landscape)))
        return NativeTgkStorage.getProfile(context, "example.game")!!
    }
    private fun draft(profile: NativeTgkProfile = seed()) = TriggerEditorDraft(profile, NativeTgkOrientation.LANDSCAPE)
        .apply { attachDisplay(landscapeDisplay, 18) }

    @Test fun oneDragUndoesInOneStepAndKeepsTheOtherTrigger() {
        val edit = draft()
        edit.beginGesture()
        repeat(20) { edit.move(true, 800 + it, 600 + it, gestureMove = true) }
        edit.endGesture()
        assertNotEquals(landscape.left, edit.mapping!!.left)
        assertEquals(landscape.right, edit.mapping!!.right)
        assertTrue(edit.undo())
        assertEquals(landscape, edit.mapping)
        assertFalse(edit.canUndo)
    }

    @Test fun nudgeAndSwapKeepNativeTargetsWithinTheDisplay() {
        val edit = draft()
        edit.nudge(true, -10_000, -10_000)
        val atEdge = edit.mapping!!.left!!
        assertTrue(atEdge.isValid())
        assertEquals(0, atEdge.left)
        assertEquals(0, atEdge.top)
        edit.nudge(false, 10_000, 10_000)
        val right = edit.mapping!!.right!!
        assertTrue(right.isValid())
        assertTrue(right.right < landscapeDisplay.width)
        assertTrue(right.bottom < landscapeDisplay.height)
        edit.swap()
        assertEquals(right, edit.mapping!!.left)
        assertEquals(atEdge, edit.mapping!!.right)
        edit.align()
        assertEquals((edit.mapping!!.left!!.top + edit.mapping!!.left!!.bottom) / 2,
            (edit.mapping!!.right!!.top + edit.mapping!!.right!!.bottom) / 2)
    }

    @Test fun cancelLeavesPositionsBehaviorsFeedbackAndLayoutsUntouched() {
        val original = seed()
        val edit = draft(original)
        edit.nudge(true, 100, 100)
        edit.setBehavior(true, NativeTgkTriggerBehavior.RAPID_FIRE, 5)
        edit.setHaptics(true)
        edit.setMarkers(true)
        edit.setOpacity(30)
        assertTrue(edit.duplicateLayout())
        assertEquals(original, NativeTgkStorage.getProfile(context, original.packageName))
        assertFalse(NativeTgkStorage.enabled(context))
    }

    @Test fun switchingOrientationSavesBothDraftsAndBehaviorAtomically() {
        val edit = draft()
        edit.nudge(true, 100, 20)
        val changedLandscape = edit.mapping
        edit.setBehavior(false, NativeTgkTriggerBehavior.LONG_PRESS)
        edit.setOrientation(NativeTgkOrientation.PORTRAIT)
        edit.attachDisplay(portraitDisplay, 18)
        edit.nudge(false, -30, -40)
        val changedPortrait = edit.mapping
        assertTrue(NativeTgkStorage.saveDraft(context, edit, enable = true))
        val saved = NativeTgkStorage.getProfile(context, edit.profile.packageName)!!
        assertEquals(changedLandscape, saved.mappingFor(NativeTgkOrientation.LANDSCAPE))
        assertEquals(changedPortrait, saved.mappingFor(NativeTgkOrientation.PORTRAIT))
        assertEquals(NativeTgkTriggerBehavior.LONG_PRESS, saved.effectiveRightBehavior())
        assertTrue(saved.enabled && NativeTgkStorage.enabled(context))
    }

    @Test fun duplicatedLayoutsRetainBothOrientationsAndRespectTheLimit() {
        val edit = draft()
        edit.setBehavior(true, NativeTgkTriggerBehavior.RAPID_FIRE, 10)
        repeat(4) { assertTrue(edit.duplicateLayout()) }
        assertFalse(edit.duplicateLayout())
        assertEquals(5, edit.profile.layouts.size)
        assertEquals(landscape, edit.mapping)
        assertEquals(portrait, edit.profile.mappingFor(NativeTgkOrientation.PORTRAIT))
        assertEquals(10, edit.profile.effectiveLeftRapidFireCount())
        assertTrue(NativeTgkStorage.saveDraft(context, edit, enable = false))
        val saved = NativeTgkStorage.getProfile(context, edit.profile.packageName)!!
        assertEquals(5, saved.layouts.size)
        assertEquals(edit.profile.activeLayoutId, saved.activeLayoutId)
        assertFalse(saved.enabled || NativeTgkStorage.enabled(context))
    }

    @Test fun saveRetainsConcurrentChangesToUntouchedFieldsAndOtherOrientations() {
        val original = seed()
        val edit = draft(original)
        edit.nudge(true, 100, 100)
        edit.setBehavior(true, NativeTgkTriggerBehavior.RAPID_FIRE, 5)
        val updatedPortrait = portrait.copy(left = portrait.left!!.copy(left = 250, right = 286))
        assertTrue(NativeTgkStorage.updateProfile(context, original.packageName) {
            it.withMapping(NativeTgkOrientation.PORTRAIT, updatedPortrait).copy(hapticsEnabled = true)
        })
        assertTrue(NativeTgkStorage.saveDraft(context, edit, enable = false))
        val saved = NativeTgkStorage.getProfile(context, original.packageName)!!
        assertEquals(updatedPortrait, saved.mappingFor(NativeTgkOrientation.PORTRAIT))
        assertTrue(saved.hapticsEnabled)
        assertEquals(edit.mapping, saved.mappingFor(NativeTgkOrientation.LANDSCAPE))
        assertEquals(5, saved.effectiveLeftRapidFireCount())
    }

    @Test fun saveCannotResurrectADeletedLayoutOrActivateAMissingGame() {
        val original = seed()
        val edit = draft(original)
        edit.nudge(true, 100, 100)
        val alternate = original.activeLayout()!!.copy(id = "replacement", name = "Replacement")
        assertTrue(NativeTgkStorage.saveProfile(context, original.copy(activeLayoutId = alternate.id, layouts = listOf(alternate))))
        val replaced = NativeTgkStorage.getProfile(context, original.packageName)
        assertFalse(NativeTgkStorage.saveDraft(context, edit, enable = true))
        assertEquals(replaced, NativeTgkStorage.getProfile(context, original.packageName))
        assertFalse(NativeTgkStorage.enabled(context))
        NativeTgkStorage.removeProfile(context, original.packageName)
        assertFalse(NativeTgkStorage.saveDraft(context, edit, enable = true))
        assertFalse(NativeTgkStorage.enabled(context))
    }
}
