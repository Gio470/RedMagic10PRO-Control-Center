package com.elitedarkkaiser.redmagic

internal object ShoulderLedActions {

    fun setPreviewEnabled(
        enabled: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        applyEffect: (String, Int) -> Unit,
        disableLed: () -> Unit,
        currentEffect: () -> String,
        currentColor: () -> Int
    ) {
        onEnabledChanged(enabled)
        if (enabled) {
            applyEffect(currentEffect(), currentColor())
        } else {
            disableLed()
        }
    }

    fun setPreviewEffect(
        effect: String,
        onEffectChanged: (String) -> Unit,
        applyPreviewIfEnabled: () -> Unit,
        refreshDialog: () -> Unit
    ) {
        onEffectChanged(effect)
        applyPreviewIfEnabled()
        refreshDialog()
    }

    fun setPreviewColor(
        color: Int,
        onColorChanged: (Int) -> Unit,
        applyPreviewIfEnabled: () -> Unit,
        refreshDialog: () -> Unit
    ) {
        onColorChanged(color)
        applyPreviewIfEnabled()
        refreshDialog()
    }

    fun restoreOriginalState(
        originalEnabled: Boolean,
        originalEffect: String,
        originalColor: Int,
        setEnabled: (Boolean) -> Unit,
        setEffect: (String) -> Unit,
        setColor: (Int) -> Unit,
        applyEffect: (String, Int) -> Unit,
        disableLed: () -> Unit
    ) {
        setEnabled(originalEnabled)
        setEffect(originalEffect)
        setColor(originalColor)

        if (originalEnabled) {
            applyEffect(originalEffect, originalColor)
        } else {
            disableLed()
        }
    }

    fun commitState(
        enabled: Boolean,
        effect: String,
        color: Int,
        saveState: () -> Unit,
        applyEffect: (String, Int) -> Unit,
        disableLed: () -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit,
        anyLedEnabled: () -> Boolean
    ) {
        saveState()

        if (enabled) {
            applyEffect(effect, color)
        } else {
            disableLed()
        }

        if (anyLedEnabled()) {
            startFanLedService()
        } else {
            stopFanLedService()
        }
    }
}
