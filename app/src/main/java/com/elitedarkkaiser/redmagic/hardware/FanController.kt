package com.elitedarkkaiser.redmagic.hardware

import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.state.FanState

object FanController {
    const val CURVE_QUIET = "quiet"
    const val CURVE_BALANCED = "balanced"
    const val CURVE_TURBO = "turbo"

    fun enable(enabled: Boolean): Boolean {
        return HardwareController.enableFan(enabled)
    }

    fun isEnabled(): Boolean {
        return HardwareController.isFanEnabled()
    }

    fun setLevel(level: Int): Boolean {
        return HardwareController.setFanLevel(level.coerceIn(0, 5))
    }

    fun setPwm(value: Int): Boolean {
        return HardwareController.setFanPwm(value.coerceAtLeast(0))
    }

    fun readRpm(): Int? {
        return HardwareController.readFanRpm()
    }

    fun readTemperatureC(): Float? {
        return HardwareController.readTemperatureC()
    }

    fun readTemperatureF(): Float? {
        return HardwareController.readTemperatureF()
    }

    fun chooseLevelForTempF(tempF: Float, curve: String): Int {
        return HardwareController.chooseFanLevelForTempF(tempF, normalizeCurve(curve))
    }

    fun chooseAutoLevelForTempF(tempF: Float): Int {
        return HardwareController.chooseAutoFanLevelForTempF(tempF)
    }

    fun applyCurve(curve: String): Int? {
        return HardwareController.applyFanCurve(normalizeCurve(curve))
    }

    fun applyAutoCurve(): Int? {
        return HardwareController.applyAutoFanCurve()
    }

    fun snapshot(
        curveMode: String = CURVE_BALANCED,
        autoEnabled: Boolean = false,
        realtimePreviewEnabled: Boolean = true
    ): FanState {
        val tempF = readTemperatureF()
        val tempText = if (tempF == null) {
            "Unavailable"
        } else {
            val tempC = (tempF - 32f) * 5f / 9f
            "%.1f°C / %.1f°F".format(tempC, tempF)
        }

        return FanState(
            enabled = isEnabled(),
            level = readLevelEstimate(),
            curveMode = normalizeCurve(curveMode),
            autoEnabled = autoEnabled,
            realtimePreviewEnabled = realtimePreviewEnabled,
            temperatureText = tempText
        )
    }

    fun normalizeCurve(curve: String): String {
        return when (curve.lowercase()) {
            CURVE_QUIET -> CURVE_QUIET
            CURVE_TURBO -> CURVE_TURBO
            else -> CURVE_BALANCED
        }
    }

    private fun readLevelEstimate(): Int {
        val rpm = readRpm() ?: return 0
        return when {
            rpm <= 0 -> 0
            rpm < 3500 -> 1
            rpm < 5000 -> 2
            rpm < 6500 -> 3
            rpm < 8000 -> 4
            else -> 5
        }
    }
}
