package com.elitedarkkaiser.redmagic

/**
 * Whether the running device is specifically a RedMagic 10 Pro — narrower than
 * [DeviceCapabilityReport.isKnownRedmagicDevice], which only checks for the shared "NX" model
 * prefix across the whole ZTE/RedMagic lineup. This app's sysfs paths (HardwareController) were
 * only confirmed against the 10 Pro, so "some other NX-family phone" is not the same guarantee.
 */
object DeviceCompatibility {
    /** Internal codename for the RedMagic 10 Pro (see git history: "NX789J Rebrand"). */
    const val REDMAGIC_10_PRO_CODENAME = "NX789J"
    private const val REDMAGIC_10_PRO_MARKET_NAME = "RedMagic 10 Pro"

    fun isRedMagic10Pro(model: String, marketName: String): Boolean {
        return model.trim().equals(REDMAGIC_10_PRO_CODENAME, ignoreCase = true) ||
            marketName.trim().contains(REDMAGIC_10_PRO_MARKET_NAME, ignoreCase = true)
    }
}
