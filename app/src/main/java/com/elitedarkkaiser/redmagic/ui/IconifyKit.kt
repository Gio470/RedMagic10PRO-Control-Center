package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * The pieces Iconify's feature screens are built from, for this app's hand-drawn Views -- taken from
 * Mahmud0808/Iconify's core/ui source rather than its screenshots:
 *
 * - list rows as grouped cards: each item its own surfaceContainerHigh surface, 24dp at the outer
 *   corners of a run and 4dp where two meet, 2dp apart (SearchResultItem, PreferenceContainer);
 * - the info block: an info icon over a paragraph, on no surface of its own (InfoPreferenceItem);
 * - the empty state: a large faded icon over one line of secondary text (SearchScreen);
 * - the slider row: title and summary with the value in primary opposite, the slider under it
 *   (SliderPreferenceItem);
 * - the preview card: a picture over a label strip, both lit in the primary pair when chosen
 *   (IconPreviewCard).
 */
object IconifyKit {

    /** Where in its run a row sits, which decides which of its corners are the group's. */
    enum class Position { SOLO, FIRST, MIDDLE, LAST }

    fun position(index: Int, count: Int): Position = when {
        count <= 1 -> Position.SOLO
        index == 0 -> Position.FIRST
        index == count - 1 -> Position.LAST
        else -> Position.MIDDLE
    }

    private const val LARGE = 24f
    private const val SMALL = 4f

    private fun radii(m3: M3, position: Position): FloatArray {
        val l = m3.dpF(LARGE)
        // Square where rows meet when cards are connected: the list is one card then.
        val s = if (CardStyle.connected) 0f else m3.dpF(SMALL)
        val (top, bottom) = when (position) {
            Position.SOLO -> l to l
            Position.FIRST -> l to s
            Position.MIDDLE -> s to s
            Position.LAST -> s to l
        }
        return floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
    }

    /**
     * A list row's surface for its [position] in the list: surfaceContainerHigh, with a ripple in
     * the content colour when [clickable].
     */
    fun rowBackground(m3: M3, position: Position, clickable: Boolean = true): Drawable {
        val r = radii(m3, position)
        val fill = BackdropFill().apply { cornerRadii = r; setColor(m3.rowSurface) }
        if (!clickable) return fill
        val mask = GradientDrawable().apply { cornerRadii = r; setColor(Color.WHITE) }
        return RippleDrawable(
            ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(m3.onSurface, 26)),
            fill, mask
        )
    }

    /**
     * Sets a ListView up to show its rows as one grouped run: no divider line, just the 2dp gap
     * Iconify leaves between items, and no background of its own -- the rows are the surface.
     */
    fun groupedList(list: ListView) {
        val m3 = M3(list.context)
        list.divider = ColorDrawable(Color.TRANSPARENT)
        list.dividerHeight = if (CardStyle.connected) 0 else m3.dp(2)
        list.background = null
        list.selector = ColorDrawable(Color.TRANSPARENT)
    }

    /** The padding every Iconify item has inside its surface. */
    fun rowPadding(m3: M3, view: View) {
        view.setPadding(m3.dp(16), m3.dp(14), m3.dp(16), m3.dp(14))
    }

    /**
     * Iconify's info block: a 24dp info icon, 12dp of air, then the text -- the first line in the
     * content colour, anything after it at 75%. Returns the block and the text view, so a line
     * that changes (a count, a status) can be rewritten in place.
     */
    fun infoBlock(context: Context, text: CharSequence = ""): Pair<LinearLayout, TextView> {
        val m3 = M3(context)
        val body = TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.secondaryText(m3.onSurface))
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(8), m3.dp(16), m3.dp(8), m3.dp(16))
            addView(TextView(context).apply {
                this.text = Icons.INFO
                m3.styleGlyph(this, 20f, m3.onSurface)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(m3.dp(24), m3.dp(24)))
            addView(body, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(12) })
        }
        return block to body
    }

    /** Iconify's empty search: a 48dp icon at 38% over a line of secondary bodyLarge. */
    fun emptyState(context: Context, text: String, glyph: String = Icons.SEARCH): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(m3.dp(32), m3.dp(48), m3.dp(32), m3.dp(32))
            addView(TextView(context).apply {
                this.text = glyph
                gravity = Gravity.CENTER
                m3.styleGlyph(this, 40f, androidx.core.graphics.ColorUtils.setAlphaComponent(
                    m3.onSurface, (255 * M3.Metrics.disabledAlpha).toInt()
                ))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)))
            addView(TextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                m3.styleText(this, M3.Type.bodyLarge, m3.secondaryText(m3.onSurface))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(16) })
        }
    }

    /**
     * Iconify's slider item: the title (and a summary, if there is one) with the value in primary
     * labelLarge opposite, and the slider under both. Joins the rows around it into one group --
     * see [M3.asRow] -- so a screen's switches and sliders read as one stack of settings.
     */
    fun sliderRow(
        context: Context,
        title: String,
        summary: String?,
        value: TextView,
        slider: View,
        glyph: String? = null
    ): LinearLayout {
        val m3 = M3(context)
        m3.styleText(value, M3.Type.labelLarge, m3.primary)
        return m3.asRow(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rowBackground(m3, Position.SOLO, clickable = false)
            rowPadding(m3, this)
            minimumHeight = m3.dp(if (summary == null) M3.Metrics.listTwoLine else M3.Metrics.listThreeLine)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(M3.Space.xs) }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                if (glyph != null) {
                    addView(m3.iconChip(glyph, title), LinearLayout.LayoutParams(m3.dp(24), m3.dp(24))
                        .apply { marginEnd = m3.dp(16) })
                }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = title
                        m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                    })
                    if (summary != null) addView(TextView(context).apply {
                        text = summary
                        m3.styleText(this, M3.Type.bodyMedium, m3.secondaryText(m3.onSurface))
                        setPadding(0, m3.dp(2), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(value, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = m3.dp(16) })
            })
            addView(slider, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(6) })
        })
    }

    /**
     * Iconify's IconPreviewCard: [preview] centred in a box, over a one-line label strip 2dp
     * below it. The box's top corners and the strip's bottom ones are the card's 24dp, the two
     * edges that meet are 4dp. When [chosen], the box takes primaryContainer inside a 2dp primary
     * border and the strip turns primary with its label in onPrimary; otherwise both are the
     * ordinary item surface.
     */
    fun previewCard(
        context: Context,
        preview: View,
        label: String,
        chosen: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val m3 = M3(context)
        val l = m3.dpF(LARGE)
        val s = m3.dpF(SMALL)
        val boxFill = if (chosen) m3.primaryContainer else m3.rowSurface
        val box = FrameLayout(context).apply {
            background = m3.cardFill(boxFill).apply {
                cornerRadii = floatArrayOf(l, l, l, l, s, s, s, s)
                setColor(boxFill)
                // Clear rather than the fill when unchosen: over a see-through card an opaque
                // ring of the fill colour would show (see CardStyle).
                setStroke(m3.dp(2), if (chosen) m3.primary else Color.TRANSPARENT)
            }
            setPadding(m3.dp(12), m3.dp(12), m3.dp(12), m3.dp(12))
            (preview.parent as? ViewGroup)?.removeView(preview)
            addView(preview, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        val strip = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            m3.styleText(this, M3.Type.labelLarge, if (chosen) m3.onPrimary else m3.onSurface)
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            background = m3.cardFill(if (chosen) m3.primary else m3.rowSurface).apply {
                cornerRadii = floatArrayOf(s, s, s, s, l, l, l, l)
                setColor(if (chosen) m3.primary else m3.rowSurface)
            }
            setPadding(m3.dp(8), m3.dp(4), m3.dp(8), m3.dp(4))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(box, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(strip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(2) })
            isClickable = true
            foreground = RippleDrawable(
                ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(m3.onSurface, 26)),
                null,
                GradientDrawable().apply { cornerRadius = l; setColor(Color.WHITE) }
            )
            contentDescription = if (chosen) "$label, selected" else label
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            m3.springPress(this, pressedScale = 0.97f)
        }
    }

    /**
     * One M3 filter chip: 32dp tall inside a 48dp target, 8dp corners. Unselected it is an
     * outline in outlineVariant with its label in onSurfaceVariant; selected it fills with
     * secondaryContainer and leads with a check, as the spec draws it.
     */
    private fun styleChip(m3: M3, chip: TextView, selected: Boolean, glyph: String? = null) {
        val corner = m3.dpF(8f)
        val fill = GradientDrawable().apply {
            cornerRadius = corner
            if (selected) setColor(m3.secondaryContainer)
            else { setColor(Color.TRANSPARENT); setStroke(m3.dp(1), m3.outlineVariant) }
        }
        val mask = GradientDrawable().apply { cornerRadius = corner; setColor(Color.WHITE) }
        val content = if (selected) m3.onSecondaryContainer else m3.onSurfaceVariant
        chip.background = android.graphics.drawable.InsetDrawable(
            RippleDrawable(
                ColorStateList.valueOf(androidx.core.graphics.ColorUtils.setAlphaComponent(content, 26)),
                fill, mask
            ),
            0, m3.dp(8), 0, m3.dp(8)
        )
        m3.styleText(chip, M3.Type.labelLarge, content)
        val lead = when {
            selected -> Icons.CHECK
            else -> glyph
        }
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
            lead?.let { GlyphDrawable(chip.context, it, content, boxDp = 18, sizeSp = 13f) },
            null, null, null
        )
        chip.compoundDrawablePadding = m3.dp(8)
        chip.setPadding(m3.dp(if (lead != null) 8 else 16), m3.dp(8), m3.dp(16), m3.dp(8))
        chip.isSelected = selected
    }

    private fun chip(context: Context): TextView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        minimumHeight = M3(context).dp(M3.Metrics.touchTarget)
        isClickable = true
        isFocusable = true
    }

    /**
     * A single scrolling row of M3 filter chips, one of which is always the selected one --
     * what a list's filters take instead of a block of buttons wrapped over several rows.
     * [lead] goes first, before the filters (a sort chip, say).
     */
    fun <T> filterChips(
        context: Context,
        entries: List<Pair<String, T>>,
        selected: T,
        lead: View? = null,
        onSelect: (T) -> Unit
    ): android.widget.HorizontalScrollView {
        val m3 = M3(context)
        var current = selected
        val chips = mutableListOf<Pair<TextView, T>>()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (lead != null) addView(lead)
            entries.forEach { (label, value) ->
                val c = chip(context).apply { text = label }
                chips += c to value
                addView(c, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (childCount > 0) marginStart = m3.dp(8) })
            }
        }
        fun render() = chips.forEach { (c, v) -> styleChip(m3, c, v == current) }
        chips.forEach { (c, v) ->
            c.setOnClickListener {
                if (v == current) return@setOnClickListener
                current = v
                render()
                onSelect(v)
            }
        }
        render()
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row)
        }
    }

    /**
     * An assist-style chip that is not a filter: an outlined chip with a leading icon, whose
     * label the caller rewrites -- the sort chip, which cycles on each tap.
     */
    fun actionChip(context: Context, label: String, glyph: String, onClick: (TextView) -> Unit): TextView {
        val m3 = M3(context)
        return chip(context).apply {
            text = label
            styleChip(m3, this, selected = false, glyph = glyph)
            setOnClickListener { onClick(this) }
        }
    }

    /**
     * One quiet line of counts under a list's controls -- what an Iconify list says about itself,
     * where a paragraph above the list pushed the list itself down the screen.
     */
    fun countLine(context: Context): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            m3.styleText(this, M3.Type.bodySmall, m3.secondaryText(m3.onSurface))
            maxLines = 2
            setPadding(m3.dp(8), m3.dp(4), m3.dp(8), m3.dp(12))
        }
    }

    /**
     * Lays [cards] out [columns] to a row, 8dp apart, each the same width -- Iconify's
     * IconPreviewGrid spacing. A short last row keeps its cards at full-row width rather than
     * stretching them.
     */
    fun grid(context: Context, cards: List<View>, columns: Int): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            cards.chunked(columns).forEachIndexed { rowIndex, rowCards ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    rowCards.forEachIndexed { i, card ->
                        addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { if (i > 0) marginStart = m3.dp(8) })
                    }
                    repeat(columns - rowCards.size) {
                        addView(View(context), LinearLayout.LayoutParams(0, 1, 1f)
                            .apply { marginStart = m3.dp(8) })
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = m3.dp(8) })
            }
        }
    }
}
