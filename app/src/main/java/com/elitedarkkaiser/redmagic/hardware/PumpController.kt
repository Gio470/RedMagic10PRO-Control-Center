package com.elitedarkkaiser.redmagic.hardware

import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.state.PumpState

object PumpController {
    const val PROFILE_SLOW = "slow"
    const val PROFILE_MEDIUM = "medium"
    const val PROFILE_QUICK = "quick"
    const val PROFILE_EXPERIMENTAL = "experimental"

    fun enable(enabled: Boolean): Boolean {
        return HardwareController.enablePump(enabled)
    }

    fun setProfile(profile: String): Boolean {
        return HardwareController.setPumpProfile(normalizeProfile(profile))
    }

    fun readEnabledRaw(): String? {
        return HardwareController.readPumpEnabled()?.trim()
    }

    fun readFreqRaw(): String? {
        return HardwareController.readPumpFreq()?.trim()
    }

    fun readSpeedRaw(): String? {
        return HardwareController.readPumpSpeed()?.trim()
    }

    fun snapshot(
        profile: String = PROFILE_QUICK,
        enabled: Boolean = false,
        autoEnabled: Boolean = false,
        experimentalAccepted: Boolean = false
    ): PumpState {
        val normalized = normalizeProfile(profile)
        return PumpState(
            enabled = enabled,
            profile = normalized,
            autoEnabled = autoEnabled,
            experimentalAccepted = experimentalAccepted,
            statusText = statusText(normalized, autoEnabled)
        )
    }

    fun statusText(profile: String, autoEnabled: Boolean): String {
        val label = normalizeProfile(profile).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        return if (autoEnabled) {
            "Pump Mode: AUTO • $label"
        } else {
            "Pump Mode: MANUAL • $label"
        }
    }

    fun speedText(profile: String): String {
        return when (normalizeProfile(profile)) {
            PROFILE_SLOW -> "Speed: Low • Freq: 4"
            PROFILE_MEDIUM -> "Speed: Medium • Freq: 4"
            PROFILE_EXPERIMENTAL -> "Speed: Experimental • Freq: 4"
            else -> "Speed: High • Freq: 4"
        }
    }

    fun normalizeProfile(profile: String): String {
        return when (profile.lowercase()) {
            PROFILE_SLOW -> PROFILE_SLOW
            PROFILE_MEDIUM -> PROFILE_MEDIUM
            PROFILE_EXPERIMENTAL -> PROFILE_EXPERIMENTAL
            else -> PROFILE_QUICK
        }
    }
}
