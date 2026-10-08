package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.RefreshIntervals
import com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/**
 * Settings, opened from the floating button over the nav bar.
 *
 * Appearance controls include a live blur strength for the animated background behind cards.
 */
object SettingsTabUi {
    data class Result(val view: LinearLayout)

    fun create(deps: SettingsTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)

        // Appearance first and on its own, because it is the only group anyone opens this tab to
        // browse. What used to sit in it -- the two bar switches and the playground -- has moved
        // out: those are about one component's material, not about the app's colours, and mixing
        // them meant eight rows that had nothing to do with each other under one header.
        container.addView(m3.groupHeader("Appearance"))
        container.addView(themeModeBlock(context, m3, deps))
        container.addView(dynamicColourRow(context, m3, deps))
        container.addView(colorPaletteRow(context, m3, deps))
        container.addView(pureBlackRow(context, m3, deps))

        container.addView(m3.groupHeader("Glass"))
        container.addView(glassRow(context, m3, deps))
        container.addView(dockedBarRow(context, m3, deps))
        container.addView(playgroundRow(m3, deps))

        container.addView(m3.groupHeader("Background"))
        container.addView(backgroundAnimationBlock(context, m3, deps))
        container.addView(cardBlurRow(context, m3))
        container.addView(cardConnectRow(context, m3, deps))

        container.addView(m3.groupHeader("Units"))
        container.addView(temperatureUnitRow(context, m3, deps))

        // One block for all three, where there used to be three. They are the same kind of setting
        // read the same way, and as separate cards they took a third of the tab's height for
        // something most people set once.
        container.addView(m3.groupHeader("Refresh intervals"))
        container.addView(m3.block().apply {
            intervalSection(
                this, context, m3,
                label = "Hardware status",
                glyph = Icons.FAN,
                supporting = "Temperature, fan speed and root. The most expensive of the three.",
                first = true,
                current = { RefreshIntervals.status(context) },
                onChanged = { ms ->
                    RefreshIntervals.setStatus(context, ms)
                    deps.onRefreshIntervalsChanged()
                }
            )
            intervalSection(
                this, context, m3,
                label = "Processor",
                glyph = Icons.PROCESSES,
                supporting = "Home's Processor card, and the window load is averaged over.",
                first = false,
                current = { RefreshIntervals.cpu(context) },
                onChanged = { ms ->
                    RefreshIntervals.setCpu(context, ms)
                    deps.onRefreshIntervalsChanged()
                }
            )
            intervalSection(
                this, context, m3,
                label = "Memory",
                glyph = Icons.MEMORY,
                supporting = "Home's Memory card.",
                first = false,
                current = { RefreshIntervals.memory(context) },
                onChanged = { ms ->
                    RefreshIntervals.setMemory(context, ms)
                    deps.onRefreshIntervalsChanged()
                }
            )
        })

        // ---- Diagnostics: the two records the app keeps of what it did, copyable without adb or
        // a terminal on the phone. Reading the code was not enough to find what kept switching the
        // fan; these exist so the next one can be answered from the device instead.
        container.addView(m3.groupHeader("Diagnostics"))
        container.addView(m3.row(
            title = "Copy crash report",
            supporting = "The last crash, if there was one",
            glyph = Icons.CRASH_LOG,
            onClick = {
                val report = com.elitedarkkaiser.redmagic.CrashLog.read(context)
                copyOut(
                    context,
                    "RedMagic crash report",
                    report.ifBlank { "(no crash recorded)" },
                    if (report.isBlank()) "No crash recorded" else "Crash report copied"
                )
            }
        ))
        container.addView(m3.row(
            title = "Copy hardware write log",
            supporting = "Every write this app made, and what asked for it",
            glyph = Icons.WRITE_LOG,
            onClick = {
                copyOut(
                    context,
                    "RedMagic hardware writes",
                    com.elitedarkkaiser.redmagic.HardwareController.writeLogSnapshot(),
                    "Write log copied"
                )
            }
        ))

        m3.connectRows(container)

        m3.tintWidgets(container)
        return Result(view = container)
    }

    /** Straight to the playground, which is where the glass above it is actually tuned. */
    private fun playgroundRow(m3: M3, deps: SettingsTabDeps): LinearLayout =
        m3.row(
            title = "Glass playground",
            supporting = "Tune the glass the bars and the drawers are drawn with",
            glyph = Icons.GLASS,
            onClick = {
                val activity = deps.activity()
                activity.startActivity(
                    android.content.Intent(
                        activity,
                        com.elitedarkkaiser.redmagic.GlassPlaygroundActivity::class.java
                    )
                )
            }
        )

    private fun copyOut(context: Context, label: String, text: String, toast: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * One period slider inside a shared block: its label and live value on a row, the slider under
     * it. Stepped in half seconds so the half-second floor is reachable exactly rather than only
     * approached.
     *
     * Appends into [parent] rather than returning a card of its own. Three cards for three
     * variations of one setting was three times the frame for no more information.
     */
    private fun intervalSection(
        parent: LinearLayout,
        context: Context,
        m3: M3,
        label: String,
        glyph: String,
        supporting: String,
        first: Boolean,
        current: () -> Int,
        onChanged: (Int) -> Unit
    ) {
        val value = TextView(context).apply {
            m3.styleText(this, M3.Type.labelLarge, m3.onSurface)
            text = RefreshIntervals.format(current())
        }

        parent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, if (first) 0 else m3.dp(M3.Space.lg), 0, 0)
            addView(m3.iconChip(glyph, label), LinearLayout.LayoutParams(
                m3.dp(24), m3.dp(24)
            ).apply { marginEnd = m3.dp(M3.Metrics.listInset) })
            addView(TextView(context).apply {
                text = label
                m3.styleText(this, M3.Type.titleSmall, m3.onSurfaceVariant)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        })

        parent.addView(TextView(context).apply {
            text = supporting
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(4), 0, 0)
        })

        val slider = ComposeSlider(
            context = context,
            initialValue = current().toFloat(),
            initialValueRange = RefreshIntervals.MIN_MS.toFloat()..RefreshIntervals.MAX_MS.toFloat(),
            initialSteps = (RefreshIntervals.MAX_MS - RefreshIntervals.MIN_MS) /
                RefreshIntervals.STEP_MS - 1,
            onValueChange = { raw ->
                val ms = (Math.round(raw / RefreshIntervals.STEP_MS.toFloat()) *
                    RefreshIntervals.STEP_MS)
                    .coerceIn(RefreshIntervals.MIN_MS, RefreshIntervals.MAX_MS)
                value.text = RefreshIntervals.format(ms)
                onChanged(ms)
            }
        )
        parent.addView(slider.view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = m3.dp(4) })
    }

    private fun themeModeBlock(
        context: Context,
        m3: M3,
        deps: SettingsTabDeps
    ): LinearLayout = m3.block("Theme mode", Icons.THEME_MODE).apply {
        val modes = listOf(ThemePrefs.MODE_SYSTEM, ThemePrefs.MODE_LIGHT, ThemePrefs.MODE_DARK)
        lateinit var group: ComposeButtonGroup

        fun choose(mode: Int) {
            if (ThemePrefs.themeMode(context) == mode) return
            ThemePrefs.setThemeMode(context, mode)
            group.selectedIndex = modes.indexOf(mode).coerceAtLeast(0)
            deps.restartForTheme()
        }

        group = ComposeButtonGroup(
            context,
            options = listOf("System", "Light", "Dark"),
            selectedIndex = modes.indexOf(ThemePrefs.themeMode(context)).coerceAtLeast(0)
        ) { index -> choose(modes[index]) }

        addView(group.view)

        addView(TextView(context).apply {
            text = "System follows the device's own light and dark setting."
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(M3.Space.xs), 0, 0)
        })
    }

    private fun dynamicColourRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val switch = MaterialSwitch(context).apply {
            isChecked = supported && ThemePrefs.useDynamicColor(context)
            isEnabled = supported
            setOnCheckedChangeListener { _, checked ->
                ThemePrefs.setUseDynamicColor(context, checked)
                deps.onPaletteChanged()
            }
        }
        return m3.row(
            title = "Dynamic Color",
            supporting = if (supported) {
                "Select colors dynamically"
            } else {
                "Needs Android 12 or newer."
            },
            glyph = Icons.DYNAMIC_COLOR,
            trailing = switch
        ).apply { alpha = if (supported) 1f else M3.Metrics.disabledAlpha }
    }

    /**
     * The seed the palette is generated from, when the wallpaper is not providing one.
     *
     * Disabled while dynamic colour is on rather than hidden, and says why: the two set the same
     * thing from different sources, and a row that silently did nothing would be worse than one
     * that explains it is not the one in charge. Ported from Gio470/Bus-tracker's Appearance
     * screen, which pairs the same two settings the same way.
     */
    /**
     * Which bottom bar to use. Ported alongside the bar itself from Gio470/Bus-tracker's
     * AxManager, and live: the bar is Compose state, so this swaps without rebuilding anything.
     */
    private fun dockedBarRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val switch = MaterialSwitch(context).apply {
            isChecked = ThemePrefs.useDockedBar(context)
            setOnCheckedChangeListener { _, checked ->
                ThemePrefs.setUseDockedBar(context, checked)
                deps.onBarStyleChanged(checked)
            }
        }
        return m3.row(
            title = "Docked navigation bar",
            supporting = "A full-width bar on the bottom edge, with Settings in it",
            glyph = Icons.DOCKED_BAR,
            trailing = switch
        )
    }

    private fun colorPaletteRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        // No Android version gate any more: the palette is generated in process, so a picked
        // colour works wherever the app runs. Only the *dynamic* source needs the system, because
        // only the system knows the wallpaper.
        val dynamicOn = ThemePrefs.useDynamicColor(context)
        val enabled = !dynamicOn
        val picked = ThemePrefs.customPrimaryColor(context)

        // The colour actually in force: what was picked, or -- with nothing picked -- whatever the
        // theme resolved to, so the swatch is never showing a colour the app is not using.
        val shown = picked ?: m3.primary
        val swatch = View(context).apply {
            background = m3.surfaceShape(shown, M3.Shape.full, m3.outlineVariant)
            layoutParams = LinearLayout.LayoutParams(m3.dp(M3.Space.lg), m3.dp(M3.Space.lg))
        }

        return m3.row(
            title = "Color Palette",
            supporting = when {
                dynamicOn -> "Overridden by Dynamic Color"
                picked == null -> "Customize primary color"
                else -> "Customize primary color · ${ColorPaletteDialog.hex(shown)}"
            },
            glyph = Icons.COLOR_PALETTE,
            trailing = swatch,
            onClick = if (!enabled) null else {
                {
                    ColorPaletteDialog.show(deps.activity(), picked) { chosen ->
                        ThemePrefs.setCustomPrimaryColor(context, chosen)
                        deps.onPaletteChanged()
                    }
                }
            }
        ).apply { alpha = if (enabled) 1f else M3.Metrics.disabledAlpha }
    }

    /**
     * True black replaces the whole dark tonal ladder with near-black steps (see M3.pureBlackActive)
     * -- meaningless against a light scheme, which has no black step to reach for, so the row is
     * dimmed and disabled there rather than hidden (it still remembers the choice for next time
     * the theme goes dark).
     */
    private fun pureBlackRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val isDark = !MaterialColors.isColorLight(m3.surface)
        val switch = MaterialSwitch(context).apply {
            isChecked = ThemePrefs.usePureBlack(context)
            isEnabled = isDark
            setOnCheckedChangeListener { _, checked ->
                ThemePrefs.setUsePureBlack(context, checked)
                deps.onPureBlackChanged()
            }
        }
        return m3.row(
            title = "Pure black",
            supporting = if (isDark) {
                "Uses true black for dark mode surfaces instead of tinted panels."
            } else {
                "Needs dark mode to be active."
            },
            glyph = Icons.PURE_BLACK,
            trailing = switch
        ).apply { alpha = if (isDark) 1f else M3.Metrics.disabledAlpha }
    }

    /**
     * Ported from MorpheApp/morphe-manager: an animated backdrop drawn behind the whole app,
     * showing through the gaps between rows and cards. Picked from a chip grid (too many options
     * for the theme mode row's single 3-across line) rather than a dialog, matching the rest of
     * this card's inline pickers.
     */
    private fun backgroundAnimationBlock(
        context: Context,
        m3: M3,
        deps: SettingsTabDeps
    ): LinearLayout = m3.block("Background animation", Icons.BACKGROUND).apply {
        // NONE first (the "off" state) and RANDOM last; everything else keeps BackgroundType's
        // own declaration order.
        val options = listOf(BackgroundType.NONE) +
            BackgroundType.entries.filter { it != BackgroundType.NONE && it != BackgroundType.RANDOM } +
            listOf(BackgroundType.RANDOM)

        lateinit var group: ComposeButtonGroup

        fun choose(type: BackgroundType) {
            if (ThemePrefs.backgroundAnimation(context) == type.name) return
            ThemePrefs.setBackgroundAnimation(context, type.name)
            group.selectedIndex = options.indexOf(type).coerceAtLeast(0)
            deps.onBackgroundAnimationChanged(type)
        }

        val current = runCatching { BackgroundType.valueOf(ThemePrefs.backgroundAnimation(context)) }
            .getOrDefault(BackgroundType.NONE)

        // Three to a row: the full set is longer than a phone is wide, and the group wraps as one
        // connected run rather than as separate rows of pills.
        group = ComposeButtonGroup(
            context,
            options = options.map { it.displayName },
            selectedIndex = options.indexOf(current).coerceAtLeast(0),
            maxItemsInEachRow = 3
        ) { index -> choose(options[index]) }

        addView(group.view)

        addView(TextView(context).apply {
            text = "Shows through the gaps between cards."
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(M3.Space.xs), 0, 0)
        })
    }

    /** A normalized blur slider; the shared background is blurred up to a 32dp radius. */
    private fun cardBlurRow(context: Context, m3: M3): LinearLayout {
        val emphasised = M3.TypeStyle(12f, 16f, 0.5f, true, weight = 700)
        val value = TextView(context).apply {
            m3.styleText(this, emphasised, m3.onSurface)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            minWidth = paint.measureText("100%").toInt() + 1
            text = if (CardStyle.blurSupported) "${(CardStyle.blur * 100).roundToInt()}%" else "Off"
        }
        val slider = KeyPointSliderView(
            context,
            initialValue = if (CardStyle.blurSupported) CardStyle.blur else 0f,
            keyPoints = listOf(0.25f, 0.5f, 0.75f),
            onValueChange = { v ->
                value.text = "${(v * 100).roundToInt()}%"
                CardStyle.setBlur(context, v)
            },
            onValueChangeFinished = { v -> CardStyle.setBlur(context, v) }
        )
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "Card blur"
                // Compose's titleMedium as ReSukiSU uses it: 16sp medium.
                m3.styleText(this, M3.TypeStyle(16f, 24f, 0.15f, true), m3.onSurface)
            })
            addView(slider.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(48)
            ))
            slider.view.visibility = if (CardStyle.blurSupported) View.VISIBLE else View.GONE
            addView(TextView(context).apply {
                text = if (CardStyle.blurSupported) {
                    "Frosts the background behind cards. 0% uses solid cards."
                } else {
                    "Card blur needs Android 12 or newer."
                }
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            })
        }
        return m3.asRow(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(m3.dp(16), m3.dp(12), m3.dp(16), m3.dp(4))
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            addView(TextView(context).apply {
                text = Icons.OPACITY
                gravity = Gravity.CENTER
                m3.styleGlyph(this, 20f, m3.onSurface)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(m3.dp(24), m3.dp(24)))
            addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = m3.dp(16) })
            addView(value, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = m3.dp(16) })
        })
    }

    /**
     * "Connect cards": joins each group of rows into one continuous card (M3.connectRows keys on
     * [CardStyle.connected]). Only the card shapes change -- it does not touch the background.
     * Rebuilds the tabs so the new grouping takes effect across the app at once.
     */
    private fun cardConnectRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val switch = MaterialSwitch(context).apply {
            isChecked = CardStyle.connectedEnabled(context)
            setOnCheckedChangeListener { _, checked ->
                CardStyle.setConnected(context, checked)
                // Groups are joined into one card (or split back) as they are built, so the tabs
                // are rebuilt to pick it up. Posted rather than run in-line: this switch's own
                // click is still unwinding through CompoundButton's animation and accessibility
                // handling, and rebuilding its own tab out from under that -- removing this very
                // switch from the tree mid-dispatch -- would crash. One post lands after that
                // unwinds; the view's own Handler, so it is dropped harmlessly if the tab is gone.
                post { runCatching { deps.onPureBlackChanged() } }
            }
        }
        return m3.row(
            title = "Connect cards",
            supporting = "Join each group of settings into one continuous card.",
            glyph = Icons.APP_LIST,
            trailing = switch,
            onClick = { switch.toggle() }
        )
    }

    private fun glassRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val supported = com.elitedarkkaiser.redmagic.ui.glassbar.isGlassBlurSupported()
        val switch = MaterialSwitch(context).apply {
            isChecked = supported && ThemePrefs.useGlassBlur(context)
            isEnabled = supported
            setOnCheckedChangeListener { _, checked ->
                ThemePrefs.setUseGlassBlur(context, checked)
                deps.onGlassBlurChanged(checked)
            }
        }
        return m3.row(
            title = "Liquid glass bottom bar",
            supporting = if (supported) {
                "Blur the screen behind the bottom bar and the settings button."
            } else {
                "Needs Android 13 or newer."
            },
            glyph = Icons.BLUR,
            trailing = switch
        ).apply { alpha = if (supported) 1f else M3.Metrics.disabledAlpha }
    }

    /**
     * The one row moved here from the Cooling tab: that switch there now controls the fan itself,
     * so where the temperature unit is chosen had to move too rather than disappear.
     */
    private fun temperatureUnitRow(context: Context, m3: M3, deps: SettingsTabDeps): LinearLayout {
        val switch = MaterialSwitch(context).apply {
            isChecked = deps.getUseFahrenheit()
            setOnCheckedChangeListener { _, checked ->
                deps.setUseFahrenheit(checked)
                deps.saveUseFahrenheit(checked)
                deps.refreshStatus()
            }
        }
        return m3.row(
            title = "Use Fahrenheit",
            supporting = "Off shows temperatures in Celsius instead.",
            glyph = Icons.FAHRENHEIT,
            trailing = switch
        )
    }
}

