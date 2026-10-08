package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import androidx.core.content.ContextCompat
import com.elitedarkkaiser.redmagic.MainActivity

/** Native mapping uses root foreground detection even when Android unbinds accessibility. */
class TriggerRuntimeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var hint: String? = null
    private var hintAt = 0L
    private var notificationTarget = ""
    private val monitor = TriggerForeground(main) { reconcile() }
    private val tick = object : Runnable {
        override fun run() {
            if (!wanted(this@TriggerRuntimeService)) { stopSelf(); return }
            reconcile()
            main.postDelayed(this, 1_000L)
        }
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                hint = null
                monitor.stop()
                GameTrigger.stop(this@TriggerRuntimeService)
            } else reconcile()
        }
    }
    private val display = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) = Unit
        override fun onDisplayChanged(id: Int) {
            if (id == Display.DEFAULT_DISPLAY) GameTrigger.onConfigurationChanged(this@TriggerRuntimeService)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startFailure = null
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Game trigger mapping", NotificationManager.IMPORTANCE_LOW))
        startForeground(242, notification())
        ContextCompat.registerReceiver(this, screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(DisplayManager::class.java).registerDisplayListener(display, main)
        GameTrigger.recover(this)
    }

    private fun notification(profile: NativeTgkProfile? = null): Notification {
        val open = PendingIntent.getActivity(this, 242, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 242, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Game trigger mapping")
            .setContentText("Applies your saved L/R targets when a game opens")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
        profile?.let {
            val edit = PendingIntent.getForegroundService(this, 243,
                Intent(this, TriggerEditorService::class.java).putExtra("package", it.packageName)
                    .putExtra("orientation", GameTrigger.currentOrientation(this).name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentText("${it.appLabel} · saved shoulder-trigger targets")
                .addAction(Notification.Action.Builder(null, "Edit targets", edit).build())
        }
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            TriggerEditorService.close(this)
            NativeTgkStorage.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!wanted(this)) { stopSelf(); return START_NOT_STICKY }
        main.removeCallbacks(tick)
        main.post(tick)
        return START_STICKY
    }

    private fun reconcile() {
        if (getSystemService(PowerManager::class.java)?.isInteractive != true) {
            monitor.stop()
            return
        }
        monitor.start()
        val root = monitor.current()
        val recentHint = hint?.takeIf { SystemClock.elapsedRealtime() - hintAt in 0L..2_500L }
        val pkg = root ?: recentHint
        val orientation = GameTrigger.currentOrientation(this)
        val profile = pkg?.let { NativeTgkStorage.getProfile(this, it) }?.takeIf {
            NativeTgkStorage.enabled(this) && it.enabled && it.hasCompleteMapping(orientation) && !TriggerEditorService.isEditing()
        }
        val key = "${profile?.packageName}:$orientation"
        if (key != notificationTarget) {
            notificationTarget = key
            getSystemService(NotificationManager::class.java).notify(242, notification(profile))
        }
        if (pkg != null) GameTrigger.onForeground(this, pkg,
            if (root != null) "Root top-resumed activity" else "Accessibility windows")
        else GameTrigger.foregroundUnavailable(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        GameTrigger.onConfigurationChanged(this)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (instance === this) instance = null
        main.removeCallbacksAndMessages(null)
        monitor.stop()
        unregisterReceiver(screen)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(display)
        GameTrigger.stop(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "game_trigger_runtime"
        private const val STOP = "stop_mapping"
        @Volatile private var instance: TriggerRuntimeService? = null
        @Volatile private var startFailure: String? = null
        fun isRunning() = instance != null
        private fun wanted(context: Context) = NativeTgkStorage.enabled(context) || TriggerEditorService.isEditing()
        fun sync(context: Context) {
            if (!wanted(context)) {
                context.stopService(Intent(context, TriggerRuntimeService::class.java))
                return
            }
            if (isRunning()) return
            runCatching { context.startForegroundService(Intent(context, TriggerRuntimeService::class.java)) }
                .onFailure { startFailure = it.message ?: it.javaClass.simpleName }
        }
        fun accessibilityHint(context: Context, pkg: String) {
            sync(context)
            instance?.let { runtime -> runtime.main.post {
                runtime.hint = pkg
                runtime.hintAt = SystemClock.elapsedRealtime()
                runtime.reconcile()
            } }
        }
        fun diagnostics() = "Mapping runtime: " + if (isRunning()) "running (root; accessibility optional)"
            else "stopped" + (startFailure?.let { "; $it" } ?: "")
    }
}

