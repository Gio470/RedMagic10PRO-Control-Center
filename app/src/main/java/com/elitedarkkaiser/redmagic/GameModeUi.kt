package com.elitedarkkaiser.redmagic

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup
import kotlin.math.roundToInt

internal object GameModeUi {

    data class Deps(
        val dp: (Int) -> Int,
        val space: (Int) -> View,
        val colorDotDrawable: (String, Boolean) -> Drawable,
        val colorDotGeneric: (String, Boolean, () -> Unit) -> View
    )

    fun showGameModeProfileDialog(
        activity: MainActivity,
        current: GameModeProfile,
        deps: Deps,
        onSaveProfile: (GameModeProfile) -> Unit
    ) {
        val m3 = M3(activity)

        var gmFanEnabled = current.fanEnabled
        var gmFanLevel = current.fanLevel
        var gmPumpEnabled = current.pumpEnabled
        var gmPumpProfile = current.pumpProfile
        var gmFanLedEnabled = current.fanLedEnabled
        var gmFanLedEffect = current.fanLedEffect
        var gmFanLedColor = current.fanLedColor

        val container = M3Dialog.panel(activity)
        val scroll = M3Dialog.scroll(activity, container)

        val titleView = M3Dialog.title(activity, "Edit Game Profile")
        val subtitleView = M3Dialog.body(
            activity,
            "Applied when one of your games launches."
        )

        val fanEnableCheck = M3Dialog.checkBox(activity, "Enable fan override", gmFanEnabled) { checked ->
            gmFanEnabled = checked
        }

        val fanLevelLabel = M3Dialog.sectionLabel(activity, "Fan level: $gmFanLevel")

        val fanLevelSeek = com.elitedarkkaiser.redmagic.ui.ComposeSlider(
            context = activity,
            initialValue = gmFanLevel.toFloat(),
            initialValueRange = 0f..5f,
            initialSteps = 4,
            onValueChange = { raw ->
                gmFanLevel = raw.roundToInt()
                fanLevelLabel.text = "Fan level: $gmFanLevel"
            }
        )

        val pumpEnableCheck = M3Dialog.checkBox(activity, "Enable pump override", gmPumpEnabled) { checked ->
            gmPumpEnabled = checked
        }

        val pumpLabel = M3Dialog.sectionLabel(activity, "Pump profile")

        lateinit var pumpGroup: ValueButtonGroup<String>

        fun refreshPumpButtons() {
            pumpGroup.value = gmPumpProfile
        }

        pumpGroup = ValueButtonGroup(
            activity,
            listOf("Slow" to "slow", "Medium" to "medium", "Quick" to "quick"),
            gmPumpProfile
        ) { value ->
            GameModeActions.updatePumpProfile(
                value = value,
                onProfileChanged = { newValue -> gmPumpProfile = newValue },
                refreshButtons = { refreshPumpButtons() }
            )
        }

        val pumpRow = pumpGroup.view

        val ledEnableCheck = M3Dialog.checkBox(activity, "Enable fan LED override", gmFanLedEnabled) { checked ->
            gmFanLedEnabled = checked
        }

        val ledEffectLabel = M3Dialog.sectionLabel(activity, "Fan LED effect")

        lateinit var ledEffectGroup: ValueButtonGroup<String>

        fun refreshLedEffectButtons() {
            ledEffectGroup.value = gmFanLedEffect
        }

        ledEffectGroup = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flashing" to "flashing"),
            gmFanLedEffect
        ) { value ->
            GameModeActions.updateLedEffect(
                value = value,
                onEffectChanged = { newValue -> gmFanLedEffect = newValue },
                refreshButtons = { refreshLedEffectButtons() }
            )
        }

        val ledEffectRow = ledEffectGroup.view

        val ledColorLabel = M3Dialog.sectionLabel(activity, "Fan LED color")

        lateinit var colorRow: LinearLayout
        lateinit var colorRow2: LinearLayout

        fun refreshLedColorDots() {
            colorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", gmFanLedColor == 1)
            colorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", gmFanLedColor == 3)
            colorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", gmFanLedColor == 4)
            colorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", gmFanLedColor == 5)
            colorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", gmFanLedColor == 6)
            colorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", gmFanLedColor == 7)
            colorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", gmFanLedColor == 8)
            colorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", gmFanLedColor == 9)
        }

        fun gmColorDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, gmFanLedColor == id && !gmFanLedEffect.startsWith("preset:")) {
                GameModeActions.updateLedColor(
                    id = id,
                    currentEffect = gmFanLedEffect,
                    onColorChanged = { newColor -> gmFanLedColor = newColor },
                    onEffectChanged = { newEffect -> gmFanLedEffect = newEffect },
                    refreshColorDots = { refreshLedColorDots() },
                    refreshEffectButtons = { refreshLedEffectButtons() }
                )
            }
        }

        colorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(gmColorDot(1, "#FF0000"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(3, "#FF8C00"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(4, "#FFD600"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(5, "#00E676"))
        }

        colorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(gmColorDot(6, "#00E5FF"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(7, "#1565FF"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(8, "#A020F0"))
            addView(deps.space(deps.dp(8)))
            addView(gmColorDot(9, "#FF69B4"))
        }

        lateinit var preset1: View
        lateinit var preset2: View
        lateinit var preset3: View
        lateinit var preset4: View
        lateinit var preset5: View
        lateinit var preset6: View
        lateinit var preset7: View

        fun refreshPresetBubbles() {
            fun presetRing(selected: Boolean): GradientDrawable {
                return GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(deps.dp(3), if (selected) m3.onSurface else Color.TRANSPARENT)
                }
            }

            preset1.alpha = 1f
            preset2.alpha = 1f
            preset3.alpha = 1f
            preset4.alpha = 1f
            preset5.alpha = 1f
            preset6.alpha = 1f
            preset7.alpha = 1f

            preset1.background = presetRing(gmFanLedEffect == "preset:0x3002101")
            preset2.background = presetRing(gmFanLedEffect == "preset:0x3002102")
            preset3.background = presetRing(gmFanLedEffect == "preset:0x3002103")
            preset4.background = presetRing(gmFanLedEffect == "preset:0x3002104")
            preset5.background = presetRing(gmFanLedEffect == "preset:0x3002105")
            preset6.background = presetRing(gmFanLedEffect == "preset:0x3002106")
            preset7.background = presetRing(gmFanLedEffect == "preset:0x3002107")
        }

            fun gmPresetBubble(hex1: String, hex2: String, hex3: String, hex4: String, value: String): View {
        return object : View(activity) {
            private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = deps.dp(3).toFloat()
            }

            init {
                val size = deps.dp(42)
                layoutParams = LinearLayout.LayoutParams(size, size)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    GameModeActions.applyLedPreset(
                        value = value,
                        onEffectChanged = { gmFanLedEffect = it },
                        onColorChanged = { gmFanLedColor = it },
                        refreshEffectButtons = { refreshLedEffectButtons() },
                        refreshColorDots = { refreshLedColorDots() },
                        refreshPresetBubbles = { refreshPresetBubbles() }
                    )
                }
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)

                val pad = deps.dp(3).toFloat()
                val rect = android.graphics.RectF(
                    pad, pad,
                    width.toFloat() - pad,
                    height.toFloat() - pad
                )

                val save = canvas.save()
                val path = android.graphics.Path().apply {
                    addOval(rect, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(path)

                val midX = rect.centerX()
                val midY = rect.centerY()

                val colors = arrayOf(hex1, hex2, hex3, hex4)

                fillPaint.color = Color.parseColor(colors[0])
                canvas.drawRect(rect.left, rect.top, midX, midY, fillPaint)

                fillPaint.color = Color.parseColor(colors[1])
                canvas.drawRect(midX, rect.top, rect.right, midY, fillPaint)

                fillPaint.color = Color.parseColor(colors[2])
                canvas.drawRect(rect.left, midY, midX, rect.bottom, fillPaint)

                fillPaint.color = Color.parseColor(colors[3])
                canvas.drawRect(midX, midY, rect.right, rect.bottom, fillPaint)

                canvas.restoreToCount(save)

                ringPaint.color = if (gmFanLedEffect == "preset:$value") {
                    m3.onSurface
                } else {
                    Color.TRANSPARENT
                }

                canvas.drawOval(rect, ringPaint)
            }
        }
    }

        preset1 = gmPresetBubble("#FF69B4", "#FF8C00", "#1565FF", "#00E676", "0x3002101")
        preset2 = gmPresetBubble("#A020F0", "#FFD600", "#00E5FF", "#FF69B4", "0x3002102")
        preset3 = gmPresetBubble("#A020F0", "#00E5FF", "#1565FF", "#FF69B4", "0x3002103")
        preset4 = gmPresetBubble("#FF69B4", "#1565FF", "#1565FF", "#FF69B4", "0x3002104")
        preset5 = gmPresetBubble("#A020F0", "#00E5FF", "#00E5FF", "#A020F0", "0x3002105")
        preset6 = gmPresetBubble("#00E5FF", "#FF8C00", "#FF8C00", "#00E5FF", "0x3002106")
        preset7 = gmPresetBubble("#FFD600", "#00E676", "#00E676", "#FFD600", "0x3002107")

        val presetRow1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(preset1)
            addView(deps.space(deps.dp(8)))
            addView(preset2)
            addView(deps.space(deps.dp(8)))
            addView(preset3)
            addView(deps.space(deps.dp(8)))
            addView(preset4)
        }

        val presetRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(preset5)
            addView(deps.space(deps.dp(8)))
            addView(preset6)
            addView(deps.space(deps.dp(8)))
            addView(preset7)
        }

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") {}
        val saveBtn = M3Dialog.filledButton(activity, "Save") {}
        val buttonRow = M3Dialog.buttonRow(activity, cancelBtn, saveBtn)

        container.addView(titleView)
        container.addView(subtitleView)
        // pump UI omitted: RM 10 Pro has no liquid cooling pump.
        container.addView(m3.insetGroup("Fan").apply {
            addView(fanEnableCheck)
            addView(fanLevelLabel)
            addView(fanLevelSeek.view)
        })
        container.addView(m3.insetGroup("Fan LED").apply {
            addView(ledEnableCheck)
            addView(ledEffectLabel)
            addView(ledEffectRow)
            addView(ledColorLabel)
            addView(colorRow)
            addView(colorRow2)
            addView(presetRow1)
            addView(presetRow2)
        })

        var gmLogoLedEnabled = current.logoLedEnabled
        var gmLogoLedEffect = current.logoLedEffect
        var gmLogoLedColor = current.logoLedColor
        var gmShoulderLedEnabled = current.shoulderLedEnabled
        var gmShoulderLedEffect = current.shoulderLedEffect
        var gmShoulderLedColor = current.shoulderLedColor

        val logoEnable = M3Dialog.checkBox(activity, "Enable logo LED", gmLogoLedEnabled) { checked ->
            gmLogoLedEnabled = checked
        }

        val logoEffectRow = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flashing" to "flashing"),
            gmLogoLedEffect
        ) { value ->
            GameModeActions.updateLogoLedEffect(
                value = value,
                onEffectChanged = { newValue -> gmLogoLedEffect = newValue }
            )
        }.view

        lateinit var logoColorRow: LinearLayout
        lateinit var logoColorRow2: LinearLayout

        fun refreshLogoColorDots() {
            logoColorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", gmLogoLedColor == 1)
            logoColorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", gmLogoLedColor == 3)
            logoColorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", gmLogoLedColor == 4)
            logoColorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", gmLogoLedColor == 5)
            logoColorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", gmLogoLedColor == 6)
            logoColorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", gmLogoLedColor == 7)
            logoColorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", gmLogoLedColor == 8)
            logoColorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", gmLogoLedColor == 9)
        }

        fun logoDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, gmLogoLedColor == id) {
                GameModeActions.updateLogoLedColor(
                    id = id,
                    onColorChanged = { newColor -> gmLogoLedColor = newColor },
                    refreshColorDots = { refreshLogoColorDots() }
                )
            }
        }

        logoColorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(logoDot(1, "#FF0000"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(3, "#FF8C00"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(4, "#FFD600"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(5, "#00E676"))
        }

        logoColorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(logoDot(6, "#00E5FF"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(7, "#1565FF"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(8, "#A020F0"))
            addView(deps.space(deps.dp(8)))
            addView(logoDot(9, "#FF69B4"))
        }

        container.addView(m3.insetGroup("Logo LED").apply {
            addView(logoEnable)
            addView(logoEffectRow)
            addView(logoColorRow)
            addView(logoColorRow2)
        })

        val shoulderLabel = M3Dialog.sectionLabel(activity, "Shoulder LEDs")

        val shoulderEnable = M3Dialog.checkBox(activity, "Enable shoulder LEDs", gmShoulderLedEnabled) { checked ->
            gmShoulderLedEnabled = checked
        }

        lateinit var shoulderEffectGroup: ValueButtonGroup<String>

        fun refreshShoulderEffectButtons() {
            shoulderEffectGroup.value = gmShoulderLedEffect
        }

        shoulderEffectGroup = ValueButtonGroup(
            activity,
            listOf("Steady" to "steady", "Breathe" to "breathe", "Flashing" to "flashing"),
            gmShoulderLedEffect
        ) { value ->
            GameModeActions.updateShoulderLedEffect(
                value = value,
                onEffectChanged = { newValue -> gmShoulderLedEffect = newValue },
                refreshButtons = { refreshShoulderEffectButtons() }
            )
        }

        val shoulderEffectRow = shoulderEffectGroup.view

        lateinit var shoulderColorRow: LinearLayout
        lateinit var shoulderColorRow2: LinearLayout

        fun refreshShoulderColorDots() {
            shoulderColorRow.getChildAt(0).background = deps.colorDotDrawable("#FF0000", gmShoulderLedColor == 1)
            shoulderColorRow.getChildAt(2).background = deps.colorDotDrawable("#FF8C00", gmShoulderLedColor == 3)
            shoulderColorRow.getChildAt(4).background = deps.colorDotDrawable("#FFD600", gmShoulderLedColor == 4)
            shoulderColorRow.getChildAt(6).background = deps.colorDotDrawable("#00E676", gmShoulderLedColor == 5)
            shoulderColorRow2.getChildAt(0).background = deps.colorDotDrawable("#00E5FF", gmShoulderLedColor == 6)
            shoulderColorRow2.getChildAt(2).background = deps.colorDotDrawable("#1565FF", gmShoulderLedColor == 7)
            shoulderColorRow2.getChildAt(4).background = deps.colorDotDrawable("#A020F0", gmShoulderLedColor == 8)
            shoulderColorRow2.getChildAt(6).background = deps.colorDotDrawable("#FF69B4", gmShoulderLedColor == 9)
        }

        fun gmShoulderColorDot(id: Int, hex: String): View {
            return deps.colorDotGeneric(hex, gmShoulderLedColor == id) {
                GameModeActions.updateShoulderLedColor(
                    id = id,
                    onColorChanged = { newColor -> gmShoulderLedColor = newColor },
                    refreshColorDots = { refreshShoulderColorDots() }
                )
            }
        }

        shoulderColorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(gmShoulderColorDot(1, "#FF0000"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(3, "#FF8C00"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(4, "#FFD600"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(5, "#00E676"))
        }

        shoulderColorRow2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, deps.dp(12), 0, 0)
            addView(gmShoulderColorDot(6, "#00E5FF"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(7, "#1565FF"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(8, "#A020F0"))
            addView(deps.space(deps.dp(8)))
            addView(gmShoulderColorDot(9, "#FF69B4"))
        }

        // shoulder LED UI omitted: RM 10 Pro has no shoulder LED strip.

        container.addView(buttonRow)

        refreshPumpButtons()
        refreshLedEffectButtons()
        refreshLedColorDots()
        refreshPresetBubbles()
        refreshLogoColorDots()
        refreshShoulderEffectButtons()
        refreshShoulderColorDots()

        val dialog = M3Dialog.show(activity, scroll)

        cancelBtn.setOnClickListener { dialog.dismiss() }

        saveBtn.setOnClickListener {
            GameModeActions.saveProfile(
                context = activity,
                profile = GameModeActions.buildProfile(
                    fanEnabled = gmFanEnabled,
                    fanLevel = gmFanLevel,
                    pumpEnabled = gmPumpEnabled,
                    pumpProfile = gmPumpProfile,
                    fanLedEnabled = gmFanLedEnabled,
                    fanLedEffect = gmFanLedEffect,
                    fanLedColor = gmFanLedColor,
                    logoLedEnabled = gmLogoLedEnabled,
                    logoLedEffect = gmLogoLedEffect,
                    logoLedColor = gmLogoLedColor,
                    shoulderLedEnabled = gmShoulderLedEnabled,
                    shoulderLedEffect = gmShoulderLedEffect,
                    shoulderLedColor = gmShoulderLedColor
                ),
                persistProfile = onSaveProfile,
                onSaved = { dialog.dismiss() }
            )
        }
    }
}
