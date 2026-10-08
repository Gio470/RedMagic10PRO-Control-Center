package com.elitedarkkaiser.redmagic.state

data class PumpState(
    val enabled: Boolean = false,
    val profile: String = "quick",
    val autoEnabled: Boolean = false,
    val experimentalAccepted: Boolean = false,
    val statusText: String = "Pump status unavailable"
)
