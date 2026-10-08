package com.elitedarkkaiser.redmagic.state

data class AppState(
    val device: DeviceState = DeviceState(),
    val fan: FanState = FanState(),
    val pump: PumpState = PumpState(),
    val lighting: LightingState = LightingState()
)
