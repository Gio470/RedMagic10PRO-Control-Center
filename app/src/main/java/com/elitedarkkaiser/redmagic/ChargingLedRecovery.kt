package com.elitedarkkaiser.redmagic

import android.content.Context

object ChargingLedRecovery {
    fun repairStaleChargingOwnership(context: Context) {
        val active = ChargingLedState.isActive(context)
        val charging = ChargingLedState.isChargingNow(context)

        if (active && !charging) {
            ChargingLedState.setActive(context, false)
            HardwareServiceActions.startFanLed(context)
            GameModeActions.startServiceSilentlyIfPermitted(context)

            android.util.Log.i(
                "RedmagicChargingLed",
                "cleared stale charging LED ownership because device is no longer charging"
            )
        }
    }
}
