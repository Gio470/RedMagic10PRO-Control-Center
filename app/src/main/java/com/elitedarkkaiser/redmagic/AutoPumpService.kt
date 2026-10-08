package com.elitedarkkaiser.redmagic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AutoPumpService : Service() {

    companion object {
        private const val HOT_POLL_MS = 15000L
        private const val COOL_POLL_MS = 60000L
        private const val HOT_TEMP_THRESHOLD_F = 95f
        private const val CHANNEL_ID = "auto_pump_channel"
        private const val NOTIF_ID = 2202
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastProfile: String? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            val tempF = applyPumpRule()
            val nextDelay = if ((tempF ?: 0f) >= HOT_TEMP_THRESHOLD_F) HOT_POLL_MS else COOL_POLL_MS
            handler.postDelayed(this, nextDelay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Auto pump active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyPumpRule(): Float? {
        val tempF = DashboardSnapshot.readCpuTempF().toFloatOrNull() ?: return null

        if (!HardwareScreenPolicy.isScreenInteractive(this@AutoPumpService)) {
            if (HardwareScreenPolicy.coolingShouldStopWhileScreenOff(tempF)) {
                HardwareController.enablePump(false)
                lastProfile = "off"
                return tempF
            }
        }

        val profile = when {
            tempF >= 105f -> "quick"
            tempF >= 95f -> "medium"
            else -> "slow"
        }

        if (profile != lastProfile) {
            if (HardwareScreenPolicy.blockCoolingWhileScreenOffUnlessHot(this@AutoPumpService, "auto-pump-screen-off")) return tempF
            HardwareController.setPumpProfile(profile)
            lastProfile = profile

            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(
                NOTIF_ID,
                buildNotification("Pump: ${profile.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} • ${TempFormat.formatDisplayTempFromF(tempF, isUseFahrenheitStorage(this))}")
            )
        }

        return tempF
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Pump Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Adjusts pump profile based on temperature"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
