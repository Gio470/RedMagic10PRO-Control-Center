package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import com.elitedarkkaiser.redmagic.ui.M3Dialog

internal object ProfileDialogs {

    fun showDeleteProfileDialog(
        context: Context,
        profileName: String,
        onConfirmDelete: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Delete Profile")
            .setMessage("Delete $profileName?")
            .setPositiveButton("Delete") { _, _ ->
                onConfirmDelete()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** "Save Master Profile" — name-only prompt. */
    fun showStyledNameOnlyDialog(
        activity: Activity,
        title: String,
        hint: String,
        onSave: (String) -> Unit
    ) {
        showNamePrompt(activity, title, hint) { name -> onSave(name) }
    }

    /** "Save Profile" — same prompt, but it builds and persists the hardware profile itself. */
    fun showStyledSaveProfileDialog(
        activity: Activity,
        buildProfile: (String) -> HardwareProfile,
        onSaved: () -> Unit
    ) {
        showNamePrompt(activity, "Save Profile", "Profile name") { name ->
            ProfileActions.saveProfile(activity, name, buildProfile(name)) {
                onSaved()
            }
        }
    }

    /**
     * The shared M3 name prompt behind both save dialogs. Returns without saving on a blank name,
     * matching the old behaviour of simply ignoring the tap.
     */
    private fun showNamePrompt(
        activity: Activity,
        title: String,
        hint: String,
        onConfirm: (String) -> Unit
    ) {
        val input = M3Dialog.textField(activity, hint)

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, title))
            addView(input)
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() }
        val saveBtn = M3Dialog.filledButton(activity, "Save") {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) return@filledButton
            onConfirm(name)
            dialog.dismiss()
        }

        panel.addView(M3Dialog.buttonRow(activity, cancelBtn, saveBtn))

        dialog = M3Dialog.show(activity, panel)
    }
}
