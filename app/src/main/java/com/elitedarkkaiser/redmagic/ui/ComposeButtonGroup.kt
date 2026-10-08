package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive's connected button group, after androidx's
 * SingleSelectConnectedButtonGroupWithFlowLayoutSample, hosted in a ComposeView so this app's
 * hand-drawn screens can drop one in where a row of hand-styled filter chips used to sit.
 *
 * It replaces the chips wholesale rather than restyling them: a chip row was a set of separate
 * pills that each had to be re-styled by hand whenever the selection moved (see the old
 * M3.styleFilterChip calls dotted through every dialog), while this is one view that owns the
 * selection. Callers set [selectedIndex] and the group redraws itself.
 *
 * Connected means the buttons share edges -- the first and last are rounded on their outer sides
 * only, everything between is square -- and the checked one swells into a rounded shape of its own,
 * which is what carries the selection now that there is no separate filled/outlined chip look.
 *
 * Colours come from [M3], so the group follows the same Material You palette as the rest of the app
 * and is read fresh on each composition (a Pure Black toggle rebuilds the hosting tab from
 * scratch).
 */
class ComposeButtonGroup(
    context: Context,
    options: List<String>,
    selectedIndex: Int = 0,
    /**
     * How many buttons a row may hold before the group wraps. Left at the option count by default,
     * so a group is a single connected row unless a caller says otherwise; a long set of labels on
     * a narrow screen reads better split across two.
     */
    maxItemsInEachRow: Int = options.size,
    private val onSelect: (Int) -> Unit = {}
) {
    private val optionsState = mutableStateOf(options)
    private val selectedState = mutableIntStateOf(selectedIndex)
    private val enabledState = mutableStateOf(true)
    private val rowLimitState = mutableStateOf(maxItemsInEachRow)

    /**
     * The selection, as an index into [options]. Assigning it only moves the highlight -- [onSelect]
     * fires for a tap and nothing else, so syncing the group to state read back from hardware or
     * storage can't loop back into the caller that is doing the syncing.
     */
    var selectedIndex: Int
        get() = selectedState.intValue
        set(value) { selectedState.intValue = value }

    /** For a group whose labels depend on live state (a profile list that grows, say). */
    var options: List<String>
        get() = optionsState.value
        set(value) {
            optionsState.value = value
            if (rowLimitState.value > value.size) rowLimitState.value = value.size
        }

    var isEnabled: Boolean
        get() = enabledState.value
        set(value) { enabledState.value = value }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
    val view: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val m3 = M3(context)
            val scheme = m3.composeColorScheme()
            val labels = optionsState.value
            val selected = selectedState.intValue
            MaterialTheme(colorScheme = scheme) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalArrangement =
                        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    maxItemsInEachRow = rowLimitState.value.coerceAtLeast(1)
                ) {
                    labels.forEachIndexed { index, label ->
                        ToggleButton(
                            checked = selected == index,
                            onCheckedChange = {
                                selectedState.intValue = index
                                onSelect(index)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            enabled = enabledState.value,
                            // Shapes are picked from the position in the whole group, not in the
                            // wrapped row, so a group that wraps still reads as one run of buttons.
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                labels.lastIndex ->
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.toggleButtonColors().copy(
                                containerColor = Color(m3.surfaceContainerHighest),
                                contentColor = Color(m3.onSurface),
                                checkedContainerColor = Color(m3.primary),
                                checkedContentColor = Color(m3.onPrimary)
                            ),
                            // An unchecked button's fill sits only one tonal step off the block it
                            // lives in, which read as barely-there -- a border gives it an edge
                            // against that background regardless of how close the two tones land.
                            // It stays on the checked button too, but is invisible there: the
                            // primary fill already meets or beats this contrast on its own.
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(m3.outlineVariant)
                            ),
                            // Tighter than the default so four or five labels still fit a phone
                            // width without every one of them ellipsizing.
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A [ComposeButtonGroup] whose selection is a value rather than an index — the shape nearly every
 * group in this app actually wants, since what a group picks is a profile or an effect name
 * ("steady", "quick") and never the position it happens to sit at.
 *
 * Built from label-to-value pairs, so the two lists can't drift apart the way the old parallel
 * chip/`styleFilterChip(chip, it == selected)` pairs could.
 */
class ValueButtonGroup<T>(
    context: Context,
    private val entries: List<Pair<String, T>>,
    selected: T,
    maxItemsInEachRow: Int = entries.size,
    onSelect: (T) -> Unit = {}
) {
    private val group = ComposeButtonGroup(
        context,
        options = entries.map { it.first },
        selectedIndex = entries.indexOfFirst { it.second == selected }.coerceAtLeast(0),
        maxItemsInEachRow = maxItemsInEachRow,
        onSelect = { index -> onSelect(entries[index].second) }
    )

    val view: View get() = group.view

    /** Assigning it moves the highlight only; a value the group doesn't hold leaves it as it is. */
    var value: T
        get() = entries[group.selectedIndex].second
        set(newValue) {
            val index = entries.indexOfFirst { it.second == newValue }
            if (index >= 0) group.selectedIndex = index
        }

    var isEnabled: Boolean
        get() = group.isEnabled
        set(newValue) { group.isEnabled = newValue }
}

/**
 * A single standalone action button carrying the same M3 Expressive ToggleButton shape and filled
 * pill colours as a selected button in [ComposeButtonGroup] -- for one-off actions (Recheck
 * compatibility, Disable home mono icons) that sit outside any selection group but should still
 * read as part of the same button family rather than the plain flat pill they used before.
 *
 * Modelled as an always-checked [ToggleButton] rather than a plain [androidx.compose.material3.Button]:
 * that is what gets the identical rounded-pill checked shape and colours for free, with the tap
 * itself carrying the action instead of a real check state. There is nothing to un-check, so
 * [onCheckedChange] ignores the boolean it's given and just calls [onClick].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ComposeActionButton(
    context: Context,
    label: String,
    private val onClick: () -> Unit
) {
    private val labelState = mutableStateOf(label)
    private val enabledState = mutableStateOf(true)

    var label: String
        get() = labelState.value
        set(value) { labelState.value = value }

    var isEnabled: Boolean
        get() = enabledState.value
        set(value) { enabledState.value = value }

    val view: View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val m3 = M3(context)
            val scheme = m3.composeColorScheme()
            MaterialTheme(colorScheme = scheme) {
                ToggleButton(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabledState.value,
                    shapes = ToggleButtonDefaults.shapes(),
                    colors = ToggleButtonDefaults.toggleButtonColors().copy(
                        checkedContainerColor = Color(m3.secondaryContainer),
                        checkedContentColor = Color(m3.onSecondaryContainer)
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(labelState.value, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
