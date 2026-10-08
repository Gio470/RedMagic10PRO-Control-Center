package com.elitedarkkaiser.redmagic

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import android.animation.ArgbEvaluator
import android.widget.Toast
import com.google.android.material.color.MaterialColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBarScaffold
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarColors
import com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBarTab
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var useFahrenheit = true
    private var fanPowerEnabled = false
    private var statusRefreshIntervalMs = RefreshIntervals.DEFAULT_STATUS_MS

    private lateinit var curveStatusText: TextView
    private lateinit var fanSeek: com.elitedarkkaiser.redmagic.ui.ComposeSlider
    private lateinit var autoCurveCheck: android.widget.CompoundButton
    private lateinit var fanPowerSwitch: android.widget.CompoundButton
    // Move the two Cooling switches without re-running what a tap on them does -- see
    // CoolingTabUi.Refs.setFanPowerChecked.
    private var setFanPowerChecked: (Boolean) -> Unit = {}
    private var setAutoCurveChecked: (Boolean) -> Unit = {}
    private var syncFanLevel: (Int) -> Unit = {}
    private var refreshCurveSelection: (String) -> Unit = {}
    private var setCurveControlsEnabled: (Boolean) -> Unit = {}
    private var applyFanEnabledState: (Boolean) -> Unit = {}

    // Live CPU/RAM/GPU readouts on Home. Nullable rather than lateinit: the Home tab is rebuilt
    // whenever the theme changes, so these are re-pointed, and refreshStatus can land in between.
    private var homeStatsRefs: com.elitedarkkaiser.redmagic.ui.HomeTabUi.Refs? = null

    private var lastDisplayedRpm: Int = -1
    private var lastDisplayedTempF: Float? = null

    private var hardwareTabBuilt = false
    private var softwareTabBuilt = false
    private var settingsTabBuilt = false

    // Hardware tabs are gated behind root + a confirmed RedMagic 10 Pro: refreshStatus() keeps
    // these in sync with a live check, and any change while looking at a non-Home tab re-applies
    // the gate immediately.
    private var rootAvailable = false
    private var rootCheckedThisLaunch = false
    private var proAvailable = false
    private var currentTab = "home"

    private val settingsBackCallback =
        object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                switchTab(bottomBarTabs[bottomBarSelectedIndex.intValue])
            }
        }

    private var headerRef: TextView? = null
    private lateinit var homeTab: LinearLayout
    private lateinit var hardwareTab: LinearLayout
    private lateinit var softwareTab: LinearLayout
    private lateinit var settingsTab: LinearLayout
    private var magicKeyStatusLabelRef: TextView? = null
    private var dialogRefreshPump: (() -> Unit)? = null
    private var dialogRefreshShoulderLed: (() -> Unit)? = null
    private var dialogRefreshLogoLed: (() -> Unit)? = null
    private var dialogRefreshFanLed: (() -> Unit)? = null
    private var gameModeAppsTextRef: TextView? = null

    private var smartPumpStatusView: TextView? = null
    private var smartPumpSpeedView: TextView? = null

    private var selectedCurve = "balanced"
    private var autoFanCurveEnabled = false
    private var realTimePreviewEnabled = false

    private var fanLedEnabled = false
    private var fanLedEffect = "steady"
    private var fanLedColor = 1

    private var logoLedEnabled = false
    private var logoLedEffect = "steady"
    private var logoLedColor = 1

    private var shoulderLedEnabled = false
    private var shoulderLedEffect = "breathe"
    private var shoulderLedColor = 8

    private var pumpEnabled = false
    private var pumpProfile = "quick"
    private var autoPumpEnabled = false

    // Material You: sampled from this Activity's theme, which RedMagicApplication has already
    // recolored with wallpaper-derived tones on Android 12+ (falls back to the seed palette in
    // Theme.RedMagicRootControl otherwise). Computed fresh on every access (rather than cached
    // with `by lazy`, as these used to be) so a Pure Black toggle's refreshTabColors() picks up
    // the new surface colours immediately on every one of the ~100 call sites that read these,
    // instead of every one of them keeping whatever value happened to be resolved the first time
    // any of them was ever read.
    // All through m3, which is the only thing that knows about the generated palette. These used
    // to read the Activity theme directly, which worked while Material was recolouring that theme
    // -- it no longer is (see Palette), so a direct read returns the colours baked into the XML
    // and nothing else. The bottom bar takes its accent and text colour from here, which is why
    // its icons stopped following the palette at all.
    private val bgColor: Int get() = m3.surface
    private val panelColor: Int get() = m3.surfaceContainerLow
    private val panelPressed: Int get() = m3.surfaceContainerHigh
    private val borderColor: Int get() = m3.outlineVariant

    private val accent: Int get() = m3.primary
    private val chipOn: Int get() = m3.surfaceContainerHighest
    private val chipActive: Int get() = m3.secondaryContainer
    private val danger: Int get() = m3.errorContainer
    private val textPrimary: Int get() = m3.onSurface
    private val textSecondary: Int get() = m3.onSurfaceVariant
    private val typeface: Typeface? = Typeface.SANS_SERIF

    /** Material 3 Expressive tokens + component builders; see ui/M3.kt. */
    private val m3 by lazy { M3(this) }
    // Not `by lazy`: the palette can change while the Activity lives, and a cached first read
    // would pin this to whatever colour happened to be in force then.
    private val highlightBorder: Int get() = m3.outline
    private var pendingPhoneStateResult: ((Boolean) -> Unit)? = null
    private val phoneStatePermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            pendingPhoneStateResult?.invoke(granted)
            pendingPhoneStateResult = null
        }

    private val statusRefreshHandler = Handler(Looper.getMainLooper())

    /**
     * Which run of the status loop is current. Bumped by [startStatusRefreshLoop] so a pass still
     * in flight when the interval changes doesn't schedule a second loop alongside the new one.
     */
    private var statusLoopGeneration = 0

    /**
     * Reposts itself only once the pass it started has finished, rather than on a fixed clock.
     *
     * A pass forks `su` at least twice -- once for the root check, once for the fan read -- and on
     * a fixed schedule it was reposted regardless of whether the previous one had come back. At the
     * fifteen-second default that is free. At the half-second floor the tab allows, passes overlap
     * three and four deep, and whichever finishes last wins: an older reading applied after a newer
     * one puts the fan switch back where it was before the user touched it.
     */
    private val statusRefreshRunnable = Runnable { refreshStatus(scheduleNext = true) }

    // Processor and memory poll on their own periods: they are plain file reads, so they can run
    // far faster than the root-shell status poll above, and the user sets each one separately.
    private val cpuRefreshRunnable = object : Runnable {
        override fun run() {
            refreshCpuStats()
            statusRefreshHandler.postDelayed(this, RefreshIntervals.cpu(this@MainActivity).toLong())
        }
    }
    private val memoryRefreshRunnable = object : Runnable {
        override fun run() {
            refreshMemoryStats()
            statusRefreshHandler.postDelayed(
                this, RefreshIntervals.memory(this@MainActivity).toLong()
            )
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.elitedarkkaiser.redmagic.ui.ThemePrefs.applyNightMode(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreshInstallDefaults.prepare(this)

        // The baseline for onConfigurationChanged: without it the first change of any kind reads
        // as a scale change and rebuilds.
        resources.configuration.let {
            lastDensityDpi = it.densityDpi
            lastFontScale = it.fontScale
        }

        applyFullScreen()

        // Settings is reached from the floating button rather than the bar, so back has to bring
        // the user out of it -- otherwise the only way back to the app is to leave and return.
        // Enabled only while settings is showing, so back is left alone everywhere else rather
        // than being intercepted and re-dispatched.
        onBackPressedDispatcher.addCallback(this, settingsBackCallback)

        initDefaultTriggerMappingsStorage(this)
        DeviceScanActions.runBackgroundScan(this)

        // No blocking root/permission dialogs on launch — the Home tab's Device Compatibility
        // card and the hardware tabs' own root/device gate cover that in-line instead.
        launchMainUi()
    }

    /**
     * Hides the status bar and lets the window draw edge to edge, so the app owns the whole screen
     * except the system navigation.
     *
     * Edge-to-edge has to come with it: hiding the bar alone would leave the window still fitted
     * to where it used to be, so the app would gain nothing but an empty strip. The bar stays
     * reachable — a swipe from the top shows it transiently, then it slides away again.
     */
    private fun applyFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    /** The scale the UI's pixel sizes were worked out at, to notice when it actually moves. */
    private var lastDensityDpi = 0
    private var lastFontScale = 0f

    /**
     * Rebuilds the UI on a scale change, and on nothing else.
     *
     * The activity declares density changes as handled so a density awaiting confirmation does not
     * destroy the dialog offering to undo it, and this used to recreate on every configuration
     * change to compensate -- which is why rotating still threw the whole app away after the
     * manifest stopped the system doing it. A rotation changes no pixel size: the views are laid
     * out in dp against MATCH_PARENT widths and re-measure themselves at the new width, which is
     * what handling the change is for.
     *
     * A density or font-scale change is different, because the UI is drawn in pixels worked out
     * once at build time, so a rebuild is the only way to get it right.
     */
    override fun onResume() {
        super.onResume()
        // Cheap enough to do unconditionally (a handful of SharedPreferences reads), and the
        // alternative -- a result contract on the playground -- would only cover the one screen
        // that happens to edit these today.
        glassEffectsState.value = com.elitedarkkaiser.redmagic.ui.GlassPrefs.effects(this)

        // Off the main thread: it may run a root shell. It usually does nothing at all -- see
        // reapplyIfModeChanged for the one case it exists for.
        Thread {
            com.elitedarkkaiser.redmagic.systemtheme.SystemThemeManager.reapplyIfModeChanged(this)
        }.start()
    }

    /**
     * Also takes `assetsPaths`, which is the config change the system reports when a runtime
     * resource overlay over a package this app links against is registered, enabled or removed --
     * exactly what the System theme sheet does. Without it in the manifest the Activity is torn
     * down and rebuilt on every apply, which reads as the app restarting itself each time a colour
     * is picked. Declared, the app keeps its state and just recolours: [refreshTabColors] already
     * does that job for the appearance settings, and the palette it reads is now the new one.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        val scaleChanged =
            newConfig.densityDpi != lastDensityDpi || newConfig.fontScale != lastFontScale
        lastDensityDpi = newConfig.densityDpi
        lastFontScale = newConfig.fontScale

        if (!scaleChanged) {
            // An overlay change lands here rather than as a restart now. The app's own palette is
            // generated in process, but its seed can be the system accent (dynamic colour), so the
            // cache is dropped and the tabs repaint -- the same path the appearance settings use.
            com.elitedarkkaiser.redmagic.ui.Palette.invalidate()
            refreshTabColors()
            return
        }
        if (DisplayDensity.confirmInProgress) return
        recreate()
    }

    /**
     * Re-hides the bar when the window comes back to the foreground. Returning from another app,
     * or from a dialog that took focus, otherwise leaves the status bar on screen for good.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullScreen()
    }

    private fun startStatusRefreshLoop() {
        statusLoopGeneration++
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshHandler.removeCallbacks(cpuRefreshRunnable)
        statusRefreshHandler.removeCallbacks(memoryRefreshRunnable)
        statusRefreshHandler.postDelayed(statusRefreshRunnable, statusRefreshIntervalMs.toLong())
        statusRefreshHandler.postDelayed(cpuRefreshRunnable, RefreshIntervals.cpu(this).toLong())
        statusRefreshHandler.postDelayed(memoryRefreshRunnable, RefreshIntervals.memory(this).toLong())
    }

    override fun onDestroy() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshHandler.removeCallbacks(cpuRefreshRunnable)
        statusRefreshHandler.removeCallbacks(memoryRefreshRunnable)
        super.onDestroy()
    }

    private fun showMagicKeyAppPicker(targetButton: Button) {
        MagicKeyAppPickerDialog.show(
            activity = this,
            targetButton = targetButton,
            statusLabel = magicKeyStatusLabelRef,
            applyLaunchAppMagicKeyMode = { pkg, label, statusLabel, sliderButton ->
                MagicKeyActions.applyLaunchAppMode(
                    activity = this,
                    pkg = pkg,
                    label = label,
                    statusLabel = statusLabel,
                    sliderButton = sliderButton,
                    refreshStatus = { refreshStatus() }
                )
            }
        )
    }

    private fun applyFanLedSelection(effect: String, color: Int) {
        when {
            // The rotation is a running loop rather than a single write, and the service is what
            // runs it — starting it is how the dialog previews one.
            color == FanLedCycle.CYCLE_COLOR -> HardwareServiceActions.startFanLed(this)
            effect.startsWith("preset:") -> applyFanPreset(effect.removePrefix("preset:"))
            else -> HardwareController.setFanLedEffect(effect, color)
        }
    }

    private fun buildAutoPumpStatusText(): Pair<String, String> {
        val tempF = DashboardSnapshot.readCpuTempF().toFloatOrNull()
        if (tempF == null) {
            return "Pump Mode: AUTO • Unknown temp" to "Speed: ? • Freq: ?"
        }

        val profile = when {
            tempF >= 105f -> "Quick"
            tempF >= 90f -> "Medium"
            else -> "Slow"
        }

        val speed = when (profile) {
            "Quick" -> 80
            "Medium" -> 60
            else -> 40
        }

        return "Pump Mode: AUTO • $profile (${TempFormat.formatDisplayTempFromF(tempF, useFahrenheit)})" to
            "Speed: $speed • Freq: 4"
    }

    private fun buildCurrentHardwareProfile(name: String): HardwareProfile {
        val triggerPrefs = readTriggerPrefsSnapshot(this)

        return ProfileStateHelpers.buildCurrentHardwareProfile(
            name = name,
            input = ProfileStateHelpers.ProfileInputs(
                fanEnabled = HardwareController.isFanEnabled(),
                fanLevel = fanSeek.value.roundToInt(),
                autoFanEnabled = autoFanCurveEnabled,
                fanCurveMode = selectedCurve,

                pumpEnabled = pumpEnabled,
                pumpProfile = pumpProfile,
                autoPumpEnabled = autoPumpEnabled,

                fanLedEnabled = fanLedEnabled,
                fanLedEffect = fanLedEffect,
                fanLedColor = fanLedColor,

                logoLedEnabled = logoLedEnabled,
                logoLedEffect = logoLedEffect,
                logoLedColor = logoLedColor,

                shoulderLedEnabled = shoulderLedEnabled,
                shoulderLedEffect = shoulderLedEffect,
                shoulderLedColor = shoulderLedColor,

                triggerEnabled = triggerPrefs.triggerEnabled,
                hapticsEnabled = triggerPrefs.hapticsEnabled,
                leftTriggerAction = triggerPrefs.leftTriggerAction,
                rightTriggerAction = triggerPrefs.rightTriggerAction,
                intentUnlockRightTrigger = triggerPrefs.intentUnlockRightTrigger,
                triggersAutoStart = triggerPrefs.triggersAutoStart
            )
        )
    }

    private fun applyProfileToUiState(profile: HardwareProfile) {
        FreshInstallDefaults.keepUserChoice(this, "fan", "triggers")
        ProfileStateHelpers.applyProfileToUiState(
            profile = profile,

            setAutoFanEnabled = { value -> autoFanCurveEnabled = value },
            setFanCurveMode = { value -> selectedCurve = value },

            setPumpEnabled = { value -> pumpEnabled = value },
            setPumpProfile = { value -> pumpProfile = value },
            setAutoPumpEnabled = { value -> autoPumpEnabled = value },

            setFanLedEnabled = { value -> fanLedEnabled = value },
            setFanLedEffect = { value -> fanLedEffect = value },
            setFanLedColor = { value -> fanLedColor = value },

            setLogoLedEnabled = { value -> logoLedEnabled = value },
            setLogoLedEffect = { value -> logoLedEffect = value },
            setLogoLedColor = { value -> logoLedColor = value },

            setShoulderLedEnabled = { value -> shoulderLedEnabled = value },
            setShoulderLedEffect = { value -> shoulderLedEffect = value },
            setShoulderLedColor = { value -> shoulderLedColor = value },

            setFanLevel = { value -> fanSeek.value = value.toFloat() },
            saveTriggerPrefs = { applied -> saveTriggerPrefsStorage(this, applied) },
            enableTriggersIfNeeded = { applied ->
                if (applied.triggersAutoStart) {
                    HardwareController.enableTriggers()
                    HardwareServiceActions.startTriggers(this)
                }
            },
            afterProfileApplied = { applied ->
                ProfileActions.afterProfileApplied(
                    profile = applied,
                    setAutoFanEnabledSaved = { enabled -> saveAutoFanEnabledStorage(this, enabled) },
                    savePumpState = { savePumpStateStorage(this, pumpEnabled, pumpProfile) },
                    saveAutoPumpState = { saveAutoPumpStateStorage(this, autoPumpEnabled) },
                    saveFanLedState = { saveFanLedStateStorage(this, LedState(fanLedEnabled, fanLedEffect, fanLedColor)) },
                    saveLogoLedState = { saveLogoLedStateStorage(this, LedState(logoLedEnabled, logoLedEffect, logoLedColor)) },
                    saveShoulderLedState = { saveShoulderLedStateStorage(this, LedState(shoulderLedEnabled, shoulderLedEffect, shoulderLedColor)) },
                    startAutoFanService = { HardwareServiceActions.startAutoFan(this) },
                    stopAutoFanService = { HardwareServiceActions.stopAutoFan(this) },
                    startAutoPumpService = { HardwareServiceActions.startAutoPump(this) },
                    stopAutoPumpService = { HardwareServiceActions.stopAutoPump(this) },
                    refreshStatus = { refreshStatus() },
                    refreshSmartPumpStatusViews = { refreshSmartPumpStatusViews() }
                )
            }
        )
    }

    private fun refreshSmartPumpStatusViews() {
        val statusView = smartPumpStatusView ?: return
        val speedView = smartPumpSpeedView ?: return

        if (autoPumpEnabled) {
            val status = buildAutoPumpStatusText()
            statusView.text = status.first
            speedView.text = status.second
        } else {
            val manualLabel = pumpProfile.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            val manualSpeed = when (pumpProfile.lowercase()) {
                "slow" -> 40
                "medium" -> 60
                "quick" -> 80
                "experimental" -> 90
                else -> 60
            }
            statusView.text = "Pump Mode: MANUAL • $manualLabel"
            speedView.text = "Speed: $manualSpeed • Freq: 4"
        }
    }

    private fun applyPumpProfile(profile: String) {
        pumpProfile = profile
        pumpEnabled = true
        autoPumpEnabled = false
        savePumpStateStorage(this, pumpEnabled, pumpProfile)
        saveAutoPumpStateStorage(this, autoPumpEnabled)
        HardwareServiceActions.stopAutoPump(this)
        HardwareController.setPumpProfile(profile)
        refreshStatus()
        refreshSmartPumpStatusViews()
    }

    private fun confirmExperimentalPumpThenApply() {
        if (savedPumpStateStorage(this).experimentalAccepted) {
            applyPumpProfile("experimental")
            return
        }

        ExperimentalPumpDialog.show(
            activity = this,
            onCancel = { },
            onConfirm = {
                setPumpExperimentalAcceptedStorage(this, true)
                applyPumpProfile("experimental")
            },
            deps = ExperimentalPumpDialog.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) }
            )
        )
    }

    private fun launchMainUi() {
        fanPowerEnabled = isFanPowerEnabledStorage(this)
        statusRefreshIntervalMs = RefreshIntervals.status(this)

        glassBlurEnabledState.value = com.elitedarkkaiser.redmagic.ui.ThemePrefs.useGlassBlur(this)
        com.elitedarkkaiser.redmagic.ui.CardStyle.load(this)
        glassEffectsState.value = com.elitedarkkaiser.redmagic.ui.GlassPrefs.effects(this)
        dockedBarState.value = com.elitedarkkaiser.redmagic.ui.ThemePrefs.useDockedBar(this)
        backgroundTypeState.value = runCatching {
            com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.valueOf(
                com.elitedarkkaiser.redmagic.ui.ThemePrefs.backgroundAnimation(this)
            )
        }.getOrDefault(com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.NONE)

        MainUiStartup.applySavedHardwareState(
            applySavedFanLedStateOnLaunch = {
                val state = savedFanLedStateStorage(this)
                fanLedEnabled = state.enabled
                fanLedEffect = state.effect
                fanLedColor = state.color
            },
            applySavedLogoLedStateOnLaunch = {
                val state = savedLogoLedStateStorage(this)
                logoLedEnabled = state.enabled
                logoLedEffect = state.effect
                logoLedColor = state.color
            },
            applySavedShoulderLedStateOnLaunch = {
                val state = savedShoulderLedStateStorage(this)
                shoulderLedEnabled = state.enabled
                shoulderLedEffect = state.effect
                shoulderLedColor = state.color
            },
            applySavedPumpStateOnLaunch = {
                val state = savedPumpStateStorage(this)
                pumpEnabled = state.enabled
                pumpProfile = state.profile
                autoPumpEnabled = state.autoEnabled
            },
            setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
            isRealTimePreviewEnabledSaved = { isRealTimePreviewEnabledStorage(this) },
            setUseFahrenheit = { value -> useFahrenheit = value },
            isUseFahrenheitSaved = { isUseFahrenheitStorage(this) },
            setAutoPumpEnabled = { value -> autoPumpEnabled = value },
            isAutoPumpEnabledSaved = { savedPumpStateStorage(this).autoEnabled }
        )

        val result = MainUiLauncher.launch(
            activity = this,
            dp = { value -> dp(value) },
            createHomeTab = { createHomeTab() },
            createHardwareTab = { createHardwareTab() },
            createSoftwareTab = { createSoftwareTab() },
            buildHeader = { screenHeader() },
            wrapWithGlassBar = { contentView -> glassBarHost(contentView) },
            onScrollDelta = { dy -> onContentScrollDelta(dy) }
        )

        // The bar becomes the way out of a page while one is open, so it has to be told.
        com.elitedarkkaiser.redmagic.ui.PageHost.onOpenPageChanged(this) { title ->
            openPageTitleState.value = title
        }

        headerRef = result.header
        homeTab = result.homeTab
        hardwareTab = result.hardwareTab
        softwareTab = result.softwareTab
        settingsTab = result.settingsTab

        hardwareTabBuilt = false
        softwareTabBuilt = false

        MainUiStartup.applyLaunchHardware(
            fanLedEnabled = fanLedEnabled,
            fanLedEffect = fanLedEffect,
            fanLedColor = fanLedColor,
            logoLedEnabled = logoLedEnabled,
            logoLedEffect = logoLedEffect,
            logoLedColor = logoLedColor,
            shoulderLedEnabled = shoulderLedEnabled,
            shoulderLedEffect = shoulderLedEffect,
            shoulderLedColor = shoulderLedColor,
            pumpEnabled = pumpEnabled,
            pumpProfile = pumpProfile,
            applyFanLedSelection = { effect, color -> applyFanLedSelection(effect, color) },
            startFanLedService = { HardwareServiceActions.startFanLed(this) },
            stopFanLedService = { HardwareServiceActions.stopFanLed(this) }
        )

        if (autoPumpEnabled) {
            HardwareServiceActions.startAutoPump(this)
        }

        // An optimistic seed from the last known state, so an already-qualified device doesn't
        // flash the hardware-tab gate before the first live refreshStatus() check (below) lands
        // and corrects it either way.
        rootAvailable = hasCachedRootAccessStorage(this)
        proAvailable = isRedMagic10ProStorage(this)

        switchTab("home")
        statusRefreshHandler.postDelayed({
            refreshStatus()
            startStatusRefreshLoop()
        }, 2500L)

        // Fail-safe for the loading overlay. The first refresh blocks on RootShell.hasRoot(),
        // which waits on the superuser prompt and sits there as long as the user takes to answer
        // it -- or forever if they never do. Clear the overlay regardless once that has had time
        // to land, so the worst case is stale "--" values rather than a spinner that never stops.
        statusRefreshHandler.postDelayed({ isInitialStatusLoad.value = false }, 12_000L)
        // Do not start background services just because the UI opened.
        // Game Mode starts from selected-app foreground events.
        // Charging Mode starts from boot, plug state, or explicit toggle.
    }

    private fun restoreFanCurveUiState() {
        selectedCurve = selectedCurveStorage(this)
        autoFanCurveEnabled = isAutoFanEnabledStorage(this)
        setAutoCurveChecked(autoFanCurveEnabled)

        if (autoFanCurveEnabled) {
            curveStatusText.text = "Auto fan curve active • Running in background service"
        } else {
            curveStatusText.text = "Selected curve: $selectedCurve • Manual control"
        }

        refreshCurveSelection(selectedCurve)
        updateManualCurveUiState()
    }

    private fun createHomeTab(): LinearLayout {
        val result = com.elitedarkkaiser.redmagic.ui.HomeTabUi.create(
            com.elitedarkkaiser.redmagic.ui.HomeTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                openTab = { tab -> switchTab(tab) },
                isRedMagic10Pro = { isRedMagic10ProStorage(this) },
                deviceCompatModelSummary = { deviceScanModelStorage(this) },
                hasRootAccess = { rootCheckedThisLaunch && rootAvailable },
                recheckDeviceCompatibility = { recheckDeviceCompatibility() }
            )
        )

        homeStatsRefs = result.refs

        return result.view
    }

    private fun coolingTabDeps(container: LinearLayout): com.elitedarkkaiser.redmagic.ui.CoolingTabDeps {
        return com.elitedarkkaiser.redmagic.ui.CoolingTabDeps(
                scrollTabContainer = { container },
                sectionPanel = { sectionPanel() },
                subtleLabel = { text -> subtleLabel(text) },
                bodyText = { text -> bodyText(text) },

                getSelectedCurve = { selectedCurve },
                setSelectedCurve = { value -> selectedCurve = value },
                setSelectedCurveSaved = { value -> saveSelectedCurveStorage(this, value) },

                getAutoFanCurveEnabled = { autoFanCurveEnabled },
                setAutoFanCurveEnabled = { value -> autoFanCurveEnabled = value },
                setAutoFanEnabledSaved = { value -> saveAutoFanEnabledStorage(this, value) },

                getFanPowerEnabled = { fanPowerEnabled },
                setFanPowerEnabled = { value -> fanPowerEnabled = value },
                saveFanPowerEnabled = { value -> saveFanPowerEnabledStorage(this, value) },


                getPumpEnabled = { pumpEnabled },
                setPumpEnabled = { value -> pumpEnabled = value },
                getPumpProfile = { pumpProfile },
                getAutoPumpEnabled = { autoPumpEnabled },
                setAutoPumpEnabled = { value -> autoPumpEnabled = value },

                startAutoFanService = { HardwareServiceActions.startAutoFan(this) },
                stopAutoFanService = { HardwareServiceActions.stopAutoFan(this) },
                startAutoPumpService = { HardwareServiceActions.startAutoPump(this) },
                stopAutoPumpService = { HardwareServiceActions.stopAutoPump(this) },
                savePumpState = { savePumpStateStorage(this, pumpEnabled, pumpProfile) },
                saveAutoPumpState = { saveAutoPumpStateStorage(this, autoPumpEnabled) },
                refreshStatus = { refreshStatus() },
                refreshSmartPumpStatusViews = { refreshSmartPumpStatusViews() },
                buildAutoPumpStatusText = { buildAutoPumpStatusText() },
                updateManualCurveUiState = { updateManualCurveUiState() }
            )
    }

    private fun assignCoolingRefs(refs: com.elitedarkkaiser.redmagic.ui.CoolingTabUi.Refs) {
        curveStatusText = refs.curveStatusText
        fanSeek = refs.fanSeek
        autoCurveCheck = refs.autoCurveCheck
        fanPowerSwitch = refs.fanPowerSwitch
        setFanPowerChecked = refs.setFanPowerChecked
        setAutoCurveChecked = refs.setAutoCurveChecked
        syncFanLevel = refs.syncFanLevel
        refreshCurveSelection = refs.refreshCurveSelection
        setCurveControlsEnabled = refs.setCurveControlsEnabled
        applyFanEnabledState = refs.applyFanEnabledState
        smartPumpStatusView = refs.smartPumpStatusView
        smartPumpSpeedView = refs.smartPumpSpeedView
    }

    private fun applyFanLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return
        if (fanLedEnabled) {
            applyFanLedSelection(fanLedEffect, fanLedColor)
        } else {
            HardwareController.setFanLedEnabled(false)
        }
    }

    private fun applyLogoLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return
        if (logoLedEnabled) {
            HardwareController.setLogoLedEffect(logoLedEffect, logoLedColor)
        } else {
            HardwareController.setLogoLedEnabled(false)
        }
    }

    private fun applyShoulderLedPreviewIfEnabled() {
        if (!realTimePreviewEnabled) return
        if (shoulderLedEnabled) {
            HardwareController.setShoulderLedEffect(shoulderLedEffect, shoulderLedColor)
        } else {
            HardwareController.setShoulderLedEnabled(false)
        }
    }

    /**
     * Hardware, which is now all of it: cooling, the Magic Key and triggers, and lighting.
     *
     * Three tabs became one because there was no longer much to separate. Every section on all
     * three is a card with a master switch that folds its contents away when off, so what used to
     * be three screens of controls is a single column that is mostly one-line cards -- and the
     * bottom bar drops to three tabs, which is what its widest label ("Hardware") always wanted.
     *
     * The three builders each take the container to fill rather than making their own, so they
     * stack into one scroller in the order you'd reach for them: cooling, then the physical
     * controls, then lighting.
     */
    private fun createHardwareTab(): LinearLayout {
        val container = scrollTabContainer()

        val cooling = com.elitedarkkaiser.redmagic.ui.CoolingTabUi.create(coolingTabDeps(container))
        assignCoolingRefs(cooling.refs)

        addHardwareSections(container)
        addLightingSections(container)

        // Each master-switch section as one of Iconify's home cards, opening it as a page.
        com.elitedarkkaiser.redmagic.ui.SectionPages.convert(this, container,
            listOf(com.elitedarkkaiser.redmagic.ui.GameTriggerUi.card(this)))

        return container
    }

    /** The Magic Key, triggers, haptics and display density. */
    private fun addHardwareSections(container: LinearLayout) {
        val result = com.elitedarkkaiser.redmagic.ui.HardwareTabUi.create(
            this,
            com.elitedarkkaiser.redmagic.ui.HardwareTabDeps(
                scrollTabContainer = { container },
                bodyText = { text -> bodyText(text) },

                showTriggerSetupDialog = { showTriggerSetupDialog() },
                enableTriggersAndService = {
                    HardwareController.enableTriggers()
                    HardwareServiceActions.startTriggers(this)
                    refreshStatus()
                    Toast.makeText(this, "Triggers enabled", Toast.LENGTH_SHORT).show()
                },
                disableTriggersAndService = {
                    HardwareServiceActions.stopTriggers(this)
                    refreshStatus()
                    Toast.makeText(this, "Triggers disabled", Toast.LENGTH_SHORT).show()
                },
                isTriggersEnabled = { HardwareController.isTriggersEnabled() },
                testHaptic = {
                    // At the strength the slider is set to, or the test would feel nothing like
                    // what the triggers actually do.
                    val strength = HapticStrength.get(this)
                    Thread {
                        HardwareController.vibrate(
                            durationMs = 100,
                            gain = HapticStrength.scale(220, strength)
                        )
                    }.start()
                    Toast.makeText(
                        this,
                        if (strength <= HapticStrength.OFF) "Strength is off"
                        else "Haptic test sent",
                        Toast.LENGTH_SHORT
                    ).show()
                },


                readMagicKeyModeLabel = { MagicKeyActions.readModeLabel() },
                applyStockMagicKeyMode = { label, action, statusLabel, sliderButton ->
                    MagicKeyActions.applyStockMode(
                        activity = this,
                        label = label,
                        applyMode = action,
                        statusLabel = statusLabel,
                        sliderButton = sliderButton,
                        refreshStatus = { refreshStatus() }
                    )
                },
                disableMagicKeyMode = { statusLabel, sliderButton ->
                    MagicKeyActions.disableMode(
                        activity = this,
                        statusLabel = statusLabel,
                        sliderButton = sliderButton,
                        refreshStatus = { refreshStatus() }
                    )
                },
                resolveMagicKeyAppLabel = { pkg -> MagicKeyActions.resolveAppLabel(this, pkg) },
                savedMagicKeyAppPackage = { savedMagicKeyAppPackageStorage(this) },
                showMagicKeyAppPicker = { button -> showMagicKeyAppPicker(button) }
            )
        )

        magicKeyStatusLabelRef = result.refs.magicKeyStatusLabel
    }

    /** LED zones, Game Mode, call lighting and charging mode. */
    private fun addLightingSections(container: LinearLayout) {
        com.elitedarkkaiser.redmagic.ui.LightingTabUi.create(
            this,
            com.elitedarkkaiser.redmagic.ui.LightingTabDeps(
                scrollTabContainer = { container },
                sectionPanel = { sectionPanel() },
                bodyText = { text -> bodyText(text) },
                subtleLabel = { text -> subtleLabel(text) },
                actionButton = { text, isDanger, onClick -> actionButton(text, isDanger, onClick) },
                singleRow = { button -> singleRow(button) },
                row = { left, right -> row(left, right) },
                dp = { value -> dp(value) },

                getRealTimePreviewEnabled = { realTimePreviewEnabled },
                setRealTimePreviewEnabled = { value -> realTimePreviewEnabled = value },
                saveRealTimePreviewEnabled = { value -> saveRealTimePreviewEnabledStorage(this, value) },

                showFanLedDialog = { showFanLedDialog() },
                showLogoLedDialog = { showLogoLedDialog() },
                showShoulderLedDialog = { showShoulderLedDialog() },
                showGameModeAppPicker = { showGamePickerDialog() },
                showGameModeProfileDialog = { showGameModeProfileDialog() },
                gameModeAppsSummary = { gameModeAppsSummaryStorage(this) },

                getChargingLedEnabled = { ChargingLedState.isEnabled(this) },
                setChargingLedEnabled = { enabled ->
                    ChargingLedState.setEnabled(this, enabled)
                    HardwareServiceActions.startChargingMode(this)
                },
                showChargingFanLedDialog = {
                    ChargingLedActions.showFanDialog(
                        activity = this,
                                colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                        colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
                        fanPresetBubble = { c1, c2, c3, c4, presetValue, selected, onClick ->
                            selectedFanPresetBubble(c1, c2, c3, c4, presetValue, selected, onClick)
                        }
                    )
                },
                showChargingLogoLedDialog = {
                    ChargingLedActions.showLogoDialog(this, chargingLedDialogDeps())
                },
                showChargingShoulderLedDialog = {
                    ChargingLedActions.showShoulderDialog(this, chargingLedDialogDeps())
                },
                getCallLightingEnabled = { CallLightingState.isEnabled(this) },
                setCallLightingEnabled = { enabled ->
                    CallLightingState.setEnabled(this, enabled)
                    if (enabled) {
                        // The service watches the phone, which needs READ_PHONE_STATE granted at
                        // runtime -- starting it without that used to crash the app on create.
                        ensurePhoneStatePermission { granted ->
                            if (granted) {
                                HardwareServiceActions.startCallLighting(this)
                            } else {
                                CallLightingState.setEnabled(this, false)
                                Toast.makeText(
                                    this,
                                    "Call lighting needs phone permission",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        CallLightingState.setActive(this, false)
                        HardwareServiceActions.stopCallLighting(this)
                    }
                },
                getPauseFanDuringCalls = { CallLightingState.shouldPauseFanDuringCalls(this) },
                setPauseFanDuringCalls = { enabled ->
                    CallLightingState.setPauseFanDuringCalls(this, enabled)
                },
                showIncomingCallProfileDialog = {
                    CallLightingProfileUi.show(
                        activity = this,
                        title = "Incoming Call Lighting",
                        subtitle = "Applied while a call is ringing.",
                        fanKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_FAN_ENABLED_KEY,
                            CallLightingState.INCOMING_FAN_EFFECT_KEY,
                            CallLightingState.INCOMING_FAN_COLOR_KEY,
                            "Fan LED",
                            "flashing",
                            5
                        ),
                        logoKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_LOGO_ENABLED_KEY,
                            CallLightingState.INCOMING_LOGO_EFFECT_KEY,
                            CallLightingState.INCOMING_LOGO_COLOR_KEY,
                            "Logo LED",
                            "flashing",
                            1
                        ),
                        shoulderKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.INCOMING_SHOULDER_ENABLED_KEY,
                            CallLightingState.INCOMING_SHOULDER_EFFECT_KEY,
                            CallLightingState.INCOMING_SHOULDER_COLOR_KEY,
                            "Shoulder LEDs",
                            "flashing",
                            8
                        ),
                        deps = callLightingProfileDeps()
                    )
                },
                showConnectedCallProfileDialog = {
                    CallLightingProfileUi.show(
                        activity = this,
                        title = "Connected Call Lighting",
                        subtitle = "Applied while a call is connected.",
                        fanKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_FAN_ENABLED_KEY,
                            CallLightingState.CONNECTED_FAN_EFFECT_KEY,
                            CallLightingState.CONNECTED_FAN_COLOR_KEY,
                            "Fan LED",
                            "steady",
                            5
                        ),
                        logoKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_LOGO_ENABLED_KEY,
                            CallLightingState.CONNECTED_LOGO_EFFECT_KEY,
                            CallLightingState.CONNECTED_LOGO_COLOR_KEY,
                            "Logo LED",
                            "steady",
                            1
                        ),
                        shoulderKeys = CallLightingProfileUi.ZoneKeys(
                            CallLightingState.CONNECTED_SHOULDER_ENABLED_KEY,
                            CallLightingState.CONNECTED_SHOULDER_EFFECT_KEY,
                            CallLightingState.CONNECTED_SHOULDER_COLOR_KEY,
                            "Shoulder LEDs",
                            "steady",
                            8
                        ),
                        deps = callLightingProfileDeps()
                    )
                }
            )
        )
    }

    private fun createSettingsTab(): LinearLayout {
        return com.elitedarkkaiser.redmagic.ui.SettingsTabUi.create(
            com.elitedarkkaiser.redmagic.ui.SettingsTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                activity = { this },
                sectionPanel = { sectionPanel() },
                // Theme mode, dynamic colour and Pure Black are read as the Activity is built, so
                // they only take hold on a fresh one.
                restartForTheme = { recreate() },
                onGlassBlurChanged = { value -> glassBlurEnabledState.value = value },
                onBarStyleChanged = { value -> dockedBarState.value = value },
                onBackgroundAnimationChanged = { type -> backgroundTypeState.value = type },
                onPureBlackChanged = { refreshTabColors() },
                onPaletteChanged = {
                    com.elitedarkkaiser.redmagic.ui.Palette.invalidate()
                    refreshTabColors()
                },

                getUseFahrenheit = { useFahrenheit },
                setUseFahrenheit = { value -> useFahrenheit = value },
                saveUseFahrenheit = { value -> saveUseFahrenheitStorage(this, value) },
                refreshStatus = { refreshStatus() },
                onRefreshIntervalsChanged = {
                    statusRefreshIntervalMs = RefreshIntervals.status(this)
                    startStatusRefreshLoop()
                }
            )
        ).view
    }

    /**
     * Gets READ_PHONE_STATE, then reports back on the main thread.
     *
     * Root first, because this app has it and it costs no dialog; the ordinary runtime prompt is
     * the fallback. [onResult] runs either way, so the caller can put its switch back if refused.
     */
    private fun ensurePhoneStatePermission(onResult: (Boolean) -> Unit) {
        if (CallLightingPermission.granted(this)) {
            onResult(true)
            return
        }
        Thread {
            val viaRoot = CallLightingPermission.grantWithRoot(this)
            runOnUiThread {
                if (viaRoot) {
                    onResult(true)
                } else {
                    pendingPhoneStateResult = onResult
                    phoneStatePermissionLauncher.launch(CallLightingPermission.PERMISSION)
                }
            }
        }.start()
    }

    private fun createSoftwareTab(): LinearLayout {
        return com.elitedarkkaiser.redmagic.ui.SoftwareTabUi.create(
            com.elitedarkkaiser.redmagic.ui.XposedTabDeps(
                scrollTabContainer = { scrollTabContainer() },
                activity = { this }
            )
        ).view
    }


    private fun callLightingProfileDeps(): CallLightingProfileUi.Deps {
        return CallLightingProfileUi.Deps(
            colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
            colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            fanPresetBubble = { c1, c2, c3, c4, presetValue, selected, onClick ->
                selectedFanPresetBubble(c1, c2, c3, c4, presetValue, selected, onClick)
            }
        )
    }

    private fun chargingLedDialogDeps(): ChargingLedProfileDialog.Deps {
        return ChargingLedProfileDialog.Deps(
            colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
            fanPresetBubble = { h1, h2, h3, h4, value, selected, onClick ->
                selectedFanPresetBubble(h1, h2, h3, h4, value, selected, onClick)
            }
        )
    }

    private fun showTriggerSetupDialog() {
        TriggerSetupDialog.show(this)
    }

    private fun showGameModeProfileDialog() {
        GameModeUi.showGameModeProfileDialog(
            activity = this,
            current = getSavedGameModeProfileStorage(this),
            deps = GameModeUi.Deps(
                dp = { value -> dp(value) },
                space = { value -> space(value) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
            ),
            onSaveProfile = { profile ->
                saveGameModeProfileStorage(this, profile)
                GameModeActions.applyProfileNow(getSavedGameModeProfileStorage(this), applyFanLed = { effect, color -> applyFanLedSelection(effect, color) })
                GameModeActions.startServiceIfPermitted(this)
            }
        )
    }

    private fun showPumpProfileDialog() {
        PumpDialogUi.showPumpProfileDialog(
            activity = this,
            originalEnabled = pumpEnabled,
            originalProfile = pumpProfile,
            currentProfile = { pumpProfile },
            setPumpEnabled = { value -> pumpEnabled = value },
            setPumpProfile = { value -> pumpProfile = value },
            applyHardwareProfile = { value -> HardwareController.setPumpProfile(value) },
            disablePump = { HardwareController.enablePump(false) },
            savePumpState = { savePumpStateStorage(this, pumpEnabled, pumpProfile) },
            confirmExperimentalPumpThenApply = { confirmExperimentalPumpThenApply() },
            setDialogRefreshPump = { callback -> dialogRefreshPump = callback },
            deps = PumpDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) }
            )
        )
    }

    private fun showShoulderLedDialog() {
        ShoulderLedDialogUi.showShoulderLedDialog(
            activity = this,
            originalEnabled = shoulderLedEnabled,
            originalEffect = shoulderLedEffect,
            originalColor = shoulderLedColor,
            currentEnabled = { shoulderLedEnabled },
            currentEffect = { shoulderLedEffect },
            currentColor = { shoulderLedColor },
            setEnabled = { value -> shoulderLedEnabled = value },
            setEffect = { value -> shoulderLedEffect = value },
            setColor = { value -> shoulderLedColor = value },
            applyPreviewIfEnabled = { applyShoulderLedPreviewIfEnabled() },
            applyEffect = { effect, color -> HardwareController.setShoulderLedEffect(effect, color) },
            disableLed = { HardwareController.setShoulderLedEnabled(false) },
            saveState = { saveShoulderLedStateStorage(this, LedState(shoulderLedEnabled, shoulderLedEffect, shoulderLedColor)) },
            startFanLedService = { HardwareServiceActions.startFanLed(this) },
            stopFanLedService = { HardwareServiceActions.stopFanLed(this) },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            setDialogRefresh = { callback -> dialogRefreshShoulderLed = callback },
            deps = ShoulderLedDialogUi.Deps(
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                panelColor = panelColor,
                borderColor = borderColor,
                panelPressed = panelPressed,
                accent = accent,
                typeface = typeface,
                dp = { value -> dp(value) },
                roundedBg = { fill, stroke, radius -> roundedBg(fill, stroke, radius) },
                roundedFill = { color, radius -> roundedFill(color, radius) },
                space = { value -> space(value) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) }
            )
        )
    }

    private fun showLogoLedDialog() {
        LogoLedDialogUi.showLogoLedDialog(
            activity = this,
            originalEnabled = logoLedEnabled,
            originalEffect = logoLedEffect,
            originalColor = logoLedColor,
            currentEnabled = { logoLedEnabled },
            currentEffect = { logoLedEffect },
            currentColor = { logoLedColor },
            setEnabled = { value -> logoLedEnabled = value },
            setEffect = { value -> logoLedEffect = value },
            setColor = { value -> logoLedColor = value },
            applyPreviewIfEnabled = { applyLogoLedPreviewIfEnabled() },
            applyEffect = { effect, color -> HardwareController.setLogoLedEffect(effect, color) },
            disableLed = { HardwareController.setLogoLedEnabled(false) },
            saveState = { saveLogoLedStateStorage(this, LedState(logoLedEnabled, logoLedEffect, logoLedColor)) },
            startFanLedService = { HardwareServiceActions.startFanLed(this) },
            stopFanLedService = { HardwareServiceActions.stopFanLed(this) },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            setDialogRefresh = { callback -> dialogRefreshLogoLed = callback },
            deps = LogoLedDialogUi.Deps(
                dp = { value -> dp(value) },
                space = { value -> space(value) },
                colorDotGeneric = { hex, selected, onClick -> colorDotGeneric(hex, selected, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) }
            )
        )
    }

    private fun showFanLedDialog() {
        FanLedDialogUi.showFanLedDialog(
            activity = this,
            originalEnabled = fanLedEnabled,
            originalEffect = fanLedEffect,
            originalColor = fanLedColor,
            currentEnabled = { fanLedEnabled },
            currentEffect = { fanLedEffect },
            currentColor = { fanLedColor },
            setEnabled = { value -> fanLedEnabled = value },
            setEffect = { value -> fanLedEffect = value },
            setColor = { value -> fanLedColor = value },
            applyPreviewIfEnabled = { applyFanLedPreviewIfEnabled() },
            applySelection = { effect, color -> applyFanLedSelection(effect, color) },
            disableLed = { HardwareController.setFanLedEnabled(false) },
            saveState = { saveFanLedStateStorage(this, LedState(fanLedEnabled, fanLedEffect, fanLedColor)) },
            startFanLedService = { HardwareServiceActions.startFanLed(this) },
            stopFanLedService = { HardwareServiceActions.stopFanLed(this) },
            anyLedEnabled = { fanLedEnabled || logoLedEnabled || shoulderLedEnabled },
            setDialogRefresh = { callback -> dialogRefreshFanLed = callback },
            deps = FanLedDialogUi.Deps(
                colorDot = { colorId, hex, onClick -> colorDot(colorId, hex, onClick) },
                colorDotDrawable = { hex, selected -> colorDotDrawable(hex, selected) }
            )
        )
    }

    private fun applyFanPreset(effectValue: String) {
        fanLedEnabled = true
        fanLedEffect = "preset:$effectValue"
        fanLedColor = -1

        HardwareController.setFanLedStockPreset(effectValue)
        dialogRefreshFanLed?.invoke()
    }

    private fun selectedFanPresetBubble(
        c1: String,
        c2: String,
        c3: String,
        c4: String,
        presetValue: String,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        return fanPresetBubble(
            c1,
            c2,
            c3,
            c4,
            presetValue = presetValue,
            selectedOverride = { selected },
            onClick = onClick
        )
    }

    private fun fanPresetBubble(
        vararg hexes: String,
        presetValue: String,
        selectedOverride: (() -> Boolean)? = null,
        onClick: () -> Unit
    ): View {
        require(hexes.size == 4) { "fanPresetBubble requires exactly 4 colors" }

        return object : View(this) {
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = dp(3).toFloat()
            }

            init {
                val size = dp(42)
                layoutParams = LinearLayout.LayoutParams(size, size)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)

                val pad = dp(3).toFloat()
                val rect = android.graphics.RectF(
                    pad,
                    pad,
                    width.toFloat() - pad,
                    height.toFloat() - pad
                )

                val saveCount = canvas.save()
                val clipPath = android.graphics.Path().apply {
                    addOval(rect, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clipPath)

                val midX = rect.centerX()
                val midY = rect.centerY()

                fillPaint.color = Color.parseColor(hexes[0])
                canvas.drawRect(rect.left, rect.top, midX, midY, fillPaint)

                fillPaint.color = Color.parseColor(hexes[1])
                canvas.drawRect(midX, rect.top, rect.right, midY, fillPaint)

                fillPaint.color = Color.parseColor(hexes[2])
                canvas.drawRect(rect.left, midY, midX, rect.bottom, fillPaint)

                fillPaint.color = Color.parseColor(hexes[3])
                canvas.drawRect(midX, midY, rect.right, rect.bottom, fillPaint)

                canvas.restoreToCount(saveCount)

                val selected = selectedOverride?.invoke() ?: (fanLedEffect == "preset:$presetValue")
                ringPaint.color = if (selected) {
                    m3.onSurface
                } else {
                    Color.TRANSPARENT
                }
                canvas.drawOval(rect, ringPaint)
            }
        }
    }

    private fun colorDot(colorId: Int, hex: String, onClick: () -> Unit): View {
        return View(this).apply {
            val size = dp(42)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = colorDotDrawable(hex, fanLedColor == colorId)
            setOnClickListener { onClick() }
        }
    }

    /**
     * An LED colour swatch. The fill is the LED's own colour -- data, not theme -- but the ring
     * marking the selected one is onSurface, which contrasts with the page in either theme where
     * the white it used to be vanished on a light one.
     */
    private fun colorDotDrawable(hex: String, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            setStroke(dp(3), if (selected) m3.onSurface else Color.TRANSPARENT)
        }
    }

    private fun colorDotGeneric(hex: String, selected: Boolean, onClick: () -> Unit): View {
        return View(this).apply {
            val size = dp(42)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = colorDotDrawable(hex, selected)
            setOnClickListener { onClick() }
        }
    }

    /**
     * Swaps [oldTab] for [newTab] at the same position in the shared content column rather than
     * at a hardcoded index — that column also holds the screen-title header and the other tabs,
     * so positions aren't fixed.
     */
    private fun replaceTab(oldTab: LinearLayout, newTab: LinearLayout): LinearLayout {
        val parent = oldTab.parent as ViewGroup
        val index = parent.indexOfChild(oldTab)
        parent.removeViewAt(index)
        parent.addView(newTab, index)
        return newTab
    }

    /**
     * Rebuilds every tab's Views from scratch so a colour-only appearance change (Pure Black)
     * takes effect immediately, rather than the recreate() theme mode and dynamic colour still
     * need (both only take hold on a fresh Activity: night-mode resource qualifiers and the
     * DynamicColors precondition are both resolved once, at Activity creation). Safe because every
     * tab builder already tolerates being called again -- that's exactly what switchTab's own
     * lazy rebuild-on-visit already does -- and reassigns the same lateinit view refs
     * refreshStatus() writes into.
     */
    private fun refreshTabColors() {
        homeTab = replaceTab(homeTab, createHomeTab())
        hardwareTabBuilt = false
        softwareTabBuilt = false
        settingsTabBuilt = false
        switchTab(currentTab)
        refreshStatus()
        appearanceVersion.intValue++
    }

    private fun switchTab(tab: String) {
        currentTab = tab

        // Every hardware tab needs root on a confirmed RedMagic 10 Pro; without either, show a
        // gate instead of building the real UI (which would just fail silently on the wrong
        // hardware or without permission). Leaving xTabBuilt false means the real tab gets built
        // fresh the next time it's opened with both available.
        val gated = tab != "home" && (!rootAvailable || !proAvailable)

        when (tab) {
            "hardware" -> {
                if (gated) {
                    hardwareTab = replaceTab(hardwareTab, createRootGateTab("Hardware"))
                    hardwareTabBuilt = false
                } else if (!hardwareTabBuilt) {
                    hardwareTab = replaceTab(hardwareTab, createHardwareTab())
                    hardwareTabBuilt = true
                    restoreFanCurveUiState()
                }
            }

            "settings" -> {
                if (!settingsTabBuilt) {
                    settingsTab = replaceTab(settingsTab, createSettingsTab())
                    settingsTabBuilt = true
                }
            }

            "software" -> {
                if (gated) {
                    softwareTab = replaceTab(softwareTab, createRootGateTab("Software"))
                    softwareTabBuilt = false
                } else if (!softwareTabBuilt) {
                    softwareTab = replaceTab(softwareTab, createSoftwareTab())
                    softwareTabBuilt = true
                }
            }

        }

        homeTab.visibility = if (tab == "home") View.VISIBLE else View.GONE
        hardwareTab.visibility = if (tab == "hardware") View.VISIBLE else View.GONE
        softwareTab.visibility = if (tab == "software") View.VISIBLE else View.GONE
        settingsTab.visibility = if (tab == "settings") View.VISIBLE else View.GONE

        animateTabIn(tab)

        headerRef?.text = screenTitles[tab] ?: "RedMagic"
        // Settings has no tab in the bar, so leave the selection where it was: the bar still shows
        // which destination you will return to.
        val barIndex = bottomBarTabs.indexOf(tab)
        if (barIndex >= 0) bottomBarSelectedIndex.intValue = barIndex
        settingsBackCallback.isEnabled = tab == "settings"
        isOnSettingsTab.value = tab == "settings"
    }

    /**
     * The order the destinations sit in, which is what decides which way a switch moves.
     *
     * Settings is last rather than absent: it has no tab in the bar, but it is still somewhere you
     * go and come back from, and having it off the end means it enters from the same side every
     * time and leaves from the other.
     */
    private val tabOrder = listOf("home", "hardware", "software", "settings")

    /** Where the last switch left us, so the next one knows its direction. */
    private var lastTabIndex = 0

    /**
     * Slides the newly shown tab in from the side it came from.
     *
     * Only the incoming view is animated. The outgoing one is already GONE by the time this runs
     * -- switchTab flips visibility directly, and deferring that to animate a fade out would mean
     * holding two tabs visible at once, which on this app means two live status refreshes drawing
     * into the same backdrop. The slide is short enough (a quarter of a second, 28dp) that what
     * reads is the new page arriving rather than the old one missing.
     */
    private fun animateTabIn(tab: String) {
        val index = tabOrder.indexOf(tab)
        if (index < 0) return
        val previous = lastTabIndex
        lastTabIndex = index
        if (index == previous) return

        val view = when (tab) {
            "hardware" -> hardwareTab
            "software" -> softwareTab
            "settings" -> settingsTab
            else -> homeTab
        }
        val from = if (index > previous) dp(28).toFloat() else -dp(28).toFloat()

        view.animate().cancel()
        view.alpha = 0f
        view.translationX = from
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(240)
            // M3's emphasized decelerate: quick off the mark, long settle. The same curve the
            // bar's own spring approximates, so the page and the pill arrive together.
            .setInterpolator(android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
            .withEndAction {
                // Belt and braces: an animation cancelled mid-flight (a fast double switch) would
                // otherwise leave a tab parked off-centre and half transparent.
                view.alpha = 1f
                view.translationX = 0f
            }
            .start()
    }

    /**
     * Stands in for a hardware tab's real UI while it's gated — M3 Expressive style. Reads
     * rootAvailable/proAvailable directly since it's always (re)built right after they change.
     */
    private fun createRootGateTab(tabLabel: String): LinearLayout {
        val container = scrollTabContainer()

        val gateTitle = if (!proAvailable) "Unsupported device" else "Root access required"
        val gateBody = when {
            !proAvailable && !rootAvailable -> "Needs a RedMagic 10 Pro with root."
            !proAvailable -> "$tabLabel is only supported on the RedMagic 10 Pro."
            else -> "Needs root. Grant it, then recheck from Home."
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }

            addView(TextView(this@MainActivity).apply {
                text = com.elitedarkkaiser.redmagic.ui.Icons.XMARK
                gravity = Gravity.CENTER
                m3.styleGlyph(this, 28f, m3.onErrorContainer)
                background = m3.surfaceShape(m3.errorContainer, M3.Shape.full)
            }, LinearLayout.LayoutParams(dp(64), dp(64)))

            addView(TextView(this@MainActivity).apply {
                text = gateTitle
                gravity = Gravity.CENTER
                m3.styleText(this, M3.Type.titleLarge, m3.onSurface)
                setPadding(0, dp(24), 0, dp(8))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(TextView(this@MainActivity).apply {
                text = gateBody
                gravity = Gravity.CENTER
                m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(24)))

            addView(m3.button(
                "Go to Home", M3.ButtonKind.Tonal, M3.ButtonSize.Medium
            ) { switchTab("home") }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        container.addView(card)
        return container
    }

    // Tab identifiers in the order the glass bar displays/drags across them.
    private val bottomBarTabs = listOf("home", "hardware", "software")
    private val screenTitles = mapOf(
        "home" to "RedMagic",
        "hardware" to "Hardware",
        "software" to "Software",
        "settings" to "Settings"
    )

    /**
     * The large top app bar's title -- headlineMedium, regular weight -- naming the current screen,
     * on the 16dp keyline the content under it starts from. PageHost titles a page the same way,
     * at the same height, so moving between a tab and a page never moves the title.
     */
    private fun screenHeader(): TextView {
        return TextView(this).apply {
            // Iconify's expanded top app bar title: 32sp SemiBold (titleLarge scaled up).
            m3.styleText(this, M3.Type.headlineLarge, m3.onSurface)
            setPadding(dp(8), dp(4), dp(8), dp(24))
        }
    }
    private val bottomBarSelectedIndex = mutableIntStateOf(0)

    /**
     * True while Settings is the visible tab. Settings has no bar tab of its own (reached only via
     * the FAB), so [switchTab] updates this directly rather than it falling out of
     * [bottomBarSelectedIndex] the way the five real bar tabs do -- read by the FAB to show Home
     * instead of Settings while already there, since tapping Settings again would go nowhere.
     */
    private val isOnSettingsTab = mutableStateOf(false)

    /**
     * Drives the glass bar's collapse/expand, the way
     * HorizontalFloatingToolbarAsScaffoldFabSample's floatingToolbarVerticalNestedScroll ties a
     * toolbar's expanded state to the content scrolling under it. True (expanded) by default;
     * [onContentScrollDelta] flips it as the shared content scroll moves.
     */
    private val bottomBarExpanded = mutableStateOf(true)

    /** Accumulates scroll in the current direction; reset whenever direction reverses. Gates the
     *  flip in [bottomBarExpanded] behind a small threshold so a single stray pixel of scroll (or
     *  a tiny bounce at the very top/bottom of the content) doesn't toggle it. */
    private var bottomBarScrollAccumPx = 0f

    private fun onContentScrollDelta(deltaPx: Int) {
        if (deltaPx == 0) return
        if ((deltaPx > 0) != (bottomBarScrollAccumPx > 0)) bottomBarScrollAccumPx = 0f
        bottomBarScrollAccumPx += deltaPx

        val thresholdPx = dp(24)
        if (bottomBarScrollAccumPx > thresholdPx) {
            bottomBarExpanded.value = false
        } else if (bottomBarScrollAccumPx < -thresholdPx) {
            bottomBarExpanded.value = true
        }
    }

    /**
     * Settings options that only affect the glass-bar Compose layer (see glassBarHost) rather
     * than any of this app's hand-drawn Views. Read live by that Compose content instead of the
     * plain ThemePrefs value it used to read once, so toggling either updates the screen
     * immediately -- unlike theme mode or dynamic colour, which only take effect on a fresh
     * Activity (see restartForTheme) since Android resolves both once, at Activity creation.
     */
    private val glassBlurEnabledState = mutableStateOf(false)
    private val backgroundTypeState =
        mutableStateOf(com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType.NONE)

    /**
     * Bumped by [refreshTabColors] purely so the glass bar's Compose content (see glassBarHost)
     * recomposes alongside the hand-drawn tabs it rebuilds -- its own colours are plain (non-State)
     * property reads, so without this nothing would tell that Compose tree a Pure Black toggle had
     * changed them, and it would keep showing whatever it first composed with until the next
     * genuine recreate().
     */
    private val appearanceVersion = mutableIntStateOf(0)

    /**
     * True until the first [refreshStatus] pass lands. Home renders "--" placeholders until then,
     * so the loading overlay covers exactly that window and nothing after it: later refreshes
     * update values in place and must not flash the overlay again.
     */
    private val isInitialStatusLoad = mutableStateOf(true)

    /** Which bottom bar is drawn; swapped live from Settings. */
    private val dockedBarState = mutableStateOf(false)

    /** What the loading screen says it is waiting on. */
    private val loadingMessageState = mutableStateOf("Starting up…")

    /**
     * The blur/refraction numbers the glass bars are drawn with, as set in the Glass playground.
     * Held as state so the bars redraw with new values on the next frame; reloaded in [onResume]
     * because the playground is an Activity of its own, and coming back from it is the moment its
     * changes have to land.
     */
    private val glassEffectsState =
        mutableStateOf(com.elitedarkkaiser.redmagic.ui.GlassPrefs.DEFAULT)

    /**
     * The name of the page open over the tabs, or null when none is.
     *
     * Drives the bottom bar's back mode: while a page is open the bar stops being a tab row and
     * becomes the way out of it, carrying the page's name. Kept by
     * [PageHost][com.elitedarkkaiser.redmagic.ui.PageHost], which is what opens and closes them.
     */
    private val openPageTitleState = mutableStateOf<String?>(null)

    /**
     * NPatch-style (github.com/Gio470/NPatch, branch `miuix`) floating glass bottom bar, ported
     * to Compose/View interop in [com.elitedarkkaiser.redmagic.ui.glassbar.GlassBottomBar] since
     * the rest of this app is plain Android Views. Replaces the old hand-drawn pill nav row.
     */
    /**
     * Wraps the app's classic-View content in a Compose host so the glass bar can record it into
     * the backdrop layer it samples — the bar only refracts content that lives inside its own
     * Compose tree (see GlassBarScaffold).
     */
    private fun glassBarHost(contentView: View): View {
        val content = contentView
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                // Read only to subscribe this composition to it -- see appearanceVersion's own
                // doc for why a plain read (the value itself is unused) is what forces the colours
                // below to be recomputed after a Pure Black toggle.
                appearanceVersion.intValue
                val colors = GlassBottomBarColors(
                    background = ComposeColor(bgColor),
                    container = ComposeColor(panelColor),
                    // The vivid accent rather than the plain one: this is the colour that has to
                    // tell a selected tab from two unselected ones, and a tonal primary is too
                    // close to onSurface in a dark palette to do it. See M3.primaryVivid.
                    accent = ComposeColor(m3.primaryVivid),
                    onSurface = ComposeColor(textPrimary),
                    onSurfaceVariant = ComposeColor(textSecondary),
                    // Derived from the resolved surface rather than the night-mode flag, so it
                    // still holds when Material You recolours the theme from the wallpaper.
                    isLightTheme = MaterialColors.isColorLight(bgColor),
                    secondaryAccent = ComposeColor(m3.secondary),
                    tertiaryAccent = ComposeColor(m3.tertiary)
                )
                GlassBarScaffold(
                    contentView = content,
                    backgroundType = backgroundTypeState.value,
                    isBlurEnabled = com.elitedarkkaiser.redmagic.ui.glassbar.isGlassBlurSupported() &&
                        glassBlurEnabledState.value,
                    // Home is the only tab that shows loading placeholders.
                    isLoading = {
                        isInitialStatusLoad.value && bottomBarSelectedIndex.intValue == 0
                    },
                    expanded = { bottomBarExpanded.value },
                    onSettings = { switchTab(if (isOnSettingsTab.value) "home" else "settings") },
                    showHomeIcon = { isOnSettingsTab.value },
                    dockedBar = { dockedBarState.value },
                    loadingMessage = { loadingMessageState.value },
                    effects = { glassEffectsState.value },
                    backTitle = { openPageTitleState.value },
                    onBack = { com.elitedarkkaiser.redmagic.ui.PageHost.popTop(this@MainActivity) },
                    // One per entry in bottomBarTabs, in the same order: onSelected indexes
                    // straight into that list, so a bar with more tabs than the list has walks off
                    // the end of it.
                    tabs = listOf(
                        GlassBottomBarTab(
                            com.elitedarkkaiser.redmagic.ui.Icons.HOME, "Home"
                        ),
                        GlassBottomBarTab(
                            com.elitedarkkaiser.redmagic.ui.Icons.HARDWARE, "Hardware"
                        ),
                        // laptop-code: the nearest thing the set has to the screen-with-<\/> this
                        // tab was given, and now from the same font as every other icon rather
                        // than the one hand-authored vector it used to need.
                        GlassBottomBarTab(
                            com.elitedarkkaiser.redmagic.ui.Icons.SOFTWARE, "Software"
                        )
                    ),
                    selectedIndex = { bottomBarSelectedIndex.intValue },
                    onSelected = { index ->
                        bottomBarSelectedIndex.intValue = index
                        switchTab(bottomBarTabs[index])
                    },
                    colors = colors
                )
            }
        }
    }

    private fun updateManualCurveUiState() {
        setCurveControlsEnabled(!autoFanCurveEnabled)
    }

    /** See the use site in [refreshStatus]: the icon pack's system-side file, written once. */
    @Volatile
    private var ensuredIconPackSystemCopy = false

    private fun refreshStatus(scheduleNext: Boolean = false) {
        val generation = statusLoopGeneration
        Thread {
          try {
            // A live check every cycle, not just an OR against the cache — the cache alone would
            // latch "granted" forever the moment it was ever true, even after root is revoked in
            // Magisk/KernelSU later. Keeping the cache in sync (not only ever writing true) makes
            // it a fresh "last known" value again rather than a one-way ratchet.
            // Said out loud on the launch screen, because this is the call that blocks on the
            // superuser prompt: a first run can sit here for as long as the user takes to answer
            // it, and "Starting up" for half a minute reads as a hang.
            if (isInitialStatusLoad.value) {
                runOnUiThread { loadingMessageState.value = "Checking root access…" }
            }
            val rooted = RootShell.hasRoot()
            val isPro = isRedMagic10ProStorage(this)
            if (rooted && isPro) FreshInstallDefaults.initializeDeviceControls(this)
            // Once per launch, on the thread that is already holding a root shell open. The
            // Xposed module's system_server hook is skipped entirely when this file says no icon
            // pack is selected, and it can only say that if it exists -- so a device that never
            // touches the icon pack settings needs someone to write it anyway. See
            // IconPackSettings.ensureSystemCopy.
            if (rooted && !ensuredIconPackSystemCopy) {
                ensuredIconPackSystemCopy = true
                runCatching {
                    com.elitedarkkaiser.redmagic.xposed.IconPackSettings.ensureSystemCopy(this)
                }
            }
            setCachedRootAccessStorage(this, rooted)
            val compatModelSummary = deviceScanModelStorage(this)
            // The fan's enable node is not the master switch, and reading it as one is what made
            // the Cooling tab fight itself: setFanLevel(0) writes `0 > fan_enable` as well as the
            // level, so a fan sitting at level 0 -- a perfectly ordinary place for the slider, and
            // where the Quiet curve parks it below 95F -- reads back as "off". Taken as intent
            // that switched the master off, which folded the Fan section shut mid-drag and stood
            // auto mode down with it.
            //
            // So the node is only believed when the level is non-zero, which is the only time it
            // carries information. At level 0 the app's own stored intent stands.
            //
            // Noted before the read, not after: what matters is whether anything wrote to the fan
            // while this read was in the air, and a write that landed halfway through still leaves
            // a reading that describes the fan as it was before the tap.
            val fanReadStartedAt = android.os.SystemClock.uptimeMillis()
            val fan = HardwareController.readFanStatus()
            val fanEnabled = fan.enabled
            val fanLevel = fan.level ?: 0
            val fanIntent = if (fanLevel > 0) fanEnabled else fanPowerEnabled
            // Kept in sync rather than only ever written true, so the fan being switched off from
            // outside the app (Magic Key, another root tool) is picked up rather than latched past
            // -- but never from a reading a write has already overtaken, or the app persists the
            // state from before the user's own tap and restores it on the next launch.
            if (HardwareController.fanStateSettledSince(fanReadStartedAt)) {
                saveFanPowerEnabledStorage(this, fanIntent)
            }
            val rpmRaw = fan.rpm
            val tempF = fan.tempF
            runOnUiThread {
                val rpm = when {
                    rpmRaw == null -> lastDisplayedRpm.takeIf { it >= 0 }
                    lastDisplayedRpm < 0 -> rpmRaw
                    else -> ((lastDisplayedRpm * 0.7) + (rpmRaw * 0.3)).toInt()
                }

                if (rpm != null) lastDisplayedRpm = rpm

                val previousTempF = lastDisplayedTempF
                val tempTrend = when {
                    tempF == null || previousTempF == null -> ""
                    tempF > previousTempF + 1f -> " ↑"
                    tempF < previousTempF - 1f -> " ↓"
                    else -> " →"
                }
                if (tempF != null) lastDisplayedTempF = tempF

                homeStatsRefs?.let { refs ->
                    com.elitedarkkaiser.redmagic.ui.HomeTabUi.applyTemperature(
                        refs.tempGauge,
                        tempF,
                        if (tempF != null) {
                            TempFormat.formatDisplayTempFromF(tempF, useFahrenheit)
                        } else {
                            "--"
                        }
                    )
                    com.elitedarkkaiser.redmagic.ui.HomeTabUi.applyFan(
                        refs.fanGauge, fanLevel, rpm, fanIntent
                    )
                }

                // Checked again here rather than reused from the worker thread: a write can land
                // in the gap between the read finishing and this reaching the front of the main
                // thread's queue, and it is this assignment -- not the read -- that moves the
                // control. RPM and temperature above are not gated: they are readings in their own
                // right, not a control the app is about to write back to the hardware.
                if (HardwareController.fanStateSettledSince(fanReadStartedAt)) {
                    syncFanLevel(fanLevel)

                    if (::fanPowerSwitch.isInitialized) {
                        fanPowerEnabled = fanIntent
                        setFanPowerChecked(fanIntent)
                        applyFanEnabledState(fanIntent)
                    }
                }

                applyHeroState(rooted, isPro)
                updateDeviceCompatUi(isPro, compatModelSummary, rooted)
                setGateState(rooted, isPro)

                // Set after the values above are on screen, so the overlay only starts fading
                // once there is real content behind it to fade onto.
                isInitialStatusLoad.value = false
            }
          } finally {
            // From the end of the pass, so the interval is a gap between passes rather than a
            // schedule they can fall behind. Only for the loop's own pass, and only while it is
            // still the current loop.
            if (scheduleNext) {
                statusRefreshHandler.post {
                    if (generation == statusLoopGeneration) {
                        statusRefreshHandler.postDelayed(
                            statusRefreshRunnable, statusRefreshIntervalMs.toLong()
                        )
                    }
                }
            }
          }
        }.start()
    }

    /** Re-scans device model/root state on demand (Home tab's "Recheck compatibility" button). */
    private fun recheckDeviceCompatibility() {
        Thread {
            val report = DeviceCapabilityScanner.scan()
            saveDeviceCapabilityReportStorage(this, report)
            val rooted = RootShell.hasRoot()
            if (rooted && report.isRedMagic10Pro) FreshInstallDefaults.initializeDeviceControls(this)
            setCachedRootAccessStorage(this, rooted)

            runOnUiThread {
                updateDeviceCompatUi(report.isRedMagic10Pro, deviceScanModelStorage(this), rooted)
                applyHeroState(rooted, report.isRedMagic10Pro)
                setGateState(rooted, report.isRedMagic10Pro)
            }
        }.start()
    }

    private fun updateDeviceCompatUi(isPro: Boolean, modelSummary: String, rooted: Boolean) {
        // The strip decides how to show these -- one line when everything passes, the failures
        // named when it does not. The model string appears only when the model is the problem.
        val compat = homeStatsRefs?.compat ?: return
        compat.setDevice(isPro, if (isPro) "RedMagic 10 Pro" else modelSummary)
        compat.setRoot(rooted, if (rooted) "Root access" else "No root")
    }

    /**
     * Tracks whether hardware tabs should be gated, re-applying the gate immediately if root or
     * the RedMagic 10 Pro check is lost or regained while the user is already on a non-Home tab.
     */
    private fun setGateState(rooted: Boolean, isPro: Boolean) {
        val changed = rooted != rootAvailable || isPro != proAvailable
        val rootJustGranted = rooted && (!rootCheckedThisLaunch || !rootAvailable)
        rootCheckedThisLaunch = true
        rootAvailable = rooted
        proAvailable = isPro
        if (rootJustGranted) {
            refreshCpuStats()
            refreshMemoryStats()
        }
        if (changed && currentTab != "home") switchTab(currentTab)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
        }
    }

    private fun scrollTabContainer(): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addView(ScrollView(this@MainActivity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(inner)
            })

            tag = inner
        }.also { outer ->
            val scroll = outer.getChildAt(0) as ScrollView
            val child = scroll.getChildAt(0) as LinearLayout
            outer.removeAllViews()
            outer.addView(scroll.apply { removeAllViews(); addView(child) })
        }
    }

    private fun subtitleText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            setPadding(0, dp(8), 0, 0)
        }
    }

    /**
     * Home's Processor card, on its own poll.
     *
     * CPU load is null until two reads have happened -- it is a delta -- so the first pass after
     * launch shows "--" for usage while the rest of the card is already live.
     */
    private fun refreshCpuStats() {
        if (homeStatsRefs == null || !rootCheckedThisLaunch || !rootAvailable) return
        Thread {
            val cpu = SystemStats.readCpu()
            runOnUiThread {
                val refs = homeStatsRefs ?: return@runOnUiThread

                refs.cpuUsageValue.text = cpu.usagePercent?.let { "$it%" } ?: "--"
                // Only real readings: the first pass has no previous sample to difference against
                // and returns null, and feeding that in as a zero would draw a trough that never
                // happened.
                cpu.usagePercent?.let { refs.cpuSparkline.push(it / 100f) }
                refs.cpuDetail.text = listOfNotNull(
                    cpu.soc.takeIf { it != "Unknown" },
                    "${cpu.cores} cores",
                    cpu.arch,
                    cpu.governor
                ).joinToString(" · ")

                // Rebuilt rather than updated in place: the cluster count is fixed once the kernel
                // is up, but the first pass is what discovers it, so there is nothing to update on
                // that one.
                if (refs.cpuClusters.childCount != cpu.clusters.size) {
                    refs.cpuClusters.removeAllViews()
                    cpu.clusters.forEachIndexed { index, cluster ->
                        refs.cpuClusters.addView(
                            com.elitedarkkaiser.redmagic.ui.HomeTabUi.clusterRow(this, m3, index, cluster)
                        )
                    }
                } else {
                    cpu.clusters.forEachIndexed { index, cluster ->
                        val row = refs.cpuClusters.getChildAt(index) as? LinearLayout
                            ?: return@forEachIndexed
                        com.elitedarkkaiser.redmagic.ui.HomeTabUi.updateClusterRow(row, cluster)
                    }
                }
            }
        }.start()
    }

    /** Home's Memory card, on its own poll. */
    private fun refreshMemoryStats() {
        if (homeStatsRefs == null || !rootCheckedThisLaunch || !rootAvailable) return
        Thread {
            val memory = SystemStats.readMemory(this)
            runOnUiThread {
                val refs = homeStatsRefs ?: return@runOnUiThread

                refs.ramValue.text = "${memory.usedPercent}%"
                refs.ramBar.setPercent(memory.usedPercent)
                refs.ramDetail.text = "${SystemStats.formatBytes(memory.usedBytes)} of " +
                    "${SystemStats.formatBytes(memory.totalBytes)} used"

                refs.swapValue.text = if (memory.swapTotalBytes <= 0L) {
                    "Not configured"
                } else {
                    "${SystemStats.formatBytes(memory.swapUsedBytes)} / " +
                        "${SystemStats.formatBytes(memory.swapTotalBytes)} · " +
                        "${memory.swapUsedPercent}%"
                }
                refs.swapBar.setPercent(memory.swapUsedPercent)
            }
        }.start()
    }

    private fun bodyText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            setPadding(0, dp(8), 0, 0)
        }
    }

    /** A screen section: a card on the row surface, at the card corner and card padding. */
    private fun sectionPanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    /**
     * Shows or hides Home's readings as root and device compatibility change.
     *
     * Temperature and fan speed are read from sysfs nodes that need root, and are specific to this
     * phone -- without both there is nothing behind them, so they go rather than sit there reading
     * "--". The strip above them colours itself from the same facts; it is told them by
     * [updateDeviceCompatUi] rather than here, so there is one path in.
     */
    private fun applyHeroState(rooted: Boolean, isPro: Boolean) {
        homeStatsRefs?.let { refs ->
            refs.vitals.visibility = if (rooted && isPro) View.VISIBLE else View.GONE
            refs.processor.visibility = if (rooted) View.VISIBLE else View.GONE
            refs.memory.visibility = if (rooted) View.VISIBLE else View.GONE
        }
    }

    private fun subtleLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
            setPadding(0, dp(8), 0, 0)
        }
    }

    private fun segmentedChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): Button {
        // M3 segmented button: selected reads as the secondary container, unselected as an
        // outline in the outline role, labelLarge either way.
        return Button(this).apply {
            text = label
            setAllCaps(false)
            m3.styleText(
                this,
                M3.Type.labelLarge,
                if (selected) m3.onSecondaryContainer else m3.onSurface
            )
            background = if (selected) {
                m3.filled(m3.secondaryContainer, M3.Shape.full, m3.onSecondaryContainer)
            } else {
                m3.outlined(M3.Shape.full, m3.outline, m3.onSurface)
            }
            stateListAnimator = null
            minHeight = dp(M3.Metrics.touchTarget)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
    }

    /**
     * M3 Expressive filled tonal button at the medium size. Two of these share a row, so a long
     * label can be squeezed narrower than it wants; the button ellipsizes rather than wrapping.
     */
    private fun actionButton(text: String, isDanger: Boolean = false, onClick: () -> Unit): Button =
        m3.button(
            text,
            if (isDanger) M3.ButtonKind.Danger else M3.ButtonKind.Tonal,
            M3.ButtonSize.Medium,
            onClick
        )

    private fun row(left: Button, right: Button): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            left.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
            right.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }

            addView(left)
            addView(right)
        }
    }

    private fun singleRow(button: Button): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(button)
        }
    }

    private fun m3Radius(radiusDp: Int): Int = when {
        radiusDp >= 999 -> M3.Shape.full
        radiusDp >= 24 -> M3.Shape.extraLarge
        radiusDp >= 14 -> M3.Shape.largeIncreased
        radiusDp >= 8 -> M3.Shape.large
        else -> M3.Shape.medium
    }

    private fun roundedBg(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return com.elitedarkkaiser.redmagic.ui.BackdropFill().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(m3Radius(radiusDp)).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun roundedFill(fill: Int, radiusDp: Int): GradientDrawable {
        return com.elitedarkkaiser.redmagic.ui.BackdropFill().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(m3Radius(radiusDp)).toFloat()
            setColor(fill)
        }
    }

    private fun space(width: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    private fun spacer(height: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showGamePickerDialog() {
        showGamePickerDialogUI(this) {
            gameModeAppsTextRef?.text = gameModeAppsSummaryStorage(this)
        }
    }

    private fun updateGameModeStatusUI(textView: TextView) {
        textView.text = getGameModeStatusTextStorage(this)
    }

}
