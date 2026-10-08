package com.elitedarkkaiser.redmagic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.elitedarkkaiser.redmagic.xposed.GameAssistSettings
import com.elitedarkkaiser.redmagic.xposed.IconPackSettings
import com.elitedarkkaiser.redmagic.xposed.MonoIconsHelper
import com.elitedarkkaiser.redmagic.xposed.WindowModConfig
import com.elitedarkkaiser.redmagic.xposed.WindowModSettings
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/**
 * The cards for everything this app does through its Xposed module: the RedMagicOS floating-window
 * fixes (ported from github.com/Gio470/FixRedMagicWindow), the global icon pack, and the
 * GameAssist/GameSpace tweaks.
 *
 * A library of cards rather than a screen -- [SoftwareTabUi] is what arranges them. They were a tab
 * of their own until the split stopped being useful: "is it an Xposed hook?" is a fact about how
 * they are built, not about what they do.
 *
 * Every control writes to [WindowModSettings] or [GameAssistSettings], which the hooks running
 * inside system_server read back — so a change applies to the next window, with no reboot. Enabling
 * the module in LSPosed does need one; whether it is loaded at all is reported on Home, in the
 * compatibility checklist next to root access, and [SoftwareTabUi] stands these controls down when
 * it is not.
 */
object XposedTabUi {
    internal fun masterCard(
        context: Context,
        m3: M3,
        onChanged: (Boolean) -> Unit
    ): LinearLayout = m3.row(
        title = "Floating window tweaks",
        supporting = "Master switch for everything below.",
        glyph = Icons.WINDOW,
        trailing = M3.MasterSwitch.mark(MaterialSwitch(context).apply {
            isChecked = WindowModSettings.getEnabled(context)
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        })
    )

    internal fun fixesCard(context: Context, m3: M3): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(m3.row(
                title = "Keep minimised windows usable",
                supporting = "Keeps it a window, not a tap-to-restore bubble",
                glyph = Icons.WINDOW_MIN,
                trailing = MaterialSwitch(context).apply {
                    isChecked = WindowModSettings.getKeepMiniInteractive(context)
                    setOnCheckedChangeListener { _, checked ->
                        WindowModSettings.setKeepMiniInteractive(context, checked)
                        push(context)
                    }
                }
            ))

            addView(m3.row(
                title = "Keep windows where you drop them",
                supporting = "Leaves a dragged window where you drop it",
                glyph = Icons.DROP_POSITION,
                trailing = MaterialSwitch(context).apply {
                    isChecked = WindowModSettings.getKeepDropPosition(context)
                    setOnCheckedChangeListener { _, checked ->
                        WindowModSettings.setKeepDropPosition(context, checked)
                        push(context)
                    }
                }
            ))

            addView(m3.row(
                title = "Allow windows off screen",
                supporting = "Lets a window hang over the edge instead of snapping back",
                glyph = Icons.OFFSCREEN,
                trailing = MaterialSwitch(context).apply {
                    isChecked = WindowModSettings.getAllowOffscreen(context)
                    setOnCheckedChangeListener { _, checked ->
                        WindowModSettings.setAllowOffscreen(context, checked)
                        push(context)
                    }
                }
            ))

            addView(m3.row(
                title = "No drag to split screen",
                supporting = "Dragging to an edge stops flipping into split screen",
                glyph = Icons.SPLIT,
                trailing = MaterialSwitch(context).apply {
                    isChecked = WindowModSettings.getNoDragToSplit(context)
                    setOnCheckedChangeListener { _, checked ->
                        WindowModSettings.setNoDragToSplit(context, checked)
                        push(context)
                    }
                }
            ))
        }

    /**
     * The limit is stored as a count, with 0 meaning unlimited — so the "Unlimited" switch and the
     * slider are two views of one value rather than two settings that could disagree.
     */
    internal fun limitCard(
        context: Context,
        m3: M3,
        onStatus: MutableList<(Map<String, String>?) -> Unit>
    ): LinearLayout {
        val saved = WindowModSettings.getWindowLimit(context)
        var lastChosen = if (saved > 0) saved else 4

        lateinit var valueLabel: TextView
        lateinit var slider: ComposeSlider
        lateinit var unlimitedSwitch: MaterialSwitch

        fun render() {
            val unlimited = unlimitedSwitch.isChecked
            slider.isEnabled = !unlimited
            slider.view.alpha = if (unlimited) 0.4f else 1f
            valueLabel.text = if (unlimited) {
                "No limit"
            } else {
                val n = slider.value.roundToInt()
                if (n == 1) "1 window" else "$n windows"
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(m3.row(
                title = "Allow any app",
                supporting = "Floating mode for every app, not just supported ones",
                glyph = Icons.ANY_APP,
                trailing = MaterialSwitch(context).apply {
                    isChecked = WindowModSettings.getAllowAnyApp(context)
                    setOnCheckedChangeListener { _, checked ->
                        WindowModSettings.setAllowAnyApp(context, checked)
                        push(context)
                    }
                }
            ))

            addView(m3.block("Limit").apply {
                unlimitedSwitch = MaterialSwitch(context).apply {
                    isChecked = saved <= 0
                }

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = "Unlimited windows"
                        m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(unlimitedSwitch)
                })

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, m3.dp(M3.Space.md), 0, 0)
                    addView(TextView(context).apply {
                        text = "Maximum"
                        m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    valueLabel = TextView(context).apply {
                        m3.styleText(this, M3.Type.labelLarge, m3.onSurface)
                    }
                    addView(valueLabel)
                })

                slider = ComposeSlider(
                    context = context,
                    initialValue = lastChosen.toFloat(),
                    initialValueRange = WindowModConfig.MIN_WINDOW_LIMIT.toFloat()..WindowModConfig.MAX_WINDOW_LIMIT.toFloat(),
                    initialSteps = WindowModConfig.MAX_WINDOW_LIMIT - WindowModConfig.MIN_WINDOW_LIMIT - 1,
                    onValueChange = { raw ->
                        lastChosen = raw.roundToInt()
                        WindowModSettings.setWindowLimit(context, lastChosen)
                        push(context)
                        render()
                    }
                )
                addView(slider.view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = m3.dp(4) })

                val notice = TextView(context).apply {
                    text = "Checking whether this ROM exposes a window count…"
                    m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                    setPadding(0, m3.dp(M3.Space.sm), 0, 0)
                }
                addView(notice)

                // The cap is the one option that can be defeated by the ROM rather than by setup:
                // it needs a window count the ROM does not publish, so when the search comes up
                // empty say so here instead of leaving a slider that quietly does nothing.
                val copyDump = m3.button("Copy ROM details", M3.ButtonKind.Tonal).apply {
                    visibility = View.GONE
                }
                addView(copyDump, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = m3.dp(M3.Space.xs) })

                onStatus += { status ->
                    val counter = status?.get("limit_counter")
                    val members = status?.get("limit_members")
                    notice.text = when {
                        status == null || counter == null ->
                            "The cap needs the module loaded to take effect."
                        counter == "none" ->
                            "This ROM doesn't expose a window count, so the cap can't be " +
                                "enforced. Windows stay unlimited. Send the details below."
                        else -> ""
                    }
                    if (members != null) {
                        copyDump.visibility = View.VISIBLE
                        copyDump.setOnClickListener {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            cm.setPrimaryClip(
                                ClipData.newPlainText("RedMagic window API", members)
                            )
                            Toast.makeText(context, "ROM details copied", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                unlimitedSwitch.setOnCheckedChangeListener { _, checked ->
                    WindowModSettings.setWindowLimit(context, if (checked) 0 else lastChosen); push(context)
                    render()
                }
                render()
            })
        }
    }

    /**
     * A global icon pack, ported from github.com/RichardLuo0/global-icon-pack-android.
     *
     * Every control writes to [IconPackSettings], which mirrors them into the `Settings.Global`
     * keys the hooks read inside each app whose icons are being replaced, then restarts the
     * launcher — a hooked app reads the settings once when it starts, so nothing changes under it
     * mid-run.
     */
    internal fun iconPackCard(context: Context, m3: M3, activity: Activity): LinearLayout {
        lateinit var options: LinearLayout
        lateinit var packRow: LinearLayout
        lateinit var packLabel: TextView

        fun packName(): String {
            val pack = IconPackSettings.getPack(context)
            if (pack.isEmpty()) return "None — tap to choose"
            return IconPackSettings.installed(context).firstOrNull { it.packageName == pack }?.label
                ?: pack
        }

        fun applyMaster(on: Boolean) {
            options.visibility = if (on) View.VISIBLE else View.GONE
            packRow.visibility = if (on) View.VISIBLE else View.GONE
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // The launcher's own mono-icons switch. Independent of the icon pack below (it themes
            // the stock launcher rather than replacing icons), so it sits above the master and is
            // always shown.
            addView(monoIconsRow(context, m3))

            addView(m3.row(
                title = "Replace icons globally",
                supporting = "Master switch for everything below.",
                glyph = Icons.ICON_PACK_MASTER,
                trailing = M3.MasterSwitch.mark(MaterialSwitch(context).apply {
                    isChecked = IconPackSettings.getEnabled(context)
                    setOnCheckedChangeListener { _, checked ->
                        IconPackSettings.setEnabled(context, checked)
                        applyMaster(checked)
                        pushIconPack(context)
                    }
                })
            ))

            packLabel = TextView(context).apply {
                m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            }
            packRow = m3.row(
                title = "Icon pack",
                supportingView = packLabel,
                glyph = Icons.ICON_PACK,
                onClick = {
                    showIconPackPicker(activity, m3) {
                        packLabel.text = packName()
                        pushIconPack(context)
                    }
                }
            )
            packLabel.text = packName()
            addView(packRow)

            options = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )

                addView(m3.row(
                    title = "Icon pack as fallback",
                    supporting = "Match by package, so renamed apps still get themed",
                    glyph = Icons.FALLBACK,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = IconPackSettings.getAsFallback(context)
                        setOnCheckedChangeListener { _, checked ->
                            IconPackSettings.setAsFallback(context, checked)
                            pushIconPack(context)
                        }
                    }
                ))

                addView(m3.row(
                    title = "Generate icons for shortcuts",
                    supporting = "Shortcuts get the icon of the app they belong to",
                    glyph = Icons.SHORTCUTS,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = IconPackSettings.getShortcut(context)
                        setOnCheckedChangeListener { _, checked ->
                            IconPackSettings.setShortcut(context, checked)
                            pushIconPack(context)
                        }
                    }
                ))

                addView(m3.row(
                    title = "Force monochrome",
                    supporting = "Apps the pack misses fall back to themed icons. Android 13+",
                    glyph = Icons.MONOCHROME,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = IconPackSettings.getForceMonochrome(context)
                        setOnCheckedChangeListener { _, checked ->
                            IconPackSettings.setForceMonochrome(context, checked)
                            pushIconPack(context)
                        }
                    }
                ))
            }
            addView(options)
            applyMaster(IconPackSettings.getEnabled(context))
        }
    }

    /** Lists what is installed, since a pack is just an app and there is no other way to name one. */
    private fun showIconPackPicker(activity: Activity, m3: M3, onPicked: () -> Unit) {
        val packs = IconPackSettings.installed(activity)
        val current = IconPackSettings.getPack(activity)

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(
                activity,
                "Icon pack",
                supporting = "Applied to every app, by the hook rather than by the launcher.",
                glyph = Icons.ICON_PACK
            ))
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        fun choose(packageName: String) {
            IconPackSettings.setPack(activity, packageName)
            onPicked()
            dialog.dismiss()
        }

        if (packs.isEmpty()) {
            panel.addView(M3Dialog.body(activity, "No icon packs are installed. Install one from " +
                "the Play Store or F-Droid, then come back."))
        } else {
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(M3Dialog.radioButton(activity, "None", current.isEmpty()).apply {
                    setOnClickListener { choose("") }
                })
                packs.forEach { pack ->
                    addView(
                        M3Dialog.radioButton(activity, pack.label, pack.packageName == current)
                            .apply { setOnClickListener { choose(pack.packageName) } }
                    )
                }
            }
            panel.addView(M3Dialog.scroll(activity, list))
        }

        panel.addView(M3Dialog.buttonRow(
            activity,
            M3Dialog.textButton(activity, "Close") { dialog.dismiss() }
        ))

        dialog = M3Dialog.show(activity, panel)
    }

    /** Mirrors icon pack settings to the hooks and restarts the launcher. Root shell, so off-thread. */
    private fun pushIconPack(context: Context) {
        Thread { IconPackSettings.pushToDevice(context) }.start()
    }

    /**
     * GameAssist ("Game Helper") and GameSpace tweaks, ported from khanhnguyen9872/NubiaToolkit.
     *
     * The upstream module leaves "Force Stop on Apply" as a switch that defaults off, so most
     * toggles there silently do nothing until the user notices and relaunches GameAssist/GameSpace
     * by hand. There is no such switch here: [pushGameAssist] always force-stops the app a change
     * affects, right after writing it, so every toggle takes effect immediately.
     */
    internal fun gameAssistCard(context: Context, m3: M3): LinearLayout {
        lateinit var tweaks: LinearLayout
        lateinit var noKillSwitch: MaterialSwitch

        fun applyMaster(on: Boolean) {
            tweaks.visibility = if (on) View.VISIBLE else View.GONE
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(m3.row(
                title = "Enable GameAssist tweaks",
                supporting = "Master switch for everything below.",
                glyph = Icons.GAME_ASSIST,
                trailing = M3.MasterSwitch.mark(MaterialSwitch(context).apply {
                    isChecked = GameAssistSettings.getEnabled(context)
                    setOnCheckedChangeListener { _, checked ->
                        GameAssistSettings.setEnabled(context, checked)
                        applyMaster(checked)
                        pushGameAssist(
                            context, GameAssistSettings.PKG_GAME_ASSIST, GameAssistSettings.PKG_GAME_LAUNCHER
                        )
                    }
                })
            ))

            tweaks = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )

                noKillSwitch = MaterialSwitch(context).apply {
                    isChecked = GameAssistSettings.getNoKill(context)
                    setOnCheckedChangeListener { _, checked ->
                        GameAssistSettings.setNoKill(context, checked)
                        pushGameAssist(context, GameAssistSettings.PKG_GAME_ASSIST)
                    }
                }
                addView(m3.row(
                    title = "Prevent automatic cleanup",
                    supporting = "Stops GameAssist force-closing background apps",
                    glyph = Icons.SHIELD,
                    trailing = noKillSwitch
                ))

                addView(m3.row(
                    title = "Global Game Mode",
                    supporting = "Treats every app as a game, not just GameAssist's list",
                    glyph = Icons.GLOBAL_GAME,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = GameAssistSettings.getGlobalGameMode(context)
                        setOnCheckedChangeListener { _, checked ->
                            GameAssistSettings.setGlobalGameMode(context, checked)
                            pushGameAssist(context, GameAssistSettings.PKG_GAME_ASSIST)
                        }
                    }
                ))

                addView(m3.row(
                    title = "Hide Energy Cube",
                    supporting = "Hides the overlay. Also enables Prevent automatic cleanup",
                    glyph = Icons.HIDE,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = GameAssistSettings.getHideEnergyCube(context)
                        setOnCheckedChangeListener { _, checked ->
                            GameAssistSettings.setHideEnergyCube(context, checked)
                            if (checked && !noKillSwitch.isChecked) {
                                noKillSwitch.isChecked = true // also flips GameAssistSettings.NoKill
                            }
                            pushGameAssist(context, GameAssistSettings.PKG_GAME_ASSIST)
                        }
                    }
                ))

                addView(m3.row(
                    title = "Super Resolution",
                    supporting = "Super Resolution upscaling on unsupported devices",
                    glyph = Icons.RESOLUTION,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = GameAssistSettings.getSuperResolution(context)
                        setOnCheckedChangeListener { _, checked ->
                            GameAssistSettings.setSuperResolution(context, checked)
                            pushGameAssist(
                                context, GameAssistSettings.PKG_GAME_ASSIST, GameAssistSettings.PKG_GAME_LAUNCHER
                            )
                        }
                    }
                ))

                addView(m3.row(
                    title = "Remove watermark length limit",
                    supporting = "Removes the watermark text length limit",
                    glyph = Icons.WATERMARK,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = GameAssistSettings.getWatermarkLength(context)
                        setOnCheckedChangeListener { _, checked ->
                            GameAssistSettings.setWatermarkLength(context, checked)
                            pushGameAssist(context, GameAssistSettings.PKG_GAME_LAUNCHER)
                        }
                    }
                ))

                addView(m3.row(
                    title = "Allow Small Window everywhere",
                    supporting = "Small-window mode for any app, not just allowed ones",
                    glyph = Icons.SMALL_WINDOW,
                    trailing = MaterialSwitch(context).apply {
                        isChecked = GameAssistSettings.getSmallWindow(context)
                        setOnCheckedChangeListener { _, checked ->
                            GameAssistSettings.setSmallWindow(context, checked)
                            pushGameAssist(context, GameAssistSettings.PKG_GAME_ASSIST)
                        }
                    }
                ))
            }
            addView(tweaks)
            applyMaster(GameAssistSettings.getEnabled(context))
        }
    }

    /**
     * Mirrors GameAssist/GameSpace settings into the file their hooks read, then force-stops
     * [packages] so the change applies immediately -- built in, not a setting; see [gameAssistCard].
     */
    private fun pushGameAssist(context: Context, vararg packages: String) {
        Thread {
            GameAssistSettings.pushToDevice(context)
            GameAssistSettings.forceStop(*packages)
        }.start()
    }

    /**
     * A direct root shell action, not an Xposed hook: RedMagic/MiFavor launcher's "Home mono
     * icons" switch is a single provider call, with nothing to hook and nothing on-device to read
     * the current value back from -- see [MonoIconsHelper]. A button rather than a switch because
     * the on-screen state is only ever this app's own memory of what it last set, not a live
     * reading of the launcher's.
     */
    /**
     * The launcher's "Home mono icons" switch, as a row for the Icon pack page.
     *
     * There is nothing to read the real value back from (see [MonoIconsHelper]), so the switch
     * reflects the last value this app set. The write is a root shell, so it runs off the main
     * thread and the switch is disabled until it lands; if it fails the switch springs back.
     */
    private fun monoIconsRow(context: Context, m3: M3): LinearLayout {
        val handler = android.os.Handler(context.mainLooper)
        var suppress = false
        lateinit var switch: MaterialSwitch
        switch = MaterialSwitch(context).apply {
            isChecked = MonoIconsHelper.getEnabled(context)
            setOnCheckedChangeListener { _, checked ->
                if (suppress) return@setOnCheckedChangeListener
                isEnabled = false
                Thread {
                    val ok = MonoIconsHelper.apply(context, checked)
                    handler.post {
                        isEnabled = true
                        if (!ok) {
                            suppress = true
                            isChecked = MonoIconsHelper.getEnabled(context)
                            suppress = false
                            android.widget.Toast.makeText(
                                context,
                                "Couldn't change mono icons -- check root access.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.start()
            }
        }
        return m3.row(
            title = "Home mono icons",
            supporting = "Flip the launcher's own mono icons switch, and restart it to apply.",
            glyph = Icons.MONOCHROME,
            trailing = switch,
            onClick = { switch.toggle() }
        )
    }

    private fun switchRow(
        context: Context,
        m3: M3,
        label: String,
        description: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val switch = MaterialSwitch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m3.dp(M3.Space.xs) }
            setPadding(0, m3.dp(M3.Space.xs), 0, m3.dp(M3.Space.xs))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label
                    m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                })
                addView(TextView(context).apply {
                    text = description
                    m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(switch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = m3.dp(M3.Metrics.listInset) })
        }
    }

    /** Mirrors a changed setting into the file the hooks read. Root shell, so off the main thread. */
    internal fun push(context: Context) {
        Thread { WindowModSettings.pushToDevice(context) }.start()
    }

    private fun bodyText(context: Context, m3: M3, text: String): TextView =
        TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        }

    private fun card(context: Context, m3: M3): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(16), m3.dp(16), m3.dp(16), m3.dp(16))
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m3.dp(12) }
        }
}

