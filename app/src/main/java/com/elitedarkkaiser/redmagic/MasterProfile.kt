package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.state.PumpState

data class MasterProfile(
    val name: String,
    val hardware: HardwareProfile,
    val gameMode: GameModeProfile,
    val gamePackages: Set<String>,
    val chargingEnabled: Boolean,
    val chargingFanLed: LedState,
    val chargingLogoLed: LedState,
    val chargingShoulderLed: LedState,

    val callLightingEnabled: Boolean,
    val pauseFanDuringCalls: Boolean,
    val incomingCallFanLed: LedState,
    val incomingCallLogoLed: LedState,
    val incomingCallShoulderLed: LedState,
    val connectedCallFanLed: LedState,
    val connectedCallLogoLed: LedState,
    val connectedCallShoulderLed: LedState,

    val pump: PumpState,
    val selectedFanCurve: String,
    val autoFanEnabled: Boolean,
    val realtimePreviewEnabled: Boolean,
    val triggers: TriggerPrefsSnapshot
)
