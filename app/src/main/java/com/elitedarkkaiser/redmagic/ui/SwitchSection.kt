package com.elitedarkkaiser.redmagic.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A section gated by a master switch: a card carrying the title and the switch, and beneath it
 * everything that switch governs, which folds away when it is off.
 *
 * Hidden rather than greyed out. A disabled control still occupies the screen and still has to be
 * read past to reach the next thing; on a tab with four of these, three switched off, the greyed
 * version was most of the page. Folding them away leaves a short column of cards that says what is
 * on at a glance.
 *
 * Children added after construction land in the collapsing body rather than beside the card, so a
 * call site keeps writing `sectionWithSwitch(...).apply { addView(row) }` without knowing any of
 * this. [addView] is the single funnel every other overload routes through, so overriding it is
 * enough to catch them all.
 */
class SwitchSection(
    context: Context,
    m3: M3,
    /** Kept so the section can also be shown as a card that opens it (see [SectionPages]). */
    val title: String,
    switch: View,
    /** The leading icon, as [M3.row] draws one. */
    val glyph: String? = null,
    /** What the switch governs, in a line, so the card answers that without being opened. */
    val supporting: String? = null
) : LinearLayout(context) {

    private val body = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    /** False only while the constructor is putting the card and the body in place. */
    private var routeToBody = false

    private var animator: ValueAnimator? = null

    var isExpanded: Boolean = true
        private set

    /** False until the first setExpanded lands, so that one is not mistaken for a no-op. */
    private var stateApplied = false

    init {
        M3.MasterSwitch.mark(switch)
        orientation = VERTICAL
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = m3.dp(M3.Space.xs) }

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            // Iconify's master switch: a primaryContainer pill with 20dp of padding all round.
            // Iconify drops the summary to keep the pill one line; this keeps it, so a card
            // carrying one takes the group corner instead of the pill's full round.
            background = m3.cardShape(
                m3.primaryContainer, if (supporting == null) M3.Shape.full else M3.Shape.extraLarge
            )
            setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(20))
            minimumHeight = m3.dp(
                if (supporting != null) M3.Metrics.listTwoLine else M3.Metrics.listOneLine
            )
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            if (glyph != null) {
                addView(m3.iconChip(glyph, title, m3.onPrimaryContainer),
                    LayoutParams(m3.dp(24), m3.dp(24))
                        .apply { marginEnd = m3.dp(M3.Metrics.listInset) })
            }

            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(TextView(context).apply {
                    text = title
                    m3.styleText(this, M3.Type.titleMedium, m3.onPrimaryContainer)
                })
                if (supporting != null) {
                    addView(TextView(context).apply {
                        text = supporting
                        m3.styleText(this, M3.Type.bodyMedium, m3.secondaryText(m3.onPrimaryContainer))
                    })
                }
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            addView(switch, LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = m3.dp(M3.Metrics.listInset) })
        })

        addView(body, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        routeToBody = true
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (routeToBody) {
            body.addView(child, index, params)
        } else {
            super.addView(child, index, params)
        }
    }

    /**
     * Folds the body open or shut.
     *
     * [animate] is false for the initial state, where there is nothing to animate from and a fold
     * on first draw would read as the screen twitching as it opens.
     */
    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        // Genuinely nothing to do, and that has to mean nothing -- not "re-apply the same state".
        // The status refresh drives this on every pass, and applyImmediately requests a layout, so
        // re-applying made the Cooling tab relayout itself on a timer for no reason.
        if (stateApplied && isExpanded == expanded && animator == null) return

        if (!stateApplied) {
            stateApplied = true
            isExpanded = expanded
            applyImmediately(expanded)
            return
        }
        isExpanded = expanded
        animator?.cancel()

        // Only animate while attached to a window. Off-screen -- which a section reached by a
        // card's mirror writing its switch is (the page it lives on is built but not attached) --
        // there is nothing to animate, and measuring the body to animate toward would measure a
        // ComposeView inside it with no window to compose in, which throws "Cannot locate
        // windowRecomposer". applyImmediately never measures; it sets WRAP_CONTENT and requests a
        // layout, so the body measures correctly when the page is next shown and actually attached.
        if (!animate || !isAttachedToWindow) {
            applyImmediately(expanded)
            return
        }

        val start = body.height.takeIf { it > 0 && body.visibility == VISIBLE } ?: 0
        val target = if (expanded) measureBodyHeight() else 0
        if (start == target) {
            applyImmediately(expanded)
            return
        }

        body.visibility = VISIBLE
        animator = ValueAnimator.ofInt(start, target).apply {
            // The skill's pairing: what opens is entering (emphasized decelerate, medium4), what
            // folds away is leaving (emphasized accelerate, short4).
            duration = if (expanded) M3.Motion.medium4 else M3.Motion.short4
            interpolator = if (expanded) M3.Motion.emphasizedDecelerate else M3.Motion.emphasizedAccelerate
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                body.layoutParams = body.layoutParams.apply { height = value }
                body.requestLayout()
                // Fades over the first (opening) or last (closing) part of the fold rather than the
                // whole of it, so the content is legible for most of the movement instead of
                // washed out through all of it.
                body.alpha = if (target == 0) {
                    (value.toFloat() / start.coerceAtLeast(1)).coerceIn(0f, 1f)
                } else {
                    (value.toFloat() / target.coerceAtLeast(1) * 2f).coerceIn(0f, 1f)
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animator = null
                    applyImmediately(expanded)
                }
            })
            start()
        }
    }

    private fun applyImmediately(expanded: Boolean) {
        body.alpha = 1f
        body.layoutParams = body.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        body.visibility = if (expanded) VISIBLE else GONE
        body.requestLayout()
    }

    /**
     * What the body wants to be, measured at the width it will actually get. Needed because a
     * collapsed body is GONE and so has no height to animate away from.
     */
    private fun measureBodyHeight(): Int {
        val availableWidth = (width - paddingLeft - paddingRight).takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        // A ComposeView in the body throws if measured with no window (see setExpanded); callers
        // already avoid that, and this keeps any other path from crashing rather than mis-sizing.
        return runCatching {
            body.measure(
                MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            body.measuredHeight
        }.getOrDefault(0)
    }

}

