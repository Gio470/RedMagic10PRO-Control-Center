package com.elitedarkkaiser.redmagic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_USER_UNLOCKED) return

        com.elitedarkkaiser.redmagic.gametrigger.TriggerRuntimeService.sync(context)

        HardwareServiceActions.startChargingMode(context)
        if (CallLightingState.isEnabled(context)) {
            HardwareServiceActions.startCallLighting(context)
        }
    }

}

