package com.elitedarkkaiser.redmagic

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup

internal object ChargingLedProfileDialog {
    data class Deps(
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val fanPresetBubble: (String, String, String, String, String, Boolean, () -> Unit) -> View
    )

    fun show(
        activity: MainActivity,
        title: String,
        subtitle: String,
        originalEnabled: Boolean,
        originalEffect: String,
        originalColor: Int,
        onSave: (enabled: Boolean, effect: String, color: Int) -> Unit,
        deps: Deps,
        showFanPresets: Boolean = false
    ) {
        val m3 = M3(activity)

        var enabled = originalEnabled
        var effect = originalEffect
        var color = originalColor

        lateinit var effectGroup: ValueButtonGroup<String>
        lateinit var colorRow: LinearLayout
        lateinit var colorRow2: LinearLayout
        var colorRowsReady = false

        fun refreshButtons() {
            effectGroup.value = effect

            if (colorRowsReady) {
                colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", color == 1)
                colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", color == 3)
                colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", color == 4)
                colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", color == 5)

                colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", color == 6)
                colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", color == 7)
                colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", color == 8)
                colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", color == 9)
            }
        }

        effectGroup = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flash" to "flashing"),
            effect
        ) { value ->
            effect = value
            refreshButtons()
        }

        val effectRow = effectGroup.view

        colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
        }

        val chargingColors = listOf(
            1 to "#FF0000",
            3 to "#FF8C00",
            4 to "#FFD600",
            5 to "#00E676",
            6 to "#00E5FF",
            7 to "#1565FF",
            8 to "#A020F0",
            9 to "#FF69B4"
        )

        fun addColor(row: LinearLayout, colorId: Int, hex: String) {
            row.addView(deps.colorDotGeneric(hex, color == colorId) {
                color = colorId
                refreshButtons()
            })
            row.addView(M3Dialog.hGap(activity, 10))
        }

        chargingColors.take(4).forEach { (colorId, hex) -> addColor(colorRow, colorId, hex) }
        chargingColors.drop(4).forEach { (colorId, hex) -> addColor(colorRow2, colorId, hex) }

        colorRowsReady = true

        fun presetRow(vararg bubbles: View): LinearLayout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                bubbles.forEachIndexed { index, bubble ->
                    if (index > 0) addView(M3Dialog.hGap(activity, 10))
                    addView(bubble)
                }
            }

        fun presetBubble(hex1: String, hex2: String, hex3: String, hex4: String, value: String): View =
            deps.fanPresetBubble(hex1, hex2, hex3, hex4, value, effect == "preset:$value") {
                effect = "preset:$value"
                color = -1
                refreshButtons()
            }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, title))
            addView(M3Dialog.body(activity, subtitle))
            addView(M3Dialog.checkBox(activity, "Enable for charging mode", enabled) { checked ->
                enabled = checked
            })
            addView(m3.insetGroup("Effect").apply { addView(effectRow) })
            addView(m3.insetGroup("Color").apply {
                addView(colorRow)
                addView(colorRow2)
            })

            if (showFanPresets) {
                addView(m3.insetGroup("Fan presets").apply {
                    addView(presetRow(
                        presetBubble("#FF69B4", "#FF8C00", "#1565FF", "#00E676", "0x3002101"),
                        presetBubble("#A020F0", "#FFD600", "#00E5FF", "#FF69B4", "0x3002102"),
                        presetBubble("#A020F0", "#00E5FF", "#1565FF", "#FF69B4", "0x3002103"),
                        presetBubble("#FF69B4", "#1565FF", "#1565FF", "#FF69B4", "0x3002104")
                    ))
                    addView(M3Dialog.vGap(activity, 10))
                    addView(presetRow(
                        presetBubble("#A020F0", "#00E5FF", "#00E5FF", "#A020F0", "0x3002105"),
                        presetBubble("#00E5FF", "#FF8C00", "#FF8C00", "#00E5FF", "0x3002106"),
                        presetBubble("#FFD600", "#00E676", "#00E676", "#FFD600", "0x3002107")
                    ))
                })
            }
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() }
        val saveBtn = M3Dialog.filledButton(activity, "Save") {
            onSave(enabled, effect, color)
            dialog.dismiss()
        }

        panel.addView(M3Dialog.buttonRow(activity, cancelBtn, saveBtn))

        dialog = M3Dialog.show(activity, M3Dialog.scroll(activity, panel))

        refreshButtons()
    }
}
