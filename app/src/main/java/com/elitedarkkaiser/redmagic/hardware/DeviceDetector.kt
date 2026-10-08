package com.elitedarkkaiser.redmagic.hardware

import android.os.Build
import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.state.DeviceState

object DeviceDetector {
    fun readState(): DeviceState {
        val buildModel = Build.MODEL ?: "Unknown"
        val productModel = prop("ro.product.model", "Unknown")
        val vendorModel = prop("ro.product.vendor.model", "Unknown")
        val marketName = prop("ro.product.marketname", "Unknown")

        return DeviceState(
            supported = true,
            rooted = RootProvider.hasRoot(),
            model = buildModel,
            productModel = productModel,
            vendorModel = vendorModel,
            marketName = marketName,
            fingerprint = Build.FINGERPRINT ?: "Unknown",
            cpuModel = HardwareController.readCpuModel(),
            ramText = HardwareController.readRamInfo()
        )
    }

    private fun prop(name: String, fallback: String): String {
        return RootProvider.output("getprop $name")?.trim().orEmpty().ifBlank { fallback }
    }
}
