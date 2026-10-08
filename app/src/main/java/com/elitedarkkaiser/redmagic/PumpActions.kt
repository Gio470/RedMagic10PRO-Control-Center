package com.elitedarkkaiser.redmagic

import android.graphics.drawable.Drawable
import android.widget.Button

internal object PumpActions {

    fun applyPreviewSelection(
        profile: String,
        setPumpProfile: (String) -> Unit,
        setPumpEnabled: (Boolean) -> Unit,
        applyHardwareProfile: (String) -> Unit,
        refreshDialog: () -> Unit
    ) {
        setPumpProfile(profile)
        setPumpEnabled(true)
        applyHardwareProfile(profile)
        refreshDialog()
    }

    fun restoreOriginalState(
        originalEnabled: Boolean,
        originalProfile: String,
        setPumpEnabled: (Boolean) -> Unit,
        setPumpProfile: (String) -> Unit,
        applyHardwareProfile: (String) -> Unit,
        disablePump: () -> Unit
    ) {
        setPumpEnabled(originalEnabled)
        setPumpProfile(originalProfile)

        if (originalEnabled) {
            applyHardwareProfile(originalProfile)
        } else {
            disablePump()
        }
    }

}
