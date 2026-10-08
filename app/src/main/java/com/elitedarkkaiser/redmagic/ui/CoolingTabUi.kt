package com.elitedarkkaiser.redmagic.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.HardwareController
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

object CoolingTabUi {
    /**
     * How long a drag has to settle before the level is written. Long enough that a sweep across
     * the track writes once rather than six times, short enough that holding at a level still spins
     * the fan up while you are looking at it.
     */
    private const val DRAG_WRITE_DEBOUNCE_MS = 180L

    /** How long after the user last touched the slider a status pass may move it again. */
    private const val FAN_TOUCH_SETTLE_MS = 1_500L

    /** How often the background status poll may be told to run: a small enough range of steps
     *  that M3's per-step tick marks are the right affordance, same as the fan-level slider. */

    data class Refs(
        val curveStatusText: TextView,
        val fanSeek: ComposeSlider,
        val autoCurveCheck: CompoundButton,
        val fanPowerSwitch: CompoundButton,
        /**
         * Move the switches to match state read back from the device, WITHOUT re-running what a
         * tap on them does.
         *
         * Assigning isChecked fires the change listener exactly as a tap does, and these listeners
         * write the hardware and then ask for a status refresh -- so a plain assignment from inside
         * a refresh feeds straight back into another one.
         */
        val setFanPowerChecked: (Boolean) -> Unit,
        val setAutoCurveChecked: (Boolean) -> Unit,
        /**
         * Move the fan level slider to what the fan is actually running at.
         *
         * The slider was built at 0 and only ever moved by a drag or a curve, so opening the app
         * with the fan at level 3 showed a slider at 0 and a chip reading "0/5" -- the UI stating
         * the opposite of what the hardware was doing.
         */
        val syncFanLevel: (Int) -> Unit,
        /** Restyles the Quiet/Balanced/Turbo chips to show which curve is active. */
        val refreshCurveSelection: (String) -> Unit,
        /** Dims and disables the curve chips while auto mode owns the fan curve. */
        val setCurveControlsEnabled: (Boolean) -> Unit,
        /** Grays out everything the master fan switch governs — level slider and fan curve —
         *  without touching the switch itself. */
        val applyFanEnabledState: (Boolean) -> Unit,
        val smartPumpStatusView: TextView,
        val smartPumpSpeedView: TextView
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    fun create(deps: CoolingTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)

        lateinit var curveStatusText: TextView
        lateinit var autoCurveCheck: MaterialSwitch
        // Assigned once fanLevelBlock/curveCard exist further down, but the fan-power switch's
        // listener (built earlier) needs to call into it — same forward reference the rest of this
        // function already relies on for curveStatusText/autoCurveCheck.
        lateinit var applyFanEnabledState: (Boolean) -> Unit

        // False until the Fan section's initial state is set, so that first fold isn't animated.
        var fanSectionBuilt = false

        // True between the first movement of a drag and its end, so a status pass landing in the
        // middle of one doesn't yank the thumb out from under the finger holding it.
        var draggingFanLevel = false
        // When the user last moved it. A status pass that had already read the hardware before the
        // drag began is still carrying the old level, and it lands after -- the flag above is
        // already false by then, so only "did the user touch this a moment ago?" catches it.
        var lastFanTouchAt = 0L

        // Set while a switch is being moved to match the device rather than by a tap. Without it
        // the listener treats the app's own correction as a fresh instruction and writes it back.
        var settingSwitches = false

        // Live "N/5" readout next to the Fan level label — an M3 discrete slider always pairs
        // its stops with a value, otherwise the tick marks alone don't say what they mean.
        val fanLevelValueChip = TextView(context).apply {
            m3.styleText(this, M3.Type.labelLarge, m3.onSecondaryContainer)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = m3.surfaceShape(m3.secondaryContainer, M3.Shape.full)
            minWidth = m3.dp(40)
            setPadding(m3.dp(12), m3.dp(4), m3.dp(12), m3.dp(4))
        }

        fun setFanLevelValueChip(level: Int) {
            fanLevelValueChip.text = "$level/5"
        }

        // Fan level writes, debounced onto one worker.
        //
        // onValueChange fires on every pointer move, and each one used to spawn its own Thread
        // forking an `su`. A single drag across the track forked dozens of root shells all racing
        // each other, so the level that stuck was whichever landed last rather than the one asked
        // for -- and the drag stuttered while they piled up. Now a move only schedules a write, and
        // scheduling again cancels the one before it, so a sweep writes once where the finger
        // settles and a slow drag writes as it pauses.
        val fanWriteHandler = android.os.Handler(context.mainLooper)
        var pendingFanWrite: Runnable? = null
        // Where the drag last was. onValueChangeFinished is an argument to the slider's own
        // constructor, so it cannot read the slider back; this carries the value across instead.
        var draggedFanLevel = 0

        fun writeFanLevel(level: Int, delayMs: Long, thenRefresh: Boolean) {
            pendingFanWrite?.let { fanWriteHandler.removeCallbacks(it) }
            val write = Runnable {
                Thread {
                    HardwareController.setFanLevel(level)
                    if (thenRefresh) {
                        // After the write, never beside it: a status pass that overtakes it reads
                        // the old level and -- since it syncs the slider now -- snaps the thumb
                        // back to it. The flag stays set across the whole round trip for the same
                        // reason, so nothing can move the slider until the hardware agrees with it.
                        deps.refreshStatus()
                        fanWriteHandler.post {
                            lastFanTouchAt = android.os.SystemClock.uptimeMillis()
                            draggingFanLevel = false
                        }
                    }
                }.start()
            }
            pendingFanWrite = write
            fanWriteHandler.postDelayed(write, delayMs)
        }

        val fanSeek = ComposeSlider(
            context = context,
            initialValue = 0f,
            initialValueRange = 0f..5f,
            initialSteps = 4,
            onValueChange = { raw ->
                draggingFanLevel = true
                lastFanTouchAt = android.os.SystemClock.uptimeMillis()
                draggedFanLevel = raw.roundToInt()
                if (!deps.getAutoFanCurveEnabled()) {
                    writeFanLevel(draggedFanLevel, DRAG_WRITE_DEBOUNCE_MS, thenRefresh = false)
                }
            },
            onValueChangeFinished = {
                if (deps.getAutoFanCurveEnabled()) {
                    draggingFanLevel = false
                } else {
                    // Straight away, and this is the one that also refreshes: whatever the debounce
                    // was still holding is cancelled in favour of where the finger actually left it.
                    writeFanLevel(draggedFanLevel, 0L, thenRefresh = true)
                }
            },
            onValueRendered = { raw -> setFanLevelValueChip(raw.roundToInt()) }
        )

        val fanLevelEndCaptions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, m3.dp(4))
            addView(TextView(context).apply {
                text = "Off"
                m3.styleText(this, M3.Type.labelMedium, m3.onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "Max"
                m3.styleText(this, M3.Type.labelMedium, m3.onSurfaceVariant)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        // ---- Fan curve buttons: one connected button group, Material 3 Expressive's
        // single-select group, in place of the three separate chips this row used to build. The
        // group owns the selection itself, so the old restyle-every-chip-after-a-tap step is gone
        // -- refreshCurveSelection just tells it which curve is current.
        val curves = listOf("Quiet" to "quiet", "Balanced" to "balanced", "Turbo" to "turbo")
        lateinit var curveGroup: ValueButtonGroup<String>

        fun refreshCurveSelection(selected: String) {
            curveGroup.value = selected
        }

        fun selectCurve(curve: String, label: String) {
            if (deps.getAutoFanCurveEnabled()) return
            deps.setSelectedCurve(curve)
            deps.setSelectedCurveSaved(curve)
            curveStatusText.text = "$label — applied immediately"
            refreshCurveSelection(curve)
            // applyFanCurve reads the temperature and writes the level, both root shells. On the
            // calling (main) thread that blocked the tap; and the refresh has to follow the write
            // rather than race it, same as the fan switch.
            Thread {
                val level = HardwareController.applyFanCurve(curve)
                context.mainLooper.let { looper ->
                    android.os.Handler(looper).post {
                        if (level != null) fanSeek.value = level.toFloat()
                    }
                }
                deps.refreshStatus()
            }.start()
        }

        curveGroup = ValueButtonGroup(context, curves, deps.getSelectedCurve()) { curve ->
            selectCurve(curve, curves.first { it.second == curve }.first)
        }

        // Remembered so a repeat is a no-op: assigning isEnabled on either of these is a Compose
        // recomposition, and the status refresh calls this on every pass.
        var curveControlsEnabled: Boolean? = null

        fun setCurveControlsEnabled(enabled: Boolean) {
            if (curveControlsEnabled == enabled) return
            curveControlsEnabled = enabled
            // Both are M3 Compose components, which draw their own disabled state (content at
            // 38%) once told they are disabled -- dimming the view as well would stack a second
            // 38% on top and leave them barely there.
            curveGroup.isEnabled = enabled
            // Auto mode drives the fan level itself — the slider previously stayed fully
            // interactive-looking even though dragging it did nothing while auto was on.
            fanSeek.isEnabled = enabled
        }

        val modeRow = curveGroup.view

        curveStatusText = TextView(context).apply {
            text = curveStatusFor(deps.getSelectedCurve(), deps.getAutoFanCurveEnabled())
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            setPadding(0, m3.dp(M3.Space.xxs), 0, m3.dp(M3.Space.md))
        }

        autoCurveCheck = MaterialSwitch(context).apply {
            isChecked = deps.getAutoFanCurveEnabled()
            setOnCheckedChangeListener { _, checked ->
                if (settingSwitches) return@setOnCheckedChangeListener
                deps.setAutoFanCurveEnabled(checked)
                deps.setAutoFanEnabledSaved(checked)
                deps.updateManualCurveUiState()

                if (checked) {
                    deps.startAutoFanService()
                } else {
                    deps.stopAutoFanService()
                }
                curveStatusText.text = curveStatusFor(deps.getSelectedCurve(), checked)
                deps.refreshStatus()
            }
        }

        // ---- Fan power: the master switch over everything on this tab that only means anything
        // while the fan runs -- the level slider and the whole fan curve -- which the Fan section
        // folds away when it is off.
        val fanPowerSwitch = MaterialSwitch(context).apply {
            isChecked = deps.getFanPowerEnabled()
            setOnCheckedChangeListener { _, checked ->
                if (settingSwitches) return@setOnCheckedChangeListener
                com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(context, "fan")
                deps.setFanPowerEnabled(checked)
                deps.saveFanPowerEnabled(checked)
                if (!checked && autoCurveCheck.isChecked) {
                    // Toggling this fires autoCurveCheck's own listener, which is the one place
                    // that already knows how to turn auto mode off correctly (persist it, stop
                    // the service, update the status text).
                    //
                    // It has to happen: auto mode's service sets the fan level on its own
                    // temperature-driven schedule, and setFanLevel turns the fan back on -- so
                    // left running it would undo this switch within a cycle.
                    autoCurveCheck.isChecked = false
                }
                applyFanEnabledState(checked)
                // Same reasoning as the fan level slider: a root shell call blocks, and this is a
                // tap on the switch itself -- left synchronous, the switch's own toggle animation
                // stalled until the round trip landed.
                //
                // The refresh has to wait for that write, not merely be started alongside it: a
                // status pass that overtakes it reads the fan's old state and moves the switch
                // back, which is a second instruction to the hardware and the start of a loop.
                Thread {
                    HardwareController.enableFan(checked)
                    deps.refreshStatus()
                }.start()
            }
        }

        val fanLevelHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(deps.subtleLabel("Fan level"), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(fanLevelValueChip)
        }

        // Captured rather than inline: applyFanEnabledState grays this out on its own, separately
        // from refreshIntervalBlock below it, which stays interactive regardless of fan power —
        // it governs the background status poll generally, not anything specific to the fan.
        val fanLevelBlock = m3.block().apply {
            addView(fanLevelHeaderRow)
            addView(fanSeek.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(fanLevelEndCaptions)
        }

        val fanCard = m3.sectionWithSwitch(
            "Fan", fanPowerSwitch,
            glyph = Icons.FAN,
            supporting = "Fan speed, and the curve it follows"
        ).apply {
            addView(fanLevelBlock)
        }

        // ---- Fan curve: inside the Fan section, since none of it does anything with the fan off
        // and the section is what folds it away.
        val curveCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(m3.row(
                title = "Automatic fan control",
                supporting = "Ramps the fan by temperature instead of a fixed curve",
                glyph = Icons.AUTO_FAN,
                trailing = autoCurveCheck
            ))
            addView(m3.block("Preset curves").apply {
                addView(curveStatusText)
                addView(modeRow)
            })
        }

        // Master gate for everything the fan-power switch governs: the level slider and the
        // whole Fan curve card, since neither does anything with the fan off. Auto mode is
        // stood down rather than merely hidden when the fan is switched off — otherwise its
        // background service would keep re-enabling the fan on its own temperature-driven
        // schedule, silently undoing the switch (the same reason the Pump section's own power
        // switch below also turns off auto pump rather than leaving it running underneath).
        // Purely a render, with no side effects of its own: the status refresh calls this on
        // every pass, and standing auto mode down from here meant a passive sync could quietly
        // switch a service off -- and, since that fires autoCurveCheck's listener, ask for yet
        // another refresh from inside one. Turning auto off belongs to the fan switch's own
        // listener, which is the only place the fan is actually being switched off.
        applyFanEnabledState = { fanOn ->
            fanCard.setExpanded(fanOn, animate = fanSectionBuilt)
            setCurveControlsEnabled(fanOn && !deps.getAutoFanCurveEnabled())
        }
        fanCard.addView(curveCard)
        applyFanEnabledState(deps.getFanPowerEnabled())
        fanSectionBuilt = true

        // ---- Pump: built for state continuity, not shown — RM 10 Pro has no liquid pump ----
        lateinit var smartPumpStatusView: TextView
        lateinit var smartPumpSpeedView: TextView

        m3.section("Pump").apply {

            lateinit var pumpPowerSwitch: MaterialSwitch
            lateinit var autoPumpSwitch: MaterialSwitch

            fun manualSpeedLabel(): String =
                deps.getPumpProfile().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            fun manualSpeedValue(): Int = when (deps.getPumpProfile().lowercase()) {
                "slow" -> 40
                "medium" -> 60
                "quick" -> 80
                "experimental" -> 90
                else -> 80
            }

            fun refreshPumpDiagnostics() {
                if (deps.getAutoPumpEnabled()) {
                    val status = deps.buildAutoPumpStatusText()
                    smartPumpStatusView.text = status.first
                    smartPumpSpeedView.text = status.second
                } else {
                    smartPumpStatusView.text = if (deps.getPumpEnabled()) {
                        "Pump mode: manual — ${manualSpeedLabel()}"
                    } else {
                        "Pump mode: off"
                    }
                    smartPumpSpeedView.text = if (deps.getPumpEnabled()) {
                        "Speed ${manualSpeedValue()} • Freq 4"
                    } else {
                        "Speed 0 • Freq 0"
                    }
                }
            }

            pumpPowerSwitch = MaterialSwitch(context).apply {
                isChecked = deps.getPumpEnabled()
                setOnCheckedChangeListener { _, checked ->
                    deps.setPumpEnabled(checked)
                    deps.savePumpState()
                    if (checked) {
                        HardwareController.setPumpProfile(deps.getPumpProfile())
                    } else {
                        deps.setAutoPumpEnabled(false)
                        deps.saveAutoPumpState()
                        deps.stopAutoPumpService()
                        HardwareController.enablePump(false)
                    }
                    autoPumpSwitch.isChecked = deps.getAutoPumpEnabled()
                    deps.refreshStatus()
                    refreshPumpDiagnostics()
                    deps.refreshSmartPumpStatusViews()
                }
            }

            autoPumpSwitch = MaterialSwitch(context).apply {
                isChecked = deps.getAutoPumpEnabled()
                setOnCheckedChangeListener { _, checked ->
                    deps.setAutoPumpEnabled(checked)
                    deps.saveAutoPumpState()
                    if (checked) {
                        deps.setPumpEnabled(true)
                        deps.savePumpState()
                        HardwareController.setPumpProfile(deps.getPumpProfile())
                        deps.startAutoPumpService()
                    } else {
                        deps.stopAutoPumpService()
                    }
                    pumpPowerSwitch.isChecked = deps.getPumpEnabled()
                    pumpPowerSwitch.isEnabled = !checked
                    refreshPumpDiagnostics()
                    deps.refreshSmartPumpStatusViews()
                }
            }

            smartPumpStatusView = TextView(context)
            smartPumpSpeedView = TextView(context)
            pumpPowerSwitch.isEnabled = !deps.getAutoPumpEnabled()
            refreshPumpDiagnostics()
        }

        container.addView(fanCard)
        // Pump card intentionally not attached: RM 10 Pro has no liquid cooling pump. Built above
        // purely so smartPumpStatusView/smartPumpSpeedView stay valid for the shared status refresh.

        // Join each run of rows into one connected block.

        m3.connectRows(container)

        m3.tintWidgets(container)


        return Result(
            view = container,
            refs = Refs(
                curveStatusText = curveStatusText,
                fanSeek = fanSeek,
                autoCurveCheck = autoCurveCheck,
                fanPowerSwitch = fanPowerSwitch,
                setFanPowerChecked = { checked ->
                    if (fanPowerSwitch.isChecked != checked) {
                        settingSwitches = true
                        fanPowerSwitch.isChecked = checked
                        settingSwitches = false
                    }
                },
                setAutoCurveChecked = { checked ->
                    if (autoCurveCheck.isChecked != checked) {
                        settingSwitches = true
                        autoCurveCheck.isChecked = checked
                        settingSwitches = false
                    }
                },
                syncFanLevel = { level ->
                    // Only when it actually moved -- assigning value recomposes the slider, and
                    // this runs on every status pass -- and only when the user has left it alone
                    // long enough that no pass can still be carrying a pre-drag reading.
                    val target = level.toFloat().coerceIn(0f, 5f)
                    val settled = android.os.SystemClock.uptimeMillis() - lastFanTouchAt >
                        FAN_TOUCH_SETTLE_MS
                    if (!draggingFanLevel && settled && fanSeek.value.roundToInt() != target.roundToInt()) {
                        fanSeek.value = target
                    }
                },
                refreshCurveSelection = ::refreshCurveSelection,
                setCurveControlsEnabled = ::setCurveControlsEnabled,
                applyFanEnabledState = applyFanEnabledState,
                smartPumpStatusView = smartPumpStatusView,
                smartPumpSpeedView = smartPumpSpeedView
            )
        )
    }

    private fun curveStatusFor(curve: String, auto: Boolean): String {
        val label = curve.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (auto) "Auto mode active — running in the background" else "$label — manual control"
    }

}
