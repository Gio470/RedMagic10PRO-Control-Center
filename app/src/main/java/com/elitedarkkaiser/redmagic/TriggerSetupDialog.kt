package com.elitedarkkaiser.redmagic

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.ValueButtonGroup

internal object TriggerSetupDialog {

    fun show(activity: MainActivity) {
        val prefs = activity.getSharedPreferences("triggers", Context.MODE_PRIVATE)
        val m3 = M3(activity)

        val labels = arrayOf(
            "None",
            "Volume Up",
            "Volume Down",
            "Play / Pause",
            "Next Track",
            "Previous Track"
        )
        val values = arrayOf(
            "NONE",
            "VOL_UP",
            "VOL_DOWN",
            "MEDIA_PLAY_PAUSE",
            "MEDIA_NEXT",
            "MEDIA_PREVIOUS"
        )

        fun indexOfValue(value: String): Int {
            val i = values.indexOf(value)
            return if (i >= 0) i else 0
        }

        var leftChoice = indexOfValue(prefs.getString("left_trigger", "VOL_DOWN") ?: "VOL_DOWN")
        var rightChoice = indexOfValue(prefs.getString("right_trigger", "VOL_UP") ?: "VOL_UP")
        var unlockTapCount = prefs.getInt("intent_unlock_tap_count", 2).coerceIn(2, 4)
        var leftUnlockTapCount = prefs.getInt("left_intent_unlock_tap_count", 1).coerceIn(1, 4)

        /** One M3 radio list of trigger actions. */
        fun actionGroup(checkedIndex: Int, onPicked: (Int) -> Unit): RadioGroup =
            RadioGroup(activity).apply {
                orientation = RadioGroup.VERTICAL
                labels.forEachIndexed { index, label ->
                    addView(M3Dialog.radioButton(activity, label, index == checkedIndex).apply {
                        id = View.generateViewId()
                        tag = index
                    })
                }
                setOnCheckedChangeListener { group, checkedId ->
                    val selected = group.findViewById<android.widget.RadioButton>(checkedId)
                    (selected?.tag as? Int)?.let(onPicked)
                }
            }

        /**
         * Tap counts as a connected button group rather than radios — four horizontal radio
         * buttons ran off the edge of a narrow dialog, and a small set of mutually exclusive
         * values is exactly what M3 Expressive's single-select group is for.
         */
        fun tapCountRow(
            counts: List<Int>,
            initial: Int,
            onPicked: (Int) -> Unit
        ): View = ValueButtonGroup(
            activity,
            counts.map { (if (it == 1) "Single" else "$it taps") to it },
            initial,
            onSelect = onPicked
        ).view

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, "Trigger Mapping"))
            addView(M3Dialog.body(activity, "Set left and right shoulder triggers independently."))

            addView(m3.insetGroup("Left trigger (F7)").apply {
                addView(actionGroup(leftChoice) { leftChoice = it })
            })
            addView(m3.insetGroup("Right trigger (F8)").apply {
                addView(actionGroup(rightChoice) { rightChoice = it })
            })
            addView(m3.insetGroup("Left / top Intent Unlock taps").apply {
                addView(tapCountRow(listOf(1, 2, 3, 4), leftUnlockTapCount) { leftUnlockTapCount = it })
            })
            addView(m3.insetGroup("Right / bottom Intent Unlock taps").apply {
                addView(tapCountRow(listOf(2, 3, 4), unlockTapCount) { unlockTapCount = it })
            })
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() }
        val saveBtn = M3Dialog.filledButton(activity, "Save") {
            prefs.edit()
                .putString("left_trigger", values[leftChoice])
                .putString("right_trigger", values[rightChoice])
                .putInt("intent_unlock_tap_count", unlockTapCount)
                .putInt("left_intent_unlock_tap_count", leftUnlockTapCount)
                .apply()

            Toast.makeText(
                activity,
                "Saved: Left = ${labels[leftChoice]}, Right = ${labels[rightChoice]}",
                Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
        }

        panel.addView(M3Dialog.buttonRow(activity, cancelBtn, saveBtn))

        dialog = M3Dialog.show(activity, M3Dialog.scroll(activity, panel))
    }
}
