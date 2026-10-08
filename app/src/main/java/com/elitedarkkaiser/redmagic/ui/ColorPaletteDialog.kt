package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import java.util.Locale

/**
 * Picking the colour the whole palette is generated from, ported from Gio470/Bus-tracker's
 * PaletteDialog.
 *
 * Upstream is a Compose AlertDialog with a wheel tab and an RGB-slider tab. This keeps the wheel
 * (see [ColorWheelView]) and the hex field, drops the RGB sliders for a row of presets, and is a
 * bottom sheet like every other dialog in this app -- three sliders that set the same thing the
 * wheel already sets is a second way to do one job, where a preset row is the fast path the wheel
 * does not give you.
 *
 * What is picked is a *seed*, not the accent itself: Material derives every tonal role from it, so
 * the colour that ends up on screen is a tone of what was chosen rather than the exact value. The
 * dialog says so, because picking a dark navy and getting a light blue accent otherwise reads as
 * the picker being broken.
 */
object ColorPaletteDialog {

    /** Seeds worth offering, spread round the wheel so the row is a range rather than a mood. */
    private val PRESETS = intArrayOf(
        0xFFE53935.toInt(), // red -- the RedMagic one
        0xFFF56A1C.toInt(), // orange, upstream's default
        0xFFFFB300.toInt(), // amber
        0xFF43A047.toInt(), // green
        0xFF00ACC1.toInt(), // teal
        0xFF1E88E5.toInt(), // blue
        0xFF5E35B1.toInt(), // deep purple
        0xFFD81B60.toInt()  // pink
    )

    fun show(activity: Activity, initial: Int?, onPicked: (Int?) -> Unit) {
        val m3 = M3(activity)
        var current = initial ?: PRESETS[0]
        var style = Palette.Style.of(ThemePrefs.paletteStyle(activity))

        val swatch = View(activity)
        // What the app will actually show. The seed is not the accent -- Material rebuilds
        // every role off it -- and without this the only way to find out that a vivid yellow
        // comes back muted was to apply it and look, which is exactly how "it does not apply"
        // gets reported. Now the two swatches sit side by side and disagree visibly.
        val resultSwatch = View(activity)
        val hexField = M3Dialog.textField(activity, "#RRGGBB").apply {
            filters = arrayOf(InputFilter.LengthFilter(7))
        }
        val wheel = ColorWheelView(activity)
        /** Set while a control is being updated to match another, so they do not drive each other. */
        var syncing = false

        fun renderSwatch() {
            swatch.background = m3.surfaceShape(current, M3.Shape.full, m3.outlineVariant)
            resultSwatch.background = m3.surfaceShape(
                Palette.previewPrimary(activity, current, style),
                M3.Shape.full,
                m3.outlineVariant
            )
        }

        // Declared before apply() so it can drive it, assigned after so its own callback can call
        // apply() in turn. It is only ever touched from a user gesture or from apply's final
        // sync, both of which happen long after this returns.
        lateinit var brightnessSlider: ComposeSlider

        fun apply(colour: Int, from: String) {
            current = colour
            syncing = true
            renderSwatch()
            if (from != "wheel") wheel.setColor(colour)
            if (from != "hex") {
                // Touching any other control ends the hex edit. Without this the field keeps focus
                // after typing, and the guard below would then leave it showing a stale value for
                // the rest of the session; with it, focus is gone after the first event of a drag
                // and the rewrite costs nothing on the ones that follow.
                if (hexField.hasFocus()) {
                    hexField.clearFocus()
                    hideKeyboard(hexField)
                }
                hexField.setText(hex(colour))
            }
            if (from != "slider") brightnessSlider.value = hsvValue(colour) * 100f
            if (from != "wheel") wheel.setValue(hsvValue(colour))
            syncing = false
        }

        wheel.onColorChanged = { picked ->
            if (!syncing) apply(picked, from = "wheel")
        }

        brightnessSlider = ComposeSlider(
            context = activity,
            initialValue = hsvValue(current) * 100f,
            initialValueRange = 0f..100f,
            onValueChange = { raw ->
                if (!syncing) {
                    val hsv = FloatArray(3)
                    Color.colorToHSV(current, hsv)
                    hsv[2] = raw / 100f
                    wheel.setValue(hsv[2])
                    apply(Color.HSVToColor(hsv), from = "slider")
                }
            }
        )

        hexField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                // Only once it is a whole colour: every partial string on the way to one would
                // otherwise be parsed, and "#F" is not a colour anyone is asking for.
                val parsed = parseHex(s?.toString()) ?: return
                apply(parsed, from = "hex")
            }
        })

        val styleGroup = ValueButtonGroup(
            context = activity,
            entries = Palette.Style.entries.map { it.label to it },
            selected = style,
            maxItemsInEachRow = 4,
            onSelect = { picked ->
                style = picked
                renderSwatch()
            }
        )

        val presetRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(M3.Space.xxs), 0, 0)
            PRESETS.forEachIndexed { index, preset ->
                addView(View(activity).apply {
                    // A 40dp pill in a 48dp target, as the spec draws a small button.
                    background = android.graphics.drawable.InsetDrawable(
                        m3.filled(preset, M3.Shape.full, Color.WHITE), 0, m3.dp(4), 0, m3.dp(4)
                    )
                    contentDescription = "Use ${hex(preset)}"
                    setOnClickListener { apply(preset, from = "preset") }
                    m3.springPress(this, pressedScale = 0.9f)
                }, LinearLayout.LayoutParams(0, m3.dp(M3.Metrics.touchTarget), 1f).apply {
                    if (index > 0) marginStart = m3.dp(M3.Space.xs)
                })
            }
        }

        // Everything above the buttons, in one column that scrolls. The wheel alone is most of a
        // screen tall, so without this the buttons sat below the bottom of the sheet -- which is
        // why there was no way to apply anything.
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // Takes the initial focus so the hex field does not, which would open the keyboard
            // over the wheel the moment the sheet appears.
            isFocusableInTouchMode = true

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(M3Dialog.title(activity, "Color Palette"), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                addView(swatch, LinearLayout.LayoutParams(m3.dp(28), m3.dp(28)))
                addView(M3Dialog.hGap(activity, 6))
                addView(resultSwatch, LinearLayout.LayoutParams(m3.dp(28), m3.dp(28)))
            })
            addView(M3Dialog.body(
                activity,
                "The palette is generated from this colour. The left swatch is what you pick; the " +
                    "right one is the accent it produces. Auto holds a hand-picked colour at its " +
                    "own saturation and mutes a wallpaper one, the way the system does."
            ))
            // Fixed and centred rather than full width: the view squares itself to whatever width
            // it is given, so MATCH_PARENT made it as tall as the sheet was wide.
            addView(wheel, LinearLayout.LayoutParams(m3.dp(232), m3.dp(232)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(M3Dialog.sectionLabel(activity, "Brightness"))
            addView(brightnessSlider.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(M3Dialog.sectionLabel(activity, "Style"))
            addView(styleGroup.view, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(M3Dialog.sectionLabel(activity, "Presets"))
            addView(presetRow)
            addView(M3Dialog.sectionLabel(activity, "Hex"))
            addView(hexField)
        }

        lateinit var dialog: Panel

        // Reset at one end, Apply at the other, with the gap between them doing the spacing. The
        // stock button row right-aligns everything, and three buttons of this width overflowed it
        // -- Apply, being last, was the one pushed off the edge. Cancel is gone with it: a sheet
        // closes on a swipe or a tap outside, which is how every other dialog here is dismissed.
        // Reset at one end, Apply at the other. The row that right-aligned all three is what
        // pushed Apply off the edge of the screen once; actionRow pins each end instead.
        val buttons = M3Dialog.actionRow(
            activity,
            // Back to the palette baked into the theme, which is what "no colour picked" means.
            leading = M3Dialog.tonalButton(activity, "Reset") {
                ThemePrefs.setPaletteStyle(activity, Palette.Style.AUTO.name)
                dialog.dismiss()
                onPicked(null)
            },
            M3Dialog.filledButton(activity, "Apply") {
                ThemePrefs.setPaletteStyle(activity, style.name)
                dialog.dismiss()
                onPicked(current)
            }
        )

        val panel = M3Dialog.panel(activity).apply {
            // Whatever is left over the buttons, which is what the cap here was reaching for by
            // guessing a share of the screen back when this was a sheet.
            addView(M3Dialog.scroll(activity, content), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(buttons)
        }

        apply(current, from = "init")
        dialog = M3Dialog.show(activity, panel)
        content.requestFocus()
    }

    /** So the keyboard goes with the focus rather than sitting over the wheel being dragged. */
    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun hsvValue(colour: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(colour, hsv)
        return hsv[2]
    }

    fun hex(colour: Int): String =
        String.format(Locale.US, "#%06X", colour and 0xFFFFFF)

    private fun parseHex(raw: String?): Int? {
        val text = raw?.trim()?.removePrefix("#") ?: return null
        if (text.length != 6) return null
        return runCatching { Color.parseColor("#$text") }.getOrNull()
    }
}
