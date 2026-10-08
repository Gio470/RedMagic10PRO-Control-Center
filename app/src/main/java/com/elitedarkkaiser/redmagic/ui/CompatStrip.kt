package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The three things that have to be true for this app to do everything it offers: the right phone,
 * root, and the Xposed module.
 *
 * Quiet when they are. All three passing is the ordinary case and deserves one line, not a
 * checklist of three ticks restating their own titles -- so it collapses to a single row and gets
 * out of the way of the readings underneath. The moment one fails it expands, turns the error tone,
 * and names what is missing, because that is the one time this is the most important thing on the
 * screen.
 *
 * It owns its own state rather than exposing its TextViews, so the two places that feed it -- the
 * status poll for the phone and root, this tab's own read for the module -- cannot leave it showing
 * a headline that disagrees with its own rows.
 */
class CompatStrip(context: Context, private val m3: M3) : LinearLayout(context) {

    private data class Check(val badge: TextView, val label: TextView, var ok: Boolean = false)

    private val headlineMark = mark()
    private val headline = TextView(context)
    private val detail = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(0, m3.dp(M3.Space.xs), 0, 0)
    }

    private val device = check("Checking device")
    private val root = check("Checking root")
    private val module = check("Checking module")

    /** False until the first real reading, so it does not flash "all good" on the way up. */
    private var settled = false

    init {
        orientation = VERTICAL
        setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(20))
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = m3.dp(M3.Space.md) }

        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(headlineMark, LayoutParams(m3.dp(MARK_DP), ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = m3.dp(M3.Space.sm) })
            addView(headline, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })

        detail.addView(row(device))
        detail.addView(row(root))
        detail.addView(row(module))
        addView(detail)

        render()
    }

    fun setDevice(ok: Boolean, label: String) = update(device, ok, label)

    fun setRoot(ok: Boolean, label: String) = update(root, ok, label)

    fun setModule(ok: Boolean, label: String) = update(module, ok, label)

    private fun update(check: Check, ok: Boolean, label: String) {
        settled = true
        check.ok = ok
        check.label.text = label
        render()
    }

    private fun render() {
        val failed = listOf(device, root, module).filter { !it.ok }
        val allGood = settled && failed.isEmpty()

        val container = if (allGood) m3.primaryContainer else m3.errorContainer
        val onContainer = if (allGood) m3.onPrimaryContainer else m3.onErrorContainer
        // A card, so the card corner; the container pair says whether all is well.
        background = m3.filled(container, M3.Shape.extraLarge, onContainer, card = true)

        headlineMark.text = if (allGood) Icons.CHECK else Icons.WARNING
        headlineMark.setTextColor(onContainer)
        headline.text = when {
            !settled -> "Checking this phone…"
            allGood -> "Ready"
            failed.size == 1 -> failed.first().label.text.toString()
            else -> "${failed.size} things missing"
        }
        m3.styleText(headline, M3.Type.titleMedium, onContainer)

        // Collapsed when everything passes: the headline has already said it, and three ticks under
        // "Ready" is the same news three more times.
        detail.visibility = if (allGood) GONE else VISIBLE

        listOf(device, root, module).forEach {
            it.badge.text = if (it.ok) Icons.CHECK else Icons.XMARK
            it.badge.setTextColor(onContainer)
            it.badge.alpha = if (it.ok) 0.6f else 1f
            it.label.setTextColor(onContainer)
            it.label.alpha = if (it.ok) 0.75f else 1f
        }
    }

    private fun mark(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        m3.styleGlyph(this, 16f, m3.onPrimaryContainer)
    }

    private fun check(initial: String): Check = Check(
        badge = mark().apply { m3.styleGlyph(this, 14f, m3.onPrimaryContainer) },
        label = TextView(context).apply {
            text = initial
            m3.styleText(this, M3.Type.bodyMedium, m3.onPrimaryContainer)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
    )

    private fun row(check: Check): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, m3.dp(M3.Space.xxs), 0, m3.dp(M3.Space.xxs))
        addView(check.badge, LayoutParams(m3.dp(MARK_DP), ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = m3.dp(M3.Space.sm) })
        addView(check.label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private companion object {
        /** The column the marks sit in: M3's 24dp icon box. */
        const val MARK_DP = 24
    }
}

