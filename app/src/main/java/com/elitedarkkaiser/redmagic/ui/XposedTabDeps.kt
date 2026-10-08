package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout

data class XposedTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    /** The host, for the icon pack picker: [M3Dialog.show] needs a window to attach to. */
    val activity: () -> Activity
)
