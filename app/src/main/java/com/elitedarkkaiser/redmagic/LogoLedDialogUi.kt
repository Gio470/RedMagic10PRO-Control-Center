package com.elitedarkkaiser.redmagic

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup

internal object LogoLedDialogUi {
    data class Deps(
        val dp: (Int) -> Int,
        val space: (Int) -> View,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable
    )

    fun showLogoLedDialog(
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
        applyEffect: (String, Int) -> Unit,
        disableLed: () -> Unit,
        saveState: () -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit,
        anyLedEnabled: () -> Boolean,
        setDialogRefresh: (((() -> Unit)?) -> Unit),
        deps: Deps
    ) {
        val m3 = M3(activity)
        var dialogRefresh: (() -> Unit)? = null

        val container = M3Dialog.panel(activity)
        val titleView = M3Dialog.title(activity, "Logo LED")
        val subtitleView = M3Dialog.body(activity, "Customize logo LED with instant preview")

        val enableCheck = M3Dialog.checkBox(activity, "Enable logo light", currentEnabled()) { checked ->
            setEnabled(checked)
            if (checked) {
                applyEffect(currentEffect(), currentColor())
            } else {
                disableLed()
            }
        }


        val effectsGroup = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flashing" to "flashing"),
            currentEffect()
        ) { value ->
            setEffect(value)
            if (currentEnabled()) applyEffect(currentEffect(), currentColor())
            dialogRefresh?.invoke()
        }

        val effectsRow = effectsGroup.view

        val colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
        }

        fun addColor(row: LinearLayout, colorId: Int, hex: String) {
            row.addView(deps.colorDotGeneric(hex, currentColor() == colorId) {
                setColor(colorId)
                applyPreviewIfEnabled()
                dialogRefresh?.invoke()
            })
        }

        addColor(colorRow, 1, "#FF0000")
        colorRow.addView(deps.space(deps.dp(8)))
        addColor(colorRow, 3, "#FF8C00")
        colorRow.addView(deps.space(deps.dp(8)))
        addColor(colorRow, 4, "#FFD600")
        colorRow.addView(deps.space(deps.dp(8)))
        addColor(colorRow, 5, "#00E676")

        addColor(colorRow2, 6, "#00E5FF")
        colorRow2.addView(deps.space(deps.dp(8)))
        addColor(colorRow2, 7, "#1565FF")
        colorRow2.addView(deps.space(deps.dp(8)))
        addColor(colorRow2, 8, "#A020F0")
        colorRow2.addView(deps.space(deps.dp(8)))
        addColor(colorRow2, 9, "#FF69B4")

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") {}
        val saveBtn = M3Dialog.filledButton(activity, "Save") {}
        val buttonRow = M3Dialog.buttonRow(activity, cancelBtn, saveBtn)

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(enableCheck)
        container.addView(m3.insetGroup("Effect").apply { addView(effectsRow) })
        container.addView(m3.insetGroup("Color").apply {
            addView(colorRow)
            addView(colorRow2)
        })
        container.addView(buttonRow)

        val dialog = M3Dialog.show(activity, M3Dialog.scroll(activity, container))

        fun restoreOriginal() {
            setEnabled(originalEnabled)
            setEffect(originalEffect)
            setColor(originalColor)

            if (currentEnabled()) {
                applyEffect(currentEffect(), currentColor())
            } else {
                disableLed()
            }
        }

        cancelBtn.setOnClickListener {
            restoreOriginal()
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            saveState()
            if (currentEnabled()) {
                applyEffect(currentEffect(), currentColor())
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

        dialog.setOnCancelListener {
            restoreOriginal()
        }

        fun repaint() {
            effectsGroup.value = currentEffect()
        }

        fun updateColorDots() {
            colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", currentColor() == 1)
            colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", currentColor() == 3)
            colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", currentColor() == 4)
            colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", currentColor() == 5)

            colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", currentColor() == 6)
            colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", currentColor() == 7)
            colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", currentColor() == 8)
            colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", currentColor() == 9)
        }

        fun refreshUi() {
            repaint()
            updateColorDots()
        }

        dialogRefresh = { refreshUi() }
        setDialogRefresh(dialogRefresh)

        refreshUi()
    }
}
