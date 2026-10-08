package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.ui.backgrounds.BackgroundType

data class SettingsTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    /** The host, for the colour picker: [M3Dialog.show] needs a window to attach to. */
    val activity: () -> Activity,
    val sectionPanel: () -> LinearLayout,
    /**
     * Re-enters the Activity so a changed appearance setting is applied to a fresh theme. Only
     * for settings that bake into colours this app's Views draw once at construction (theme mode,
     * dynamic colour, Pure Black) -- glass blur and the background animation apply live instead
     * (see [onGlassBlurChanged], [onBackgroundAnimationChanged]) since they only affect the glass
     * bar's own Compose layer.
     */
    val restartForTheme: () -> Unit,
    /** Updates the glass bar's live Compose state; no Activity recreate needed. */
    val onGlassBlurChanged: (Boolean) -> Unit,
    /** Swaps the bottom bar between the floating pill and the docked one, live. */
    val onBarStyleChanged: (Boolean) -> Unit,
    /** Updates the animated background's live Compose state; no Activity recreate needed. */
    val onBackgroundAnimationChanged: (BackgroundType) -> Unit,
    /** Rebuilds every tab's Views in place with the new surface colours; no Activity recreate. */
    val onPureBlackChanged: () -> Unit,
    /**
     * Regenerates the palette and redraws every tab with it. Dynamic colour and the picked seed
     * both go through here rather than [restartForTheme]: the colours are computed in process now
     * (see [Palette]), so there is nothing left that only a new Activity could pick up.
     */
    val onPaletteChanged: () -> Unit,

    val getUseFahrenheit: () -> Boolean,
    val setUseFahrenheit: (Boolean) -> Unit,
    val saveUseFahrenheit: (Boolean) -> Unit,
    /** Re-reads temperature immediately so the Cooling tab's hero number reflects the new unit
     *  without waiting for the next timed poll. */
    val refreshStatus: () -> Unit,
    /** Restarts the three background polls so a changed period takes effect now, not on the next
     *  tick of the old one. */
    val onRefreshIntervalsChanged: () -> Unit
)
