package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkProfile
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkLayout
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkStorage
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkTriggerBehavior

internal object GameTriggerControlsUi {
    fun add(activity: Activity, m3: M3, content: LinearLayout, profile: NativeTgkProfile,
        save: ((NativeTgkProfile) -> NativeTgkProfile) -> Unit) {
        fun choose(title: String, options: List<Pair<String, () -> Unit>>) {
            lateinit var dialog: Panel
            val rows = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                options.forEach { (label, select) -> addView(m3.row(title = label,
                    glyph = Icons.TRIGGERS, onClick = { dialog.dismiss(); select() })) }
                m3.connectRows(this)
            }
            dialog = M3Dialog.show(activity, M3Dialog.panel(activity).apply {
                addView(M3Dialog.header(activity, title, glyph = Icons.TRIGGERS))
                addView(M3Dialog.scroll(activity, rows), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            })
        }
        content.addView(m3.groupHeader("Layouts"))
        content.addView(m3.row(title = "Selected layout", supporting = profile.activeLayout()?.name ?: "Default",
            glyph = Icons.APP_LIST, onClick = {
                choose("Choose layout", profile.layouts.map { layout -> layout.name to {
                    save { current -> if (current.layouts.any { it.id == layout.id }) current.copy(activeLayoutId = layout.id) else current }
                } })
            }))
        if (profile.layouts.size < NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) {
            content.addView(m3.row(title = "Duplicate layout", supporting = "Copy these targets and behaviors into a new layout",
                glyph = Icons.CONFIGURE, onClick = {
                    val id = java.util.UUID.randomUUID().toString()
                    save { current ->
                        if (current.layouts.size >= NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) current else {
                            val name = "Layout ${current.layouts.size + 1}"
                            val layout = current.activeLayout()?.copy(id = id, name = name) ?: NativeTgkLayout(id, name)
                            current.copy(activeLayoutId = id, layouts = current.layouts + layout)
                        }
                    }
                }))
        }
        profile.activeLayout()?.let { layout ->
            content.addView(m3.row(title = "Rename layout", supporting = layout.name, glyph = Icons.CONFIGURE,
                onClick = {
                    lateinit var dialog: Panel
                    val name = M3Dialog.textField(activity, "Layout name").apply { setText(layout.name) }
                    dialog = M3Dialog.show(activity, M3Dialog.panel(activity).apply {
                        addView(M3Dialog.header(activity, "Rename layout", glyph = Icons.CONFIGURE))
                        addView(name)
                        addView(M3Dialog.buttonRow(activity, M3Dialog.filledButton(activity, "Save") {
                            val text = name.text.toString().trim()
                            if (text.isBlank() || text.length > 40) { name.error = "Use 1–40 characters" }
                            else {
                                save { current -> current.copy(layouts = current.layouts.map {
                                    if (it.id == layout.id) it.copy(name = text) else it
                                }) }
                                dialog.dismiss()
                            }
                        }))
                    })
                }))
        }
        if (profile.layouts.size > 1) content.addView(m3.row(title = "Remove layout",
            supporting = "Delete the selected layout", glyph = Icons.XMARK, onClick = {
                save { current ->
                    val retained = current.layouts.filterNot { it.id == profile.activeLayout()?.id }
                    if (retained.isEmpty()) current else current.copy(activeLayoutId = retained.first().id, layouts = retained)
                }
            }))

        content.addView(m3.groupHeader("Trigger behavior"))
        fun behavior(left: Boolean) {
            val current = if (left) profile.effectiveLeftBehavior() else profile.effectiveRightBehavior()
            val count = if (left) profile.effectiveLeftRapidFireCount() else profile.effectiveRightRapidFireCount()
            val label = when (current) {
                NativeTgkTriggerBehavior.SINGLE_TOUCH -> "Single touch"
                NativeTgkTriggerBehavior.LONG_PRESS -> "Long press"
                NativeTgkTriggerBehavior.RAPID_FIRE -> "Rapid fire · $count"
            }
            content.addView(m3.row(title = if (left) "Left trigger behavior" else "Right trigger behavior",
                supporting = label, glyph = Icons.TRIGGERS, onClick = {
                    val choices = listOf("Single touch" to (NativeTgkTriggerBehavior.SINGLE_TOUCH to 0),
                        "Long press" to (NativeTgkTriggerBehavior.LONG_PRESS to 0)) + listOf(2, 5, 10).map {
                        "Rapid fire · $it" to (NativeTgkTriggerBehavior.RAPID_FIRE to it)
                    }
                    choose(if (left) "Left trigger" else "Right trigger", choices.map { (name, value) ->
                        name to { save { current -> current.withTriggerBehavior(left, value.first, value.second) } }
                    })
                }))
        }
        behavior(true)
        behavior(false)
    }
}
