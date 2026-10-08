package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Turns a tab's master-switch sections into Iconify's home cards, each opening its section as a
 * page of its own -- the way an Iconify home card opens a screen that starts with its master
 * switch and carries that feature's settings under it.
 *
 * The section views themselves are kept, not rebuilt: the status poll and the switches' own
 * listeners hold references into them, so the same view moves from page to page each time its
 * card is opened, and everything bound to it keeps working.
 */
object SectionPages {

    /** One card: what it says, and the view its page shows. */
    data class Entry(
        val title: String,
        val subtitle: String?,
        val glyph: String,
        val content: View
    )

    /**
     * The card for [entry], which takes over its page's master switch when it has one.
     *
     * The switch moves rather than being copied: its row comes off the page, and the card carries
     * it from then on. The switch view itself is kept, not rebuilt -- a detached View's listener
     * still fires when something sets isChecked, and that listener is what does the feature's
     * actual work -- so the card writes to the real switch and reads back what it settled on.
     *
     * A feature whose page was nothing but that switch has nothing left to open, so its card
     * toggles instead of opening anything.
     */
    fun item(activity: Activity, entry: Entry): IconifyGrid.Item {
        val switch = M3.MasterSwitch.find(entry.content) as? android.widget.CompoundButton
            ?: return IconifyGrid.Item(entry.title, entry.subtitle, entry.glyph) {
                open(activity, entry)
            }

        (switch.parent as? ViewGroup)?.let { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
        // The run of rows the master row was at the head of now starts one row later, and the
        // corners saying where a group begins were worked out while it was still there.
        M3(activity).connectRows(entry.content)

        val master = IconifyCard.Master(
            read = { switch.isChecked },
            // Only while the real switch would take a finger: a disabled one is either mid-write
            // (a root round trip still in flight) or stood down (no module to do the work), and
            // setting isChecked from code would go straight past that. The card reads back what
            // the switch settled on, so a refused flip springs back there too.
            write = { on -> if (switch.isEnabled) switch.isChecked = on },
            enabled = { switch.isEnabled }
        )
        // A feature that was nothing but its switch has no page left, so its card has nothing to
        // open and is the switch alone.
        val emptyPage = (entry.content as? ViewGroup)?.childCount == 0
        return IconifyGrid.Item(
            entry.title, entry.subtitle, entry.glyph, master,
            if (emptyPage) null else ({ open(activity, entry) })
        )
    }

    fun entry(section: SwitchSection): Entry =
        Entry(section.title, section.supporting, section.glyph ?: Icons.CONFIGURE, section)

    /**
     * Replaces every [SwitchSection] directly inside [container] with one grid of cards, placed
     * where the first of them was. Anything else in the container stays where it is.
     */
    fun convert(activity: Activity, container: ViewGroup,
        extraItems: List<IconifyGrid.Item> = emptyList()) {
        val sections = (0 until container.childCount)
            .map { container.getChildAt(it) }
            .filterIsInstance<SwitchSection>()
        if (sections.isEmpty()) return
        val at = container.indexOfChild(sections.first())
        sections.forEach { container.removeView(it) }
        container.addView(
            IconifyGrid.build(activity, sections.map { item(activity, entry(it)) } + extraItems),
            at
        )
    }

    /** Shows [entry]'s content as a page, taking it out of wherever it was last shown. */
    fun open(activity: Activity, entry: Entry) {
        (entry.content.parent as? ViewGroup)?.removeView(entry.content)
        // A feature hides its settings while it is switched off, which is what kept a tab short:
        // a section folds its body away, and the hook-backed screens hide theirs outright. The
        // switch is on the card now, so leaving them hidden here would only mean opening a screen
        // with nothing on it.
        val content = entry.content
        if (content is SwitchSection) {
            content.setExpanded(true, animate = false)
        } else if (content is ViewGroup) {
            for (i in 0 until content.childCount) content.getChildAt(i).visibility = View.VISIBLE
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // Lifted into the page's own title by PageHost.
            addView(M3Dialog.title(activity, entry.title))
            addView(entry.content, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.scroll(activity, body), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        M3Dialog.show(activity, panel)
    }
}

