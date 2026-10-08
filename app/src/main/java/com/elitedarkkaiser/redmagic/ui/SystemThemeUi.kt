package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.systemtheme.MonetStyle
import com.elitedarkkaiser.redmagic.systemtheme.SystemThemeManager
import com.elitedarkkaiser.redmagic.systemtheme.SystemThemePrefs
import com.elitedarkkaiser.redmagic.systemtheme.ThemeEngine
import com.google.android.material.materialswitch.MaterialSwitch

/** A staged system palette editor with a live sample app and focused control groups. */
object SystemThemeUi {

    /** Serialises the card's apply/clear root calls; see [master]. */
    private val writeLock = Any()

    /**
     * The card's last word on whether the theme is on. The card reads its switch's state on every
     * frame it draws. Both the card switch and this page's Apply action keep the field in sync.
     */
    @Volatile private var enabledCache: Boolean? = null

    /**
     * Whether the phone is themed, for the card that opens this screen to carry.
     *
     * Not a switch taken off the screen like the other cards' are, because this screen is built
     * fresh on every open and there is no switch to take until it is. It is the preference and
     * the two root calls that used to sit behind that switch instead.
     *
     * Switching it on applies straight away rather than waiting to be asked, which is what the
     * switch on the screen did: there, Apply was a button away; from a card there is nothing to
     * press. Switching it off clears the overlay at once either way -- it is the way back out,
     * and a way back out you have to confirm is not one.
     */
    fun master(activity: Activity): IconifyCard.Master = IconifyCard.Master(
        read = { enabledCache ?: SystemThemePrefs.enabled(activity).also { enabledCache = it } },
        write = { on ->
            SystemThemePrefs.setEnabled(activity, on)
            enabledCache = on
            android.widget.Toast.makeText(
                activity,
                if (on) "Applying the system theme…" else "Clearing the system theme…",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            Thread {
                // One at a time, and each doing whatever the preference says *now* rather than
                // what it said when its thread started: two quick flips otherwise race, and an
                // apply that finishes after the clear leaves the phone themed under a card that
                // says it is not.
                val result = synchronized(writeLock) {
                    SystemThemeManager.rememberApk(activity)
                    if (SystemThemePrefs.enabled(activity)) {
                        SystemThemeManager.apply(activity)
                    } else {
                        SystemThemeManager.clear(activity)
                    }
                }
                activity.runOnUiThread {
                    android.widget.Toast.makeText(
                        activity, result.detail, android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }
    )

    fun show(activity: Activity) {
        val m3 = M3(activity)
        val handler = Handler(Looper.getMainLooper())

        // What the last Apply did, or how staging works when nothing has been applied yet -- the
        // line this screen exists to get right, as Iconify's info block.
        val idle = "Preview your palette here. Tap Apply theme to use it on the phone."
        val unapplied = "Unapplied changes. Tap Apply theme when you are ready."
        // Settings are saved as they change, so leaving without Apply used to lose track of them:
        // the screen reopened looking applied while the phone showed the old theme.
        val startPending = SystemThemeManager.hasUnapplied(activity)
        val (statusBlock, status) = IconifyKit.infoBlock(activity, if (startPending) unapplied else idle)

        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        var applying = false
        /** Set by any control; cleared by Apply. Nothing reaches the phone until it is. */
        var pending = startPending
        var applyButton: android.widget.Button? = null
        var resetButton: android.widget.Button? = null
        var touchBlocker: View? = null
        var liveAccent = SystemThemePrefs.accentSaturation(activity)
        var liveBackground = SystemThemePrefs.backgroundSaturation(activity)
        var liveLightness = SystemThemePrefs.backgroundLightness(activity)
        var previewDark = SystemThemeManager.isDark(activity)

        /** The palette a chip or a ramp should show, for whatever seed and style it stands for. */
        fun palette(seed: Int, style: MonetStyle,
            forDark: Boolean = SystemThemeManager.isDark(activity)): Array<IntArray> = ThemeEngine.generate(
            seed = seed,
            style = style,
            dark = forDark,
            accentSaturation = liveAccent,
            backgroundSaturation = liveBackground,
            backgroundLightness = liveLightness,
            pitchBlack = SystemThemePrefs.pitchBlack(activity),
            accurateShades = SystemThemePrefs.accurateShades(activity)
        )

        // Upstream addresses its ramps by index, and the indices are the whole recipe -- accent1
        // at 200, accent3 at 300, accent2 at 400, neutral2 at 700 for the tile. Named here so the
        // two chips cannot drift apart, and so it is obvious they are not arbitrary.
        val accentTone = ThemeEngine.TONE_NAMES.indexOf("200")
        val firstQuarterTone = ThemeEngine.TONE_NAMES.indexOf("300")
        val secondQuarterTone = ThemeEngine.TONE_NAMES.indexOf("400")
        val dark = SystemThemeManager.isDark(activity)
        val squareTone = ThemeEngine.TONE_NAMES.indexOf(if (dark) "700" else "50")
        val tickTone = ThemeEngine.TONE_NAMES.indexOf(if (dark) "50" else "900")

        fun chip(seed: Int, style: MonetStyle, withCentre: Boolean): PaletteSwatchView {
            val ramps = palette(seed, style)
            return PaletteSwatchView(
                context = activity,
                square = ramps[4][squareTone],
                halfCircle = ramps[0][accentTone],
                firstQuarter = ramps[2][firstQuarterTone],
                secondQuarter = ramps[1][secondQuarterTone],
                centre = if (withCentre) seed else null,
                // Light tick on a dark seed and the other way round: upstream picks between two
                // neutral tones by the seed's own contrast, and a tick you cannot see is the one
                // part of this chip that has a job.
                tick = if (withCentre &&
                    com.google.android.material.color.MaterialColors.isColorLight(seed)
                ) {
                    ramps[4][ThemeEngine.TONE_NAMES.indexOf("900")]
                } else {
                    ramps[4][tickTone]
                },
                // 6dp in the colour grid, 10dp in the styles list -- upstream's own two values.
                insetDp = if (withCentre) 6f else 10f
            )
        }

        /** A 48dp chip centred in its cell, which is how upstream's 12dp-padded grid measures. */
        fun cell(view: View, size: Int): android.widget.FrameLayout =
            android.widget.FrameLayout(activity).apply {
                addView(view, android.widget.FrameLayout.LayoutParams(
                    m3.dp(size), m3.dp(size), Gravity.CENTER
                ))
            }

        val preview = SystemThemePreview(activity)
        val previewSummary = TextView(activity).apply {
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(8), 0, m3.dp(8))
        }
        lateinit var updatePreview: () -> Unit
        val previewCard = m3.block("App preview", Icons.PREVIEW).apply {
            tag = null
            addView(preview)
            addView(previewSummary)
            addView(ValueButtonGroup(
                context = activity,
                entries = listOf("Light preview" to false, "Dark preview" to true),
                selected = previewDark,
                onSelect = { selected -> previewDark = selected; updatePreview() }
            ).view)
        }

        // ---- ramps --------------------------------------------------------------------------

        val rampCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // A filled card, as Iconify draws every container: surfaceContainerHigh at 24dp.
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(M3.Space.xs) }
        }

        rampCard.visibility = View.GONE
        fun renderRamps() {
            val seed = SystemThemeManager.seed(activity)
            val style = SystemThemePrefs.style(activity)
            preview.render(palette(seed, style, previewDark), previewDark, SystemThemePrefs.tintText(activity))
            previewSummary.text = "${style.label} · ${ColorPaletteDialog.hex(seed)} · Sample app"
            if (rampCard.visibility != View.VISIBLE) return
            rampCard.removeAllViews()
            val ramps = palette(seed, style)
            listOf(
                "Primary" to 0, "Secondary" to 1, "Tertiary" to 2,
                "Neutral 1" to 3, "Neutral 2" to 4
            ).forEachIndexed { row, (label, index) ->
                rampCard.addView(TextView(activity).apply {
                    text = label
                    m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
                    setPadding(0, if (row == 0) 0 else m3.dp(M3.Space.md), 0, m3.dp(M3.Space.xs))
                })
                rampCard.addView(
                    ToneRampView(activity, ramps[index]),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(M3.Space.xl)
                    )
                )
            }
        }

        // ---- applying -------------------------------------------------------------------------

        updatePreview = { renderRamps() }
        val previewRefresh = Runnable { updatePreview() }
        lateinit var refreshChips: () -> Unit

        /**
         * Redraws the previews and marks the change as not yet written.
         *
         * Every control used to write straight through, which meant a slider drag was a string of
         * root shells and -- because changing a framework overlay makes the system rebuild the
         * resources every running app is holding -- a string of screens rebuilding under the
         * finger. The previews on this sheet are generated from the same code the overlay is, so
         * staging costs nothing to see and the write happens once, when asked for.
         */
        fun stage() {
            handler.removeCallbacks(previewRefresh)
            renderRamps()
            refreshChips()
            pending = SystemThemeManager.hasUnapplied(activity)
            status.text = if (pending) unapplied else idle
            applyButton?.isEnabled = pending && !applying
        }

        fun applyNow() {
            if (applying) return
            applying = true
            pending = false
            applyButton?.isEnabled = false
            status.text = "Applying theme…"
            touchBlocker?.visibility = View.VISIBLE
            resetButton?.isEnabled = false
            Thread {
                val result = runCatching { synchronized(writeLock) {
                    SystemThemePrefs.setEnabled(activity, true)
                    enabledCache = true
                    SystemThemeManager.rememberApk(activity)
                    SystemThemeManager.apply(activity)
                } }.getOrElse { error ->
                    SystemThemeManager.Result(false, "Could not apply theme: ${error.message ?: "Unknown error"}")
                }
                // Anything staged while the write ran is still waiting for its own Apply.
                val stillPending = SystemThemeManager.hasUnapplied(activity)
                handler.post {
                    applying = false
                    touchBlocker?.visibility = View.GONE
                    resetButton?.isEnabled = true
                    pending = stillPending
                    status.text = result.detail
                    applyButton?.isEnabled = pending
                }
            }.start()
        }

        // No master switch here: it is on the card that opens this screen, where it can be
        // reached without opening it at all. See [master].
        body.addView(previewCard)
        body.addView(statusBlock)

        // ---- colours ----------------------------------------------------------------------------

        var showingWallpaper = SystemThemePrefs.followWallpaper(activity)
        val colourGrid = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(12), 0, 0)
        }

        fun renderColours() {
            colourGrid.removeAllViews()
            val style = SystemThemePrefs.style(activity)
            // The seed actually in force -- for a wallpaper that is the colour it resolves to,
            // so the tile lit is the one the previews are showing.
            val seed = SystemThemeManager.seed(activity)
            val following = SystemThemePrefs.followWallpaper(activity)

            val colours = if (showingWallpaper) {
                SystemThemeManager.wallpaperColors(activity)
            } else {
                SystemThemeManager.BASIC_COLORS.toList()
            }

            if (colours.isEmpty()) {
                colourGrid.addView(IconifyKit.emptyState(
                    activity,
                    "This wallpaper publishes no colours. Pick a basic one instead.",
                    Icons.PAINTBRUSH
                ))
                return
            }

            // Iconify's preview cards, four to a row: the palette the colour would make over its
            // hex, lit in the primary pair when it is the one in force.
            val tiles = colours.map { colour ->
                val chosen = colour == seed && following == showingWallpaper
                IconifyKit.previewCard(
                    activity,
                    cell(chip(colour, style, withCentre = true).apply { checked = chosen }, 48),
                    ColorPaletteDialog.hex(colour),
                    chosen
                ) {
                    SystemThemePrefs.setFollowWallpaper(activity, showingWallpaper)
                    SystemThemePrefs.setSeed(activity, colour)
                    stage()
                }
            }.toMutableList()

            if (!showingWallpaper) {
                // Upstream's eyedropper: the presets are a starting point, not the choice.
                val custom = !following && SystemThemeManager.BASIC_COLORS.none { it == seed }
                tiles += IconifyKit.previewCard(
                    activity,
                    cell(chip(seed, style, withCentre = true).apply { checked = custom }, 48),
                    "Custom",
                    custom
                ) {
                    ColorPaletteDialog.show(activity, seed) { picked ->
                        if (picked != null) {
                            SystemThemePrefs.setFollowWallpaper(activity, false)
                            SystemThemePrefs.setSeed(activity, picked)
                            stage()
                        }
                    }
                }
            }

            colourGrid.addView(IconifyKit.grid(activity, tiles, columns = 4))
        }

        val colorsCard = m3.block("Theme color", Icons.COLOR_PALETTE).apply { tag = null }
        body.addView(colorsCard)
        colorsCard.addView(ValueButtonGroup(
            context = activity,
            entries = listOf("Wallpaper colors" to true, "Basic colors" to false),
            selected = showingWallpaper,
            onSelect = { wallpaper ->
                showingWallpaper = wallpaper
                renderColours()
            }
        ).view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        colorsCard.addView(colourGrid)

        // ---- styles -----------------------------------------------------------------------------

        val stylesList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        fun renderStyles() {
            stylesList.removeAllViews()
            val seed = SystemThemeManager.seed(activity)
            val selected = SystemThemePrefs.style(activity)

            // Iconify's preview cards, three to a row, each the style's own palette over its name.
            stylesList.addView(IconifyKit.grid(activity, MonetStyle.entries.map { style ->
                IconifyKit.previewCard(
                    activity,
                    cell(chip(seed, style, withCentre = false), 56),
                    style.label,
                    style == selected
                ) {
                    SystemThemePrefs.setStyle(activity, style)
                    stage()
                }
            }, columns = 3))

            // A card has room for a name and nothing else, and a style called "Fruit Salad" means
            // nothing without the sentence under it -- so the chosen one's is spelled out here.
            stylesList.addView(TextView(activity).apply {
                text = selected.description
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                setPadding(0, m3.dp(12), 0, 0)
            })
        }

        body.addView(m3.block("Palette style", Icons.PAINTBRUSH).apply {
            tag = null
            addView(stylesList)
        })

        refreshChips = {
            renderColours()
            renderStyles()
        }

        // ---- theme ------------------------------------------------------------------------------

        val adjustmentsCard = m3.block("Fine-tune", Icons.CONFIGURE).apply { tag = null }
        body.addView(adjustmentsCard)

        val sliders = mutableListOf<ComposeSlider>()
        val switches = mutableListOf<Pair<MaterialSwitch, Boolean>>()
        val sliderValues = mutableListOf<TextView>()

        fun themeSlider(label: String, summary: String, glyph: String, read: () -> Int,
            write: (Int) -> Unit, previewChange: (Int) -> Unit) {
            val value = TextView(activity).apply { text = "${read()}%" }
            val slider = ComposeSlider(
                context = activity,
                initialValue = read().toFloat(),
                // Upstream's own range. Half of it (50..150) was the other reason these read as
                // doing nothing: every effect was at half travel before it started.
                initialValueRange = 0f..200f,
                // Render the sample during the drag; save settings when the gesture ends.
                // The phone's system palette changes only after Apply theme.
                onValueChange = { raw ->
                    value.text = "${raw.toInt()}%"
                    previewChange(raw.toInt())
                    handler.removeCallbacks(previewRefresh)
                    handler.postDelayed(previewRefresh, 32)
                },
                onValueChangeFinished = { raw ->
                    write(raw.toInt())
                    previewChange(raw.toInt())
                    value.text = "${raw.toInt()}%"
                    stage()
                }
            )
            sliders += slider
            sliderValues += value
            adjustmentsCard.addView(IconifyKit.sliderRow(activity, label, summary, value, slider.view, glyph))
        }

        themeSlider(
            "Accent saturation",
            "How vivid the accent colours are",
            Icons.COLOR_PALETTE,
            { SystemThemePrefs.accentSaturation(activity) },
            { SystemThemePrefs.setAccentSaturation(activity, it) },
            { liveAccent = it }
        )
        themeSlider(
            "Background saturation",
            "How much colour the backgrounds carry",
            Icons.PAINTBRUSH,
            { SystemThemePrefs.backgroundSaturation(activity) },
            { SystemThemePrefs.setBackgroundSaturation(activity, it) },
            { liveBackground = it }
        )
        themeSlider(
            "Background lightness",
            "How light or dark the backgrounds sit",
            Icons.THEME_MODE,
            { SystemThemePrefs.backgroundLightness(activity) },
            { SystemThemePrefs.setBackgroundLightness(activity, it) },
            { liveLightness = it }
        )

        // ---- settings ----------------------------------------------------------------------------

        val appearanceCard = m3.block("Appearance", Icons.THEME_MODE).apply { tag = null }
        body.addView(appearanceCard)

        var resetting = false

        fun switchRow(
            title: String,
            supporting: String,
            glyph: String,
            default: Boolean,
            read: () -> Boolean,
            write: (Boolean) -> Unit
        ) {
            val toggle = MaterialSwitch(activity).apply {
                isChecked = read()
                setOnCheckedChangeListener { _, checked ->
                    write(checked)
                    if (!resetting) stage()
                }
            }
            switches += toggle to default
            appearanceCard.addView(m3.row(
                title = title,
                supporting = supporting,
                glyph = glyph,
                trailing = toggle,
                // The whole row flips it, not just the switch, as every other row in the app does.
                onClick = { toggle.toggle() }
            ))
        }

        switchRow(
            "Accurate shades",
            "Off copies accent 300 into accent 100, the way older Pixels did.",
            Icons.COLOR_PALETTE,
            false,
            { SystemThemePrefs.accurateShades(activity) },
            { SystemThemePrefs.setAccurateShades(activity, it) }
        )
        switchRow(
            "Pitch black theme",
            "True black backgrounds in dark mode.",
            Icons.PURE_BLACK,
            false,
            { SystemThemePrefs.pitchBlack(activity) },
            { SystemThemePrefs.setPitchBlack(activity, it) }
        )
        switchRow(
            "Tint text color",
            "Off makes body text plain white and black instead of tinted.",
            Icons.MONOCHROME,
            false,
            { SystemThemePrefs.tintText(activity) },
            { SystemThemePrefs.setTintText(activity, it) }
        )

        body.addView(m3.block("Palette details", Icons.COLOR_PALETTE).apply {
            tag = null
            addView(m3.button("Show tonal palettes", M3.ButtonKind.Text).apply {
                setOnClickListener {
                    val show = rampCard.visibility != View.VISIBLE
                    rampCard.visibility = if (show) View.VISIBLE else View.GONE
                    text = if (show) "Hide tonal palettes" else "Show tonal palettes"
                    renderRamps()
                }
            })
            addView(rampCard)
        })

        // ---- frame -------------------------------------------------------------------------------

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            addView(M3Dialog.title(activity, "System theme"))
            addView(body)
        }


        // Reset and Apply only: the way out is the back bar every page has. Reset is tonal rather
        // than plain text because it is a real action with a real cost.
        val buttons = M3Dialog.actionRow(
            activity,
            leading = M3Dialog.tonalButton(activity, "Reset tuning") {
                SystemThemePrefs.resetTheme(activity)
                liveAccent = 100
                liveBackground = 100
                liveLightness = 100
                sliders.forEach { it.value = 100f }
                sliderValues.forEach { it.text = "100%" }
                // One stage for the lot rather than one per switch the reset flips.
                resetting = true
                switches.forEach { (toggle, default) -> toggle.isChecked = default }
                resetting = false
                stage()
            }.also { resetButton = it },
            M3Dialog.filledButton(activity, "Apply theme") { applyNow() }.also {
                it.isEnabled = pending
                applyButton = it
            }
        )

        val scroller = M3Dialog.scroll(activity, content)
        val panel = M3Dialog.panel(activity).apply {
            // Whatever is left under the page's title and over its action row, rather than the
            // share of the screen this used to guess at. The guess dated from when this was a
            // sheet that sized itself to its content; on a page it added up to more than the
            // screen and pushed the buttons off the bottom of it.
            addView(android.widget.FrameLayout(activity).apply {
                addView(scroller, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(View(activity).apply {
                    isClickable = true
                    visibility = View.GONE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setOnTouchListener { _, _ -> true }
                    touchBlocker = this
                }, android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }

        renderColours()
        renderStyles()
        renderRamps()

        m3.connectRows(adjustmentsCard)
        m3.connectRows(appearanceCard)
        m3.tintWidgets(content)
        panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { handler.removeCallbacks(previewRefresh) }
        })
        M3Dialog.show(activity, panel)
        content.requestFocus()
        // Opens at the top, preview first. Focus landing in the middle of the page (the Compose
        // button group takes it) used to scroll the preview and the status line out of sight.
        handler.post { scroller.scrollTo(0, 0) }
    }
}

