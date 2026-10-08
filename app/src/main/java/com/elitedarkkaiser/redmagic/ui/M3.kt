package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import kotlin.math.abs
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.color.MaterialColors
import com.google.android.material.R.attr as MaterialAttr
import kotlin.math.roundToInt

/**
 * Material 3 Expressive design system for the app's hand-drawn Views.
 *
 * Colors are the real M3 tonal roles, read off the Activity theme — which RedMagicApplication has
 * already recolored from the wallpaper on Android 12+ — so the whole app follows Material You.
 * The hex values are only a fallback for an unresolved attribute.
 *
 * Every other token here -- [Shape], [Type], [Motion], [Space] and the component metrics -- is the
 * published M3 value, not a tuning of it: the numbers are the ones the Material 3 skill
 * (hamen/material-3-skill) documents and the Compose Material3 token tables carry. Where the app
 * needs something the spec leaves open, the choice is made *from* these tokens rather than beside
 * them, and says so where it is made.
 */
class M3(private val context: Context) {

    /**
     * Every colour in the app comes through here, which is why the generated palette can be swapped
     * in at one line: [Palette] answers for the roles it models, and anything it does not falls
     * through to the theme exactly as before.
     */
    private fun color(attr: Int, fallback: String): Int =
        Palette.color(context, attr)
            ?: MaterialColors.getColor(context, attr, Color.parseColor(fallback))

    // Accent roles
    val primary: Int get() = color(MaterialAttr.colorPrimary, "#A8C7FA")
    val onPrimary: Int get() = color(MaterialAttr.colorOnPrimary, "#0A0D12")
    val primaryContainer: Int get() = color(MaterialAttr.colorPrimaryContainer, "#2B3A4F")
    val onPrimaryContainer: Int get() = color(MaterialAttr.colorOnPrimaryContainer, "#D7E3F7")
    /** Bare secondary/tertiary hero colors, distinct from their (muted) container roles below --
     *  used to give the animated background variety instead of every shape reading as primary. */
    val secondary: Int get() = color(MaterialAttr.colorSecondary, "#B8C4D9")
    /**
     * [primary] with a floor under its chroma, for the one place a *readable* accent matters more
     * than an exact tonal role: the bottom bar's selected tab.
     *
     * A tonal-spot primary in dark mode is a tone-80 pastel -- around #A3C9FE off this app's own
     * seed -- and onSurface is a tone-90 near-white. Those are two pale colours a couple of steps
     * apart in lightness, which at 11sp over a blurred bar reads as no difference at all, and a
     * bar whose selected tab does not look selected is the complaint that produced this. Pushing
     * chroma up and tone down turns the accent into an obviously coloured thing while leaving its
     * hue -- and so the palette's identity -- alone. Hct.from clamps to what sRGB can actually
     * hold at that tone, so this asks for more chroma than it can get and takes what it gets.
     */
    val primaryVivid: Int
        get() {
            val hct = com.google.android.material.color.utilities.Hct.fromInt(primary)
            val dark = !MaterialColors.isColorLight(surface)
            return com.google.android.material.color.utilities.Hct.from(
                hct.hue,
                maxOf(hct.chroma, 72.0),
                if (dark) 74.0 else 44.0
            ).toInt()
        }
    val tertiary: Int get() = color(MaterialAttr.colorTertiary, "#D5BCE3")
    val secondaryContainer: Int get() = color(MaterialAttr.colorSecondaryContainer, "#2B3A4F")
    val onSecondaryContainer: Int get() = color(MaterialAttr.colorOnSecondaryContainer, "#D7E3F7")
    val tertiaryContainer: Int get() = color(MaterialAttr.colorTertiaryContainer, "#3B2F4A")
    val onTertiaryContainer: Int get() = color(MaterialAttr.colorOnTertiaryContainer, "#EFDBFF")

    // Destructive
    val error: Int get() = color(MaterialAttr.colorError, "#F2B8B5")
    val onError: Int get() = color(MaterialAttr.colorOnError, "#601410")
    val errorContainer: Int get() = color(MaterialAttr.colorErrorContainer, "#5B2C33")
    val onErrorContainer: Int get() = color(MaterialAttr.colorOnErrorContainer, "#F9DEDC")

    /**
     * True black replaces the whole dark tonal ladder with near-black steps when the user has
     * turned Pure Black on and the resolved theme is actually dark -- it must never darken a
     * light scheme, which has no "black" step to reach for.
     */
    private val pureBlackActive: Boolean
        get() = ThemePrefs.usePureBlack(context) &&
            !MaterialColors.isColorLight(color(MaterialAttr.colorSurface, "#0A0D12"))

    // Surfaces — the tonal ladder Expressive leans on to separate grouped content
    val surface: Int get() = if (pureBlackActive) Color.BLACK else color(MaterialAttr.colorSurface, "#0A0D12")
    val surfaceContainerLow: Int get() = if (pureBlackActive) Color.parseColor("#0A0A0A") else color(MaterialAttr.colorSurfaceContainerLow, "#121720")
    val surfaceContainer: Int get() = if (pureBlackActive) Color.parseColor("#121212") else color(MaterialAttr.colorSurfaceContainer, "#161C27")
    val surfaceContainerHigh: Int get() = if (pureBlackActive) Color.parseColor("#1C1C1C") else color(MaterialAttr.colorSurfaceContainerHigh, "#1A2230")
    val surfaceContainerHighest: Int get() = if (pureBlackActive) Color.parseColor("#242424") else color(MaterialAttr.colorSurfaceContainerHighest, "#202B38")
    /**
     * What rows and blocks sit on: surfaceContainerHigh in both themes, as Iconify's preference
     * items are drawn (PreferenceContainer's default containerColor).
     */
    val rowSurface: Int
        get() = surfaceContainerHigh
    val onSurface: Int get() = color(MaterialAttr.colorOnSurface, "#E8EEF7")
    val onSurfaceVariant: Int get() = color(MaterialAttr.colorOnSurfaceVariant, "#C0C8D4")
    val outline: Int get() = color(MaterialAttr.colorOutline, "#8B95A5")
    val outlineVariant: Int get() = color(MaterialAttr.colorOutlineVariant, "#414A57")

    // The rest of the scheme: rarely drawn with directly, but a Compose component reads them all
    // off its MaterialTheme, so they have to exist for it to be themed from the same palette.
    val onSecondary: Int get() = color(MaterialAttr.colorOnSecondary, "#233144")
    val onTertiary: Int get() = color(MaterialAttr.colorOnTertiary, "#3B2948")
    val surfaceVariant: Int get() = color(MaterialAttr.colorSurfaceVariant, "#414A57")
    val surfaceContainerLowest: Int get() = if (pureBlackActive) Color.BLACK else color(MaterialAttr.colorSurfaceContainerLowest, "#070A0F")
    val surfaceDim: Int get() = if (pureBlackActive) Color.BLACK else color(MaterialAttr.colorSurfaceDim, "#0A0D12")
    val surfaceBright: Int get() = color(MaterialAttr.colorSurfaceBright, "#30363F")
    val inverseSurface: Int get() = color(MaterialAttr.colorSurfaceInverse, "#E8EEF7")
    val inverseOnSurface: Int get() = color(MaterialAttr.colorOnSurfaceInverse, "#2B3038")
    val inversePrimary: Int get() = color(MaterialAttr.colorPrimaryInverse, "#3A5F8F")

    /**
     * The M3 corner scale, in dp -- the published values, extended by the three Expressive steps.
     *
     * Which step a component takes follows the skill's component mapping: buttons, chips' pills,
     * switches and badges are [full]; cards (and so the row groups, which are a card split into
     * segments) are [medium]; the FAB is [large]; dialogs and sheets are [extraLarge]; text fields
     * and menus are [small] or, for a filled field's top edge, [extraSmall].
     *
     * This used to be a retuned copy of the scale (12/16/20 where M3 runs 8/12/16, 24 for cards)
     * taken from another app's look. Every size moved down to the spec at once, so relative
     * relationships between components -- a chip rounder than its card, a card rounder than its
     * segments -- are the ones the spec intends rather than the ones the retuning happened to keep.
     */
    object Shape {
        const val none = 0
        const val extraSmall = 4
        const val small = 8
        const val medium = 12
        const val large = 16
        const val largeIncreased = 20
        const val extraLarge = 28
        const val extraLargeIncreased = 32
        const val extraExtraLarge = 48
        const val full = 999
    }

    /**
     * The M3 spacing scale: the 4dp grid, with the 8dp steps the I/O 2026 spacing system is built
     * from. Screen margin is [md] on compact windows ([lg] is a section break); gutters between
     * side-by-side items are [xs].
     */
    object Space {
        const val xxs = 4
        const val xs = 8
        const val sm = 12
        const val md = 16
        const val lg = 24
        const val xl = 32
        const val xxl = 48
    }

    /**
     * Component metrics the spec fixes, gathered here so a screen building one of these by hand
     * cannot drift from the one next to it.
     */
    object Metrics {
        /** Minimum touch target, whatever the visual size of the control. */
        const val touchTarget = 48

        /** List item: container heights by line count, leading/trailing inset, avatar size. */
        const val listOneLine = 56
        const val listTwoLine = 72
        const val listThreeLine = 88
        const val listInset = 16
        const val listAvatar = 40

        /** The gap between the segments of a connected group (Expressive connected button group). */
        const val connectedGap = 2

        /** Buttons: small (the default) and medium, with their leading/trailing space. */
        const val buttonSmall = 40
        const val buttonMedium = 56
        const val buttonPadding = 24
        const val textButtonPadding = 12
        const val buttonMinWidth = 58

        /** Filter/assist chip height, and the filled text field's container height. */
        const val chip = 32
        const val textField = 56

        /** Iconify's card corner: preference groups, list wells and standalone cards. */
        const val groupCorner = 24

        /** Divider thickness; the rest of a divider is its colour, outlineVariant. */
        const val divider = 1

        /** M3's disabled state: content at 38% of its enabled opacity. */
        const val disabledAlpha = 0.38f
    }

    /**
     * An icon chip's fill and glyph, as one of the scheme's accent container pairs.
     *
     * M3 gives a list item's leading avatar the primary container and its content the matching
     * on-colour. A settings screen where every row wore the same chip would lose what the chips are
     * for -- telling rows apart at a glance -- so the row's own title picks one of the three accent
     * pairs instead: still only colours the scheme defines, and only ever in their intended pairs,
     * which is what keeps the glyph legible under every palette, contrast level and theme. It
     * replaces a hue rotated off the secondary container, which was a colour the scheme never
     * produced and so needed its own contrast search to stay readable.
     */
    private fun chipPair(key: String): Pair<Int, Int> = when (abs(key.hashCode()) % 3) {
        0 -> primaryContainer to onPrimaryContainer
        1 -> secondaryContainer to onSecondaryContainer
        else -> tertiaryContainer to onTertiaryContainer
    }

    fun chipFillFor(key: String): Int = chipPair(key).first

    fun chipIconFor(key: String): Int = chipPair(key).second

    /**
     * One role of the M3 type scale: its size, line height and tracking, in sp, and whether it is
     * set in the medium (500) weight. Values are the skill's baseline type-scale table.
     */
    class TypeStyle internal constructor(
        val sizeSp: Float,
        val lineHeightSp: Float,
        val trackingSp: Float,
        val medium: Boolean,
        /** Overrides the role's weight: Iconify sets its titles and headline in SemiBold. */
        val weight: Int = if (medium) 500 else 400
    )

    /** The M3 baseline type scale. */
    object Type {
        val displayLarge = TypeStyle(57f, 64f, -0.25f, false)
        val displayMedium = TypeStyle(45f, 52f, 0f, false)
        val displaySmall = TypeStyle(36f, 44f, 0f, false)
        val headlineLarge = TypeStyle(32f, 40f, 0f, false, weight = 600)
        val headlineMedium = TypeStyle(28f, 36f, 0f, false)
        val headlineSmall = TypeStyle(24f, 32f, 0f, false, weight = 600)
        val titleLarge = TypeStyle(22f, 28f, 0f, false, weight = 600)
        val titleMedium = TypeStyle(16f, 24f, 0.1f, true, weight = 600)
        val titleSmall = TypeStyle(14f, 20f, 0.1f, true)
        val bodyLarge = TypeStyle(16f, 24f, 0.5f, false)
        val bodyMedium = TypeStyle(14f, 20f, 0.25f, false)
        val bodySmall = TypeStyle(12f, 16f, 0.4f, false)
        val labelLarge = TypeStyle(14f, 20f, 0.1f, true)
        val labelMedium = TypeStyle(12f, 16f, 0.5f, true)
        val labelSmall = TypeStyle(11f, 16f, 0.5f, true)
    }

    /**
     * M3 motion: the duration scale and easing curves for transitions, and the Expressive spring
     * tokens for anything that follows a finger or settles after a press.
     *
     * Pairings, from the skill: something entering takes [emphasizedDecelerate] over [medium4];
     * something leaving for good takes [emphasizedAccelerate] over [short4]; something that stays on
     * screen while it changes takes [emphasized] over [long2].
     */
    object Motion {
        const val short1 = 50L
        const val short2 = 100L
        const val short3 = 150L
        const val short4 = 200L
        const val medium1 = 250L
        const val medium2 = 300L
        const val medium3 = 350L
        const val medium4 = 400L
        const val long1 = 450L
        const val long2 = 500L

        val emphasized: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        val emphasizedDecelerate: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val emphasizedAccelerate: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        val standard: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        val standardDecelerate: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0f, 0f, 0f, 1f)
        val standardAccelerate: android.view.animation.Interpolator
            get() = android.view.animation.PathInterpolator(0.3f, 0f, 1f, 1f)

        /** Expressive motion scheme, fast spatial spring: presses and small movements. */
        const val springFastSpatialDamping = 0.6f
        const val springFastSpatialStiffness = 800f

        /** Expressive motion scheme, default spatial spring: larger movements. */
        const val springDefaultSpatialDamping = 0.8f
        const val springDefaultSpatialStiffness = 380f
    }

    fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    /**
     * How much further in than the compact window's 16dp margin a screen's content should sit at
     * the current window width, in px.
     *
     * Zero on a phone held upright. From the medium window class up (600dp) M3's margin is 24dp,
     * and past that the content column stops growing at [MAX_CONTENT_DP] and centres, so a phone
     * turned sideways -- or a tablet -- gets a readable column rather than rows stretched edge to
     * edge. Read from the configuration rather than cached, so a screen that re-applies its
     * insets after a rotation picks up the new width.
     */
    fun extraGutterPx(): Int {
        val widthDp = context.resources.configuration.screenWidthDp
        if (widthDp < MEDIUM_WINDOW_DP) return 0
        val column = (widthDp - 2 * Space.lg).coerceAtMost(MAX_CONTENT_DP)
        return dp((widthDp - column) / 2 - Space.md)
    }

    fun dpF(value: Float): Float = value * context.resources.displayMetrics.density

    /** State-layer color: M3 draws a pressed state as the content color at 10% opacity. */
    private fun stateLayer(onColor: Int): Int =
        MaterialColors.compositeARGBWithAlpha(onColor, PRESSED_STATE_ALPHA)

    private companion object {
        /** M3's pressed state-layer opacity (0.10), as an alpha byte. */
        const val PRESSED_STATE_ALPHA = 26

        /** Where the medium window class starts, and the widest a content column should get. */
        const val MEDIUM_WINDOW_DP = 600
        const val MAX_CONTENT_DP = 840

        /** A leading icon's glyph, and the 24dp box it sits in. */
        const val CHIP_GLYPH_SP = 19f
        const val ICON_BOX_DP = 24

        /** The two weights the M3 type scale uses. Built once; typefaces are process-wide. */
        val REGULAR: android.graphics.Typeface by lazy {
            android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 400, false)
        }
        val MEDIUM: android.graphics.Typeface by lazy {
            android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 500, false)
        }
        private val byWeight = HashMap<Int, android.graphics.Typeface>()

        fun typefaceFor(weight: Int): android.graphics.Typeface = when (weight) {
            400 -> REGULAR
            500 -> MEDIUM
            else -> byWeight.getOrPut(weight) {
                android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, weight, false)
            }
        }

        /** Iconify's secondary text: the content colour at 75%. */
        const val SECONDARY_TEXT_ALPHA = 0.75f

        /** Iconify's preference groups: 24dp outside, 4dp where two items meet. */
        const val GROUP_CORNER_LARGE = 24
        const val GROUP_CORNER_SMALL = 4
    }

    /** A filled, rounded surface with a proper M3 ripple state layer. */
    /** Card surfaces use blur regardless of which theme role supplies their tint. */
    fun cardFill(fill: Int): GradientDrawable =
        BackdropFill(if (fill == primary || fill == secondary || fill == tertiary) 0.85f else 0f)

    /** Small controls and colour swatches keep their exact fill. */
    fun filled(fill: Int, cornerDp: Int, rippleOn: Int, card: Boolean = false): Drawable {
        val content = (if (card) cardFill(fill) else GradientDrawable()).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(fill)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(stateLayer(rippleOn)), content, mask)
    }

    /** An outlined surface (transparent fill, hairline outline) with a ripple. */
    fun outlined(cornerDp: Int, strokeColor: Int, rippleOn: Int): Drawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), strokeColor)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(stateLayer(rippleOn)), content, mask)
    }

    /** A plain rounded fill with no ripple, for non-interactive containers. */
    fun surfaceShape(fill: Int, cornerDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(fill)
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    /** An explicitly marked card, including tinted cards and nested control groups. */
    fun cardShape(fill: Int, cornerDp: Int, strokeColor: Int? = null): GradientDrawable =
        cardFill(fill).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp.toFloat())
            setColor(fill)
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    /**
     * Expressive motion: a real spring, not a timed curve. M3 Expressive's whole motion story is
     * spring-based, so presses settle with a little overshoot rather than easing to a stop -- on
     * the Expressive scheme's fast spatial spring, the one it assigns to small, direct movements.
     */
    fun springPress(view: View, pressedScale: Float = 0.94f) {
        fun spring(property: androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty) =
            SpringAnimation(view, property).apply {
                spring = SpringForce().apply {
                    // Iconify's bounceClick: medium-bouncy, low stiffness.
                    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                    stiffness = SpringForce.STIFFNESS_LOW
                }
            }

        val scaleX = spring(SpringAnimation.SCALE_X)
        val scaleY = spring(SpringAnimation.SCALE_Y)

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    scaleX.animateToFinalPosition(pressedScale)
                    scaleY.animateToFinalPosition(pressedScale)
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    scaleX.animateToFinalPosition(1f)
                    scaleY.animateToFinalPosition(1f)
                }
            }
            // Never consume: the view's own click/ripple handling must still run.
            false
        }
    }

    /** The five M3 button types, highest emphasis first, plus the tonal error variant. */
    enum class ButtonKind { Filled, Tonal, Outlined, Text, Danger }

    /** The Expressive button sizes this app uses: small (the default, 40dp) and medium (56dp). */
    enum class ButtonSize { Small, Medium }

    /**
     * An M3 button, built to the spec in one place so every screen's buttons agree.
     *
     * The container is [Metrics.buttonSmall] or [Metrics.buttonMedium] tall and fully round, with
     * the label on labelLarge (small) or titleMedium (medium), as the Compose button picks its text
     * style from its height. A small button's container is drawn inset inside a view that is still
     * [Metrics.touchTarget] tall, so the 40dp shape keeps the 48dp target the spec requires.
     *
     * The press is the Expressive shape morph -- the full round corners tighten to the size's
     * pressed shape (small for a small button, medium for a medium one) on the fast spatial spring,
     * and spring back on release -- rather than a scale, which M3 does not use on buttons.
     */
    fun button(
        label: String,
        kind: ButtonKind,
        size: ButtonSize = ButtonSize.Small,
        onClick: (() -> Unit)? = null
    ): android.widget.Button = android.widget.Button(context).apply {
        text = label
        isAllCaps = false
        stateListAnimator = null
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END

        val containerDp = if (size == ButtonSize.Small) Metrics.buttonSmall else Metrics.buttonMedium
        val insetDp = ((Metrics.touchTarget - containerDp) / 2).coerceAtLeast(0)
        val (fill, onFill) = when (kind) {
            ButtonKind.Filled -> primary to onPrimary
            ButtonKind.Tonal -> secondaryContainer to onSecondaryContainer
            ButtonKind.Outlined -> Color.TRANSPARENT to onSurfaceVariant
            ButtonKind.Text -> Color.TRANSPARENT to primary
            ButtonKind.Danger -> errorContainer to onErrorContainer
        }
        styleText(this, if (size == ButtonSize.Small) Type.labelLarge else Type.titleMedium, onFill)

        val restRadius = dpF(containerDp / 2f)
        val pressedRadius = dpF(
            (if (size == ButtonSize.Small) Shape.small else Shape.medium).toFloat()
        )
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = restRadius
            setColor(fill)
            if (kind == ButtonKind.Outlined) setStroke(dp(1), outlineVariant)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = restRadius
            setColor(Color.WHITE)
        }
        background = android.graphics.drawable.InsetDrawable(
            RippleDrawable(ColorStateList.valueOf(stateLayer(onFill)), content, mask),
            0, dp(insetDp), 0, dp(insetDp)
        )
        minWidth = 0
        minimumWidth = dp(if (kind == ButtonKind.Text) Metrics.touchTarget else Metrics.buttonMinWidth)
        minHeight = 0
        minimumHeight = dp(maxOf(containerDp, Metrics.touchTarget))
        val horizontal = if (kind == ButtonKind.Text) Metrics.textButtonPadding else Metrics.buttonPadding
        setPadding(dp(horizontal), dp(insetDp), dp(horizontal), dp(insetDp))

        morphOnPress(this, listOf(content, mask), restRadius, pressedRadius)
        if (onClick != null) setOnClickListener { onClick() }
    }

    /**
     * Springs [shapes]' corners from [restPx] to [pressedPx] while [view] is held. See [button].
     */
    private fun morphOnPress(view: View, shapes: List<GradientDrawable>, restPx: Float, pressedPx: Float) {
        val holder = androidx.dynamicanimation.animation.FloatValueHolder(restPx)
        val animation = SpringAnimation(holder).apply {
            spring = SpringForce(restPx).apply {
                dampingRatio = Motion.springFastSpatialDamping
                stiffness = Motion.springFastSpatialStiffness
            }
            addUpdateListener { _, value, _ ->
                // Clamped at the rest radius: past half the height a rounded rectangle cannot get
                // any rounder, and the overshoot on release would otherwise ask it to.
                val radius = value.coerceIn(0f, restPx)
                shapes.forEach { it.cornerRadius = radius }
                view.invalidate()
            }
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> animation.animateToFinalPosition(pressedPx)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> animation.animateToFinalPosition(restPx)
            }
            // Never consume: the view's own click/ripple handling must still run.
            false
        }
    }

    /**
     * A label over a stack of rows: M3's list subheader, which Android's Material 3 settings set in
     * titleSmall on the primary colour. Inset by the list inset so it starts on the same keyline as
     * the rows' own content rather than at their outer edge.
     *
     * 16dp above it, on top of the 8dp a [section] leaves under the one before, puts the 24dp
     * the spacing scale gives a section break between one group and the next.
     */
    fun groupHeader(text: String): TextView =
        TextView(context).apply {
            this.text = text
            // Iconify's CategoryTitleRow: titleSmall emphasized (bold) in primary, 8dp in from the
            // cards' edge, 12dp above the first card.
            styleText(this, Type.titleSmall, primary)
            typeface = typefaceFor(700)
            setPadding(dp(8), dp(Space.md), dp(8), dp(12))
        }

    /**
     * A screen section: a label with its content stacked underneath, on no surface of its own.
     *
     * The reference app has no outer card — grouping comes from the header and from each control
     * being its own rounded surface, which is why this container draws nothing itself.
     */
    fun section(title: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(Space.xs) }
            addView(groupHeader(title))
        }

    /**
     * A section governed by one master switch: a card carrying the title and the switch, with what
     * it governs folded away beneath while it is off. See [SwitchSection].
     */
    fun sectionWithSwitch(
        title: String,
        switch: View,
        glyph: String? = null,
        supporting: String? = null
    ): SwitchSection = SwitchSection(context, this, title, switch, glyph, supporting)

    /**
     * One row of the reference app's lists: a pastel icon chip, a title with optional supporting
     * text, and whatever control belongs on the right — separated from it by a hairline rule when
     * the row is tappable in its own right, which is how that app signals the two do different
     * things.
     *
     * Rows are their own rounded surfaces stacked with a small gap, rather than sub-sections of
     * one large card. That grouping is the reference app's most recognisable trait, so it is the
     * one thing worth copying exactly.
     */
    fun row(
        title: String,
        supporting: String? = null,
        /** Pass one in to keep a handle on it when the supporting line changes later. */
        supportingView: TextView? = null,
        glyph: String? = null,
        trailing: View? = null,
        divided: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val iconKey = title

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = if (onClick != null) {
                filled(rowSurface, GROUP_CORNER_LARGE, onSurface, card = true)
            } else {
                cardShape(rowSurface, GROUP_CORNER_LARGE)
            }
            // The M3 list item: 16dp in from either edge, and at least the container height the
            // spec gives its line count -- 56 for a headline alone, 72 once a supporting line joins
            // it. Longer supporting text grows past that on its own.
            setPadding(dp(Metrics.listInset), dp(14), dp(Metrics.listInset), dp(14))
            minimumHeight = dp(
                if (supporting != null || supportingView != null) Metrics.listTwoLine
                else Metrics.listOneLine
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(Space.xs) }

            tag = RowMarker

            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
                springPress(this, pressedScale = 0.985f)
            }

            if (glyph != null) {
                addView(iconChip(glyph, iconKey),
                    LinearLayout.LayoutParams(dp(ICON_BOX_DP), dp(ICON_BOX_DP))
                        .apply { marginEnd = dp(Metrics.listInset) })
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    styleText(this, Type.bodyLarge, onSurface)
                })
                if (supporting != null || supportingView != null) {
                    addView((supportingView ?: TextView(context)).apply {
                        if (supporting != null) text = supporting
                        styleText(this, Type.bodyMedium, secondaryText(onSurface))
                        setPadding(0, dp(2), 0, 0)
                    })
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (trailing != null) {
                if (divided) {
                    addView(View(context).apply {
                        setBackgroundColor(outlineVariant)
                    }, LinearLayout.LayoutParams(dp(Metrics.divider), dp(Space.xl)).apply {
                        marginStart = dp(Metrics.listInset)
                        marginEnd = dp(Metrics.listInset)
                    })
                } else {
                    addView(View(context), LinearLayout.LayoutParams(dp(Metrics.listInset), 1))
                }
                addView(trailing)
            }
        }
    }

    /**
     * The leading icon a row or section carries, the way Iconify draws one: a bare 24dp icon in
     * the content colour, no container behind it. [key] is kept so callers are unchanged.
     */
    @Suppress("UNUSED_PARAMETER")
    fun iconChip(glyph: String, key: String, color: Int = onSurface): TextView =
        TextView(context).apply {
            text = glyph
            gravity = Gravity.CENTER
            styleGlyph(this, CHIP_GLYPH_SP, color)
            // Decorative: the row it sits in already says what it is.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    /** The accent a home card is tinted with: Iconify cycles primary, secondary, neutral, tertiary. */
    enum class HomeTint { Primary, Secondary, Neutral, Tertiary }

    /**
     * An Iconify home card's fill and content colour: the accent's container at 20% over
     * surfaceContainerHigh, and its on-colour at 25% over onSurface -- tinted enough to tell the
     * cards apart, quiet enough that the page stays one surface.
     */
    fun homeCardColors(tint: HomeTint): Pair<Int, Int> {
        fun over(top: Int, alpha: Float, base: Int) = androidx.core.graphics.ColorUtils.compositeColors(
            androidx.core.graphics.ColorUtils.setAlphaComponent(top, (255 * alpha).toInt()), base
        )
        val (container, onContainer) = when (tint) {
            HomeTint.Primary -> primaryContainer to onPrimaryContainer
            HomeTint.Secondary -> secondaryContainer to onSecondaryContainer
            HomeTint.Tertiary -> tertiaryContainer to onTertiaryContainer
            HomeTint.Neutral -> return surfaceContainerHigh to onSurface
        }
        return over(container, 0.2f, surfaceContainerHigh) to over(onContainer, 0.25f, onSurface)
    }

    /** Iconify's secondary text colour: [color] at 75%. */
    fun secondaryText(color: Int): Int =
        MaterialColors.compositeARGBWithAlpha(color, (255 * SECONDARY_TEXT_ALPHA).toInt())

    /**
     * Styles a glyph from the app's icon font: its size and colour, and none of the type scale's
     * tracking or line height, which are for words.
     *
     * The icon font is set here and nowhere else. If it cannot be loaded the glyph is still drawn,
     * as whatever box the system font has for that codepoint, rather than the view failing to build.
     */
    fun styleGlyph(view: TextView, sizeSp: Float, colorInt: Int) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        view.setTextColor(colorInt)
        view.letterSpacing = 0f
        view.includeFontPadding = false
        view.typeface = Icons.typeface(context) ?: MEDIUM
    }

    /**
     * Marks the one switch on a screen that turns the whole feature on and off, so the card that
     * opens that screen can mirror it. See [SectionPages] and [IconifyCard.Master].
     */
    object MasterSwitch {
        fun mark(switch: View): View = switch.apply { setTag(com.elitedarkkaiser.redmagic.R.id.master_switch, MasterSwitch) }

        /** The marked switch under [root], if it has one. */
        fun find(root: View): View? {
            if (root.getTag(com.elitedarkkaiser.redmagic.R.id.master_switch) === MasterSwitch) return root
            if (root is ViewGroup) {
                for (i in 0 until root.childCount) find(root.getChildAt(i))?.let { return it }
            }
            return null
        }
    }

    /**
     * Makes [view] one of the rows [connectRows] joins into a group, for a row built by hand -- a
     * slider row, say -- so it takes its place in a run of ordinary rows rather than sitting apart
     * from them. Its layout params have to be a LinearLayout's.
     */
    fun asRow(view: LinearLayout): LinearLayout = view.apply {
        tag = RowMarker
        if (layoutParams == null) {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /** Marks a view built by [row], so [connectRows] can find runs of them. */
    private object RowMarker

    /**
     * Joins consecutive rows into one connected block, the way a grouped settings list reads: the
     * run's outer corners stay at the card corner (medium), the corners where two rows meet drop to
     * extraSmall, and the gap between them tightens to the connected group's 2dp.
     *
     * Done as a pass over the finished tree rather than a position argument on [row], because a row
     * cannot know whether it is first or last until its neighbours exist -- call sites add rows
     * conditionally, so that is only settled once the screen is built. Recurses, so one call per
     * screen covers every group on it.
     */
    fun connectRows(root: View) {
        if (root !is ViewGroup) return

        var runStart = -1
        fun closeRun(endExclusive: Int) {
            if (runStart < 0) return
            val count = endExclusive - runStart
            // With "connect cards" on, a group is one card, as ReSukiSU draws it: no gap between
            // rows and square corners where they meet, so the whole group reads as one piece
            // instead of a stack.
            val inner = if (CardStyle.connected) 0 else GROUP_CORNER_SMALL
            for (i in 0 until count) {
                val child = root.getChildAt(runStart + i) as ViewGroup
                val top = if (i == 0) GROUP_CORNER_LARGE else inner
                val bottom = if (i == count - 1) GROUP_CORNER_LARGE else inner
                applyRowShape(child, top, bottom, first = i == 0)
            }
            runStart = -1
        }

        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child.tag === RowMarker) {
                if (runStart < 0) runStart = index
            } else {
                closeRun(index)
                connectRows(child)
            }
        }
        closeRun(root.childCount)
    }

    private fun applyRowShape(row: ViewGroup, topDp: Int, bottomDp: Int, first: Boolean) {
        val t = dpF(topDp.toFloat())
        val b = dpF(bottomDp.toFloat())
        val radii = floatArrayOf(t, t, t, t, b, b, b, b)
        val clickable = row.isClickable

        val content = BackdropFill().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(rowSurface)
        }
        row.background = if (clickable) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                setColor(Color.WHITE)
            }
            RippleDrawable(ColorStateList.valueOf(stateLayer(onSurface)), content, mask)
        } else {
            content
        }

        (row.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            // A hairline between rows of one group; the usual gap before a new one starts.
            params.topMargin = when {
                first -> dp(Space.xs)
                CardStyle.connected -> 0
                else -> dp(Metrics.connectedGap)
            }
            row.layoutParams = params
        }
    }

    /**
     * A row-shaped surface for content that is not a row: a slider, a chip strip, a colour grid.
     * Same fill and corner as [row] so a group stays one visual family whatever is inside it.
     */
    fun block(label: String? = null, glyph: String? = null): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardShape(rowSurface, GROUP_CORNER_LARGE)
            setPadding(dp(Space.md), dp(Space.md), dp(Space.md), dp(Space.md))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(Space.xs) }
            // Joins a run of rows around it into one group, as Iconify's slider and list items
            // sit in the same card stack as its switches.
            tag = RowMarker
            if (label != null) {
                // A block with an icon puts it beside the label, at the size a row's chip would be
                // -- so a screen mixing blocks and rows keeps one column of icons down its left
                // edge instead of some lines starting further in than others.
                if (glyph != null) {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 0, 0, dp(Space.sm))
                        addView(iconChip(glyph, label),
                            LinearLayout.LayoutParams(dp(ICON_BOX_DP), dp(ICON_BOX_DP))
                                .apply { marginEnd = dp(Metrics.listInset) })
                        addView(TextView(context).apply {
                            text = label
                            styleText(this, Type.bodyLarge, onSurface)
                        })
                    })
                } else {
                    addView(TextView(context).apply {
                        text = label
                        styleText(this, Type.titleSmall, onSurfaceVariant)
                        setPadding(0, 0, 0, dp(Space.sm))
                    })
                }
            }
        }

    /**
     * A darker inset well for grouping related controls inside a card or a dialog.
     *
     * This is the nested two-tone look the Call Lighting zone editors use: the container sits a
     * step up the tonal ladder and each cluster of controls drops back down onto
     * surfaceContainerLow, so a busy screen reads as a few groups rather than one long run of
     * loose rows. Pass [label] to caption the group.
     */
    fun insetGroup(label: String? = null, cornerDp: Int = GROUP_CORNER_LARGE): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Highest, not a mid container: a well sits inside a dialog panel that is already a
            // step off the page, and only the top of the ladder is away from that panel in both
            // schemes. A mid tone lands lighter than its own panel in light and reads as raised.
            background = cardShape(surfaceContainerHighest, cornerDp)
            setPadding(dp(Space.md), dp(Space.md), dp(Space.md), dp(Space.md))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(Space.sm) }

            if (label != null) {
                addView(TextView(context).apply {
                    text = label
                    styleText(this, Type.titleSmall, onSurfaceVariant)
                    setPadding(0, 0, 0, dp(Space.sm))
                })
            }
        }

    /**
     * The M3 card-title convention this app settled on: a plain bold heading, not the older
     * icon-in-a-circle-badge + all-caps-tracked-label pattern (sectionHeader) some tabs still
     * carry from before the Material 3 Expressive pass.
     */
    fun cardTitle(text: String, bottomPaddingDp: Int = Space.sm): TextView =
        TextView(context).apply {
            this.text = text
            styleText(this, Type.titleMedium, onSurface)
            setPadding(0, 0, 0, dp(bottomPaddingDp))
        }

    /**
     * Sets [view] in one role of the M3 type scale: size, line height, tracking and weight.
     *
     * [bold] asks for the medium (500) weight on a role that is regular by default -- the
     * emphasized variant of a display, headline or body style. It never goes heavier than 500: the
     * type scale has no bold role, and the 700 this used to reach for is what made every heading in
     * the app heavier than any M3 surface draws one.
     *
     * The line height is applied as the distance between baselines, which is how the spec states
     * it; a single line keeps its own height. Anything that calls setLineSpacing afterwards replaces
     * it, which is why no call site does any more.
     */
    fun styleText(view: TextView, style: TypeStyle, colorInt: Int, bold: Boolean = false) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
        view.setTextColor(colorInt)
        view.typeface = typefaceFor(if (bold) maxOf(style.weight, 500) else style.weight)
        view.letterSpacing = style.trackingSp / style.sizeSp
        view.setLineHeight(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, style.lineHeightSp, view.resources.displayMetrics
            ).roundToInt()
        )
    }

    /** Tints a CheckBox/Switch with the M3 selection colors. */
    /**
     * Recolours the Material widgets under [root] from the generated palette.
     *
     * Switches, checkboxes and radio buttons resolve their own colours from the theme when they are
     * inflated, and the generated palette deliberately leaves the theme alone -- so without this
     * pass they would keep the shipped blue while every hand-drawn surface around them followed the
     * user's colour. One walk per tab build, where the rows are already being walked anyway.
     */
    fun tintWidgets(root: View) {
        when (root) {
            is com.google.android.material.materialswitch.MaterialSwitch -> tintSwitch(root)
            is android.widget.CompoundButton -> tintCompoundButton(root)
            is ViewGroup -> for (i in 0 until root.childCount) tintWidgets(root.getChildAt(i))
        }
    }

    /**
     * A switch's four states: the thumb rides on the track, so both need the checked and unchecked
     * pair or the thumb vanishes into it at one end.
     */
    fun tintSwitch(switch: com.google.android.material.materialswitch.MaterialSwitch) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf(-android.R.attr.state_checked)
        switch.thumbTintList = ColorStateList(
            arrayOf(checked, unchecked), intArrayOf(onPrimary, outline)
        )
        switch.trackTintList = ColorStateList(
            arrayOf(checked, unchecked), intArrayOf(primary, surfaceContainerHighest)
        )
        switch.trackDecorationTintList = ColorStateList(
            arrayOf(checked, unchecked), intArrayOf(primary, outline)
        )
    }

    /** Checkbox and radio: primary when selected, onSurfaceVariant when not, as the spec pairs them. */
    fun tintCompoundButton(button: android.widget.CompoundButton) {
        button.buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primary, onSurfaceVariant)
        )
    }
}

