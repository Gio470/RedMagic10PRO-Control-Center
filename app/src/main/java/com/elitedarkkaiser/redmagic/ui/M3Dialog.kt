package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.widget.NestedScrollView

/**
 * Material 3 Expressive scaffolding for this app's hand-drawn dialogs.
 *
 * Every dialog here used to hand-roll the same panel: a bordered surface at a middling corner
 * radius, a 20sp title, 13sp captions, and buttons filled with literal hex (#1E2633) that ignored
 * the Material You palette entirely. These builders are the single M3 recipe they all share —
 * a borderless tonal surface at the extraLarge end of the shape scale, the real type scale, and
 * fully-round buttons with the same spring press as the rest of the app.
 *
 * Presented as a screen in the app ([show]) rather than a dialog. These were modal bottom sheets
 * until the sheet itself was dropped; the builders below are unchanged, because what they build
 * was always just a column of content — it is the thing around it that went away. See [PageHost].
 */
object M3Dialog {

    /**
     * The page's content column. Padded, but draws nothing itself: the page around it carries the
     * surface and the way back out.
     */
    fun panel(context: Context): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The tab gutter. A panel is a screen of this app now, and its content has to line
            // up with the content of the screen it was opened from rather than sit in from it.
            setPadding(m3.dp(16), 0, m3.dp(16), m3.dp(24))
        }
    }

    /**
     * A sheet's opening line: the mark, the headline, what it is for, and anything that belongs on
     * the same line as the title.
     *
     * Every sheet was building this by hand and each one landed somewhere different -- some had a
     * headline alone, some a headline over a paragraph, one a headline with a swatch pushed to the
     * end. The icon is the part that was missing everywhere: a sheet arrives over whatever you
     * were looking at, and a mark at the top says what opened far faster than a line of text does.
     */
    fun header(
        context: Context,
        title: String,
        supporting: String? = null,
        glyph: String? = null,
        trailing: View? = null
    ): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(M3.Space.xxs), 0, m3.dp(M3.Space.xxs))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Which of these the page may lift: a row that is only a chip and a headline goes
                // whole, and the chip goes with it. One with something in its trailing slot -- a
                // master switch -- has to stay where it is, or the switch would be left sitting
                // against a stranded icon with nothing between them. See PageHost.hoistTitle.
                tag = if (trailing == null) HeadlineRow else HeadlineRowKept

                if (glyph != null) {
                    addView(
                        m3.iconChip(glyph, title),
                        LinearLayout.LayoutParams(m3.dp(24), m3.dp(24))
                            .apply { marginEnd = m3.dp(M3.Metrics.listInset) }
                    )
                }
                addView(TextView(context).apply {
                    this.text = title
                    // The dialog headline role: headlineSmall, regular weight.
                    m3.styleText(this, M3.Type.headlineSmall, m3.onSurface)
                    // See PageHost.hoistTitle: the page this lands on shows the title itself, in
                    // the place every other screen in the app puts one, and hides this.
                    tag = Headline
                }, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                if (trailing != null) addView(trailing)
            })

            if (supporting != null) {
                addView(TextView(context).apply {
                    this.text = supporting
                    m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
                    setPadding(0, m3.dp(M3.Space.xs), 0, 0)
                })
            }
        }
    }

    /** M3's divider -- 1dp of outlineVariant -- between sections long enough to need the break. */
    fun divider(context: Context): View {
        val m3 = M3(context)
        return View(context).apply {
            setBackgroundColor(m3.outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, m3.dp(M3.Metrics.divider))
            ).apply {
                topMargin = m3.dp(M3.Space.md)
                bottomMargin = m3.dp(M3.Space.xxs)
            }
        }
    }

    /** M3 dialog headline. */
    fun title(context: Context, text: String): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.headlineSmall, m3.onSurface)
            setPadding(0, 0, 0, m3.dp(M3.Space.xs))
            // See PageHost.hoistTitle.
            tag = Headline
        }
    }

    /**
     * Marks the one view in a panel that carries its name, whether it came from [header] or from
     * [title]. [PageHost] lifts it into the page's own title, so a screen does not open with its
     * name written twice a few dp apart.
     */
    internal object Headline

    /** A [header] row carrying nothing but its chip and headline: the page lifts all of it. */
    internal object HeadlineRow

    /** A [header] row with something else in it: the page leaves it alone. */
    internal object HeadlineRowKept

    /**
     * Marks a panel's row of buttons, which every panel adds last.
     *
     * [PageHost] pulls it out of the panel and floats it over the page, the way the bottom bar
     * floats over a tab: recovered because a *stacked* row does not just take its own height off
     * the scrollable content, it takes that height twice -- once as the row itself, once again as
     * the top padding ([actionRow]/[buttonRow] both add 24dp) that used to separate it from the
     * content above. Floating the row keeps the visual gap and gives the content the second 24dp
     * back.
     */
    internal object ActionRow


    /** Supporting text under the headline. */
    fun body(context: Context, text: String): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            setPadding(0, 0, 0, m3.dp(M3.Space.md))
        }
    }

    /**
     * Label above a group of controls ("Effect", "Color", "Left trigger") -- the same subheader
     * the tabs use ([M3.groupHeader]), titleSmall in primary, so a section reads the same wherever
     * it is. Without the list inset that one has: what follows a label here is usually a bare
     * control -- a slider, a button group, a row of swatches -- that starts at the content's own
     * edge, and the label has to start there with it. Rows get [M3.groupHeader] instead.
     */
    fun sectionLabel(context: Context, text: String): TextView =
        M3(context).groupHeader(text).apply {
            setPadding(0, paddingTop, 0, paddingBottom)
        }

    /**
     * M3 filled text field: surfaceContainerHighest with only its top corners rounded
     * (extraSmall), over an active indicator -- 1dp of onSurfaceVariant at rest, 2dp of primary
     * while focused -- at the spec's 56dp height, with the input in bodyLarge.
     */
    fun textField(context: Context, hint: String): EditText {
        val m3 = M3(context)
        return EditText(context).apply {
            this.hint = hint
            isSingleLine = true
            m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            setHintTextColor(m3.onSurfaceVariant)
            highlightColor = androidx.core.graphics.ColorUtils.setAlphaComponent(m3.primary, 0x66)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(m3.primary)
                    setSize(m3.dp(2), 0)
                }
            }
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    filledFieldShape(m3, m3.primary, 2)
                )
                addState(intArrayOf(), filledFieldShape(m3, m3.onSurfaceVariant, 1))
            }
            minimumHeight = m3.dp(M3.Metrics.textField)
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md))
        }
    }

    /**
     * M3's search bar, for filtering a list: a full-round surfaceContainerHigh pill at the spec's
     * 56dp, a leading search icon, and the query in bodyLarge -- the component M3 gives a search,
     * where a filled text field is for entering a value.
     */
    fun searchField(context: Context, hint: String): EditText {
        val m3 = M3(context)
        return EditText(context).apply {
            this.hint = hint
            isSingleLine = true
            m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            setHintTextColor(m3.onSurfaceVariant)
            highlightColor = androidx.core.graphics.ColorUtils.setAlphaComponent(m3.primary, 0x66)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(m3.primary)
                    setSize(m3.dp(2), 0)
                }
            }
            background = m3.cardShape(m3.surfaceContainerHigh, M3.Shape.full)
            val lead = GlyphDrawable(context, Icons.SEARCH, m3.onSurface, boxDp = 24, sizeSp = 16f)
            val clear = GlyphDrawable(context, Icons.CLEAR, m3.onSurfaceVariant, boxDp = 24, sizeSp = 18f)
            // Iconify's clear button: there only while there is something to clear.
            fun showClear(on: Boolean) = setCompoundDrawablesRelativeWithIntrinsicBounds(
                lead, null, if (on) clear else null, null
            )
            showClear(false)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) = showClear(!s.isNullOrEmpty())
            })
            // A compound drawable is not a view and takes no taps of its own, so the tap is read
            // off the field: anything that lands on the clear icon's end of it empties the query.
            // The whole gesture is taken, not just its UP: an EditText handed the DOWN arms its
            // long-press and pressed state, and never seeing the UP that would disarm them, it
            // would pop the selection/paste menu on the field just emptied.
            var clearing = false
            setOnTouchListener { view, event ->
                val end = compoundDrawablesRelative[2]
                val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
                val zone = (end?.bounds?.width() ?: 0) + paddingEnd + compoundDrawablePadding
                val hit = end != null &&
                    if (rtl) event.x <= zone else event.x >= view.width - zone
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> clearing = hit
                    android.view.MotionEvent.ACTION_UP -> if (clearing) {
                        clearing = false
                        if (hit) {
                            text?.clear()
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        return@setOnTouchListener true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> if (clearing) {
                        clearing = false
                        return@setOnTouchListener true
                    }
                }
                clearing
            }
            compoundDrawablePadding = m3.dp(M3.Space.md)
            minimumHeight = m3.dp(M3.Metrics.textField)
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md))
        }
    }

    /** A filled text field's container over its active indicator, [indicatorDp] thick. */
    private fun filledFieldShape(m3: M3, indicator: Int, indicatorDp: Int): android.graphics.drawable.Drawable {
        val corner = m3.dpF(M3.Shape.extraSmall.toFloat())
        val container = android.graphics.drawable.GradientDrawable().apply {
            cornerRadii = floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f)
            setColor(m3.surfaceContainerHighest)
        }
        val line = android.graphics.drawable.GradientDrawable().apply { setColor(indicator) }
        return android.graphics.drawable.LayerDrawable(arrayOf(container, line)).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerHeight(1, m3.dp(indicatorDp))
        }
    }

    fun checkBox(
        context: Context,
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): CheckBox {
        val m3 = M3(context)
        return CheckBox(context).apply {
            text = label
            isChecked = checked
            m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            m3.tintCompoundButton(this)
            setPadding(m3.dp(8), m3.dp(8), 0, m3.dp(8))
            setOnCheckedChangeListener { _, nowChecked -> onChanged(nowChecked) }
        }
    }

    fun radioButton(context: Context, label: String, checked: Boolean): RadioButton {
        val m3 = M3(context)
        return RadioButton(context).apply {
            text = label
            isChecked = checked
            m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
            m3.tintCompoundButton(this)
            setPadding(m3.dp(8), m3.dp(12), m3.dp(8), m3.dp(12))
        }
    }

    /** Low-emphasis dialog action (Cancel): M3's text button -- no fill, the primary label. */
    fun textButton(context: Context, label: String, onClick: () -> Unit): Button =
        M3(context).button(label, M3.ButtonKind.Text, onClick = onClick)

    /** The dialog's confirming action (Save): M3's filled button. */
    fun filledButton(context: Context, label: String, onClick: () -> Unit): Button =
        M3(context).button(label, M3.ButtonKind.Filled, onClick = onClick)

    /**
     * The middle of the three emphases: a fill, but the quiet one.
     *
     * For the action a sheet offers alongside its main one and does not want mistaken for it --
     * Reset beside Apply, where a second filled button would read as the thing to press.
     */
    fun tonalButton(context: Context, label: String, onClick: () -> Unit): Button =
        M3(context).button(label, M3.ButtonKind.Tonal, onClick = onClick)

    /**
     * An action row with something pinned to each end.
     *
     * [buttonRow] right-aligns everything, which three buttons overflow -- that is how the colour
     * picker lost its Apply off the edge of the screen once already. This keeps the destructive or
     * secondary action at the start, the confirming ones at the end, and the gap between them
     * doing the spacing.
     */
    fun actionRow(context: Context, leading: View?, vararg trailing: View): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, m3.dp(M3.Space.lg), 0, 0)
            tag = ActionRow
            if (leading != null) addView(leading)
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            trailing.forEachIndexed { index, button ->
                if (index > 0) addView(hGap(context, 8))
                addView(button)
            }
        }
    }

    /** Right-aligned action row, M3's dialog button layout. */
    fun buttonRow(context: Context, vararg buttons: View): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, m3.dp(M3.Space.lg), 0, 0)
            tag = ActionRow
            buttons.forEachIndexed { index, button ->
                if (index > 0) addView(hGap(context, 8))
                addView(button)
            }
        }
    }

    /**
     * A filled card for content that scrolls inside a page (app lists, long option groups):
     * surfaceContainerHigh at the group corner, as Iconify draws its containers.
     */
    fun inset(context: Context): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = m3.cardShape(m3.rowSurface, M3.Metrics.groupCorner)
            setPadding(m3.dp(M3.Space.xxs), m3.dp(M3.Space.xxs), m3.dp(M3.Space.xxs), m3.dp(M3.Space.xxs))
        }
    }

    /**
     * A selectable row inside [inset] -- secondaryContainer when selected, as M3 marks the active
     * item in a list of destinations, and transparent when not. Its corners are the card's less the
     * card's own padding, so the selection sits concentric inside the border around it.
     */
    fun rowBackground(context: Context, selected: Boolean) = M3(context).let { m3 ->
        val corner = M3.Metrics.groupCorner - M3.Space.xxs
        if (selected) {
            m3.filled(m3.secondaryContainer, corner, m3.onSecondaryContainer)
        } else {
            m3.filled(Color.TRANSPARENT, corner, m3.onSurface)
        }
    }

    fun vGap(context: Context, heightDp: Int): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                M3(context).dp(heightDp)
            )
        }

    fun hGap(context: Context, widthDp: Int): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(M3(context).dp(widthDp), 1)
        }

    /**
     * Wraps tall page content so it scrolls without the edge glow the rest of the app drops.
     *
     * A NestedScrollView rather than a plain ScrollView, for the same reason the tabs use one (see
     * MainUiLauncher): only a view that reports nested scrolling can scroll inside something that
     * scrolls too — here the sheet itself. See [scrollsInsideSheet].
     */
    fun scroll(context: Context, content: View): NestedScrollView =
        NestedScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            // No fading edge. It was here for a sheet, where content ran up against a drag
            // handle above and a button row below and dissolving into them beat being cut off by
            // them. A page has neither, and what is at the bottom of one now is the back bar --
            // which is glass, and the whole point of glass is that you can see what is behind it.
            // Fading the content out first left it hovering over nothing.
            scrollsInsideSheet(this)
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

    /**
     * Marks the list a page's panel scrolls by itself, so [PageHost] runs it to the bottom of the
     * screen -- under the back bar and the navigation bar, as the tabs scroll -- with the room to
     * clear them as padding inside the list rather than a strip reserved under it.
     */
    fun scrollsUnderBar(list: View) {
        list.setTag(com.elitedarkkaiser.redmagic.R.id.page_scroller, true)
        (list as? ViewGroup)?.clipToPadding = false
    }

    /**
     * The view a page scrolls by: one marked with [scrollsUnderBar] if there is one, otherwise the
     * first vertical scroller in the panel. A horizontal one -- a row of chips -- never counts.
     */
    internal fun findUnderBar(root: View): View? = findTagged(root) ?: findScroller(root)

    private fun findTagged(root: View): View? {
        if (root.getTag(com.elitedarkkaiser.redmagic.R.id.page_scroller) == true) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findTagged(root.getChildAt(i))?.let { return it }
        }
        return null
    }

    // Only down the trailing edge: a scroller with something under it -- a button row that is not
    // pinned, say -- would carry that something behind the back bar with it.
    private fun findScroller(root: View): View? {
        if (root is NestedScrollView || root is android.widget.ScrollView ||
            root is android.widget.AbsListView
        ) return root
        if (root is ViewGroup) {
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                if (child.visibility == View.GONE) continue
                return findScroller(child)
            }
        }
        return null
    }

    /**
     * Hands [view] the sheet's vertical drags instead of the sheet itself taking them.
     *
     * [BottomSheetBehavior] gives a drag to the content only when it can find a child that reports
     * nested scrolling — that is exactly what its findScrollingChild() looks for — and a plain
     * ListView or ScrollView reports false until asked. Without this the sheet takes every drag
     * over the list: it stutters as the two argue over a slow gesture, and a downward drag closes
     * the sheet rather than scrolling the list.
     */
    fun scrollsInsideSheet(view: View) {
        view.isNestedScrollingEnabled = true
        view.overScrollMode = View.OVER_SCROLL_NEVER
    }

    /**
     * Shows [content] as a modal bottom sheet, under a drag handle and on this app's own surface —
     * both of which stay put while [content] scrolls, so the handle and the rounded top corners
     * can't slide away with a long list. The sheet's stock background is made transparent rather
     * than left to double up an extra rectangle behind that surface.
     *
     * Expands fully rather than resting at a partial peek — every dialog here is a single-purpose
     * form, not content worth browsing at a glance — but still swipes down to dismiss, unless
     * [cancelable] is false (a confirmation the caller does not want dismissed by a stray swipe).
     */
    /**
     * Opens [content] under the row that was tapped, and hands back something to close it with.
     *
     * This used to build a `BottomSheetDialog`: a floating card in a window of its own, over a
     * dimmed copy of the app, with a still of the screen captured behind it so the glass had
     * something to refract. All of that followed from it being a separate window -- a dialog
     * cannot reach the Activity's graphics layer, so the screen behind it had to be photographed,
     * its background cleared in three places to stop the host theme showing through, and its
     * immersive state copied over by hand or the status bar came back the moment it took focus.
     *
     * None of that applies to a View in the app's own tree. It opens as a screen of the app --
     * see [PageHost], which also explains why that, and not an inline panel, is what this content
     * wants to be. The name and the shape of what comes back are unchanged through all of it,
     * because every screen in this app opens one of these and they have all moved together each
     * time.
     */
    fun show(activity: Activity, content: View, cancelable: Boolean = true): Panel =
        PageHost.push(activity, content, cancelable)
}

