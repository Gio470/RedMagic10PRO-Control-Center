package com.elitedarkkaiser.redmagic.gametrigger

import android.app.Notification
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Three small windows leave the game touchable while its actual display controls placement. */
class TriggerEditorService : Service() {
    private data class TargetView(val view: View, val params: WindowManager.LayoutParams)
    private var targetPackage = ""
    private var requestedOrientation = NativeTgkOrientation.LANDSCAPE
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelDialog: Dialog? = null
    private var panelWindow: Window? = null
    private var menu: TriggerEditorMenu? = null
    private lateinit var draft: TriggerEditorDraft
    private var controls = TriggerEditorMenu.State()
    private var panelX: Int? = null
    private var panelY = 0
    private var left: TargetView? = null
    private var right: TargetView? = null
    private var shownDisplay: TriggerDisplayState? = null
    private var sawGame = false
    private var startedAt = 0L
    private val main = Handler(Looper.getMainLooper())
    // A window context supplies display geometry, but does not inherit the Activity's theme.
    // MaterialSwitch (the Options tab) enforces Material attributes during construction.
    private val overlayContext by lazy {
        android.view.ContextThemeWrapper(TriggerDisplay.windowContext(this),
            com.elitedarkkaiser.redmagic.R.style.Theme_RedMagicRootControl)
    }
    private val manager by lazy { overlayContext.getSystemService(WindowManager::class.java) }
    private val poll = object : Runnable {
        override fun run() {
            if (instance !== this@TriggerEditorService) return
            if (GameTrigger.currentForeground() == targetPackage) {
                sawGame = true
                val display = TriggerDisplay.read(this@TriggerEditorService)
                if (display.isValid() && getSystemService(PowerManager::class.java).isInteractive && !GameTrigger.ownsTriggers()) {
                    if (shownDisplay != display) { removeOverlay(); showOverlay(display) }
                } else removeOverlay()
            } else if (!sawGame && SystemClock.elapsedRealtime() - startedAt > 30_000L) {
                toast("Couldn't detect the game. Check root access and Trigger service.")
                stopSelf()
                return
            }
            main.postDelayed(this, 250L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        val pkg = intent?.getStringExtra("package").orEmpty()
        val profile = NativeTgkStorage.getProfile(this, pkg)
        if (profile == null || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        main.removeCallbacksAndMessages(null)
        removeOverlay()
        targetPackage = pkg
        requestedOrientation = runCatching { NativeTgkOrientation.valueOf(intent?.getStringExtra("orientation").orEmpty()) }
            .getOrDefault(GameTrigger.currentOrientation(this))
        draft = TriggerEditorDraft(profile, requestedOrientation)
        controls = TriggerEditorMenu.State()
        panelX = null
        panelY = dp(12)
        sawGame = false
        startedAt = SystemClock.elapsedRealtime()
        instance = this
        NativeTgkStorage.recordEditorStatus(this, "Opened $pkg · $requestedOrientation; waiting for the game")
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Game trigger editor", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(241, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("${profile.appLabel} · ${requestedOrientation.name.lowercase()} targets")
            .setContentText("Rotate to ${requestedOrientation.name.lowercase()}, drag L/R, then Save & enable")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Cancel", stop).build()).build())
        TriggerRuntimeService.sync(this)
        GameTrigger.refresh(this)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) { toast("Couldn't open the game"); stopSelf(); return START_NOT_STICKY }
        runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
            .onFailure { toast("Couldn't open the game"); stopSelf(); return START_NOT_STICKY }
        toast("Place ${requestedOrientation.name.lowercase()} targets: turn the game that way, then drag L and R")
        main.post(poll)
        return START_NOT_STICKY
    }

    private fun foreground(pkg: String) {
        if (pkg == targetPackage) sawGame = true
        else if (sawGame) { stopSelf(); return }
        main.removeCallbacks(poll)
        main.post(poll)
    }

    private fun params(width: Int, height: Int) = WindowManager.LayoutParams(width, height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun showOverlay(display: TriggerDisplayState) {
        val ready = display.orientation == requestedOrientation
        shownDisplay = display
        if (ready) draft.attachDisplay(display, dp(18))
        runCatching {
            if (ready) {
                left = target("L", Color.rgb(232, 88, 101), true)
                right = target("R", Color.rgb(77, 153, 246), false)
            }
            val floating = TriggerEditorMenu(overlayContext, draft, display, controls,
                updated = ::menuUpdated,
                orientationChanged = { orientation ->
                    requestedOrientation = orientation
                    draft.setOrientation(orientation)
                    val current = TriggerDisplay.read(this)
                    removeOverlay()
                    showOverlay(current)
                }, save = ::save, cancel = { stopSelf() })
            menu = floating
            panel = floating.view
            val width = dp(364).coerceAtMost(display.width - dp(16))
            val dialog = Dialog(overlayContext).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
            panelDialog = dialog
            val window = dialog.window ?: error("Editor window unavailable")
            panelWindow = window
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.setPadding(0, 0, 0, 0)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            window.setGravity(Gravity.TOP or Gravity.START)
            window.attributes = window.attributes.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                x = panelX ?: (display.width - width) / 2
                y = panelY
            }
            dialog.setContentView(floating.view)
            dialog.show()
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            panelParams = window.attributes
            menuUpdated()
            NativeTgkStorage.recordEditorStatus(this, "Placing $targetPackage · $requestedOrientation on ${display.width}x${display.height}, rotation=${display.rotation}")
        }.onFailure {
            removeOverlay()
            toast("Couldn't show the editor: ${it.message}")
            stopSelf()
        }
    }

    private fun menuUpdated() {
        shownDisplay?.takeIf { it.orientation == draft.orientation }?.let { draft.attachDisplay(it, dp(18)) }
        syncTargetPositions()
        val floating = menu ?: return
        floating.refreshPosition()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        floating.dragHandle.setOnTouchListener { view, event ->
            val position = panelParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = position.x; startY = position.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    position.x = (startX + event.rawX - downX).roundToInt()
                    position.y = (startY + event.rawY - downY).roundToInt()
                    clampPanel()
                    true
                }
                MotionEvent.ACTION_UP -> { view.performClick(); true }
                else -> false
            }
        }
        floating.view.post { if (menu === floating) clampPanel() }
    }

    private fun clampPanel() {
        val view = panel ?: return
        val position = panelParams ?: return
        val display = shownDisplay ?: return
        position.x = position.x.coerceIn(0, (display.width - position.width).coerceAtLeast(0))
        position.y = position.y.coerceIn(0, (display.height - view.height).coerceAtLeast(0))
        panelX = position.x
        panelY = position.y
        runCatching { panelWindow?.attributes = position }
    }

    private fun target(label: String, color: Int, isLeft: Boolean): TargetView {
        val puck = TextView(overlayContext).apply {
            text = label; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            contentDescription = if (isLeft) "Left trigger target" else "Right trigger target"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            setOnClickListener { menu?.select(isLeft) }
        }
        val position = params(dp(60), dp(60))
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        puck.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    menu?.select(isLeft)
                    draft.beginGesture()
                    downX = event.rawX; downY = event.rawY; startX = position.x; startY = position.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!controls.locked) {
                        draft.move(isLeft, (startX + position.width / 2f + event.rawX - downX).roundToInt(),
                            (startY + position.height / 2f + event.rawY - downY).roundToInt(), gestureMove = true)
                        syncTargetPositions()
                        menu?.refreshPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { draft.endGesture(); view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { draft.endGesture(); true }
                else -> false
            }
        }
        manager.addView(puck, position)
        return TargetView(puck, position)
    }

    private fun syncTargetPositions() {
        val mapping = draft.mapping ?: return
        fun move(target: TargetView?, rect: NativeTgkRect?, selected: Boolean) {
            if (target == null || rect == null) return
            target.params.x = (rect.left + rect.right - target.params.width) / 2
            target.params.y = (rect.top + rect.bottom - target.params.height) / 2
            (target.view.background as? GradientDrawable)?.setStroke(dp(if (selected) 4 else 1), Color.WHITE)
            runCatching { manager.updateViewLayout(target.view, target.params) }
        }
        move(left, mapping.left, controls.leftSelected)
        move(right, mapping.right, !controls.leftSelected)
    }

    private fun save(enable: Boolean) {
        val display = TriggerDisplay.read(this)
        if (display != shownDisplay || display.orientation != requestedOrientation) {
            saveError("Display changed. Wait for ${requestedOrientation.name.lowercase()}, then save again.",
                "Save rejected: shown=$shownDisplay; current=$display")
            return
        }
        val l = left?.params
        val r = right?.params
        if (l == null || r == null) { saveError("Both targets must be visible before saving"); return }
        val saved = runCatching {
            draft.endGesture()
            NativeTgkStorage.saveDraft(this, draft, enable)
        }.getOrElse { saveError("Couldn't save: ${it.message}"); return }
        if (saved) {
            val active = NativeTgkStorage.enabled(this) && NativeTgkStorage.getProfile(this, targetPackage)?.enabled == true
            NativeTgkStorage.recordEditorStatus(this, "Saved $targetPackage · $requestedOrientation; ${display.width}x${display.height}; enabled=$active")
            TriggerRuntimeService.sync(this)
            toast("${requestedOrientation.name.lowercase().replaceFirstChar { it.uppercase() }} targets saved" +
                if (active) "; applying to the game" else "; mapping is off")
            stopSelf()
        } else saveError("Couldn't save the targets. Please try again.")
    }

    private fun saveError(message: String, details: String = message) {
        menu?.showError(message)
        NativeTgkStorage.recordEditorStatus(this, "$targetPackage · $requestedOrientation: $details")
        toast(message)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        main.removeCallbacks(poll)
        main.post(poll)
    }

    private fun removeOverlay() {
        runCatching { panelDialog?.dismiss() }
        panelDialog = null; panelWindow = null
        listOfNotNull(left?.view, right?.view).forEach { view -> runCatching { manager.removeViewImmediate(view) } }
        panel = null; panelParams = null; menu = null; left = null; right = null; shownDisplay = null
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        removeOverlay()
        if (instance === this) instance = null
        TriggerRuntimeService.sync(this)
        GameTrigger.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * overlayContext.resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val CHANNEL = "game_trigger_editor"
        private const val STOP = "stop_editor"
        @Volatile private var instance: TriggerEditorService? = null
        fun isEditing() = instance != null
        fun isEditing(pkg: String) = instance?.targetPackage == pkg
        fun foregroundChanged(pkg: String) { instance?.foreground(pkg) }
        fun close(context: Context) { context.stopService(Intent(context, TriggerEditorService::class.java)) }
        fun open(context: Context, profile: NativeTgkProfile, orientation: NativeTgkOrientation? = null) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Allow Draw over apps before placing targets", Toast.LENGTH_LONG).show()
                return
            }
            context.startForegroundService(Intent(context, TriggerEditorService::class.java)
                .putExtra("package", profile.packageName)
                .putExtra("orientation", orientation?.name))
        }
    }
}


