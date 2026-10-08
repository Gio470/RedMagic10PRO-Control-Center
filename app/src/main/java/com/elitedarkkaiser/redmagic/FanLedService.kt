package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class FanLedService : Service() {

    companion object {
        private const val CHANNEL_ID = "fan_led_service_channel"
        private const val NOTIF_ID = 1102
    }

    private val handler = Handler(Looper.getMainLooper())
    private var cycleRunnable: Runnable? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (ChargingLedState.isEnabled(this@FanLedService) &&
                        ChargingLedState.isChargingNow(this@FanLedService)
                    ) {
                        ChargingLedState.setActive(this@FanLedService, true)
                        ChargingLedState.applyChargingProfile(this@FanLedService)
                    } else {
                        turnOffAllManagedLeds()
                    }
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    handler.postDelayed({
                        reapplySavedLedState()
                    }, 1500)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ChargingLedRecovery.repairStaleChargingOwnership(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Fan LED persistence active"))
        registerFanLedReceiver()
        reapplySavedLedState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reapplySavedLedState()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Throwable) {
        }
        stopColorCycle()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Walks the chosen colours under [effect], one write per step, for as long as the rotation is
     * what's selected. Each write is a root shell, so the step is floored in [FanLedCycle].
     *
     * Off the main thread: the write blocks on that shell, and this is the only loop in the app
     * that makes one on a timer.
     */
    private fun startColorCycle(effect: String) {
        val colors = FanLedCycle.colors(this)
        val periodMs = FanLedCycle.speedMs(this).toLong()
        val effectName = if (effect.startsWith("preset:")) "flow" else effect
        var index = 0

        cycleRunnable = object : Runnable {
            override fun run() {
                // Checked every step rather than only at the start: the rotation outlives whatever
                // set it going, so it has to notice the LED being switched off, the selection
                // moving to a single colour, or charging mode taking the LEDs over — none of which
                // come back through here.
                if (!stillCycling()) {
                    stopColorCycle()
                    return
                }
                val color = colors[index % colors.size]
                index++
                Thread { HardwareController.setFanLedEffect(effectName, color) }.start()
                handler.postDelayed(this, periodMs)
            }
        }.also { handler.post(it) }
    }

    private fun stillCycling(): Boolean {
        if (!LedOwnership.canNormalApply(this)) return false
        val prefs = getSharedPreferences("redmagic_hw_controls_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("fan_led_enabled", false) &&
            prefs.getInt("fan_led_color", 5) == FanLedCycle.CYCLE_COLOR
    }

    private fun stopColorCycle() {
        cycleRunnable?.let { handler.removeCallbacks(it) }
        cycleRunnable = null
    }

    private fun registerFanLedReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun reapplySavedLedState() {
        val prefs = getSharedPreferences("redmagic_hw_controls_prefs", Context.MODE_PRIVATE)

        if (!LedOwnership.canNormalApply(this)) {
            android.util.Log.i(
                "RedmagicLedOwnership",
                "FanLedService skipped normal LED apply because owner=${LedOwnership.current(this)}"
            )
            return
        }

        
        val fanEnabled = prefs.getBoolean("fan_led_enabled", false)
        val fanEffect = prefs.getString("fan_led_effect", "steady") ?: "steady"
        val fanColor = prefs.getInt("fan_led_color", 5)

        val logoEnabled = prefs.getBoolean("logo_led_enabled", true)
        val logoEffect = prefs.getString("logo_led_effect", "steady") ?: "steady"
        val logoColor = prefs.getInt("logo_led_color", 1)

        val shoulderEnabled = prefs.getBoolean("shoulder_led_enabled", true)
        val shoulderEffect = prefs.getString("shoulder_led_effect", "breathe") ?: "breathe"
        val shoulderColor = prefs.getInt("shoulder_led_color", 8)

        stopColorCycle()
        if (fanEnabled) {
            when {
                fanColor == FanLedCycle.CYCLE_COLOR -> startColorCycle(fanEffect)
                // No setFanLedEnabled(true) first: there is no enable for this zone, so that call
                // is a plain green write, and the preset that follows has to undo it.
                fanEffect.startsWith("preset:") ->
                    HardwareController.setFanLedStockPreset(fanEffect.removePrefix("preset:"))
                else -> HardwareController.setFanLedEffect(fanEffect, fanColor)
            }
        } else {
            HardwareController.setFanLedEnabled(false)
        }

        if (logoEnabled) {
            HardwareController.setLogoLedEffect(logoEffect, logoColor)
        } else {
            HardwareController.setLogoLedEnabled(false)
        }

        if (shoulderEnabled) {
            HardwareController.setShoulderLedEffect(shoulderEffect, shoulderColor)
        } else {
            HardwareController.setShoulderLedEnabled(false)
        }

        if (fanEnabled || logoEnabled || shoulderEnabled) {
            updateNotification(
                "LED persistence active • Fan: " +
                    (if (fanEnabled) "on" else "off") +
                    " • Logo: " +
                    (if (logoEnabled) "on" else "off") +
                    " • Shoulder: " +
                    (if (shoulderEnabled) "on" else "off")
            )
        } else {
            stopSelf()
        }
    }

    private fun turnOffAllManagedLeds() {
        // Or the rotation keeps writing colours over the off state, with the screen off.
        stopColorCycle()
        HardwareController.setFanLedEnabled(false)
        HardwareController.setLogoLedEnabled(false)
        HardwareController.setShoulderLedEnabled(false)
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("RedMagic Control")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LED Persistence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps custom LED settings synced with screen state"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
