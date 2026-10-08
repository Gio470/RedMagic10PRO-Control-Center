package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.DisplayDensity
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/**
 * The display density card, modelled on Android Screener's resolution screen and drawn in this
 * app's own M3 Expressive style.
 *
 * Applying is a two-step commit: the new density goes in, then a countdown offers to keep it. That
 * is not ceremony — a density the screen cannot really show leaves the user unable to find a button
 * to undo it, so the undo has to be the default outcome rather than something they must reach.
 */
object DisplayDensityUi {

    /** Long enough to judge the change, short enough to be bearable when it went badly. */
    private const val CONFIRM_SECONDS = 12

    fun card(activity: Activity, deps: HardwareTabDeps): LinearLayout {
        val m3 = M3(activity)

        // The master switch stands for "use a density of my own": off puts the panel's own back
        // and folds the controls away. It is synced from the device only on the first read -- after
        // that it is the user's stated intent, so turning it on and not yet applying a value must
        // not be undone by the next refresh finding no override in place.
        lateinit var card: SwitchSection
        var syncedFromDevice = false
        var settingSwitch = false
        // The last reading, so switching off can tell "put the panel's own density back" apart from
        // "there was never an override to put back".
        var state = DisplayDensity.State(physical = 0, current = 0)

        val masterSwitch = MaterialSwitch(activity).apply {
            setOnCheckedChangeListener { _, checked ->
                if (settingSwitch) return@setOnCheckedChangeListener
                com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(activity, "density")
                card.setExpanded(checked)
                // Only when there is actually an override in force. Resetting and recreating
                // regardless meant switching off with the panel already at its own density threw
                // the whole activity away to change nothing.
                if (!checked && state.isOverridden) {
                    Thread {
                        DisplayDensity.reset()
                        // The activity declares density changes as handled, so nothing else would
                        // redraw the UI at the size it is now.
                        activity.runOnUiThread { activity.recreate() }
                    }.start()
                }
            }
        }

        card = m3.sectionWithSwitch(
            "Display density", masterSwitch,
            glyph = Icons.DENSITY,
            supporting = "How large everything on screen is drawn"
        )
        // Starts closed: whether a density override is in force isn't known until the read below
        // lands, and opening only to fold shut again reads worse than the other way round.
        card.setExpanded(false, animate = false)

        val physicalValue = TextView(activity)
        val currentValue = TextView(activity)
        lateinit var slider: ComposeSlider
        lateinit var chosenLabel: TextView

        // Range spans a useful spread either side of the panel's own density. Anchored to it
        // rather than fixed numbers, since what counts as small differs per panel. Continuous
        // (no steps): a span this wide would put a tick roughly every pixel of track.
        fun minDpi() = (state.physical * 0.5f).roundToInt().coerceAtLeast(80)
        fun maxDpi() = (state.physical * 1.3f).roundToInt().coerceAtLeast(minDpi() + 1)

        fun chosenDpi() = slider.value.roundToInt()

        fun renderChosen() {
            val dpi = chosenDpi()
            val percent = if (state.physical > 0) dpi * 100 / state.physical else 100
            chosenLabel.text = "$dpi dpi  ·  $percent%"
        }

        fun renderState() {
            physicalValue.text = "${state.physical} dpi"
            currentValue.text = if (state.isOverridden) {
                "${state.current} dpi (changed)"
            } else {
                "${state.current} dpi (default)"
            }
            slider.valueRange = minDpi().toFloat()..maxDpi().toFloat()
            slider.value = state.current.toFloat()
            renderChosen()
        }

        card.addView(m3.block().apply {
            addView(valueRow(activity, m3, "Panel density", physicalValue))
            addView(valueRow(activity, m3, "Now showing", currentValue))
        })

        card.addView(m3.block("Density").apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = "Selected"
                    m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                chosenLabel = TextView(activity).apply {
                    m3.styleText(this, M3.Type.labelLarge, m3.onSurface)
                }
                addView(chosenLabel)
            })

            slider = ComposeSlider(
                context = activity,
                initialValue = 0f,
                initialValueRange = 0f..1f,
                onValueRendered = { renderChosen() }
            )
            addView(slider.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(4) })

        })

        fun refresh(onDone: (() -> Unit)? = null) {
            Thread {
                val read = DisplayDensity.read()
                activity.runOnUiThread {
                    if (read != null) {
                        state = read
                        renderState()
                        if (!syncedFromDevice) {
                            syncedFromDevice = true
                            settingSwitch = true
                            masterSwitch.isChecked = read.isOverridden
                            settingSwitch = false
                            card.setExpanded(read.isOverridden, animate = false)
                        }
                    } else {
                        physicalValue.text = "unavailable"
                        currentValue.text = "needs root"
                    }
                    onDone?.invoke()
                }
            }.start()
        }

        // A plain guard rather than a disabled button: the action is a row now, and a row has no
        // enabled state to lean on.
        var applying = false

        fun applyDensity() {
            if (applying || state.physical == 0) return
            val target = chosenDpi()
            if (target == state.current) {
                Toast.makeText(activity, "Already at $target dpi", Toast.LENGTH_SHORT).show()
                return
            }
            val previous = state
            applying = true
            DisplayDensity.confirmInProgress = true
            Thread {
                val ok = DisplayDensity.apply(activity, target, previous, CONFIRM_SECONDS)
                activity.runOnUiThread {
                    applying = false
                    if (!ok) {
                        DisplayDensity.confirmInProgress = false
                        Toast.makeText(activity, "Could not change density", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    showConfirmation(activity, previous) { refresh() }
                }
            }.start()
        }

        card.addView(m3.row(
            title = "Apply density",
            supporting = "Asks to keep it, and puts it back if you don't answer",
            glyph = Icons.CHECK,
            onClick = { applyDensity() }
        ))
        refresh()
        return card
    }

    /**
     * Offers to keep the change, and undoes it if the user does not answer. The countdown here
     * mirrors the one already armed in a detached root shell; this dialog is the pleasant path,
     * that watchdog is the one that still works if this app is gone.
     */
    private fun showConfirmation(
        activity: Activity,
        previous: DisplayDensity.State,
        onSettled: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = CONFIRM_SECONDS - 2
        var settled = false

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel
        lateinit var undoButton: Button

        fun settle(keep: Boolean) {
            if (settled) return
            settled = true
            handler.removeCallbacksAndMessages(null)
            Thread {
                if (keep) DisplayDensity.keep() else DisplayDensity.revert(previous)
                activity.runOnUiThread {
                    DisplayDensity.confirmInProgress = false
                    runCatching { dialog.dismiss() }
                    onSettled()
                    // Rebuild at the density now in force: the activity declares density changes
                    // as handled, so nothing else would resize the UI it drew at the old scale.
                    if (keep) activity.recreate()
                }
            }.start()
        }

        val keepButton = M3Dialog.filledButton(activity, "Keep") { settle(keep = true) }
        undoButton = M3Dialog.textButton(activity, "Undo (${remaining}s)") { settle(keep = false) }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, "Keep this density?"))
            addView(M3Dialog.body(
                activity,
                "If you don't confirm, it goes back on its own."
            ))
            addView(M3Dialog.buttonRow(activity, undoButton, keepButton))
        }

        dialog = M3Dialog.show(activity, panel, cancelable = false)

        val tick = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    settle(keep = false)
                    return
                }
                undoButton.text = "Undo (${remaining}s)"
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(tick, 1000)
    }

    private fun valueRow(activity: Activity, m3: M3, label: String, value: TextView): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, m3.dp(4), 0, m3.dp(4))
            addView(TextView(activity).apply {
                text = label
                m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            value.apply { m3.styleText(this, M3.Type.bodyLarge, m3.onSurfaceVariant) }
            addView(value)
        }
}
