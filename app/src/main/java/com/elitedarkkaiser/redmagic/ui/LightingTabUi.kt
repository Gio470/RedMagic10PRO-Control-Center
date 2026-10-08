package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.MasterSwitches
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Lighting: LED zones contains the zone controls and the three lighting-profile cards.
 * Charging wins over calls, calls over Game Mode, and Game Mode over the plain zones.
 *
 * Each section is gated by a master switch on its own header, so what a section governs can be
 * switched off as a whole rather than row by row.
 */
object LightingTabUi {
    fun create(activity: Activity, deps: LightingTabDeps): LinearLayout {
        val container = deps.scrollTabContainer()
        val m3 = M3(activity)

        // ---- LED zones. The master darkens all three zones at once and stands the LED service
        // down; switching it back on re-applies each zone's own saved state, so a zone the user had
        // turned off individually stays off. The real-time preview toggle lives in here rather than
        // in a section of its own, since it only governs the two pickers directly below it.
        lateinit var zones: SwitchSection
        val zonesSwitch = MaterialSwitch(activity).apply {
            isChecked = MasterSwitches.ledZonesEnabled(activity)
            setOnCheckedChangeListener { _, checked ->
                zones.setExpanded(checked)
                Thread { MasterSwitches.setLedZonesEnabled(activity, checked) }.start()
            }
        }

        zones = m3.sectionWithSwitch(
            "LED zones", zonesSwitch,
            glyph = Icons.LED,
            supporting = "LED colours, effects, and lighting profiles"
        ).apply {
            addView(m3.row(
                title = "Real-time preview",
                supporting = "Apply changes as you pick them",
                glyph = Icons.PREVIEW,
                trailing = switchFor(activity, deps.getRealTimePreviewEnabled()) { checked ->
                    deps.setRealTimePreviewEnabled(checked)
                    deps.saveRealTimePreviewEnabled(checked)
                }
            ))
            addView(m3.row(title = "Fan LED", supporting = "Colour and effect for the fan ring",
                glyph = Icons.LED, onClick = { deps.showFanLedDialog() }))
            addView(m3.row(title = "Logo LED", supporting = "Colour and effect for the back logo",
                glyph = Icons.LOGO, onClick = { deps.showLogoLedDialog() }))
        }

        lateinit var gameMode: SwitchSection
        val gameModeSwitch = MaterialSwitch(activity).apply {
            isChecked = MasterSwitches.gameModeEnabled(activity)
            setOnCheckedChangeListener { _, checked ->
                MasterSwitches.setGameModeEnabled(activity, checked)
                gameMode.setExpanded(checked)
            }
        }

        gameMode = m3.sectionWithSwitch(
            "Game Mode", gameModeSwitch,
            glyph = Icons.GAME_MODE,
            supporting = "A hardware profile applied while one of your games is in front"
        ).apply {
            addView(m3.row(
                title = "Selected apps",
                supporting = deps.gameModeAppsSummary(),
                glyph = Icons.GAME_MODE,
                onClick = { deps.showGameModeAppPicker() }
            ))
            addView(m3.row(
                title = "Game profile",
                supporting = "Hardware profile applied while a game is active",
                glyph = Icons.PROFILE,
                onClick = { deps.showGameModeProfileDialog() }
            ))
        }

        lateinit var callLighting: SwitchSection
        val callSwitch = switchFor(activity, deps.getCallLightingEnabled()) { checked ->
            deps.setCallLightingEnabled(checked)
            callLighting.setExpanded(checked)
        }

        callLighting = m3.sectionWithSwitch(
            "Call lighting", callSwitch,
            glyph = Icons.CALLS,
            supporting = "Applies during incoming and connected calls only"
        ).apply {
            addView(m3.row(
                title = "Pause fan during calls",
                supporting = "Off during a call, restored afterwards",
                glyph = Icons.PAUSE,
                trailing = switchFor(activity, deps.getPauseFanDuringCalls()) { checked ->
                    deps.setPauseFanDuringCalls(checked)
                }
            ))
            addView(m3.row(title = "Incoming call profile", glyph = Icons.CALL_INCOMING,
                onClick = { deps.showIncomingCallProfileDialog() }))
            addView(m3.row(title = "Connected call profile", glyph = Icons.CALL_ACTIVE,
                onClick = { deps.showConnectedCallProfileDialog() }))
        }

        lateinit var chargingMode: SwitchSection
        val chargingSwitch = switchFor(activity, deps.getChargingLedEnabled()) { checked ->
            deps.setChargingLedEnabled(checked)
            chargingMode.setExpanded(checked)
        }

        chargingMode = m3.sectionWithSwitch(
            "Charging mode", chargingSwitch,
            glyph = Icons.CHARGING,
            supporting = "Applies only while the device is plugged in and charging"
        ).apply {
            addView(m3.row(title = "Charging fan LED", glyph = Icons.FAN,
                onClick = { deps.showChargingFanLedDialog() }))
            addView(m3.row(title = "Charging logo LED", glyph = Icons.LOGO,
                onClick = { deps.showChargingLogoLedDialog() }))
        }

        // Not animated: this is the state the tab opens in, and folding on the first draw reads
        // as the screen twitching rather than as anything the user did.
        zones.setExpanded(zonesSwitch.isChecked, animate = false)
        gameMode.setExpanded(gameModeSwitch.isChecked, animate = false)
        callLighting.setExpanded(callSwitch.isChecked, animate = false)
        chargingMode.setExpanded(chargingSwitch.isChecked, animate = false)

        // Keep each profile's switch and page while grouping them inside LED zones.
        zones.addView(IconifyGrid.build(activity, listOf(gameMode, callLighting, chargingMode).map {
            SectionPages.item(activity, SectionPages.entry(it))
        }, topMarginDp = M3.Space.md))
        container.addView(zones)

        // Join each run of rows into one connected block.
        m3.connectRows(container)
        m3.tintWidgets(container)

        return container
    }

    /** Title (plus an optional supporting line) on the left, M3 switch on the right. */
    /** A switch already wired to its listener, for [M3.row]'s trailing slot. */
    private fun switchFor(
        activity: android.app.Activity,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): MaterialSwitch =
        MaterialSwitch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }

    private fun switchRow(
        activity: Activity,
        m3: M3,
        label: String,
        description: String?,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = label
                m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
            })
            if (description != null) {
                addView(TextView(activity).apply {
                    text = description
                    m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
                })
            }
        }

        val switch = MaterialSwitch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, nowChecked -> onChanged(nowChecked) }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, m3.dp(M3.Space.xs), 0, m3.dp(M3.Space.xxs))
            addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(View(activity), LinearLayout.LayoutParams(m3.dp(M3.Metrics.listInset), 1))
            addView(switch)
        }
    }

    /** A read-only "label over value" line, as the Home tab's device info card uses. */
    private fun valueRow(activity: Activity, m3: M3, label: String, value: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(12), 0, 0)
            addView(TextView(activity).apply {
                text = label
                m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
            })
            addView(TextView(activity).apply {
                text = value
                m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            })
        }
}
