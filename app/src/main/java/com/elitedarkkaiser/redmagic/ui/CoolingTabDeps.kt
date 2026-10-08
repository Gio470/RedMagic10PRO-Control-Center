package com.elitedarkkaiser.redmagic.ui

import android.widget.LinearLayout
import android.widget.TextView

data class CoolingTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val sectionPanel: () -> LinearLayout,
    val subtleLabel: (String) -> TextView,
    val bodyText: (String) -> TextView,

    val getSelectedCurve: () -> String,
    val setSelectedCurve: (String) -> Unit,
    val setSelectedCurveSaved: (String) -> Unit,

    val getAutoFanCurveEnabled: () -> Boolean,
    val setAutoFanCurveEnabled: (Boolean) -> Unit,
    val setAutoFanEnabledSaved: (Boolean) -> Unit,

    /** The master fan power switch next to the temperature hero — separate from the per-level
     *  on/off HardwareController.setFanLevel(0) already does, this is the whole card's own switch. */
    val getFanPowerEnabled: () -> Boolean,
    val setFanPowerEnabled: (Boolean) -> Unit,
    val saveFanPowerEnabled: (Boolean) -> Unit,

    /** Persists the new interval and restarts the background poll loop at it immediately. */

    val getPumpEnabled: () -> Boolean,
    val setPumpEnabled: (Boolean) -> Unit,
    val getPumpProfile: () -> String,
    val getAutoPumpEnabled: () -> Boolean,
    val setAutoPumpEnabled: (Boolean) -> Unit,

    val startAutoFanService: () -> Unit,
    val stopAutoFanService: () -> Unit,
    val startAutoPumpService: () -> Unit,
    val stopAutoPumpService: () -> Unit,
    val savePumpState: () -> Unit,
    val saveAutoPumpState: () -> Unit,
    val refreshStatus: () -> Unit,
    val refreshSmartPumpStatusViews: () -> Unit,
    val buildAutoPumpStatusText: () -> Pair<String, String>,
    val updateManualCurveUiState: () -> Unit
)
