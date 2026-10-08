package com.elitedarkkaiser.redmagic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Iconify's home category card (features/home/main/components/HomeCategoryCard.kt), as a View.
 *
 * Large: 190dp tall, a 48dp icon box at the top, an ExtraBold title shrunk to fit one line and a
 * two-line summary at the bottom, and the same icon drawn 160dp wide at 5% in the bottom corner.
 * Small (no summary): 84dp tall, the title on the left and a 40dp icon box on the right.
 *
 * Pressing it does what Iconify's does: the card scales to 92%, its corners open from 28 to 40dp
 * and the icon box's from 16 to 23 (12 to 17 small), all on one spring (damping 0.6, low
 * stiffness). A soft shadow in the card's own colour sits 6dp under it with a 20dp blur.
 */
@SuppressLint("ViewConstructor")
class IconifyCard(
    context: Context,
    title: String,
    subtitle: String?,
    glyph: String,
    private val fillColor: Int,
    contentColor: Int,
    master: Master? = null,
    onClick: (() -> Unit)? = null
) : FrameLayout(context) {

    /**
     * The feature's own on/off state, when the card stands for something that has one.
     *
     * A mirror, not the control itself: the page this card opens keeps the master switch it has
     * always had, because the tab -- and so this card -- is hidden for as long as that page is
     * open, and a feature you cannot turn off from its own screen would be worse than one with
     * two ways to do it. [write] flips the real switch, which is what does the work; [read] is
     * re-read whenever the card comes back into view, so whatever happened on the page (or was
     * refused by the hardware and put back) is showing here by the time it can be seen.
     */
    class Master(
        val read: () -> Boolean,
        val write: (Boolean) -> Unit,
        /** Whether the real switch would take a tap right now; the mirror greys out when not. */
        val enabled: () -> Boolean = { true }
    )

    private val masterState = master

    /** Everything the card dims while its feature is off -- which is all of it but the switch. */
    private val dimmed = mutableListOf<View>()

    /** Whether there is anything behind this card to open. */
    private val openable = onClick != null

    // [bindMirror] runs while this is being constructed, so it touches nothing but the switch it
    // is handed: a Kotlin property holds null until its own initialiser has run, and the views
    // [applyEnabled] works on are built further down. The card applies that state once, at the
    // end of init, and from the listener and the watcher after that.
    private val mirror: com.google.android.material.materialswitch.MaterialSwitch? =
        master?.let { m ->
            com.google.android.material.materialswitch.MaterialSwitch(context).apply {
                M3(context).tintSwitch(this)
                bindMirror(this, m)
            }
        }

    private val m3 = M3(context)
    private val small = subtitle == null

    private var cornerPx = m3.dpF(CORNER_REST)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        setShadowLayer(
            m3.dpF(20f), 0f, m3.dpF(6f),
            ColorUtils.setAlphaComponent(fillColor, (255 * 0.25f).toInt())
        )
    }

    /** The card surface, clipped to its rounded outline so the big faded icon stays inside. */
    private val surface = FrameLayout(context).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
            }
        }
        // The card transparency and blur apply here as on every other surface (see CardStyle);
        // the outline clip above gives the blur its corners.
        background = BackdropFill().apply { setColor(fillColor) }
        foreground = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(contentColor, 26)
            ),
            null,
            GradientDrawable().apply { setColor(Color.WHITE) }
        )
    }

    private val iconBox: TextView
    private val iconBoxShape = GradientDrawable().apply {
        setColor(ColorUtils.setAlphaComponent(contentColor, (255 * 0.15f).toInt()))
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(if (small) HEIGHT_SMALL else HEIGHT_LARGE)
        )
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        iconBox = TextView(context).apply {
            text = glyph
            gravity = Gravity.CENTER
            m3.styleGlyph(this, if (small) 20f else 24f, contentColor)
            background = iconBoxShape
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        iconBoxShape.cornerRadius = m3.dpF(boxRest())

        val titleView = TextView(context).apply {
            text = title
            m3.styleText(this, if (small) M3.Type.titleMedium else M3.Type.titleLarge, contentColor)
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF, if (small) 700 else 800, false
            )
            if (!small) letterSpacing = -0.5f / 22f
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 6, if (small) 16 else 22, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }

        if (small) {
            surface.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(m3.dp(20), 0, m3.dp(20), 0)
                addView(titleView, LinearLayout.LayoutParams(0, m3.dp(24), 1f)
                    .apply { marginEnd = m3.dp(12) })
                addView(iconBox, LinearLayout.LayoutParams(m3.dp(40), m3.dp(40)))
                dimmed += listOf(titleView, iconBox)
                mirror?.let { addView(it, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = m3.dp(12) }) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            // The watermark: the same icon at 160dp and 5%, pushed 24dp past the corner.
            surface.addView(TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                m3.styleGlyph(this, 140f, ColorUtils.setAlphaComponent(contentColor, (255 * 0.05f).toInt()))
                translationX = m3.dpF(24f)
                translationY = m3.dpF(24f)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }.also { dimmed += it }, LayoutParams(m3.dp(160), m3.dp(160), Gravity.BOTTOM or Gravity.END))

            // The icon at the top left, and -- when the feature has one -- its switch opposite.
            topRow.addView(iconBox, LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
            mirror?.let {
                topRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                topRow.addView(it)
            }

            surface.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(20))
                addView(topRow, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
                addView(titleView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, m3.dp(28)
                ))
                addView(TextView(context).apply {
                    text = subtitle
                    m3.styleText(this, M3.Type.bodyMedium, m3.secondaryText(contentColor))
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.SANS_SERIF, 600, false
                    )
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, m3.dp(2), 0, 0)
                    dimmed += this
                })
                dimmed += listOf(iconBox, titleView)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        // The clipped surface takes the touch, so its ripple is shaped by the same outline.
        surface.contentDescription = if (subtitle != null) "$title. $subtitle" else title
        surface.setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick?.invoke()
        }
        bindPress()
        applyEnabled()
    }

    /**
     * There is nothing to see behind a card whose feature is switched off, so it does not open:
     * its content dims to M3's disabled 38% and the surface stops taking taps -- no ripple, no
     * press, nothing that suggests it would lead anywhere. Only the switch stays lit, which is
     * both the one thing still worth pressing and the way to make the rest reachable again.
     *
     * A card with no page behind it at all -- a feature that is nothing but its switch -- is never
     * tappable outside that switch, whichever way it is set.
     */
    private fun applyEnabled() {
        val on = masterState?.read() ?: true
        val accessible = openable && on
        surface.isClickable = accessible
        surface.isFocusable = accessible
        val alpha = if (on) 1f else M3.Metrics.disabledAlpha
        dimmed.forEach { it.alpha = alpha }
    }

    /**
     * Keeps the mirror on the real switch.
     *
     * Per frame the window draws, rather than on some event: what moves the real switch is the
     * page this card opens, the status poll a tab runs while it is on screen, and the hardware
     * putting back a change it refused -- and none of those is something a card can be told
     * about. The check is a boolean read against a boolean, and it does nothing at all unless the
     * two have actually parted company.
     */
    private val watch = android.view.ViewTreeObserver.OnPreDrawListener {
        val m = masterState
        val view = mirror
        if (m != null && view != null) {
            if (view.isChecked != m.read()) {
                bindMirror(view, m)
                applyEnabled()
            }
            // A real switch stands down while a root write is in flight, or for good when the
            // module that would do its work is missing -- and a mirror that still looked tappable
            // would flip, go nowhere, and spring back.
            val usable = m.enabled()
            if (view.isEnabled != usable) view.isEnabled = usable
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mirror != null) viewTreeObserver.addOnPreDrawListener(watch)
        // The shadow reaches past the card, and a parent that clips its children would cut it off:
        // column, grid and whatever holds the grid -- not further, so a folding section's own clip
        // (which is what hides its body while it folds) is left alone.
        var p = parent as? ViewGroup
        repeat(3) {
            p?.clipChildren = false
            p = p?.parent as? ViewGroup
        }
    }

    override fun onDetachedFromWindow() {
        if (mirror != null) viewTreeObserver.removeOnPreDrawListener(watch)
        super.onDetachedFromWindow()
    }

    private fun bindMirror(
        view: com.google.android.material.materialswitch.MaterialSwitch,
        master: Master
    ) {
        // Assigning isChecked fires the listener exactly as a finger does, so it comes off first.
        view.setOnCheckedChangeListener(null)
        view.isChecked = master.read()
        view.setOnCheckedChangeListener { _, wanted ->
            master.write(wanted)
            // What the real switch settled on, which a feature that refused to start may have put
            // straight back.
            if (master.read() != wanted) bindMirror(view, master)
            applyEnabled()
        }
    }

    private fun boxRest() = if (small) 12f else 16f
    private fun boxPressed() = if (small) 17f else 23f

    /** One spring from 0 (rest) to 1 (pressed) drives the scale and both corner radii. */
    private fun bindPress() {
        val holder = FloatValueHolder(0f)
        val spring = SpringAnimation(holder).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = 0.6f
                stiffness = SpringForce.STIFFNESS_LOW
            }
            addUpdateListener { _, p, _ ->
                val scale = 1f - (1f - PRESSED_SCALE) * p
                scaleX = scale
                scaleY = scale
                cornerPx = m3.dpF(CORNER_REST + (CORNER_PRESSED - CORNER_REST) * p)
                iconBoxShape.cornerRadius = m3.dpF(boxRest() + (boxPressed() - boxRest()) * p)
                surface.invalidateOutline()
                invalidate()
            }
        }
        surface.setOnTouchListener { _, event ->
            if (!surface.isClickable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> spring.animateToFinalPosition(1f)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> spring.animateToFinalPosition(0f)
            }
            // Never consumed: the surface's own ripple and click still run.
            false
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Iconify's softShadow: drawn behind, at the resting corner.
        // Not under a see-through card, where it would show as a dark slab: the shadow drops
        // once the card stops being opaque.
        if (CardStyle.alpha >= 1f) {
            val r = m3.dpF(CORNER_REST)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, shadowPaint)
        }
        super.onDraw(canvas)
    }

    private companion object {
        const val HEIGHT_LARGE = 190
        const val HEIGHT_SMALL = 84
        const val CORNER_REST = 28f
        const val CORNER_PRESSED = 40f
        const val PRESSED_SCALE = 0.92f
    }
}

/**
 * Iconify's HomeCategories grid: two columns 16dp apart, items dealt alternately into them, and
 * each column cycling its own four tints so neighbours never match.
 */
object IconifyGrid {

    data class Item(
        val title: String,
        val subtitle: String?,
        val glyph: String,
        val master: IconifyCard.Master? = null,
        val onClick: (() -> Unit)? = null
    )

    private val LEFT = listOf(
        M3.HomeTint.Primary, M3.HomeTint.Secondary, M3.HomeTint.Neutral, M3.HomeTint.Tertiary
    )
    private val RIGHT = listOf(
        M3.HomeTint.Neutral, M3.HomeTint.Tertiary, M3.HomeTint.Primary, M3.HomeTint.Secondary
    )

    fun build(context: Context, items: List<Item>, topMarginDp: Int = 0): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = m3.dp(topMarginDp)
                bottomMargin = m3.dp(8)
            }
            fill(this, items)
        }
    }

    /**
     * Rebuilds [grid] from [items], for a card that can only be ruled out once a root read comes
     * back. Dropping one card cannot be a matter of hiding it: which column a card sits in and
     * which tint it takes both follow from its position, so the grid is laid out again from the
     * shorter list.
     */
    fun fill(grid: LinearLayout, items: List<Item>) {
        val context = grid.context
        val m3 = M3(context)
        fun column(side: Int): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            val tints = if (side == 0) LEFT else RIGHT
            items.filterIndexed { i, _ -> i % 2 == side }.forEachIndexed { row, item ->
                val (bg, fg) = m3.homeCardColors(tints[row % 4])
                addView(IconifyCard(context, item.title, item.subtitle, item.glyph, bg, fg,
                    item.master, item.onClick)
                    .apply {
                        (layoutParams as LinearLayout.LayoutParams).topMargin =
                            if (row == 0) 0 else m3.dp(16)
                    })
            }
        }
        grid.removeAllViews()
        grid.addView(column(0), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // A lone card takes the full width rather than leaving half the row empty.
        if (items.size > 1) {
            grid.addView(View(context), LinearLayout.LayoutParams(m3.dp(16), 1))
            grid.addView(column(1), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
}
