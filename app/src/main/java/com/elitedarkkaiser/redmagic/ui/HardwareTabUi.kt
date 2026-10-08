package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.HapticStrength
import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.HardwareProfile
import com.elitedarkkaiser.redmagic.MasterSwitches
import com.elitedarkkaiser.redmagic.ProfileDialogs
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/**
 * Hardware: the Magic Key, the shoulder triggers and their haptics, and display density.
 *
 * Laid out as four sections in the order you'd reach for them, each gated by a master switch on
 * its own card -- glyph, name and what it governs in a line -- and each answering "what is this set
 * to right now?" before offering anything to change:
 *
 *  - the Magic Key's actions as a single-select list -- every action in one run of rows with a tick
 *    against the live one, rather than the old flat list where nothing marked the current choice
 *    and a separate "Current:" caption above was the only way to tell,
 *  - triggers and haptics as their own sections, each on/off on a switch like the rest of the app
 *    (the trigger service used to be a row you toggled by tapping it),
 *  - and display density.
 */
object HardwareTabUi {
    data class Refs(
        val magicKeyStatusLabel: TextView
    )

    data class Result(
        val view: LinearLayout,
        val refs: Refs
    )

    /**
     * One Magic Key action. [mode] is the label MagicKeyActions.readModeLabel reads back off the
     * device, which is what marks a row as the live one; [apply] is null for picking an app.
     */
    private data class MagicKeyAction(
        val title: String,
        val mode: String,
        val glyph: String,
        val apply: (() -> Boolean)? = null
    )

    private val MAGIC_KEY_ACTIONS = listOf(
        MagicKeyAction("Camera", "Camera", Icons.CAMERA) {
            HardwareController.setSliderOpenCamera()
        },
        MagicKeyAction("Game Space", "GameSpace", Icons.GAME_SPACE) {
            HardwareController.setSliderOpenGameSpace()
        },
        MagicKeyAction("Sound mode", "Sound Mode", Icons.SOUND_MODE) {
            HardwareController.setSliderSoundMode()
        },
        MagicKeyAction("Flashlight", "Flashlight", Icons.FLASHLIGHT) {
            HardwareController.setSliderFlashlight()
        },
        MagicKeyAction("Voice recorder", "Voice Recorder", Icons.RECORDER) {
            HardwareController.setSliderVoiceRecorder()
        },
        MagicKeyAction("Launch an app", "Launch App", Icons.LAUNCH_APP)
    )

    fun create(activity: Activity, deps: HardwareTabDeps): Result {
        val container = deps.scrollTabContainer()
        val m3 = M3(activity)

        // ---- Magic Key ----------------------------------------------------------------------
        //
        // MagicKeyActions and the app picker both report what they applied by writing "Current: X"
        // into a TextView, and the picker also writes the chosen app's name into a Button. Both are
        // kept as plain carriers here -- never added to the screen -- with a watcher on the TextView
        // driving render(). That makes one place responsible for what the tab shows: whatever a hook
        // or the picker settled on, read back through the same path.
        val magicKeyStatusLabel = TextView(activity)
        val magicKeyAppCarrier = Button(activity)

        val magicKeyAppLine = TextView(activity)
        val actionTicks = mutableMapOf<String, TextView>()
        var currentMode = ""

        // Declared ahead of render(), which drives them, but built below where the section is.
        var magicKeySwitchRef: MaterialSwitch? = null
        var magicKeySectionRef: SwitchSection? = null
        // Set while render() is moving the switch to match the device, so the listener doesn't
        // read its own update back as a user toggle and re-apply it.
        var renderingMagicKeySwitch = false
        // The first render is the device read landing, not a change the user made.
        var magicKeyRendered = false

        fun savedAppLabel(): String? {
            val pkg = deps.savedMagicKeyAppPackage()
            return if (pkg.isNullOrBlank()) null else deps.resolveMagicKeyAppLabel(pkg)
        }

        /**
         * Redraws the master switch and the ticks from [mode], the label the device last reported.
         * The ticked row is what says which action is live -- there is no separate readout.
         */
        fun render(mode: String) {
            currentMode = mode
            magicKeyAppLine.text = savedAppLabel() ?: "Choose an app"

            // "On" is simply "the key is not disabled". Set without firing the listener, or
            // rendering the result of a change would apply that change a second time.
            val on = mode.isNotEmpty() && mode != "Disabled"
            if (on) MasterSwitches.setLastMagicKeyMode(activity, mode)
            renderingMagicKeySwitch = true
            magicKeySwitchRef?.isChecked = on
            renderingMagicKeySwitch = false
            magicKeySectionRef?.setExpanded(on, animate = magicKeyRendered)

            actionTicks.forEach { (mode_, tick) ->
                // Kept in the layout at zero alpha rather than hidden, so the titles beside them
                // don't shift as the selection moves down the list.
                tick.alpha = if (mode_ == mode) 1f else 0f
            }
            magicKeyRendered = true
        }

        magicKeyStatusLabel.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                render(s?.toString()?.substringAfter("Current: ").orEmpty())
            }

            override fun beforeTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(c: CharSequence?, a: Int, b: Int, d: Int) {}
        })

        // Off the main thread: reading the mode is a root shell round trip, and on the main thread
        // it held up the tab's first frame.
        Thread {
            val mode = deps.readMagicKeyModeLabel()
            activity.runOnUiThread { magicKeyStatusLabel.text = "Current: $mode" }
        }.start()

        val magicKeySwitch = MaterialSwitch(activity).apply {
            setOnCheckedChangeListener { _, checked ->
                if (renderingMagicKeySwitch) return@setOnCheckedChangeListener
                com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(activity, "magic_key")
                if (checked) {
                    // Back to whatever it last did. The device has one setting for both the action
                    // and "off", so switching off would otherwise lose the action entirely.
                    val mode = MasterSwitches.lastMagicKeyMode(activity)
                    val action = MAGIC_KEY_ACTIONS.firstOrNull { it.mode == mode }
                    if (action?.apply != null) {
                        deps.applyStockMagicKeyMode(
                            action.mode, action.apply, magicKeyStatusLabel, magicKeyAppCarrier
                        )
                    } else {
                        deps.showMagicKeyAppPicker(magicKeyAppCarrier)
                    }
                } else {
                    deps.disableMagicKeyMode(magicKeyStatusLabel, magicKeyAppCarrier)
                }
            }
        }

        val magicKeyCard = m3.sectionWithSwitch(
            "Magic Key", magicKeySwitch,
            glyph = Icons.MAGIC_KEY,
            supporting = "What the slider switch on the side of the phone does"
        ).apply {

            MAGIC_KEY_ACTIONS.forEach { action ->
                val tick = TextView(activity).apply {
                    text = Icons.CHECK
                    // M3 marks a list item's selection with its trailing icon in primary.
                    m3.styleGlyph(this, 16f, m3.primary)
                    alpha = 0f
                }
                actionTicks[action.mode] = tick

                addView(m3.row(
                    title = action.title,
                    // Only the app action carries a second line, and it is the app's own name.
                    supportingView = if (action.mode == "Launch App") magicKeyAppLine else null,
                    glyph = action.glyph,
                    trailing = tick,
                    onClick = {
                        com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(activity, "magic_key")
                        if (action.apply != null) {
                            deps.applyStockMagicKeyMode(
                                action.mode, action.apply, magicKeyStatusLabel, magicKeyAppCarrier
                            )
                        } else {
                            deps.showMagicKeyAppPicker(magicKeyAppCarrier)
                        }
                    }
                ))
            }
        }
        magicKeySwitchRef = magicKeySwitch
        magicKeySectionRef = magicKeyCard
        container.addView(magicKeyCard)
        // The read above can only land once create() returns, so the ticks always exist by the
        // time it renders -- this draws whatever has arrived anyway, rather than relying on that.
        render(currentMode)

        // ---- Triggers -----------------------------------------------------------------------
        lateinit var triggerCard: SwitchSection
        // Set while the switch is being corrected to what the service actually did, so that
        // correction isn't read back as a second instruction.
        var correctingTriggerSwitch = false
        val triggerSwitch = MaterialSwitch(activity).apply {
            isChecked = deps.isTriggersEnabled()
            setOnCheckedChangeListener { _, checked ->
                if (correctingTriggerSwitch) return@setOnCheckedChangeListener
                com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(activity, "triggers")
                if (checked) deps.enableTriggersAndService() else deps.disableTriggersAndService()
                // The service can decline to come up; show what actually happened, not the tap.
                val actual = deps.isTriggersEnabled()
                if (isChecked != actual) {
                    correctingTriggerSwitch = true
                    isChecked = actual
                    correctingTriggerSwitch = false
                }
                triggerCard.setExpanded(actual)
            }
        }

        triggerCard = m3.sectionWithSwitch(
            "Triggers", triggerSwitch,
            glyph = Icons.TRIGGERS,
            supporting = "Map the shoulder triggers to quick actions"
        ).apply {

            addView(m3.row(
                title = "Configure triggers",
                supporting = "Choose what each trigger does",
                glyph = Icons.CONFIGURE,
                onClick = { deps.showTriggerSetupDialog() }
            ))
            addView(m3.row(
                title = "Intent Unlock",
                supporting = "Double tap the right trigger to arm it, against accidental touches",
                glyph = Icons.UNLOCK,
                trailing = prefSwitch(activity, "triggers", "intent_unlock_right_trigger", false) { checked ->
                    Toast.makeText(
                        activity,
                        "Intent Unlock " + if (checked) "enabled" else "disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ))
            addView(m3.row(
                title = "Start with the phone",
                supporting = "Turn triggers on at boot and when the app opens",
                glyph = Icons.AUTOSTART,
                trailing = prefSwitch(activity, "triggers", "triggers_auto_start", false) { checked ->
                    if (checked) {
                        deps.enableTriggersAndService()
                        triggerSwitch.isChecked = deps.isTriggersEnabled()
                    }
                    Toast.makeText(
                        activity,
                        "Auto-start " + if (checked) "enabled" else "disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ))
        }
        triggerCard.setExpanded(deps.isTriggersEnabled(), animate = false)
        container.addView(triggerCard)

        // ---- Haptics: its own section rather than a run of rows under a heading inside Triggers,
        // since it applies to the hardware generally and is what you come looking for by name.
        lateinit var hapticsCard: SwitchSection
        val hapticsSwitch = prefSwitch(activity, "triggers", "haptics_enabled", false) { checked ->
            hapticsCard.setExpanded(checked)
            Toast.makeText(
                activity,
                "Haptics " + if (checked) "enabled" else "disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        val strengthChip = TextView(activity).apply {
            m3.styleText(this, M3.Type.labelLarge, m3.onSecondaryContainer)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = m3.surfaceShape(m3.secondaryContainer, M3.Shape.full)
            minWidth = m3.dp(52)
            setPadding(m3.dp(12), m3.dp(4), m3.dp(12), m3.dp(4))
        }

        // Rounded to fives. Continuous under the finger would store 63% as readily as 65%, and no
        // one can feel the difference -- the stops just make the setting something you can return
        // to deliberately.
        fun roundStrength(raw: Float): Int = (raw / 5f).roundToInt() * 5

        val strengthSlider = ComposeSlider(
            context = activity,
            initialValue = HapticStrength.get(activity).toFloat(),
            initialValueRange = 0f..100f,
            onValueChange = { raw -> strengthChip.text = HapticStrength.label(roundStrength(raw)) },
            onValueChangeFinished = { raw ->
                val picked = roundStrength(raw)
                HapticStrength.set(activity, picked)
                // Fire one at the new strength so it can be judged by feel rather than by number.
                // On release only: a pulse per drag frame is a root shell per frame, which is what
                // made the fan slider stutter, and a continuous buzz tells you nothing anyway.
                if (picked > HapticStrength.OFF && hapticsSwitch.isChecked) {
                    Thread {
                        HardwareController.vibrate(
                            durationMs = 60,
                            gain = HapticStrength.scale(220, picked)
                        )
                    }.start()
                }
            },
            onValueRendered = { raw -> strengthChip.text = HapticStrength.label(roundStrength(raw)) }
        )

        val strengthBlock = m3.block().apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    text = "Strength"
                    m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(strengthChip)
            })
            addView(strengthSlider.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, m3.dp(4))
                addView(TextView(activity).apply {
                    text = "Off"
                    m3.styleText(this, M3.Type.labelMedium, m3.onSurfaceVariant)
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
                addView(TextView(activity).apply {
                    text = "Max"
                    m3.styleText(this, M3.Type.labelMedium, m3.onSurfaceVariant)
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
            })
        }

        hapticsCard = m3.sectionWithSwitch(
            "Haptics", hapticsSwitch,
            glyph = Icons.HAPTICS,
            supporting = "Vibrate when a trigger fires"
        ).apply {
            addView(strengthBlock)
            addView(m3.row(
                title = "Test haptic",
                supporting = "Fire one now to feel it",
                glyph = Icons.TEST,
                onClick = { deps.testHaptic() }
            ))
        }
        hapticsCard.setExpanded(hapticsSwitch.isChecked, animate = false)
        container.addView(hapticsCard)

        container.addView(DisplayDensityUi.card(activity, deps))

        // Join each run of rows into one connected block.
        m3.connectRows(container)
        m3.tintWidgets(container)

        return Result(
            view = container,
            refs = Refs(magicKeyStatusLabel = magicKeyStatusLabel)
        )
    }

    private fun summarise(profile: HardwareProfile): String {
        val fan = when {
            profile.autoFanEnabled -> "Fan auto"
            profile.fanEnabled -> "Fan ${profile.fanLevel}"
            else -> "Fan off"
        }
        val leds = listOf(
            profile.fanLedEnabled,
            profile.logoLedEnabled,
            profile.shoulderLedEnabled
        ).count { it }
        val ledPart = if (leds == 0) "no LEDs" else "$leds LED${if (leds == 1) "" else "s"}"
        val triggers = if (profile.triggerEnabled) "triggers on" else "triggers off"
        return "$fan · $ledPart · $triggers"
    }

    private fun profileRow(
        activity: Activity,
        m3: M3,
        name: String,
        summary: String,
        glyph: String,
        onApply: () -> Unit,
        onDelete: () -> Unit
    ): LinearLayout {
        val delete = TextView(activity).apply {
            text = Icons.XMARK
            gravity = Gravity.CENTER
            // M3's filled tonal icon button, in the error pair: a 40dp container drawn inside a
            // 48dp view, so the touch target is the spec's even though the circle is smaller.
            m3.styleGlyph(this, 16f, m3.onErrorContainer)
            background = android.graphics.drawable.InsetDrawable(
                m3.filled(m3.errorContainer, M3.Shape.full, m3.onErrorContainer), m3.dp(4)
            )
            layoutParams = LinearLayout.LayoutParams(
                m3.dp(M3.Metrics.touchTarget), m3.dp(M3.Metrics.touchTarget)
            )
            contentDescription = "Delete $name"
            isClickable = true
            setOnClickListener { onDelete() }
            m3.springPress(this)
        }
        return m3.row(
            title = name,
            supporting = summary,
            glyph = glyph,
            trailing = delete,
            divided = true,
            onClick = onApply
        )
    }

    /** A switch bound to a preference, for [M3.row]'s trailing slot. */
    private fun prefSwitch(
        activity: Activity,
        prefsName: String,
        key: String,
        defaultValue: Boolean,
        onChanged: (Boolean) -> Unit
    ): MaterialSwitch {
        val prefs = activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return MaterialSwitch(activity).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChanged(checked)
            }
        }
    }
}

