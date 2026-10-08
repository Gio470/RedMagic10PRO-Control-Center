package com.elitedarkkaiser.redmagic

import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.ComposeSlider
import com.elitedarkkaiser.redmagic.ui.CycleSwatch
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

internal object FanLedDialogUi {
    data class Deps(
        val colorDot: (Int, String, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable
    )

    fun showFanLedDialog(
        activity: MainActivity,
        originalEnabled: Boolean,
        originalEffect: String,
        originalColor: Int,
        currentEnabled: () -> Boolean,
        currentEffect: () -> String,
        currentColor: () -> Int,
        setEnabled: (Boolean) -> Unit,
        setEffect: (String) -> Unit,
        setColor: (Int) -> Unit,
        applyPreviewIfEnabled: () -> Unit,
        applySelection: (String, Int) -> Unit,
        disableLed: () -> Unit,
        saveState: () -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit,
        anyLedEnabled: () -> Boolean,
        setDialogRefresh: (((() -> Unit)?) -> Unit),
        deps: Deps,
        cycleSupported: Boolean = true,
        title: String = "Fan LED",
        subtitle: String = "Confirmed working options for fan LED",
        enableLabel: String = "Enable fan light"
    ) {
        val m3 = M3(activity)
        var dialogRefresh: (() -> Unit)? = null

        val enableCheck = M3Dialog.checkBox(activity, enableLabel, currentEnabled()) { checked ->
            setEnabled(checked)
            if (checked) {
                applySelection(currentEffect(), currentColor())
            } else {
                disableLed()
            }
        }

        // Flow and burst were never offered, though the chip has always played them, and they are
        // what the stock app played its own presets under. Five labels is more than a phone width
        // holds, so the group wraps three and two — one connected run either way.
        val effectsGroup = ValueButtonGroup(
            activity,
            listOf(
                "Steady" to "steady",
                "Breathe" to "breathe",
                "Flashing" to "flashing",
                "Flow" to "flow",
                "Burst" to "burst"
            ),
            currentEffect(),
            maxItemsInEachRow = 3
        ) { value ->
            setEffect(value)
            applyPreviewIfEnabled()
            dialogRefresh?.invoke()
        }

        val effectsRow = effectsGroup.view

        val colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
        }

        // While the rotation is what's selected, the swatches say which colours are in it rather
        // than picking one — the same list the module calls a zone's active colours.
        fun cycling() = cycleSupported && currentColor() == FanLedCycle.CYCLE_COLOR

        var cycleColors = FanLedCycle.colors(activity)

        fun onColorTapped(colorId: Int) {
            if (cycling()) {
                val next = if (colorId in cycleColors) cycleColors - colorId
                else cycleColors + colorId
                // A rotation of nothing has nothing to show, so the last one stays put.
                if (next.isNotEmpty()) {
                    cycleColors = next
                    FanLedCycle.setColors(activity, next)
                    applyPreviewIfEnabled()
                }
            } else {
                setColor(colorId)
                // Anything saved back when presets were their own thing carries no effect of its
                // own; flow is the one the stock app played them under.
                if (currentEffect().startsWith("preset:")) setEffect("flow")
                applyPreviewIfEnabled()
            }
            dialogRefresh?.invoke()
        }

        fun addColor(row: LinearLayout, colorId: Int, hex: String) {
            row.addView(deps.colorDot(colorId, hex) { onColorTapped(colorId) })
        }

        addColor(colorRow, 1, "#FF0000")
        colorRow.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow, 3, "#FF8C00")
        colorRow.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow, 4, "#FFD600")
        colorRow.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow, 5, "#00E676")

        addColor(colorRow2, 6, "#00E5FF")
        colorRow2.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow2, 7, "#1565FF")
        colorRow2.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow2, 8, "#A020F0")
        colorRow2.addView(M3Dialog.hGap(activity, 10))
        addColor(colorRow2, 9, "#FF69B4")

        // The upper half of the palette lights the fan's matrix in several colours at once, so each
        // is drawn as the quarters it actually shows rather than as one flat tone.
        val patterns = HardwareController.FAN_LED_PATTERNS

        val solidHex = mapOf(
            1 to "#FF0000", 3 to "#FF8C00", 4 to "#FFD600", 5 to "#00E676",
            6 to "#00E5FF", 7 to "#1565FF", 8 to "#A020F0", 9 to "#FF69B4"
        )

        // A pattern stands in as its first quarter wherever one colour has to represent it — in a
        // rotation's swatch, say, where it is one wedge among several.
        val paletteHex = solidHex +
            patterns.associate { (colorId, quarters) -> colorId to solidHex.getValue(quarters.first()) }

        val patternSwatches = mutableListOf<Pair<Int, CycleSwatch>>()

        fun addPattern(row: LinearLayout, colorId: Int, quarters: List<Int>) {
            val swatch = CycleSwatch(activity).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { onColorTapped(colorId) }
            }
            swatch.setSwatch(quarters.mapNotNull { solidHex[it] }, false)
            patternSwatches += colorId to swatch
            row.addView(swatch)
        }

        val colorRow3 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
        }
        val colorRow4 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(12), 0, 0)
        }

        patterns.take(4).forEachIndexed { index, (colorId, quarters) ->
            if (index > 0) colorRow3.addView(M3Dialog.hGap(activity, 10))
            addPattern(colorRow3, colorId, quarters)
        }
        patterns.drop(4).forEachIndexed { index, (colorId, quarters) ->
            if (index > 0) colorRow4.addView(M3Dialog.hGap(activity, 10))
            addPattern(colorRow4, colorId, quarters)
        }

        val cycleSwatch = CycleSwatch(activity)

        // The colour to come back to when the rotation is switched off, so turning it on and off
        // again lands where it started rather than on a default.
        var lastSolidColor = currentColor().takeIf { it != FanLedCycle.CYCLE_COLOR } ?: 5

        val cycleSwitch = MaterialSwitch(activity).apply {
            isChecked = cycleSupported && currentColor() == FanLedCycle.CYCLE_COLOR
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    lastSolidColor = currentColor().takeIf { it != FanLedCycle.CYCLE_COLOR }
                        ?: lastSolidColor
                    setColor(FanLedCycle.CYCLE_COLOR)
                    if (currentEffect().startsWith("preset:")) setEffect("flow")
                } else {
                    setColor(lastSolidColor)
                }
                // Saved as it is switched, because the rotation is previewed by the service and the
                // service reads what is saved. Cancel puts the old state back the same way.
                saveState()
                applyPreviewIfEnabled()
                dialogRefresh?.invoke()
            }
        }

        val cycleHint = M3Dialog.body(
            activity,
            "Pick a set below, or tap the colours above to build your own."
        )

        val speedLabel = TextView(activity).apply {
            m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
        }

        lateinit var speedSlider: ComposeSlider
        speedSlider = ComposeSlider(
            context = activity,
            initialValue = FanLedCycle.speedMs(activity).toFloat(),
            initialValueRange = FanLedCycle.MIN_SPEED_MS.toFloat()..FanLedCycle.MAX_SPEED_MS.toFloat(),
            initialSteps = 8,
            onValueChange = { raw ->
                val ms = raw.roundToInt()
                FanLedCycle.setSpeedMs(activity, ms)
                speedLabel.text = "Speed: ${"%.1f".format(ms / 1000f)}s"
                applyPreviewIfEnabled()
            }
        )

        val cycleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, m3.dp(16), 0, 0)
            addView(cycleSwatch)
            addView(M3Dialog.hGap(activity, 12))
            addView(TextView(activity).apply {
                text = "Colour cycle"
                m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(cycleSwitch)
        }

        // The old preset row: each swatch shows its four quarters and cycles the distinct colours
        // among them, so what it draws is what the fan plays.
        val presetSwatches = mutableListOf<Triple<List<Int>, List<Int>, CycleSwatch>>()

        fun presetRow(presets: List<List<Int>>): LinearLayout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, m3.dp(12), 0, 0)
                presets.forEachIndexed { index, quarters ->
                    if (index > 0) addView(M3Dialog.hGap(activity, 10))
                    val rotation = quarters.distinct()
                    val swatch = CycleSwatch(activity).apply {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            cycleColors = rotation
                            FanLedCycle.setColors(activity, rotation)
                            applyPreviewIfEnabled()
                            dialogRefresh?.invoke()
                        }
                    }
                    presetSwatches += Triple(quarters, rotation, swatch)
                    addView(swatch)
                }
            }

        val presetRows = FanLedCycle.PRESETS.chunked(4).map { presetRow(it) }

        val cycleControls = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(cycleHint)
            presetRows.forEach { addView(it) }
            addView(speedLabel)
            addView(speedSlider.view)
        }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, title))
            addView(M3Dialog.body(activity, subtitle))
            addView(enableCheck)
            addView(m3.insetGroup("Effect").apply {
                addView(effectsRow)
            })
            addView(m3.insetGroup("Color").apply {
                addView(colorRow)
                addView(colorRow2)
                addView(colorRow3)
                addView(colorRow4)
                if (cycleSupported) {
                    addView(cycleRow)
                    addView(cycleControls)
                }
            })
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        fun restoreOriginal() {
            setEnabled(originalEnabled)
            setEffect(originalEffect)
            setColor(originalColor)
            // Picking the rotation saves as it previews, so undoing has to save too.
            saveState()

            if (currentEnabled()) {
                applySelection(currentEffect(), currentColor())
            } else {
                disableLed()
            }
        }

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") {
            restoreOriginal()
            dialog.dismiss()
        }

        val saveBtn = M3Dialog.filledButton(activity, "Save") {
            saveState()
            if (currentEnabled()) {
                applySelection(currentEffect(), currentColor())
            } else {
                disableLed()
            }
            if (anyLedEnabled()) {
                startFanLedService()
            } else {
                stopFanLedService()
            }
            dialog.dismiss()
        }

        panel.addView(M3Dialog.buttonRow(activity, cancelBtn, saveBtn))

        fun repaint() {
            effectsGroup.value = currentEffect()
        }

        fun updateColorDots() {
            // A "preset:" effect is only ever left over from what was saved before these became
            // ordinary colours; it names no colour, so nothing is shown as selected.
            val presetActive = currentEffect().startsWith("preset:")
            fun selected(colorId: Int) = when {
                presetActive -> false
                // In cycle mode a ringed swatch means "in the rotation", not "this is the colour".
                cycling() -> colorId in cycleColors
                else -> currentColor() == colorId
            }

            colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", selected(1))
            colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", selected(3))
            colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", selected(4))
            colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", selected(5))

            colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", selected(6))
            colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", selected(7))
            colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", selected(8))
            colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", selected(9))

            patternSwatches.forEach { (colorId, swatch) ->
                swatch.setSwatch(
                    patterns.first { it.first == colorId }.second.mapNotNull { solidHex[it] },
                    selected(colorId)
                )
            }
        }

        fun refreshUi() {
            repaint()
            updateColorDots()
            cycleSwatch.setSwatch(cycleColors.mapNotNull { paletteHex[it] }, cycling())
            if (cycleSwitch.isChecked != cycling()) cycleSwitch.isChecked = cycling()
            presetSwatches.forEach { (quarters, rotation, swatch) ->
                swatch.setSwatch(quarters.mapNotNull { paletteHex[it] }, rotation == cycleColors)
            }
            // The speed and the hint only mean anything once the rotation is the selection.
            cycleControls.visibility = if (cycling()) View.VISIBLE else View.GONE
            speedLabel.text = "Speed: ${"%.1f".format(FanLedCycle.speedMs(activity) / 1000f)}s"
        }

        dialogRefresh = { refreshUi() }
        setDialogRefresh(dialogRefresh)
        refreshUi()

        dialog = M3Dialog.show(activity, M3Dialog.scroll(activity, panel))
        dialog.setOnCancelListener { restoreOriginal() }
    }
}
