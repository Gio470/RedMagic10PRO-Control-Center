package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class ChargingModeService : Service() {

    private var lastEnabled: Boolean? = null
    private var lastCharging: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            evaluateChargingState(force = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ChargingLedRecovery.repairStaleChargingOwnership(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        registerReceiver(receiver, filter)
        evaluateChargingState(force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        evaluateChargingState(force = true)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun evaluateChargingState(force: Boolean) {
        val enabled = ChargingLedState.isEnabled(this)
        val charging = ChargingLedState.isChargingNow(this)

        if (!force && lastEnabled == enabled && lastCharging == charging) {
            return
        }

        lastEnabled = enabled
        lastCharging = charging

        if (enabled && charging) {
            if (!ChargingLedState.isActive(this) || force) {
                ChargingLedState.setActive(this, true)
                ChargingLedState.applyChargingProfile(this)
            }
        } else {
            val wasActive = ChargingLedState.isActive(this)
            ChargingLedState.setActive(this, false)

            if (wasActive) {
                HardwareController.turnOffAllLeds()
                GameModeActions.startServiceSilentlyIfPermitted(this)
                HardwareServiceActions.startFanLed(this)
                HardwareServiceActions.enqueueFanLedRestore(this, delaySeconds = 1)
            }
        }
    }
}
