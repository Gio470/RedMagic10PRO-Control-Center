package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Orchestrates the REDMAGIC native Touch-Game-Key (TGK) trigger mapping for the app in the
 * foreground, ported from austineyoung2000/Redmagic-11-Toolbox.
 *
 * The firmware does the actual shoulder-to-touch injection ([NativeTgkBridge]); this just programs
 * it when a mapped game is foreground and clears it when that game leaves, and shows the saved L/R
 * targets over the game. One owner at a time, tracked in [RuntimeState] so a flood of window events
 * does not re-program the vendor service on every tick.
 *
 * Everything here that touches the bridge sleeps for seconds (the vendor's settle delay), so it is
 * driven off the main thread -- see [onForeground]/[onLeftGame], which post to a single worker.
 */
object GameTrigger {
    private const val TAG = "RedmagicGameTrigger"
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "game-trigger").apply { isDaemon = true } }
    @Volatile private var foreground: String? = null
    @Volatile private var owned = false
    @Volatile private var context: Context? = null
    @Volatile var lastMessage = "No game mapping active"
        private set
    @Volatile private var lastApply = "No mapping has been attempted"
    @Volatile private var foregroundSource = "Not detected"

    private data class Target(val profile: NativeTgkProfile, val orientation: NativeTgkOrientation,
        val width: Int, val height: Int, val rotation: Int)
    private val queue = LatestMappingQueue<Target>(worker, ::apply, ::disableOwned)

    fun available(context: Context): Boolean = NativeTgkBridge.readState(context).success
    fun ownsTriggers(): Boolean = queue.requested() || owned
    fun currentForeground(): String? = foreground

    fun currentOrientation(context: Context): NativeTgkOrientation {
        return TriggerDisplay.read(context).orientation
    }

    fun displaySize(context: Context): Size {
        val display = TriggerDisplay.read(context)
        return Size(display.width, display.height)
    }

    fun onForeground(context: Context, packageName: String, source: String = "Accessibility") {
        this.context = context.applicationContext
        foreground = packageName
        foregroundSource = source
        TriggerEditorService.foregroundChanged(packageName)
        request(context, packageName)
    }

    private fun request(context: Context, packageName: String?, force: Boolean = false) {
        val display = TriggerDisplay.read(context)
        val orientation = display.orientation
        val profile = packageName?.let { NativeTgkStorage.getProfile(context, it) }
        val target = profile?.takeIf { display.isValid() && NativeTgkStorage.enabled(context) && it.enabled &&
            it.hasCompleteMapping(orientation) && !TriggerEditorService.isEditing(it.packageName) }
            ?.let { Target(it, orientation, display.width, display.height, display.rotation) }
        if (target == null) {
            SavedTargetOverlay.hide()
            lastMessage = when {
                TriggerEditorService.isEditing(packageName.orEmpty()) -> "Placing ${orientation.name.lowercase()} targets"
                !NativeTgkStorage.enabled(context) -> "Game trigger mapping is switched off"
                packageName == null -> "Waiting for a foreground game"
                profile == null -> "No saved mapping for $packageName"
                !profile.enabled -> "Mapping for ${profile.appLabel} is switched off"
                !display.isValid() -> "Waiting for display dimensions"
                else -> "No ${orientation.name.lowercase()} targets saved for ${profile.appLabel}"
            }
        }
        queue.submit(target, force)
    }

    fun diagnostics(context: Context): String {
        val display = TriggerDisplay.read(context)
        val profiles = NativeTgkStorage.readProfiles(context).joinToString("\n") {
            "${it.packageName}: enabled=${it.enabled}; layout=${it.activeLayout()?.name ?: "Default"}; " +
                "portrait=${it.hasCompleteMapping(NativeTgkOrientation.PORTRAIT)}; " +
                "landscape=${it.hasCompleteMapping(NativeTgkOrientation.LANDSCAPE)}"
        }
        return "Mapping enabled: ${NativeTgkStorage.enabled(context)}\n" +
            TriggerRuntimeService.diagnostics() + "\nEditor: ${NativeTgkStorage.editorStatus(context)}\n" +
            "Foreground: $foreground ($foregroundSource)\n" +
            "Display: ${display.width}x${display.height}, ${display.orientation}, rotation=${display.rotation}\n" +
            "Profiles:\n${profiles.ifBlank { "None" }}\nStatus: $lastMessage\nLast apply: $lastApply"
    }

    fun refresh(context: Context) {
        this.context = context.applicationContext
        TriggerRuntimeService.sync(context)
        request(context, foreground, force = true)
    }
    fun recover(context: Context) {
        this.context = context.applicationContext
        owned = context.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).getBoolean("runtime_owned", false)
        foregroundUnavailable(context)
        queue.submit(null, force = true)
    }
    fun foregroundUnavailable(context: Context) {
        this.context = context.applicationContext
        foreground = null
        foregroundSource = "Waiting for a fresh foreground sample"
        request(context, null)
    }
    fun onConfigurationChanged(context: Context) = request(context, foreground)
    fun stop(context: Context) {
        this.context = context.applicationContext
        foreground = null
        TriggerEditorService.close(context)
        SavedTargetOverlay.hide()
        queue.submit(null, force = true)
    }

    private fun apply(target: Target, current: () -> Boolean) {
        val app = context ?: return
        runCatching {
            if (!current()) return
            owned = true
            app.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().putBoolean("runtime_owned", true).commit()
            lastMessage = "Applying ${target.orientation.name.lowercase()} targets for ${target.profile.appLabel}…"
            check(com.elitedarkkaiser.redmagic.HardwareController.enableTriggers()) { "Couldn't enable the shoulder triggers" }
            val profile = target.profile
            val mapping = profile.mappingFor(target.orientation) ?: return
            val result = NativeTgkBridge.applyMapping(app, mapping, target.width, target.height,
                profile.hapticsEnabled, profile.effectiveLeftBehavior(), profile.effectiveRightBehavior(),
                profile.effectiveLeftRapidFireCount(), profile.effectiveRightRapidFireCount(), current)
            lastApply = "${profile.packageName} ${target.orientation}: ${result.message} (${result.backend})"
            lastMessage = if (result.success) "Active: ${profile.appLabel} · ${target.orientation.name.lowercase()}" else result.message
            if (result.success && current()) {
                SavedTargetOverlay.show(app, profile, mapping, target.width, target.height)
                Log.i(TAG, "Applied ${target.orientation} mapping for ${profile.packageName} via ${result.backend}")
            } else {
                disableOwned()
                if (current()) Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(app, "Game trigger mapping couldn't be applied on this firmware",
                        android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.onFailure {
            lastMessage = it.message ?: "Trigger mapping failed"
            lastApply = "${target.profile.packageName} ${target.orientation}: $lastMessage"
            Log.e(TAG, "Mapping failed", it)
            disableOwned()
        }
    }

    private fun disableOwned() {
        SavedTargetOverlay.hide()
        if (!owned) return
        val app = context ?: return
        runCatching {
            val result = NativeTgkBridge.disable(app)
            if (result.success) {
                owned = false
                app.getSharedPreferences("native_tgk_profiles", Context.MODE_PRIVATE).edit().putBoolean("runtime_owned", false).commit()
            } else Log.e(TAG, result.message)
            if (!com.elitedarkkaiser.redmagic.readTriggerPrefsSnapshot(app).triggerEnabled) {
                com.elitedarkkaiser.redmagic.HardwareController.disableTriggers()
            }
        }.onFailure { Log.e(TAG, "Mapping cleanup failed", it) }
    }
}

/**
 * The saved L/R targets, drawn as faint non-interactive markers over the foreground game so the
 * player can see where each trigger lands. Ported from the toolbox's gameplay overlay (minus its
 * Game Space button). The bright held-press indication comes from the firmware's own center effect.
 */
object SavedTargetOverlay {
    private const val TAG = "RedmagicGameTrigger"
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0L)

    @Volatile private var manager: WindowManager? = null
    @Volatile private var root: View? = null

    fun show(
        context: Context,
        profile: NativeTgkProfile,
        mapping: NativeTgkOrientationMapping,
        displayWidth: Int,
        displayHeight: Int
    ) {
        val appContext = TriggerDisplay.windowContext(context)
        val gen = generation.incrementAndGet()
        main.post {
            if (generation.get() != gen) return@post
            runCatching {
                hideOnMain()
                if (!mapping.isComplete() ||
                    !Settings.canDrawOverlays(appContext)
                ) return@runCatching
                val wm = appContext.getSystemService(WindowManager::class.java)
                    ?: return@runCatching
                // A touchable Edit window can receive native mapped touches aimed beneath it.
                // Editing is available in the runtime notification, leaving gameplay untouched.
                if (!profile.showSavedTargets) return@runCatching

                val container = FrameLayout(appContext).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = false
                    isFocusable = false
                }
                if (profile.showSavedTargets) {
                addTarget(appContext, container, "L", Color.rgb(215, 45, 55),
                    mapping.left!!, displayWidth, displayHeight, profile.savedTargetOpacityPercent)
                addTarget(appContext, container, "R", Color.rgb(30, 120, 230),
                    mapping.right!!, displayWidth, displayHeight, profile.savedTargetOpacityPercent)
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    alpha = 0.3f // Below Android's overlay obscuring threshold; game touches pass through.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                if (generation.get() != gen) return@runCatching
                wm.addView(container, params)
                manager = wm
                root = container
                }.onFailure {
                hideOnMain()
                Log.e(TAG, "Saved-target overlay failed", it)
            }
        }
    }

    fun hide() {
        val gen = generation.incrementAndGet()
        main.post { if (generation.get() == gen) hideOnMain() }
    }

    private fun hideOnMain() {
        val r = root
        val wm = manager
        root = null
        manager = null
        if (r != null && wm != null) runCatching { wm.removeViewImmediate(r) }
    }

    private fun addTarget(
        context: Context,
        root: FrameLayout,
        label: String,
        color: Int,
        rect: NativeTgkRect,
        displayWidth: Int,
        displayHeight: Int,
        opacityPercent: Int
    ) {
        val scaled = rect.scaledTo(displayWidth, displayHeight)
        val size = dp(context, 58)
        val centerX = (scaled[0] + scaled[2]) / 2
        val centerY = (scaled[1] + scaled[3]) / 2
        val marker = TextView(context).apply {
            text = label
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            alpha = opacityPercent.coerceIn(5, 30) / 100f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(context, 1), Color.WHITE)
            }
            isClickable = false
            isFocusable = false
        }
        root.addView(marker, FrameLayout.LayoutParams(size, size).apply {
            leftMargin = (centerX - size / 2).coerceIn(0, (displayWidth - size).coerceAtLeast(0))
            topMargin = (centerY - size / 2).coerceIn(0, (displayHeight - size).coerceAtLeast(0))
        })
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}

