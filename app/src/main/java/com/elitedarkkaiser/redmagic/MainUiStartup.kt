package com.elitedarkkaiser.redmagic

internal object MainUiStartup {

    fun applySavedHardwareState(
        applySavedFanLedStateOnLaunch: () -> Unit,
        applySavedLogoLedStateOnLaunch: () -> Unit,
        applySavedShoulderLedStateOnLaunch: () -> Unit,
        applySavedPumpStateOnLaunch: () -> Unit,
        setRealTimePreviewEnabled: (Boolean) -> Unit,
        isRealTimePreviewEnabledSaved: () -> Boolean,
        setUseFahrenheit: (Boolean) -> Unit,
        isUseFahrenheitSaved: () -> Boolean,
        setAutoPumpEnabled: (Boolean) -> Unit,
        isAutoPumpEnabledSaved: () -> Boolean
    ) {
        // UI launch should restore UI state only.
        // Hardware writes are owned by explicit actions/services, not app open.
        applySavedFanLedStateOnLaunch()
        applySavedLogoLedStateOnLaunch()
        applySavedShoulderLedStateOnLaunch()
        applySavedPumpStateOnLaunch()

        setRealTimePreviewEnabled(isRealTimePreviewEnabledSaved())
        setUseFahrenheit(isUseFahrenheitSaved())
        setAutoPumpEnabled(isAutoPumpEnabledSaved())
    }

    fun applyLaunchHardware(
        fanLedEnabled: Boolean,
        fanLedEffect: String,
        fanLedColor: Int,
        logoLedEnabled: Boolean,
        logoLedEffect: String,
        logoLedColor: Int,
        shoulderLedEnabled: Boolean,
        shoulderLedEffect: String,
        shoulderLedColor: Int,
        pumpEnabled: Boolean,
        pumpProfile: String,
        applyFanLedSelection: (String, Int) -> Unit,
        startFanLedService: () -> Unit,
        stopFanLedService: () -> Unit
    ) {
        // Do not write hardware on every app launch.
        // Services and user actions apply hardware state.
    }
}
