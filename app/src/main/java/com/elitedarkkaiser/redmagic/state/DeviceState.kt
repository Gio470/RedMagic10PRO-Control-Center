package com.elitedarkkaiser.redmagic.state

data class DeviceState(
    val supported: Boolean = false,
    val rooted: Boolean = false,
    val model: String = "Unknown",
    val productModel: String = "Unknown",
    val vendorModel: String = "Unknown",
    val marketName: String = "Unknown",
    val fingerprint: String = "Unknown",
    val cpuModel: String = "Snapdragon Elite gen 5",
    val ramText: String = "Unknown"
)
