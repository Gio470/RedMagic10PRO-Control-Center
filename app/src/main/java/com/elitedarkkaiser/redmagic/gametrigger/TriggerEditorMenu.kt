package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.M3
import com.google.android.material.materialswitch.MaterialSwitch

/** A solid Material You panel; all editor controls stay inside this touchable window. */
class TriggerEditorMenu(
    private val context: Context,
    private val draft: TriggerEditorDraft,
    private val display: TriggerDisplayState,
    val state: State,
    private val updated: () -> Unit,
    private val orientationChanged: (NativeTgkOrientation) -> Unit,
    private val save: (Boolean) -> Unit,
    private val cancel: () -> Unit
) {
    class State {
        var leftSelected = true
        var collapsed = false
        var locked = false
        var step = 1
        var tab = "Targets"
    }

    private val m3 = M3(context)
    val view = column().apply {
        background = m3.surfaceShape(m3.surfaceContainerHigh, 28, m3.outlineVariant)
        clipToOutline = true
        elevation = m3.dp(8).toFloat()
        setPadding(m3.dp(12), m3.dp(8), m3.dp(12), m3.dp(12))
    }
    lateinit var dragHandle: TextView
        private set
    private lateinit var status: TextView
    private var positionLabel: TextView? = null
    private var undoButton: Button? = null
    private var errorMessage: String? = null
    private val ready get() = display.orientation == draft.orientation

    init { render() }

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun text(label: String, small: Boolean = false) = TextView(context).apply {
        text = label
        m3.styleText(this, if (small) M3.Type.bodySmall else M3.Type.titleSmall,
            if (small) m3.onSurfaceVariant else m3.onSurface)
        setPadding(0, m3.dp(4), 0, m3.dp(4))
    }

    private fun row(parent: LinearLayout, vararg children: View) {
        parent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            children.forEachIndexed { index, child ->
                addView(child, LinearLayout.LayoutParams(0, m3.dp(48), 1f).apply {
                    if (index > 0) marginStart = m3.dp(8)
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun button(label: String, selected: Boolean = false, enabled: Boolean = true,
        kind: M3.ButtonKind = M3.ButtonKind.Outlined, click: () -> Unit): Button =
        m3.button(label, if (selected) M3.ButtonKind.Filled else kind, onClick = click).apply {
            isEnabled = enabled
            isSelected = selected
            alpha = if (enabled) 1f else 0.4f
            minimumWidth = m3.dp(48)
            setPadding(m3.dp(8), 0, m3.dp(8), 0)
            contentDescription = when (label) {
                "↑" -> "Move selected target up"
                "↓" -> "Move selected target down"
                "←" -> "Move selected target left"
                "→" -> "Move selected target right"
                else -> label
            }
            if (Build.VERSION.SDK_INT >= 30) stateDescription = if (selected) "Selected" else null
        }

    private fun section(parent: LinearLayout, label: String, content: (LinearLayout) -> Unit) {
        val group = column().apply {
            background = m3.surfaceShape(m3.surfaceContainerLow, 20)
            setPadding(m3.dp(12), m3.dp(8), m3.dp(12), m3.dp(8))
            addView(text(label).apply { m3.styleText(this, M3.Type.titleSmall, m3.onSurfaceVariant) })
        }
        content(group)
        parent.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = m3.dp(8) })
    }

    private fun edit(action: () -> Unit) {
        errorMessage = null
        action()
        updated()
        render()
    }

    fun select(left: Boolean) {
        state.leftSelected = left
        updated()
        if (!state.collapsed && state.tab in listOf("Targets", "Behavior")) render()
    }

    fun showError(message: String) {
        errorMessage = message
        refreshPosition()
    }

    fun refreshPosition() {
        undoButton?.apply {
            isEnabled = draft.canUndo && !state.locked
            alpha = if (isEnabled) 1f else 0.4f
        }
        val rect = if (state.leftSelected) draft.mapping?.left else draft.mapping?.right
        positionLabel?.text = rect?.let {
            "${if (state.leftSelected) "Left" else "Right"} target   ·   X ${(it.left + it.right) / 2}   Y ${(it.top + it.bottom) / 2}"
        } ?: "Place both targets to begin"
        if (::status.isInitialized) {
            status.text = errorMessage ?: if (!ready) {
                "Rotate to ${draft.orientation.name.lowercase()} to place targets"
            } else {
                "${draft.profile.appLabel} · ${draft.layout.name}\n" +
                    draft.orientation.name.lowercase().replaceFirstChar { it.uppercase() } +
                    if (draft.hasChanges) " · Unsaved changes" else " · Ready to edit"
            }
            status.setTextColor(if (errorMessage != null) m3.error else m3.onSurfaceVariant)
        }
    }

    fun render() {
        view.removeAllViews()
        positionLabel = null
        undoButton = null
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        dragHandle = text("Trigger editor").apply {
            m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
            contentDescription = "Drag to move the trigger editor"
            minimumHeight = m3.dp(48)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(dragHandle, LinearLayout.LayoutParams(0, m3.dp(48), 1f))
        header.addView(button(if (state.collapsed) "+" else "−", kind = M3.ButtonKind.Text) {
            state.collapsed = !state.collapsed
            render()
        }.apply { contentDescription = if (state.collapsed) "Expand editor" else "Collapse editor" },
            LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
        header.addView(button("×", kind = M3.ButtonKind.Text, click = cancel).apply {
            contentDescription = "Cancel editing"
        }, LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
        view.addView(header)
        status = text("", small = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        view.addView(status)

        if (!ready) {
            row(view, button("Use ${display.orientation.name.lowercase()}", kind = M3.ButtonKind.Tonal) {
                orientationChanged(display.orientation)
            })
        } else if (!state.collapsed) {
            tabs()
            val body = column()
            when (state.tab) {
                "Targets" -> targets(body)
                "Behavior" -> behavior(body)
                "Layouts" -> layouts(body)
                else -> options(body)
            }
            view.addView(ScrollView(context).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = true
                clipToPadding = false
                addView(body)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (display.height - m3.dp(244)).coerceIn(m3.dp(64), m3.dp(280))).apply {
                    topMargin = m3.dp(8)
                    bottomMargin = m3.dp(8)
                })
        }

        view.addView(View(context).apply { setBackgroundColor(m3.outlineVariant) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(1)).apply {
                topMargin = m3.dp(4)
                bottomMargin = m3.dp(4)
            })
        row(view, button("Save only", enabled = ready, kind = M3.ButtonKind.Text) { save(false) },
            button("Save & enable", enabled = ready, kind = M3.ButtonKind.Filled) { save(true) })
        refreshPosition()
        updated()
    }

    private fun tabs() {
        val strip = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        var selected: Button? = null
        listOf("Targets", "Behavior", "Layouts", "Options").forEachIndexed { index, tab ->
            val item = button(tab, state.tab == tab, kind = M3.ButtonKind.Text) {
                state.tab = tab
                render()
            }
            strip.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                m3.dp(48)).apply { if (index > 0) marginStart = m3.dp(4) })
            if (state.tab == tab) selected = item
        }
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(strip)
        }
        view.addView(scroller)
        scroller.post { selected?.let { scroller.smoothScrollTo((it.left - m3.dp(12)).coerceAtLeast(0), 0) } }
    }

    private fun sideSelector(parent: LinearLayout) = row(parent,
        button("L · Left", state.leftSelected) { select(true) },
        button("R · Right", !state.leftSelected) { select(false) })

    private fun targets(parent: LinearLayout) {
        section(parent, "Selected trigger") { group ->
            sideSelector(group)
            positionLabel = text("", small = true).also { group.addView(it) }
        }
        section(parent, "Fine adjustment") { group ->
            row(group, button("Step: ${state.step} px", kind = M3.ButtonKind.Tonal) {
                state.step = when (state.step) { 1 -> 5; 5 -> 10; else -> 1 }
                render()
            })
            row(group, View(context), button("↑", enabled = !state.locked) {
                edit { draft.nudge(state.leftSelected, 0, -state.step) }
            }, View(context))
            row(group, button("←", enabled = !state.locked) {
                edit { draft.nudge(state.leftSelected, -state.step, 0) }
            }, button("↓", enabled = !state.locked) {
                edit { draft.nudge(state.leftSelected, 0, state.step) }
            }, button("→", enabled = !state.locked) {
                edit { draft.nudge(state.leftSelected, state.step, 0) }
            })
        }
        section(parent, "Arrange targets") { group ->
            row(group, button("Swap L/R", enabled = !state.locked) { edit(draft::swap) },
                button("Align height", enabled = !state.locked) { edit(draft::align) })
            undoButton = button("Undo", enabled = draft.canUndo && !state.locked) { edit { draft.undo() } }
            row(group, undoButton!!,
                button(if (state.locked) "Unlock" else "Lock", state.locked) {
                    state.locked = !state.locked
                    render()
                })
            row(group, button("Reset positions", enabled = !state.locked, kind = M3.ButtonKind.Text) {
                edit { draft.reset(display, m3.dp(18)) }
            })
        }
        parent.addView(text("Drag L or R to place it. Tap a target to select it, then use the arrows to fine-tune.", small = true))
    }

    private fun behavior(parent: LinearLayout) {
        section(parent, "Selected trigger", ::sideSelector)
        val left = state.leftSelected
        val current = if (left) draft.profile.effectiveLeftBehavior() else draft.profile.effectiveRightBehavior()
        val count = if (left) draft.profile.effectiveLeftRapidFireCount() else draft.profile.effectiveRightRapidFireCount()
        section(parent, "Touch mode") { group ->
            row(group, button("Single touch", current == NativeTgkTriggerBehavior.SINGLE_TOUCH) {
                edit { draft.setBehavior(left, NativeTgkTriggerBehavior.SINGLE_TOUCH) }
            }, button("Long press", current == NativeTgkTriggerBehavior.LONG_PRESS) {
                edit { draft.setBehavior(left, NativeTgkTriggerBehavior.LONG_PRESS) }
            })
        }
        section(parent, "Rapid fire") { group ->
            row(group, *listOf(2, 5, 10).map { value ->
                button("$value taps", current == NativeTgkTriggerBehavior.RAPID_FIRE && count == value) {
                    edit { draft.setBehavior(left, NativeTgkTriggerBehavior.RAPID_FIRE, value) }
                }
            }.toTypedArray())
            group.addView(text("Choose a tap count to enable rapid fire for this trigger.", small = true))
        }
        parent.addView(text("Each shoulder trigger has its own behavior. Changes apply when you save.", small = true))
    }

    private fun layouts(parent: LinearLayout) {
        section(parent, "Game layouts") { group ->
            draft.profile.layouts.forEach { layout ->
                row(group, button(layout.name, layout.id == draft.profile.activeLayoutId) {
                    edit { draft.selectLayout(layout.id); draft.attachDisplay(display, m3.dp(18)) }
                })
            }
            row(group, button("Duplicate layout", kind = M3.ButtonKind.Tonal,
                enabled = draft.profile.layouts.size < NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) {
                edit { draft.duplicateLayout() }
            })
        }
        parent.addView(text("Keep up to five layouts per game. Copies include both orientations and trigger behaviors.", small = true))
    }

    private fun toggle(parent: LinearLayout, label: String, description: String, checked: Boolean,
        changed: (Boolean) -> Unit) {
        val line = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = m3.dp(64)
        }
        line.addView(column().apply {
            addView(text(label))
            addView(text(description, small = true))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = m3.dp(8)
        })
        val switch = MaterialSwitch(context).apply {
            isChecked = checked
            contentDescription = label
            minimumWidth = m3.dp(48)
            minimumHeight = m3.dp(48)
            m3.tintSwitch(this)
            setOnCheckedChangeListener { _, value -> edit { changed(value) } }
        }
        line.addView(switch)
        line.setOnClickListener { switch.isChecked = !switch.isChecked }
        parent.addView(line)
    }

    private fun options(parent: LinearLayout) {
        section(parent, "In-game feedback") { group ->
            toggle(group, "Haptics", "Vibrate when a trigger activates", draft.profile.hapticsEnabled, draft::setHaptics)
            toggle(group, "Target markers", "Show saved positions in the game", draft.profile.showSavedTargets, draft::setMarkers)
        }
        section(parent, "Marker opacity") { group ->
            row(group, *listOf(5, 12, 20, 30).map { value ->
                button("$value%", draft.profile.savedTargetOpacityPercent == value) { edit { draft.setOpacity(value) } }
            }.toTypedArray())
        }
        section(parent, "Edit orientation") { group ->
            row(group, *NativeTgkOrientation.entries.map { value ->
                button(value.name.lowercase().replaceFirstChar { it.uppercase() }, draft.orientation == value) {
                    orientationChanged(value)
                }
            }.toTypedArray())
        }
        parent.addView(text("Drag the header to move this panel. Collapse it for more room. Cancel discards unsaved changes.", small = true))
    }
}
