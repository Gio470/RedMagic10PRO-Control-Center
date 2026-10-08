package com.elitedarkkaiser.redmagic

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup
import com.elitedarkkaiser.redmagic.ui.hostComposeFrom

internal object PumpDialogUi {

    data class Deps(
        val textPrimary: Int,
        val textSecondary: Int,
        val panelColor: Int,
        val borderColor: Int,
        val panelPressed: Int,
        val typeface: Typeface?,
        val dp: (Int) -> Int,
        val roundedBg: (Int, Int, Int) -> Drawable,
        val roundedFill: (Int, Int) -> Drawable,
        val space: (Int) -> View
    )

    fun showPumpProfileDialog(
        activity: MainActivity,
        originalEnabled: Boolean,
        originalProfile: String,
        currentProfile: () -> String,
        setPumpEnabled: (Boolean) -> Unit,
        setPumpProfile: (String) -> Unit,
        applyHardwareProfile: (String) -> Unit,
        disablePump: () -> Unit,
        savePumpState: () -> Unit,
        confirmExperimentalPumpThenApply: () -> Unit,
        setDialogRefreshPump: (((() -> Unit)?) -> Unit),
        deps: Deps
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(deps.dp(22), deps.dp(18), deps.dp(22), deps.dp(12))
            background = deps.roundedBg(deps.panelColor, deps.borderColor, 22)
        }

        val titleView = TextView(activity).apply {
            text = "Liquid Cooling Flow Rate"
            textSize = 20f
            setTextColor(deps.textPrimary)
            setTypeface(deps.typeface, Typeface.BOLD)
        }

        val subtitleView = TextView(activity).apply {
            text = "Choose a pump profile with instant preview"
            textSize = 13f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(8), 0, 0)
        }

        val profileLabel = TextView(activity).apply {
            text = "Flow rate"
            textSize = 12f
            setTextColor(deps.textSecondary)
            setPadding(0, deps.dp(16), 0, deps.dp(8))
        }

        // One connected button group in place of the four separate profile chips: it owns the
        // selection, so the old PumpActions.repaintButtons -- which had to be handed every button
        // and repaint each one's background by hand -- is gone.
        lateinit var profilesGroup: ValueButtonGroup<String>

        // Set once the dialog exists; a tap only ever needs to re-sync the group with whatever the
        // apply actually settled on, which may not be what was tapped (the experimental profile
        // goes through a confirmation first).
        var refreshDialog: (() -> Unit)? = null

        profilesGroup = ValueButtonGroup(
            activity,
            listOf(
                "Slow" to "slow",
                "Medium" to "medium",
                "Quick" to "quick",
                "△ Exp" to "experimental"
            ),
            currentProfile()
        ) { value ->
            if (value == "experimental") {
                confirmExperimentalPumpThenApply()
            } else {
                PumpActions.applyPreviewSelection(
                    profile = value,
                    setPumpProfile = setPumpProfile,
                    setPumpEnabled = setPumpEnabled,
                    applyHardwareProfile = applyHardwareProfile,
                    refreshDialog = { refreshDialog?.invoke() }
                )
            }
        }

        val profilesRow = profilesGroup.view

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, deps.dp(18), 0, 0)
        }

        val cancelBtn = Button(activity).apply {
            text = "Cancel"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)
            background = deps.roundedFill(Color.parseColor("#1E2633"), 14)
            setPadding(deps.dp(18), deps.dp(10), deps.dp(18), deps.dp(10))
        }

        val saveBtn = Button(activity).apply {
            text = "Save"
            textSize = 13f
            isAllCaps = false
            setTextColor(deps.textPrimary)
            background = deps.roundedFill(deps.panelPressed, 14)
            setPadding(deps.dp(20), deps.dp(10), deps.dp(20), deps.dp(10))
        }

        buttonRow.addView(cancelBtn)
        buttonRow.addView(deps.space(deps.dp(10)))
        buttonRow.addView(saveBtn)

        container.addView(titleView)
        container.addView(subtitleView)
        container.addView(profileLabel)
        container.addView(profilesRow)
        container.addView(buttonRow)

        // These two still use the framework AlertDialog rather than M3Dialog's sheet, so the
        // button group's ComposeView has to be given the activity's owners by hand.
        container.hostComposeFrom(activity)

        val dialog = AlertDialog.Builder(activity)
            .setView(container)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )

        fun repaint() {
            profilesGroup.value = currentProfile()
        }

        setDialogRefreshPump { repaint() }
        refreshDialog = { repaint() }

        cancelBtn.setOnClickListener {
            PumpActions.restoreOriginalState(
                originalEnabled = originalEnabled,
                originalProfile = originalProfile,
                setPumpEnabled = setPumpEnabled,
                setPumpProfile = setPumpProfile,
                applyHardwareProfile = applyHardwareProfile,
                disablePump = disablePump
            )
            dialog.dismiss()
        }

        saveBtn.setOnClickListener {
            savePumpState()
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            PumpActions.restoreOriginalState(
                originalEnabled = originalEnabled,
                originalProfile = originalProfile,
                setPumpEnabled = setPumpEnabled,
                setPumpProfile = setPumpProfile,
                applyHardwareProfile = applyHardwareProfile,
                disablePump = disablePump
            )
        }

        repaint()
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            setDimAmount(0.65f)
        }
    }
}
