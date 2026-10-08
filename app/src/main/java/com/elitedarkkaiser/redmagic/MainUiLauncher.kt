package com.elitedarkkaiser.redmagic

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView

internal object MainUiLauncher {
    data class Result(
        val header: TextView,
        val homeTab: LinearLayout,
        val hardwareTab: LinearLayout,
        val softwareTab: LinearLayout,
        val settingsTab: LinearLayout
    )

    fun launch(
        activity: MainActivity,
        dp: (Int) -> Int,
        createHomeTab: () -> LinearLayout,
        createHardwareTab: () -> LinearLayout,
        createSoftwareTab: () -> LinearLayout,
        buildHeader: () -> TextView,
        wrapWithGlassBar: (View) -> View,
        /** Raw per-frame vertical scroll delta (positive = scrolling down) of the one scroll
         *  container shared by every tab, so the glass bar can collapse/expand the way
         *  HorizontalFloatingToolbarAsScaffoldFabSample's floatingToolbarVerticalNestedScroll
         *  does -- fired for whichever tab happens to be showing, since they all scroll inside
         *  this same NestedScrollView. */
        onScrollDelta: (Int) -> Unit
    ): Result {
        // The content root is handed to the Compose glass host, which draws it full-bleed and
        // floats the bar over it — the bar can only refract content recorded inside its own
        // Compose tree, so the content must be hosted there rather than sitting beside the bar.
        // Left transparent rather than painted here: GlassBarScaffold paints the page's solid
        // colour (or an animated background) as a Compose layer behind this whole View tree, so
        // an opaque fill here would hide it completely regardless of what that layer draws.
        val root = FrameLayout(activity)

        val contentFrame = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(96))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // The window is edge-to-edge with the status bar hidden, so the top padding can no longer
        // be the status bar height -- that would reserve a strip for a bar that is not there. It
        // is the display cutout instead, which is the only thing left at the top that content
        // must clear (zero on a device without one).
        //
        // The bottom takes the navigation bar inset for the same reason in reverse: edge-to-edge
        // means content now runs underneath the system navigation, so the fixed 96dp that used to
        // clear the floating glass bar no longer clears the gesture bar as well.
        //
        // Sideways it is M3's margin for the window's width: 16dp on a phone held upright, 24dp
        // and a column capped at 840dp on anything wider (see M3.extraGutterPx). Recomputed here
        // because a rotation re-applies the insets, and the width changes with it.
        val m3 = com.elitedarkkaiser.redmagic.ui.M3(activity)
        ViewCompat.setOnApplyWindowInsetsListener(contentFrame) { view, insets ->
            val cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val side = dp(16) + m3.extraGutterPx()
            view.setPadding(side, cutoutTop + dp(32), side, dp(96) + navBottom)
            insets
        }

        // A plain android.widget.ScrollView here would abruptly snap on reaching the top/bottom
        // while dragging — it doesn't implement the NestedScrollingChild contract Compose's touch
        // dispatch expects from a scrolling View hosted inside AndroidView (this content is handed
        // to GlassBarScaffold below), so scroll clamping ends up a hard jump instead of a smooth
        // stop. NestedScrollView implements that contract correctly.
        val contentScroll = NestedScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(contentFrame)
            setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    onScrollDelta(scrollY - oldScrollY)
                }
            )
        }

        // M3 Expressive large screen title.
        val header = buildHeader()
        contentFrame.addView(header)

        val homeTab = createHomeTab()
        val hardwareTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val softwareTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        // Not a bar destination: reached from the floating settings button instead.
        val settingsTab = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        contentFrame.addView(homeTab)
        contentFrame.addView(hardwareTab)
        contentFrame.addView(softwareTab)
        contentFrame.addView(settingsTab)

        root.addView(contentScroll)

        // Pages open in here, over the tabs. Inside the glass bar's Compose host rather than over
        // it, so the bar stays where it is and refracts the page the same way it refracts a tab.
        // See PageHost, which is where the app's bottom sheets went.
        com.elitedarkkaiser.redmagic.ui.PageHost.attach(activity, root)

        activity.setContentView(wrapWithGlassBar(root))

        return Result(
            header = header,
            homeTab = homeTab,
            hardwareTab = hardwareTab,
            softwareTab = softwareTab,
            settingsTab = settingsTab
        )
    }
}
