package com.elitedarkkaiser.redmagic

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup

internal object CallLightingProfileUi {

    data class Deps(
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val fanPresetBubble: (String, String, String, String, String, Boolean, () -> Unit) -> View
    )

    data class ZoneKeys(
        val enabledKey: String,
        val effectKey: String,
        val colorKey: String,
        val label: String,
        val defaultEffect: String,
        val defaultColor: Int
    )

    fun show(
        activity: MainActivity,
        title: String,
        subtitle: String,
        fanKeys: ZoneKeys,
        logoKeys: ZoneKeys,
        shoulderKeys: ZoneKeys,
        deps: Deps
    ) {
        var fan = CallLightingState.readLed(activity, fanKeys.enabledKey, fanKeys.effectKey, fanKeys.colorKey, false, fanKeys.defaultEffect, fanKeys.defaultColor)
        var logo = CallLightingState.readLed(activity, logoKeys.enabledKey, logoKeys.effectKey, logoKeys.colorKey, false, logoKeys.defaultEffect, logoKeys.defaultColor)
        val shoulder = CallLightingState.readLed(activity, shoulderKeys.enabledKey, shoulderKeys.effectKey, shoulderKeys.colorKey, false, shoulderKeys.defaultEffect, shoulderKeys.defaultColor)

        val modeLabel = if (title.contains("Incoming", ignoreCase = true)) "incoming calls" else "connected calls"

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, title))
            addView(M3Dialog.body(activity, subtitle))
            addView(zoneEditor(activity, fanKeys.label, fan, deps, modeLabel, showFanPresets = true) { fan = it })
            addView(zoneEditor(activity, logoKeys.label, logo, deps, modeLabel, showFanPresets = false) { logo = it })
            // shoulder zone omitted: RM 10 Pro has no shoulder LED strip.
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() }
        val saveBtn = M3Dialog.filledButton(activity, "Save") {
            CallLightingState.saveLed(activity, fanKeys.enabledKey, fanKeys.effectKey, fanKeys.colorKey, fan)
            CallLightingState.saveLed(activity, logoKeys.enabledKey, logoKeys.effectKey, logoKeys.colorKey, logo)
            CallLightingState.saveLed(activity, shoulderKeys.enabledKey, shoulderKeys.effectKey, shoulderKeys.colorKey, shoulder)

            if (CallLightingState.isEnabled(activity)) {
                HardwareServiceActions.startCallLighting(activity)
            }

            dialog.dismiss()
        }

        panel.addView(M3Dialog.buttonRow(activity, cancelBtn, saveBtn))

        dialog = M3Dialog.show(activity, M3Dialog.scroll(activity, panel))
    }

    private fun zoneEditor(
        activity: MainActivity,
        label: String,
        initial: LedState,
        deps: Deps,
        modeLabel: String,
        showFanPresets: Boolean,
        onChanged: (LedState) -> Unit
    ): LinearLayout {
        val m3 = M3(activity)

        var enabled = initial.enabled
        var effect = initial.effect
        var color = initial.color

        lateinit var effectGroup: ValueButtonGroup<String>
        lateinit var row1: LinearLayout
        lateinit var row2: LinearLayout

        fun publish() {
            onChanged(LedState(enabled, effect, color))
        }

        fun refreshEffects() {
            effectGroup.value = effect
        }

        fun refreshColors() {
            row1.getChildAt(0).background = deps.colorDotDrawable("#FF0000", color == 1)
            row1.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", color == 3)
            row1.getChildAt(4).background = deps.colorDotDrawable("#FFD600", color == 4)
            row1.getChildAt(6).background = deps.colorDotDrawable("#00E676", color == 5)
            row2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", color == 6)
            row2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", color == 7)
            row2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", color == 8)
            row2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", color == 9)
        }

        fun applyFanPreset(value: String) {
            enabled = true
            effect = "preset:$value"
            color = -1
            refreshEffects()
            refreshColors()
            publish()
        }

        fun colorDot(id: Int, hex: String): View =
            deps.colorDotGeneric(hex, color == id) {
                color = id
                if (effect.startsWith("preset:")) effect = "steady"
                refreshEffects()
                refreshColors()
                publish()
            }

        /** Each zone reads as its own darker inset so the two stacked editors stay distinct. */
        val card = m3.insetGroup(label)

        card.addView(M3Dialog.checkBox(activity, "Enable $label for $modeLabel", enabled) { checked ->
            enabled = checked
            publish()
        })

        effectGroup = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flashing" to "flashing"),
            effect
        ) { value ->
            effect = value
            refreshEffects()
            publish()
        }

        val effectRow = effectGroup.view

        row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
            addView(colorDot(1, "#FF0000"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(3, "#FF8C00"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(4, "#FFD600"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(5, "#00E676"))
        }

        row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
            addView(colorDot(6, "#00E5FF"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(7, "#1565FF"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(8, "#A020F0"))
            addView(M3Dialog.hGap(activity, 10))
            addView(colorDot(9, "#FF69B4"))
        }

        card.addView(M3Dialog.sectionLabel(activity, "Effect"))
        card.addView(effectRow)
        card.addView(M3Dialog.sectionLabel(activity, "Color"))
        card.addView(row1)
        card.addView(row2)

        if (showFanPresets) {
            fun presetBubble(hex1: String, hex2: String, hex3: String, hex4: String, value: String): View =
                deps.fanPresetBubble(hex1, hex2, hex3, hex4, value, effect == "preset:$value") {
                    applyFanPreset(value)
                }

            fun presetRow(topPaddingDp: Int, vararg bubbles: View): LinearLayout =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, m3.dp(topPaddingDp), 0, 0)
                    bubbles.forEachIndexed { index, bubble ->
                        if (index > 0) addView(M3Dialog.hGap(activity, 10))
                        addView(bubble)
                    }
                }

            card.addView(M3Dialog.sectionLabel(activity, "Fan LED presets"))
            card.addView(presetRow(
                0,
                presetBubble("#FF69B4", "#FF8C00", "#1565FF", "#00E676", "0x3002101"),
                presetBubble("#A020F0", "#FFD600", "#00E5FF", "#FF69B4", "0x3002102"),
                presetBubble("#A020F0", "#00E5FF", "#1565FF", "#FF69B4", "0x3002103"),
                presetBubble("#FF69B4", "#1565FF", "#1565FF", "#FF69B4", "0x3002104")
            ))
            card.addView(presetRow(
                10,
                presetBubble("#A020F0", "#00E5FF", "#00E5FF", "#A020F0", "0x3002105"),
                presetBubble("#00E5FF", "#FF8C00", "#FF8C00", "#00E5FF", "0x3002106"),
                presetBubble("#FFD600", "#00E676", "#00E676", "#FFD600", "0x3002107")
            ))
        }

        refreshEffects()
        refreshColors()
        return card
    }
}
