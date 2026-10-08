package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.xposed.SoftwareControlsConfig
import com.elitedarkkaiser.redmagic.xposed.SoftwareControlsSettings
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

object SoftwareControlsUi {
    private val writes = Executors.newSingleThreadExecutor()

    private fun apply(activity: Activity, write: () -> Boolean) {
        writes.execute {
            val ok = runCatching(write).getOrDefault(false)
            if (!ok) Handler(Looper.getMainLooper()).post {
                Toast.makeText(activity, "Couldn't apply settings. Check root access and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun volumePage(activity: Activity, m3: M3): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(m3.row(
            title = "Volume Step Control",
            supporting = "Change media volume by more than one level per button press",
            glyph = Icons.SOUND_MODE,
            trailing = M3.MasterSwitch.mark(MaterialSwitch(activity).apply {
                isChecked = SoftwareControlsSettings.volumeEnabled(activity)
                setOnCheckedChangeListener { _, on ->
                    SoftwareControlsSettings.setVolumeEnabled(activity, on)
                    apply(activity) { SoftwareControlsSettings.pushVolume(activity) }
                }
            })
        ))
        addView(m3.block("Levels per press").apply {
            val value = TextView(activity).apply {
                gravity = Gravity.CENTER
                m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
            }
            fun render(step: Int) { value.text = if (step == 1) "1 level" else "$step levels" }
            render(SoftwareControlsSettings.volumeStep(activity))
            addView(value)
            val slider = ComposeSlider(
                activity,
                initialValue = SoftwareControlsSettings.volumeStep(activity).toFloat(),
                initialValueRange = SoftwareControlsConfig.MIN_STEP.toFloat()..SoftwareControlsConfig.MAX_STEP.toFloat(),
                initialSteps = SoftwareControlsConfig.MAX_STEP - SoftwareControlsConfig.MIN_STEP - 1,
                onValueChange = { render(it.roundToInt()) },
                onValueChangeFinished = {
                    SoftwareControlsSettings.setVolumeStep(activity, it.roundToInt())
                    apply(activity) { SoftwareControlsSettings.pushVolume(activity) }
                },
                onValueRendered = { render(it.roundToInt()) }
            )
            addView(slider.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(activity).apply {
                text = "1 keeps the normal step. Ring and call volume stay unchanged."
                m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            })
        })
        addView(m3.row(
            title = "Module setup",
            supporting = "Enable this app for System Framework (android) in LSPosed, then reboot once after installing this update. Later step changes apply immediately.",
            glyph = Icons.CONFIGURE
        ))
    }

    fun launcherPage(activity: Activity, m3: M3): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(m3.row(
            title = "Hide launcher from recents",
            supporting = "Prevent the selected HOME launcher from becoming a recent-app card",
            glyph = Icons.HOME,
            trailing = M3.MasterSwitch.mark(MaterialSwitch(activity).apply {
                isChecked = SoftwareControlsSettings.hideLauncher(activity)
                setOnCheckedChangeListener { _, on ->
                    SoftwareControlsSettings.setHideLauncher(activity, on)
                    apply(activity) { SoftwareControlsSettings.pushLauncher(activity) }
                }
            })
        ))
        addView(m3.row(
            title = "Gesture navigation",
            supporting = "Tap your third-party launcher below to set it as your Home app using root. Hiding its recents card also requires LSPosed.",
            glyph = Icons.APP_LIST
        ))
        val launchers = SoftwareControlsSettings.launchers(activity)
        var selected = SoftwareControlsSettings.launcherPackage(activity)
        val ticks = mutableMapOf<String, TextView>()
        fun render() { ticks.forEach { (pkg, tick) -> tick.alpha = if (pkg == selected) 1f else 0f } }
        val choices = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            if (launchers.isEmpty()) addView(m3.row(
                title = "No third-party launchers installed",
                supporting = "Install a launcher that can be selected as your Home app",
                glyph = Icons.HOME
            ))
            launchers.forEach { launcher ->
                val tick = TextView(activity).apply {
                    text = Icons.CHECK
                    m3.styleGlyph(this, 16f, m3.primary)
                }
                ticks[launcher.packageName] = tick
                addView(m3.row(
                    title = launcher.label,
                    supporting = launcher.packageName,
                    glyph = Icons.HOME,
                    trailing = tick,
                    onClick = {
                        writes.execute {
                            val ok = runCatching { SoftwareControlsSettings.selectLauncher(activity, launcher) }
                                .getOrDefault(false)
                            Handler(Looper.getMainLooper()).post {
                                selected = SoftwareControlsSettings.launcherPackage(activity)
                                render()
                                Toast.makeText(activity, if (ok) "${launcher.label} is now your Home app"
                                    else "Couldn't apply the launcher. Check root access and try again.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ))
            }
        }
        render()
        addView(choices)
        addView(m3.row(
            title = "Module setup",
            supporting = "Enable both System Framework (android) and RedMagic Launcher (com.zte.mifavor.launcher) for this app in LSPosed, then reboot after installing this update. Root alone cannot hide the gesture-navigation card.",
            glyph = Icons.CONFIGURE
        ))
    }
}
