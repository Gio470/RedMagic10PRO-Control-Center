package com.elitedarkkaiser.redmagic

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.elitedarkkaiser.redmagic.state.LedState

@Suppress("DEPRECATION")
class CallLightingService : Service() {

    private var telephonyManager: TelephonyManager? = null
    private var lastState: Int = TelephonyManager.CALL_STATE_IDLE

    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallState(state)
        }
    }

    /** False when READ_PHONE_STATE was missing, so onDestroy knows there is nothing to unregister. */
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // Guarded, and never allowed to throw. listen() needs the runtime READ_PHONE_STATE grant,
        // and without it, it raises SecurityException from onCreate -- which Android turns into
        // "Unable to create service", taking the whole app down. A service that cannot watch the
        // phone should do nothing, not crash; CallLightingPermission is what gets the grant.
        if (CallLightingPermission.granted(this)) {
            startListening()
            return
        }

        // Started at boot, or restored by another service, with nobody here to answer a prompt.
        // Try the root grant and pick listening up if it takes; otherwise this service simply
        // watches nothing until the user switches call lighting on and is asked properly.
        Thread {
            if (CallLightingPermission.grantWithRoot(this)) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { startListening() }
            } else {
                android.util.Log.w("RedmagicCallLighting", "no READ_PHONE_STATE; not watching calls")
            }
        }.start()
    }

    private fun startListening() {
        if (listening) return
        listening = runCatching {
            telephonyManager?.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            true
        }.getOrElse {
            android.util.Log.w("RedmagicCallLighting", "could not listen for call state: $it")
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleCallState(lastState)
        return START_STICKY
    }

    override fun onDestroy() {
        if (listening) {
            runCatching { telephonyManager?.listen(phoneListener, PhoneStateListener.LISTEN_NONE) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleCallState(state: Int) {
        lastState = state

        if (!CallLightingState.isEnabled(this)) {
            CallLightingState.setActive(this, false)
            return
        }

        if (!LedOwnership.canCallApply(this)) {
            CallLightingState.setActive(this, false)
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                beginCallOwnership()
                applyIncomingProfile()
                enforceFanPauseIfNeeded()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                beginCallOwnership()
                applyConnectedProfile()
                enforceFanPauseIfNeeded()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (CallLightingState.isActive(this)) {
                    CallLightingState.setActive(this, false)
                    restorePausedFanIfNeeded()
                    restorePreviousLedOwner()
                }
            }
        }
    }


    private fun beginCallOwnership() {
        val wasAlreadyActive = CallLightingState.isActive(this)

        if (!wasAlreadyActive && CallLightingState.shouldPauseFanDuringCalls(this)) {
            CallLightingState.savePreCallFanState(
                context = this,
                enabled = HardwareController.isFanEnabled(),
                level = HardwareController.readFanLevel() ?: 0
            )
            HardwareServiceActions.stopAutoFan(this)
            HardwareController.setFanLevel(0)
            HardwareController.enableFan(false)
        }

        CallLightingState.setActive(this, true)
    }

    private fun restorePausedFanIfNeeded() {
        if (CallLightingState.shouldPauseFanDuringCalls(this)) {
            CallLightingState.restorePreCallFanState(this)
            if (isAutoFanEnabledStorage(this)) {
                HardwareServiceActions.startAutoFan(this)
            }
        }
    }

    private fun enforceFanPauseIfNeeded() {
        if (CallLightingState.shouldPauseFanDuringCalls(this)) {
            HardwareServiceActions.stopAutoFan(this)
            HardwareController.setFanLevel(0)
            HardwareController.enableFan(false)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (CallLightingState.isActive(this) && CallLightingState.shouldPauseFanDuringCalls(this)) {
                    HardwareServiceActions.stopAutoFan(this)
                    HardwareController.setFanLevel(0)
                    HardwareController.enableFan(false)
                }
            }, 750L)
        }
    }

    private fun applyIncomingProfile() {
        applyProfile(
            fan = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_FAN_ENABLED_KEY,
                CallLightingState.INCOMING_FAN_EFFECT_KEY,
                CallLightingState.INCOMING_FAN_COLOR_KEY,
                false,
                "flashing",
                5
            ),
            logo = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_LOGO_ENABLED_KEY,
                CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                CallLightingState.INCOMING_LOGO_COLOR_KEY,
                false,
                "flashing",
                1
            ),
            shoulder = CallLightingState.readLed(
                this,
                CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
                CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                CallLightingState.INCOMING_SHOULDER_COLOR_KEY,
                false,
                "flashing",
                8
            )
        )
    }

    private fun applyConnectedProfile() {
        applyProfile(
            fan = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_FAN_ENABLED_KEY,
                CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                CallLightingState.CONNECTED_FAN_COLOR_KEY,
                false,
                "steady",
                5
            ),
            logo = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
                CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                CallLightingState.CONNECTED_LOGO_COLOR_KEY,
                false,
                "steady",
                1
            ),
            shoulder = CallLightingState.readLed(
                this,
                CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
                CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                CallLightingState.CONNECTED_SHOULDER_COLOR_KEY,
                false,
                "steady",
                8
            )
        )
    }

    private fun applyProfile(fan: LedState, logo: LedState, shoulder: LedState) {
        if (fan.enabled) {
            if (fan.effect.startsWith("preset:")) {
                HardwareController.setFanLedStockPreset(fan.effect.removePrefix("preset:"))
            } else {
                HardwareController.setFanLedEffect(fan.effect, fan.color)
            }
        } else {
            HardwareController.setFanLedEnabled(false)
        }

        if (logo.enabled) {
            HardwareController.setLogoLedEffect(logo.effect, logo.color)
        } else {
            HardwareController.setLogoLedEnabled(false)
        }

        if (shoulder.enabled) {
            HardwareController.setShoulderLedEffect(shoulder.effect, shoulder.color)
        } else {
            HardwareController.setShoulderLedEnabled(false)
        }
    }

    private fun restorePreviousLedOwner() {
        if (ChargingLedState.isEnabled(this) && ChargingLedState.isChargingNow(this)) {
            ChargingLedState.setActive(this, true)
            ChargingLedState.applyChargingProfile(this)
            return
        }

        GameModeActions.startServiceSilentlyIfPermitted(this)
        HardwareServiceActions.startFanLed(this)
    }
}
