package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.BuildConfig
import com.elitedarkkaiser.redmagic.gametrigger.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** A game library, separate per-game controls, and focused setup/diagnostics. */
object GameTriggerUi {
    /** The same hardware card as Display density, opening the library directly. */
    fun card(activity: Activity) = IconifyGrid.Item(
        title = "Game trigger mapping",
        subtitle = "L/R targets, layouts and controls for each game",
        glyph = Icons.TRIGGERS,
        master = IconifyCard.Master(
            read = { NativeTgkStorage.enabled(activity) },
            write = { NativeTgkStorage.setEnabled(activity, it) }
        ),
        onClick = { show(activity) }
    )

    fun show(activity: Activity) {
        TriggerRuntimeService.sync(activity)
        val m3 = M3(activity)
        val content = column(activity)
        lateinit var rebuild: () -> Unit
        rebuild = {
            content.removeAllViews()
            val profiles = NativeTgkStorage.readProfiles(activity)
            val master = NativeTgkStorage.enabled(activity)
            content.addView(m3.row("Automatic mapping", "Apply saved controls when your enabled games open.",
                glyph = Icons.TRIGGERS, trailing = MaterialSwitch(activity).apply {
                    isChecked = master
                    setOnCheckedChangeListener { _, on ->
                        NativeTgkStorage.setEnabled(activity, on); rebuild()
                    }
                }))
            val summary = m3.block().apply {
                tag = null
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    listOf("Games" to profiles.size,
                        "Configured" to profiles.count { it.hasAnyCompleteMapping() },
                        "Layouts" to profiles.sumOf { it.layouts.size }).forEach { (label, count) ->
                        addView(column(activity).apply {
                            addView(text(activity, count.toString(), M3.Type.headlineSmall, m3.primary))
                            addView(text(activity, label, M3.Type.labelMedium, m3.onSurfaceVariant))
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                })
            }
            content.addView(summary)
            content.addView(m3.groupHeader("Your games"))
            content.addView(m3.button("Add a game", M3.ButtonKind.Filled, onClick = {
                pickGame(activity) { pkg, label ->
                    val existing = NativeTgkStorage.getProfile(activity, pkg)
                    if (existing != null || NativeTgkStorage.saveProfile(activity, NativeTgkProfile(pkg, label))) {
                        rebuild()
                        showGame(activity, pkg, rebuild)
                    } else toast(activity, "Couldn't add this game")
                }
            }), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(52)))
            if (profiles.isEmpty()) content.addView(m3.block().apply {
                tag = null
                addView(text(activity, "Your controls, per game", M3.Type.titleMedium, m3.onSurface))
                addView(text(activity, "Add a game, choose an orientation, then drag L and R onto its controls. " +
                    "The floating editor keeps layouts and trigger behaviors close at hand.",
                    M3.Type.bodyMedium, m3.onSurfaceVariant))
            })
            profiles.forEach { profile ->
                content.addView(gameCard(activity, profile, master,
                    customize = { showGame(activity, profile.packageName, rebuild) },
                    edit = { chooseOrientation(activity, profile) }))
            }
            content.addView(m3.row("Mapping status", "Copy the latest mapping report.",
                glyph = Icons.CONFIGURE, onClick = { copyMappingStatus(activity) }))
            m3.connectRows(content)
            m3.tintWidgets(content)
        }
        rebuild()
        val handle = showPage(activity, "Game trigger mapping",
            "Build a control layout for each game, then switch it on when you're ready.", content)
        watch(activity, handle, rebuild)
        TriggerSetup.ensure(activity) { if (!activity.isDestroyed && handle.isShowing) rebuild() }
        rebuild()
    }

    private fun gameCard(activity: Activity, profile: NativeTgkProfile, master: Boolean,
        customize: () -> Unit, edit: () -> Unit): View {
        val m3 = M3(activity)
        return m3.block().apply {
            tag = null
            val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(ImageView(activity).apply {
                setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(profile.packageName) }
                    .getOrElse { activity.getDrawable(android.R.drawable.sym_def_app_icon) })
                contentDescription = null
            }, LinearLayout.LayoutParams(m3.dp(44), m3.dp(44)).apply { marginEnd = m3.dp(12) })
            header.addView(column(activity).apply {
                addView(text(activity, profile.appLabel, M3.Type.titleMedium, m3.onSurface))
                addView(text(activity, profile.activeLayout()?.name ?: "Default", M3.Type.bodySmall, m3.onSurfaceVariant))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(chip(activity, when {
                !profile.enabled -> "Paused"
                !profile.hasAnyCompleteMapping() -> "Needs targets"
                !master -> "Mapping off"
                else -> "Ready"
            }, profile.enabled && profile.hasAnyCompleteMapping() && master))
            addView(header)
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, m3.dp(12), 0, m3.dp(8))
                listOf(NativeTgkOrientation.LANDSCAPE, NativeTgkOrientation.PORTRAIT).forEach { orientation ->
                    addView(chip(activity, "${orientation.label()} · " +
                        if (profile.hasCompleteMapping(orientation)) "Set" else "Add",
                        profile.hasCompleteMapping(orientation)),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (orientation == NativeTgkOrientation.PORTRAIT) marginStart = m3.dp(6)
                        })
                }
            })
            addView(LinearLayout(activity).apply {
                addView(m3.button("Customize", M3.ButtonKind.Tonal, onClick = customize),
                    LinearLayout.LayoutParams(0, m3.dp(48), 1f))
                addView(m3.button("Edit targets", M3.ButtonKind.Filled, onClick = edit),
                    LinearLayout.LayoutParams(0, m3.dp(48), 1f).apply { marginStart = m3.dp(8) })
            })
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = m3.dp(12)
        }
    }

    private fun showGame(activity: Activity, packageName: String, onChanged: () -> Unit) {
        val initial = NativeTgkStorage.getProfile(activity, packageName) ?: return
        val m3 = M3(activity)
        val content = column(activity)
        lateinit var rebuild: () -> Unit
        lateinit var handle: Panel
        fun save(change: (NativeTgkProfile) -> NativeTgkProfile) {
            if (!NativeTgkStorage.updateProfile(activity, packageName, change)) toast(activity, "Couldn't save this change")
            GameTrigger.refresh(activity)
            rebuild()
            onChanged()
        }
        rebuild = rebuild@{
            val profile = NativeTgkStorage.getProfile(activity, packageName) ?: return@rebuild
            content.removeAllViews()
            content.addView(m3.row("Enable for this game",
                if (profile.hasAnyCompleteMapping()) "Use the selected layout when this game opens."
                else "Place L/R targets to finish this game's controls.",
                glyph = Icons.TRIGGERS, trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.enabled
                    setOnCheckedChangeListener { _, on -> save { it.copy(enabled = on) } }
                }))
            if (profile.enabled && !NativeTgkStorage.enabled(activity)) {
                content.addView(IconifyKit.infoBlock(activity,
                    "Automatic mapping is off. Save & enable in the editor switches it on.").first)
            }
            content.addView(m3.groupHeader("Target placement"))
            listOf(NativeTgkOrientation.LANDSCAPE, NativeTgkOrientation.PORTRAIT).forEach { orientation ->
                content.addView(m3.row(orientation.label(),
                    if (profile.hasCompleteMapping(orientation)) "Edit saved L/R targets in the floating editor."
                    else "Place L/R targets over the game's controls.",
                    glyph = Icons.CONFIGURE, onClick = { editTargets(activity, profile, orientation) }))
            }
            GameTriggerControlsUi.add(activity, m3, content, profile, ::save)
            content.addView(m3.groupHeader("Feedback"))
            content.addView(m3.row("Trigger haptics", "Feel the firmware's feedback on each press.",
                glyph = Icons.HAPTICS, trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.hapticsEnabled
                    setOnCheckedChangeListener { _, on -> save { it.copy(hapticsEnabled = on) } }
                }))
            content.addView(m3.row("Saved target markers", "Keep faint L/R markers visible while playing.",
                glyph = Icons.PREVIEW, trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.showSavedTargets
                    setOnCheckedChangeListener { _, on -> save { it.copy(showSavedTargets = on) } }
                }))
            if (profile.showSavedTargets) content.addView(m3.row("Marker opacity",
                "${profile.savedTargetOpacityPercent}%", glyph = Icons.PREVIEW, onClick = {
                    MaterialAlertDialogBuilder(activity).setTitle("Marker opacity")
                        .setItems(arrayOf("5%", "12%", "20%", "30%")) { _, index ->
                            save { it.copy(savedTargetOpacityPercent = listOf(5, 12, 20, 30)[index]) }
                        }.show()
                }))
            content.addView(m3.groupHeader("Manage game"))
            content.addView(m3.row("Remove game", "Delete its layouts and saved targets.",
                glyph = Icons.XMARK, onClick = {
                    MaterialAlertDialogBuilder(activity).setTitle("Remove ${profile.appLabel}?")
                        .setMessage("Its layouts and L/R targets will be deleted.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                            NativeTgkStorage.removeProfile(activity, packageName)
                            GameTrigger.refresh(activity)
                            handle.dismiss()
                        }.show()
                }))
            m3.connectRows(content)
            m3.tintWidgets(content)
        }
        rebuild()
        handle = showPage(activity, initial.appLabel, "Layouts, target placement, and feedback for this game.", content)
        watch(activity, handle, rebuild, onChanged)
    }

    private fun chooseOrientation(activity: Activity, profile: NativeTgkProfile) {
        val m3 = M3(activity)
        val content = column(activity)
        lateinit var handle: Panel
        listOf(NativeTgkOrientation.LANDSCAPE, NativeTgkOrientation.PORTRAIT).forEach { orientation ->
            content.addView(m3.row(orientation.label(),
                if (profile.hasCompleteMapping(orientation)) "Edit saved targets" else "Place new targets",
                glyph = Icons.CONFIGURE, onClick = { handle.dismiss(); editTargets(activity, profile, orientation) }))
        }
        m3.connectRows(content)
        handle = showPage(activity, "Edit targets", profile.appLabel, content)
    }

    private fun editTargets(activity: Activity, profile: NativeTgkProfile, orientation: NativeTgkOrientation) {
        fun open() {
            NativeTgkStorage.getProfile(activity, profile.packageName)?.let {
                TriggerEditorService.open(activity, it, orientation)
            }
        }
        if (TriggerSetup.state(activity).ready) { open(); return }
        toast(activity, "Preparing trigger permissions…")
        TriggerSetup.ensure(activity) { result ->
            if (!activity.isDestroyed) {
                if (result.state.overlay) open()
                else {
                    toast(activity, result.message)
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")))
                }
            }
        }
    }

    private var readingStatus = false
    private fun copyMappingStatus(activity: Activity) {
        if (readingStatus) return
        readingStatus = true
        toast(activity, "Reading mapping status…")
        Thread({
            val result = runCatching { NativeTgkBridge.readState(activity) }.getOrNull()
            activity.runOnUiThread {
                readingStatus = false
                if (!activity.isDestroyed) {
                    val state = TriggerSetup.state(activity)
                    val report = "RedMagic Control ${BuildConfig.VERSION_NAME}\n" +
                        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
                        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nFirmware: ${Build.DISPLAY}\n" +
                        (result?.message ?: "Native support check failed") + "\n" +
                        "Automatic permissions: ${TriggerSetup.lastMessage}; overlay=${state.overlay}; " +
                        "accessibility=${state.accessibility}\n" +
                        NativeTgkAbi.diagnostics + "\n" + GameTrigger.diagnostics(activity)
                    activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText("Game trigger diagnostics", report))
                    toast(activity, "Mapping status copied")
                }
            }
        }, "trigger-diagnostics").start()
    }

    private fun chip(context: Context, label: String, selected: Boolean): TextView {
        val m3 = M3(context)
        return text(context, label, M3.Type.labelMedium, if (selected) m3.onPrimaryContainer else m3.onSurfaceVariant).apply {
            gravity = Gravity.CENTER
            setPadding(m3.dp(8), m3.dp(6), m3.dp(8), m3.dp(6))
            background = m3.surfaceShape(if (selected) m3.primaryContainer else m3.surfaceContainerHighest, 12)
        }
    }
    private fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(context: Context, value: String, style: M3.TypeStyle, color: Int) = TextView(context).apply {
        text = value; M3(context).styleText(this, style, color)
    }
    private fun NativeTgkOrientation.label() = name.lowercase().replaceFirstChar { it.uppercase() }
    private fun toast(context: Context, value: String) = Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
    private fun showPage(activity: Activity, title: String, supporting: String, content: View): Panel =
        M3Dialog.show(activity, M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, title, supporting, Icons.TRIGGERS))
            addView(M3Dialog.scroll(activity, content).apply { M3Dialog.scrollsUnderBar(this) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    private fun watch(activity: Activity, handle: Panel, rebuild: () -> Unit, dismissed: () -> Unit = {}) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(current: Activity) { if (current === activity && handle.isShowing) rebuild() }
            override fun onActivityDestroyed(current: Activity) { if (current === activity) activity.application.unregisterActivityLifecycleCallbacks(this) }
            override fun onActivityCreated(current: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(current: Activity) = Unit
            override fun onActivityPaused(current: Activity) = Unit
            override fun onActivityStopped(current: Activity) = Unit
            override fun onActivitySaveInstanceState(current: Activity, state: android.os.Bundle) = Unit
        }
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        handle.setOnDismissListener { activity.application.unregisterActivityLifecycleCallbacks(callbacks); dismissed() }
    }

    private fun pickGame(activity: Activity, onPicked: (String, String) -> Unit) =
        GamePickerUi.show(activity, onPicked)
}

