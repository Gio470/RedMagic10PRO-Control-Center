package com.elitedarkkaiser.redmagic.gametrigger

import java.util.ArrayDeque
import java.util.UUID

/** Unsaved editor changes, including layouts and behavior; cancel never writes this draft. */
class TriggerEditorDraft(initial: NativeTgkProfile, orientation: NativeTgkOrientation) {
    var orientation = orientation
        private set
    private val baseline = initial
    var profile: NativeTgkProfile = initial
        private set
    private val history = ArrayDeque<NativeTgkProfile>()
    private var gesture: NativeTgkProfile? = null
    val canUndo get() = history.isNotEmpty()
    val hasChanges get() = profile != baseline
    val layout get() = profile.activeLayout()!!
    val mapping get() = profile.mappingFor(orientation)

    private fun remember(value: NativeTgkProfile) {
        if (history.peekLast() != value) history.addLast(value)
        while (history.size > 30) history.removeFirst()
    }
    private fun change(updated: NativeTgkProfile) {
        if (updated == profile) return
        remember(profile)
        profile = updated
    }
    fun beginGesture() { gesture = profile }
    fun endGesture() {
        gesture?.takeIf { it != profile }?.let(::remember)
        gesture = null
    }
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        profile = history.removeLast()
        return true
    }
    fun setOrientation(value: NativeTgkOrientation) {
        endGesture()
        orientation = value
        history.clear()
    }

    fun attachDisplay(display: TriggerDisplayState, radius: Int) {
        require(display.isValid() && display.orientation == orientation)
        val current = mapping
        if (current?.isComplete() != true) {
            profile = profile.withMapping(orientation, TriggerPlacement.mapping(display, orientation,
                display.width * 0.2f, display.height * 0.7f, display.width * 0.8f, display.height * 0.7f, radius))
        } else {
            fun scale(rect: NativeTgkRect): NativeTgkRect {
                val v = rect.scaledTo(display.width, display.height)
                return NativeTgkRect(v[0], v[1], v[2], v[3], display.width, display.height)
            }
            profile = profile.withMapping(orientation, NativeTgkOrientationMapping(scale(current.left!!), scale(current.right!!)))
        }
    }

    fun move(left: Boolean, x: Int, y: Int, gestureMove: Boolean = false) {
        val current = mapping ?: return
        val source = (if (left) current.left else current.right) ?: return
        val width = source.right - source.left
        val height = source.bottom - source.top
        val l = (x - width / 2).coerceIn(0, (source.captureWidth - width - 1).coerceAtLeast(0))
        val t = (y - height / 2).coerceIn(0, (source.captureHeight - height - 1).coerceAtLeast(0))
        val rect = source.copy(left = l, top = t, right = l + width, bottom = t + height)
        val updated = profile.withMapping(orientation, if (left) current.copy(left = rect) else current.copy(right = rect))
        if (gestureMove) profile = updated else change(updated)
    }
    fun nudge(left: Boolean, dx: Int, dy: Int) {
        val rect = (if (left) mapping?.left else mapping?.right) ?: return
        move(left, (rect.left + rect.right) / 2 + dx, (rect.top + rect.bottom) / 2 + dy)
    }
    fun swap() { mapping?.let { change(profile.withMapping(orientation, it.copy(left = it.right, right = it.left))) } }
    fun align() {
        val current = mapping ?: return
        val l = current.left ?: return
        val r = current.right ?: return
        move(false, (r.left + r.right) / 2, (l.top + l.bottom) / 2)
    }
    fun reset(display: TriggerDisplayState, radius: Int) {
        change(profile.withMapping(orientation, TriggerPlacement.mapping(display, orientation,
            display.width * 0.2f, display.height * 0.7f, display.width * 0.8f, display.height * 0.7f, radius)))
    }
    fun setBehavior(left: Boolean, behavior: NativeTgkTriggerBehavior, count: Int = 0) {
        require(if (behavior == NativeTgkTriggerBehavior.RAPID_FIRE) count in setOf(2, 5, 10) else count == 0)
        change(profile.withTriggerBehavior(left, behavior, count))
    }
    fun setHaptics(value: Boolean) = change(profile.copy(hapticsEnabled = value))
    fun setMarkers(value: Boolean) = change(profile.copy(showSavedTargets = value))
    fun setOpacity(value: Int) { require(value in 5..30); change(profile.copy(savedTargetOpacityPercent = value)) }
    fun selectLayout(id: String): Boolean {
        if (profile.layouts.none { it.id == id }) return false
        change(profile.copy(activeLayoutId = id))
        return true
    }
    fun duplicateLayout(): Boolean {
        if (profile.layouts.size >= NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) return false
        val id = UUID.randomUUID().toString()
        val copy = layout.copy(id = id, name = "Layout ${profile.layouts.size + 1}")
        change(profile.copy(activeLayoutId = id, layouts = profile.layouts + copy))
        return true
    }

    /** Merge edited fields only, retaining other orientations and concurrent settings updates. */
    fun mergeInto(current: NativeTgkProfile): NativeTgkProfile? {
        if (current.packageName != baseline.packageName) return null
        val originals = baseline.layouts.associateBy { it.id }
        val changed = profile.layouts.filter { it != originals[it.id] }
        val merged = current.layouts.toMutableList()
        for (edited in changed) {
            val old = originals[edited.id]
            val index = merged.indexOfFirst { it.id == edited.id }
            if (old == null) {
                if (index >= 0 || merged.size >= NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) return null
                merged.add(edited)
            } else {
                if (index < 0) return null
                val latest = merged[index]
                merged[index] = latest.copy(
                    leftBehavior = if (edited.leftBehavior != old.leftBehavior) edited.leftBehavior else latest.leftBehavior,
                    rightBehavior = if (edited.rightBehavior != old.rightBehavior) edited.rightBehavior else latest.rightBehavior,
                    leftRapidFireCount = if (edited.leftBehavior != old.leftBehavior || edited.leftRapidFireCount != old.leftRapidFireCount)
                        edited.leftRapidFireCount else latest.leftRapidFireCount,
                    rightRapidFireCount = if (edited.rightBehavior != old.rightBehavior || edited.rightRapidFireCount != old.rightRapidFireCount)
                        edited.rightRapidFireCount else latest.rightRapidFireCount,
                    portrait = if (edited.portrait != old.portrait) edited.portrait else latest.portrait,
                    landscape = if (edited.landscape != old.landscape) edited.landscape else latest.landscape)
            }
        }
        if (merged.none { it.id == profile.activeLayoutId }) return null
        return current.copy(activeLayoutId = profile.activeLayoutId, layouts = merged,
            hapticsEnabled = if (profile.hapticsEnabled != baseline.hapticsEnabled) profile.hapticsEnabled else current.hapticsEnabled,
            showSavedTargets = if (profile.showSavedTargets != baseline.showSavedTargets) profile.showSavedTargets else current.showSavedTargets,
            savedTargetOpacityPercent = if (profile.savedTargetOpacityPercent != baseline.savedTargetOpacityPercent)
                profile.savedTargetOpacityPercent else current.savedTargetOpacityPercent)
    }
}
