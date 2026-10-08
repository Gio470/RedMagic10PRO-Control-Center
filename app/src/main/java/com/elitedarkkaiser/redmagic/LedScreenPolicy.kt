package com.elitedarkkaiser.redmagic

import android.content.Context
import android.os.PowerManager

object LedScreenPolicy {
    fun isScreenInteractive(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        } catch (_: Throwable) {
            true
        }
    }

    fun blockNonChargingLedWriteIfScreenOff(context: Context, reason: String): Boolean {
        if (isScreenInteractive(context)) return false

        android.util.Log.i(
            "RedmagicLedPolicy",
            "Blocked non-charging LED write while screen is off: $reason"
        )

        if (!ChargingLedState.isEnabled(context) || !ChargingLedState.isChargingNow(context)) {
            HardwareController.turnOffAllLeds()
        }

        return true
    }
}
