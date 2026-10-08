package com.elitedarkkaiser.redmagic.state

data class FanState(
    val enabled: Boolean = false,
    val level: Int = 0,
    val curveMode: String = "balanced",
    val autoEnabled: Boolean = false,
    val realtimePreviewEnabled: Boolean = true,
    val temperatureText: String = "Unavailable"
)
