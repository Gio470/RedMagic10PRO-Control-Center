package com.elitedarkkaiser.redmagic.ui

import android.widget.LinearLayout

data class HomeTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val openTab: (String) -> Unit,
    val isRedMagic10Pro: () -> Boolean,
    val deviceCompatModelSummary: () -> String,
    val hasRootAccess: () -> Boolean,
    val recheckDeviceCompatibility: () -> Unit
)
