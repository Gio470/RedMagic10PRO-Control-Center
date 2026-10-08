package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import java.util.WeakHashMap

/**
 * A screen of the app, for the panels that used to be bottom sheets.
 *
 * ## Why a screen
 *
 * They were tried as a card unfolded under the row that opened them, and it looked wrong for a
 * reason worth writing down: this content was never small. Every one of these panels is built
 * from [M3Dialog]'s own parts -- a headline with an icon chip and a supporting paragraph, section
 * labels, rows, an action row with Done and Cancel -- and three of them are lists hundreds of
 * entries long. That is the anatomy of a screen. Folded into a clipped box under a row it repeats
 * the name of the row it is under, then squeezes everything that was meant to breathe.
 *
 * So a panel opens as a screen, and the work is making it look like one of this app's screens
 * rather than a dialog that happens to be full size: the same gutter as a tab, the title in the
 * place every tab puts its title -- lifted out of the content by [hoistTitle], so it is not
 * written twice -- and the bottom bar still there over the top of it.
 *
 * ## What went away with the sheet
 *
 * These were `BottomSheetDialog`s once, each in a window of its own, and nearly everything that
 * made them complicated came from that. A dialog cannot reach the Activity's graphics layer, so
 * the screen behind it had to be photographed for the glass to refract; its background had to be
 * cleared in three places to stop the host theme showing through; its immersive state had to be
 * copied over by hand or the status bar came back the moment it took focus. A page is a View in
 * the same frame the tabs live in, so none of it applies -- and because that frame is inside the
 * Compose tree that draws the bottom bar, the bar stays put over the page and refracts it with
 * nothing captured and nothing to keep in sync.
 */
object PageHost {

    private class Entry(val panel: Panel, val view: View, val title: String?)

    /**
     * Where pages are added: the frame holding the tab content, set by
     * [MainUiLauncher][com.elitedarkkaiser.redmagic.MainUiLauncher]. Weak, because it holds a
     * View -- an Activity on its way out must not be kept alive by this.
     */
    private val hosts = WeakHashMap<Activity, ViewGroup>()
    private val stacks = WeakHashMap<Activity, MutableList<Entry>>()
    private val backCallbacks = WeakHashMap<Activity, OnBackPressedCallback>()
    private val listeners = WeakHashMap<Activity, (String?) -> Unit>()

    fun attach(activity: Activity, host: ViewGroup) {
        hosts[activity] = host
    }

    /**
     * Told the name of the open page, or null when none is, every time that changes.
     *
     * This is what puts the bottom bar into back mode -- see
     * [BackGlassBar][com.elitedarkkaiser.redmagic.ui.glassbar.GlassBarScaffold]. The bar is the
     * way out of a page, so it has to know there is one.
     */
    fun onOpenPageChanged(activity: Activity, listener: (String?) -> Unit) {
        listeners[activity] = listener
        listener(topTitle(activity))
    }

    /** Backs out of the open page, for the bar's own back control. */
    fun popTop(activity: Activity) {
        val top = stacks[activity]?.lastOrNull() ?: return
        if (top.panel.cancelable) top.panel.cancel()
    }

    private fun topTitle(activity: Activity): String? = stacks[activity]?.lastOrNull()?.title

    private fun notify(activity: Activity) {
        listeners[activity]?.invoke(topTitle(activity))
    }

    fun push(activity: Activity, content: View, cancelable: Boolean): Panel {
        val host = hosts[activity]
            ?: activity.findViewById(android.R.id.content)
            ?: throw IllegalStateException("No host to put a page in")

        val m3 = M3(activity)
        val page = FrameLayout(activity).apply {
            // Draws nothing. The app's background is a Compose layer behind this whole View tree,
            // and an opaque fill here would cover it -- which is what made a sheet read as a
            // separate thing sitting on the app. What a page covers is the tab underneath, by
            // hiding it, so the background still shows through and a page reads as a tab.
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val panel = Panel(cancelable) { remove(activity, page) }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val title = hoistTitle(content)
        column.addView(titleBar(activity, m3, title))
        // Pulled out before content goes into the column: see [ActionRow][M3Dialog.ActionRow] for
        // why a stacked row costs the scrollable content more than its own height, and
        // [pinActionRow] for where it goes instead. A panel with no such row is untouched.
        val actionRow = takeActionRow(content)
        // Everything gets the rest of the height, scrolling or not.
        //
        // WRAP_CONTENT here was wrong in a way that only showed up on the panels built as a column
        // of [scrolling region][action row]: with nothing bounding the column, the scrolling
        // region fell back on the height it had asked for -- a fraction of the screen, guessed
        // back when these were sheets -- and the action row under it was pushed off the bottom of
        // the page. Giving the column the page's own remaining height is what lets those regions
        // ask for "whatever is left" instead of guessing, and a short panel stretched to fill lays
        // its children out from the top exactly as it did before.
        val fills = content is NestedScrollView || content is ScrollView
        column.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        page.addView(column)
        if (actionRow != null) pinActionRow(page, actionRow, m3)

        // Where the room for the bar (and, when there is one, the pinned action row above it)
        // goes -- which decides whether the page floats over both or just sits above a reserved
        // strip.
        //
        // Content that scrolls edge to edge gets it inside the scrolling region, which is exactly
        // where the tabs put theirs (see MainUiLauncher): padding inside scrolled content is
        // travel, so the last row rises clear of the bar at the end of the scroll while everything
        // before it passes underneath. This is the shape a bare `M3Dialog.scroll(...)` panel has
        // -- no action row, [actionRow] is null -- and it is unchanged from before.
        //
        // A panel with an action row is never this shape: every one of them wraps its scroller in
        // [M3Dialog.panel], so `content` here is that wrapper, not the scroller itself, and
        // [scrolled] is null. Its clearance is viewport, not travel -- the page reserves the room
        // at the bottom, [column] gets less height as a result, and the scroller inside `content`
        // is handed whatever [column] has left, the same weighted division that already keeps it
        // off the title bar. That is a plain measurement the layout system does for free, which is
        // why this is the version that does not collide: the alternative, tried in 2.8.5, added
        // the row's height as extra padding *inside* the scrolled content instead, and a value
        // read off a view before that view has been laid out is a race, not a number.
        val scrolled = (content as? ViewGroup)?.takeIf { fills }?.getChildAt(0)
        // A panel that is a few fixed controls over a list that scrolls on its own -- De-Bloat,
        // Processes -- marks that list (M3Dialog.scrollsUnderBar). With no action row to pin, it
        // gets the tabs' treatment too: the page stops reserving a strip at the bottom, the list
        // runs to the bottom edge of the screen, under the back bar and the navigation bar, and
        // its own bottom padding is the travel that lets its last row rise clear of both. Left to
        // the page's reserved strip, the list stopped at a line above the bar and the strip under
        // it showed nothing but background -- the "blocked" band at the bottom of those screens.
        //
        // Every page that has somewhere to scroll now does this, pinned action row or not: the
        // band a reserved strip left under the content -- background, and nothing on it -- read
        // as something stuck behind the back bar. A pinned row floats over the scrolling content
        // the way the back bar does, and the scroller's padding covers both of them.
        val underBar = if (fills) null else M3Dialog.findUnderBar(content)
        if (underBar != null) {
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, 0)
            (underBar as? ViewGroup)?.clipToPadding = false
        }
        // Last known inset values, read by [recompute] whichever of the two things below calls it:
        // a fresh window inset (rotation, IME) or the action row's own first real measurement.
        // Neither is known at push() time, so both start at zero and correct once the real numbers
        // arrive -- a page opens under the bar's usual clearance for one frame at most.
        var cutoutTop = 0
        var barTop = m3.dp(BAR_TOP_DP)

        fun recompute() {
            // page's own padding is bar clearance ONLY, in both branches -- it is what a pinned
            // row's own gravity=BOTTOM position is measured against, so it cannot also carry the
            // row's height or the row ends up floating rowHeight too high, above its own clearance
            // rather than sitting on it. The row needs no margin of its own as a result: page's
            // padding already puts its bottom edge exactly barTop above the true bottom, which is
            // where the glass bar is.
            //
            // The room the row itself needs comes out of [column] instead, on the far side of that
            // shared padding, where it only ever affects `content`'s own weighted share -- never
            // the row, which is column's *sibling* in [page], not its child.
            // Sideways, the extra gutter a wider window gets (M3.extraGutterPx): zero on a phone
            // held upright, the 24dp margin and a capped column past that.
            val side = m3.extraGutterPx()
            val travel = scrolled ?: underBar
            val rowRoom = if (actionRow != null) actionRow.height + m3.dp(ROW_GAP_DP) else 0
            page.setPadding(side, cutoutTop, side, if (travel != null) 0 else barTop)
            if (travel != null) {
                // Everything runs to the bottom edge; the row stands on the bar by its own margin,
                // and the scroller's padding is the travel that lifts its last line over both.
                column.setPadding(0, 0, 0, 0)
                actionRow?.let { row ->
                    (row.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        if (lp.bottomMargin != barTop) {
                            lp.bottomMargin = barTop
                            row.layoutParams = lp
                        }
                    }
                }
                travel.setPadding(
                    travel.paddingLeft, travel.paddingTop, travel.paddingRight,
                    // A little over the bar, so the last row does not sit flush against it.
                    barTop + rowRoom + if (underBar != null) m3.dp(M3.Space.md) else 0
                )
            } else {
                column.setPadding(0, 0, 0, rowRoom)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            barTop = m3.dp(BAR_TOP_DP) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            recompute()
            insets
        }
        // The row's real height is not known until its first layout pass, which recompute() above
        // may well have run ahead of (insets and the first layout race each other). Corrected once
        // more here rather than inside the insets callback itself, which runs again on every future
        // inset change and would otherwise stack a fresh listener on the row each time.
        //
        // Posted, and only when the row's height has actually changed: this listener runs in the
        // middle of a layout pass, and resizing the scroller (or the row's own margin) from in
        // there is a requestLayout during layout -- which Android defers and can drop, leaving
        // parts of the page measured but never laid out or drawn. That was the System theme
        // page opening with blank gaps where its tiles should be.
        var rowHeight = -1
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        actionRow?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height != rowHeight) {
                rowHeight = v.height
                mainHandler.post { recompute() }
            }
        }

        val stack = stacks.getOrPut(activity) { mutableListOf() }
        // What this page stands in front of: the page below it, or -- for the first -- the tabs.
        cover(host, stack.lastOrNull()?.view, hidden = true)

        host.addView(page)
        // The window handed its insets out long before this page existed, and a view added
        // afterwards is not sent them again unless it asks -- without this the page never learnt
        // how tall the navigation bar is and kept only the back bar's clearance, leaving its
        // bottom rows under the navigation bar. It also starts from the insets the window has now,
        // so the very first frame is already right rather than corrected a dispatch later.
        ViewCompat.getRootWindowInsets(host)?.let { insets ->
            cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            barTop = m3.dp(BAR_TOP_DP) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }
        recompute()
        ViewCompat.requestApplyInsets(page)
        enter(page)

        stack.add(Entry(panel, page, title))
        installBackCallback(activity)
        backCallbacks[activity]?.isEnabled = true
        notify(activity)
        return panel
    }

    /**
     * Takes a panel's trailing row of buttons out of it, if it has one.
     *
     * Every one of these panels is built as a column ending in an action row -- see
     * [M3Dialog.ActionRow] for why that costs the scrollable content more than the row's own
     * height. Nothing about the panels themselves changes: they still add the row last, the same
     * call they always made. This is what turns "last" into "found."
     *
     * Only finds it when it is [content]'s own direct last child. A panel that calls
     * `M3Dialog.show(activity, M3Dialog.scroll(activity, panel))` hands this a NestedScrollView
     * whose one child is `panel`, not `panel`'s own last child -- the row is a grandchild here,
     * not a child, and this looks one level too shallow to find it. That is a deliberate scope
     * limit rather than a bug worth widening yet: it means those panels (Fan LED, Logo LED,
     * Charging LED, Trigger setup, Call lighting, Game mode) keep their row stacked exactly as
     * before, which is safe -- `last.tag` is simply never [M3Dialog.ActionRow] for them, so this
     * returns null and changes nothing -- rather than silently mispositioning a row on panels this
     * was never checked against.
     */
    private fun takeActionRow(content: View): View? {
        val column = content as? ViewGroup ?: return null
        val last = column.getChildAt(column.childCount - 1) ?: return null
        if (last.tag !== M3Dialog.ActionRow) return null
        column.removeView(last)
        return last
    }

    /**
     * Puts the row back over the page rather than in it, at the same shelf the bottom bar sits on.
     *
     * No background of its own: these are shaped, filled buttons, and what belongs behind them is
     * the page they act on, the same as the bar above them. [column]'s own gutter (16dp) is
     * repeated here so the buttons land on the same vertical lines as the content they came from.
     */
    private fun pinActionRow(page: FrameLayout, row: View, m3: M3) {
        page.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = m3.dp(16)
            rightMargin = m3.dp(16)
        })
    }

    private fun remove(activity: Activity, view: View) {
        val host = view.parent as? ViewGroup
        val stack = stacks[activity]
        stack?.removeAll { it.view === view }
        // Uncovered before the animation rather than after it, so what the page slides off is
        // already there rather than appearing once it has gone.
        if (host != null) cover(host, stack?.lastOrNull()?.view, hidden = false)
        exit(view) { host?.removeView(view) }
        backCallbacks[activity]?.isEnabled = stack?.isNotEmpty() == true
        notify(activity)
    }

    /**
     * Hides or restores what a page stands in front of.
     *
     * A null [target] means everything in the host that is not a page -- the tab content. The app's
     * background is a layer behind all of it and is never touched, which is what lets a page draw
     * nothing of its own.
     */
    private fun cover(host: ViewGroup, target: View?, hidden: Boolean) {
        val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (target != null) {
            target.visibility = visibility
            return
        }
        val pages = stacks.values.flatten().map { it.view }
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (pages.none { it === child }) child.visibility = visibility
        }
    }

    /**
     * Re-registered on every push rather than installed once: the dispatcher runs the most
     * recently added enabled callback, so re-adding is what keeps Back going to whichever of a
     * page and an [Expander] was opened last.
     */
    private fun installBackCallback(activity: Activity) {
        val owner = activity as? ComponentActivity ?: return
        backCallbacks.remove(activity)?.remove()
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val top = stacks[activity]?.lastOrNull() ?: return
                if (top.panel.cancelable) top.panel.cancel()
            }
        }
        owner.onBackPressedDispatcher.addCallback(owner, callback)
        backCallbacks[activity] = callback
    }

    /**
     * The page's own heading: the panel's name, where a tab puts its name.
     *
     * No back control of its own. That lives on the bottom bar, which switches into back mode for
     * as long as a page is open -- the pattern is from github.com/sameerasw/essentials, and the
     * reason it is better than a control up here is that the bar is already the one thing on
     * screen that is always under a thumb. See BackGlassBar.
     *
     * The title is M3's large top app bar title -- headlineMedium, regular weight, 16dp in from
     * the edge -- rather than the smaller one a sheet's headline used: the point is that this reads
     * as a screen of the app.
     */
    private fun titleBar(activity: Activity, m3: M3, title: String?): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // The tab gutter, so the title lines up with the content under it.
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.xs), m3.dp(M3.Space.md), 0)
            if (title != null) {
                addView(TextView(activity).apply {
                    text = title
                    m3.styleText(this, M3.Type.headlineLarge, m3.onSurface)
                    // Level with a tab's own title (32dp of tab padding plus its 4dp), counting
                    // this bar's 8dp, so the title does not jump between a tab and a page.
                    setPadding(m3.dp(8), m3.dp(28), m3.dp(8), m3.dp(M3.Space.lg))
                })
            }
        }

    /**
     * Takes the panel's name out of its content, so the page can show it and the content will not.
     *
     * Every panel names itself at the top, through [M3Dialog.header] or [M3Dialog.title], and both
     * tag that view. Left alone it would sit a few dp under the page's own title saying the same
     * thing in a smaller size. Hidden rather than removed, so nothing that kept a reference to it
     * -- a header with a switch in its trailing slot -- finds it gone.
     */
    private fun hoistTitle(content: View): String? {
        val headline = findHeadline(content) ?: return null
        val text = headline.text?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val row = headline.parent as? View
        when (row?.tag) {
            // A header row of chip plus headline: all of it goes, chip included.
            M3Dialog.HeadlineRow -> row.visibility = View.GONE
            // A header row with a control in it stays put, and so does its headline -- lifting the
            // title out from over a switch would leave the switch explaining itself.
            M3Dialog.HeadlineRowKept -> return null
            // A bare title(): nothing around it to worry about.
            else -> headline.visibility = View.GONE
        }
        // Only the headline moves either way. A header's supporting line explains the screen and
        // belongs on it.
        return text
    }

    private fun findHeadline(view: View): TextView? {
        if (view.tag === M3Dialog.Headline) return view as? TextView
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findHeadline(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // ---- Coming and going ----
    //
    // Sideways, not up: a sheet rose from the bottom edge because that is where it lived, but a
    // page replaces what is on screen, and sliding in from the side is what that reads as -- M3's
    // shared-axis X transition. The timing is the skill's pairing for each direction: a page
    // entering takes the emphasized-decelerate curve over medium4 (400ms); one leaving for good
    // takes emphasized-accelerate over short4 (200ms), so the way out is quicker than the way in.

    /**
     * How far it travels: the shared-axis transition's 30dp. A fixed distance rather than a
     * fraction of the page's width, because the width is not known yet: a page animates in the
     * moment it is added, and waiting for a layout pass to measure it means either a posted
     * runnable that can land a frame late or a first frame with nothing on it.
     */
    private const val SLIDE_DP = 30f

    /**
     * Where the bottom bar's top edge is, measured up from the bottom of the window.
     *
     * The bar is 64dp tall and stands 12dp off the navigation inset (GlassBarScaffold), so this is
     * those two added up -- not the 96dp the tabs reserve, which carries extra air a tab wants
     * under its last row and a page's button row does not. Guessing high here is exactly what put
     * a band of nothing between the buttons and the bar.
     */
    private const val BAR_TOP_DP = 76

    /**
     * Extra room between the scrolled content and a pinned action row, on top of whatever
     * [M3Dialog.panel]'s own bottom padding (24dp) already contributes on the content side.
     * Without this the two clearances -- the row's own height and the panel's existing gutter --
     * add up to the same total a *stacked* row's 20dp top padding used to give, which pinning was
     * supposed to improve on and would otherwise just relocate.
     */
    private const val ROW_GAP_DP = 16

    private fun enter(page: View) {
        val slide = SLIDE_DP * page.resources.displayMetrics.density
        page.alpha = 0f
        page.translationX = slide
        page.animate()
            .translationX(0f).alpha(1f)
            .setDuration(M3.Motion.medium4)
            .setInterpolator(M3.Motion.emphasizedDecelerate)
            .start()
    }

    private fun exit(page: View, then: () -> Unit) {
        val slide = SLIDE_DP * page.resources.displayMetrics.density
        page.animate()
            .translationX(slide).alpha(0f)
            .setDuration(M3.Motion.short4)
            .setInterpolator(M3.Motion.emphasizedAccelerate)
            .withEndAction(then)
            .start()
    }
}
