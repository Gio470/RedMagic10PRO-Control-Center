package com.elitedarkkaiser.redmagic.state

data class LedState(
    val enabled: Boolean = false,
    val effect: String = "steady",
    val color: Int = 5
)

data class LightingState(
    val fan: LedState = LedState(enabled = false),
    val logo: LedState = LedState(enabled = true),
    val shoulder: LedState = LedState(enabled = true)
)
