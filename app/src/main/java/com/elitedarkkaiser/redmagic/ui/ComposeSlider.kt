package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * The real Material 3 [Slider] (androidx.compose.material3.Slider), hosted in a small ComposeView
 * so this app's hand-drawn tab screens can drop one in exactly where a hand-styled SeekBar used
 * to sit -- replacing M3.styleSlider's custom-drawn track and thumb entirely, this app no longer
 * draws its own slider, it hosts the library's.
 *
 * Colours are pulled from [M3] so it follows the same Material You palette as everything else, and
 * are read fresh on every recomposition rather than cached, so a Pure Black toggle -- which rebuilds
 * whichever tab holds this slider from scratch (see MainActivity.refreshTabColors) -- gets a
 * genuinely fresh instance rather than one still reading colours from before the toggle.
 *
 * [onValueChange] and [onValueChangeFinished] only ever fire from a real user drag or tap. Unlike
 * the old SeekBar's onProgressChanged(fromUser), assigning [value] here programmatically (syncing
 * to hardware- or storage-read state, restoring a saved profile) never triggers either one, since
 * Slider's plain (value, onValueChange) overload only calls onValueChange from its own internal
 * gesture handling -- never merely because the value parameter it was given changed. Callers that
 * used to need an explicit `if (fromUser)` guard don't need one any more.
 *
 * [onValueRendered], in contrast, fires for a change from *either* source -- it exists purely for
 * keeping a plain-View label in sync with whatever [value] currently is, which is safe to do
 * redundantly and needs to stay correct even when [value] is set from outside a drag.
 */
class ComposeSlider(
    context: Context,
    initialValue: Float,
    initialValueRange: ClosedFloatingPointRange<Float>,
    initialSteps: Int = 0,
    private val onValueChange: (Float) -> Unit = {},
    private val onValueChangeFinished: (Float) -> Unit = {},
    private val onValueRendered: (Float) -> Unit = {}
) {
    private val valueState = mutableFloatStateOf(initialValue)
    private val rangeState = mutableStateOf(initialValueRange)
    private val stepsState = mutableStateOf(initialSteps)
    private val enabledState = mutableStateOf(true)

    var value: Float
        get() = valueState.floatValue
        set(newValue) {
            val range = rangeState.value
            valueState.floatValue = newValue.coerceIn(range.start, range.endInclusive)
        }

    /** Mutable so a slider whose range depends on live-read hardware state (e.g. display density,
     *  spanning a spread either side of the panel's own value) can widen or narrow it later. */
    var valueRange: ClosedFloatingPointRange<Float>
        get() = rangeState.value
        set(newRange) { rangeState.value = newRange }

    /** Positive for a small, fixed set of stops where M3's per-step tick marks are the right
     *  affordance (a handful of fan levels); 0 for a continuous slider spanning a wide range,
     *  where a tick per step would just be visual noise. */
    var steps: Int
        get() = stepsState.value
        set(newSteps) { stepsState.value = newSteps }

    var isEnabled: Boolean
        get() = enabledState.value
        set(newValue) { enabledState.value = newValue }

    val view: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val m3 = M3(context)
            val range = rangeState.value
            val current = valueState.floatValue.coerceIn(range.start, range.endInclusive)
            val scheme = m3.composeColorScheme()
            LaunchedEffect(current) { onValueRendered(current) }
            MaterialTheme(colorScheme = scheme) {
                Slider(
                    value = current,
                    onValueChange = { newValue ->
                        valueState.floatValue = newValue
                        onValueChange(newValue)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledState.value,
                    valueRange = range,
                    steps = stepsState.value,
                    onValueChangeFinished = { onValueChangeFinished(valueState.floatValue) },
                    colors = SliderDefaults.colors().copy(
                        thumbColor = Color(m3.primary),
                        activeTrackColor = Color(m3.primary),
                        activeTickColor = Color(m3.onPrimary),
                        inactiveTrackColor = Color(m3.secondaryContainer),
                        inactiveTickColor = Color(m3.onSecondaryContainer)
                    )
                )
            }
        }
    }
}
