package com.elitedarkkaiser.redmagic.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.OtaUpdates
import com.google.android.material.materialswitch.MaterialSwitch
import com.elitedarkkaiser.redmagic.xposed.GameAssistSettings
import com.elitedarkkaiser.redmagic.xposed.ModuleStatus
import com.elitedarkkaiser.redmagic.xposed.WindowModSettings
import com.elitedarkkaiser.redmagic.xposed.SoftwareControlsSettings

/**
 * Software: everything this app changes about other software rather than about the hardware.
 *
 * The module status first, because it decides whether any of the rest does anything; then
 * RedMagicOS's floating windows, the global icon pack, GameAssist and GameSpace, the status bar
 * and update switches.
 *
 * This was two tabs. Splitting them by "is it an Xposed hook?" split them by implementation, which
 * is not a thing anyone browsing the app is looking for -- the icon pack and the window fixes are
 * both hooks, and both are just tweaks to other software. One tab, in the order the things depend
 * on each other.
 *
 * The cards themselves still live in [XposedTabUi], which is now a library of them rather than a
 * screen of its own.
 */
object SoftwareTabUi {
    private const val BLOCK_UPDATES = "Block system updates"

    data class Result(val view: LinearLayout)

    fun create(deps: XposedTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)

        val onStatus = mutableListOf<(Map<String, String>?) -> Unit>()

        val fixes = XposedTabUi.fixesCard(context, m3)
        val limit = XposedTabUi.limitCard(context, m3, onStatus)

        // The master switch hides the rest rather than disabling it in place: an off feature has
        // no settings worth reading, and the hooks stand down on the same flag, so what is on
        // screen and what the ROM is doing stay in step.
        fun applyWindowMaster(on: Boolean) {
            val visibility = if (on) View.VISIBLE else View.GONE
            fixes.visibility = visibility
            limit.visibility = visibility
        }

        // What only works because a hook is in place. Each of the three master switches opens as
        // a page of its own from a card, as Iconify's home cards do; these holders are what those
        // pages show, kept for the tab's lifetime so the status pass below can still reach them.
        val windowsPage = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(XposedTabUi.masterCard(context, m3) { on ->
                WindowModSettings.setEnabled(context, on)
                applyWindowMaster(on)
                XposedTabUi.push(context)
            })
            addView(fixes)
            addView(limit)
        }
        val iconPackPage = XposedTabUi.iconPackCard(context, m3, deps.activity())
        val gameAssistPage = XposedTabUi.gameAssistCard(context, m3)
        val volumePage = SoftwareControlsUi.volumePage(deps.activity(), m3)
        val launcherPage = SoftwareControlsUi.launcherPage(deps.activity(), m3)
        val modulePages = listOf(windowsPage, iconPackPage, gameAssistPage)
        val softwareControlsPages = listOf(volumePage, launcherPage)
        (modulePages + softwareControlsPages).forEach {
            m3.connectRows(it)
            m3.tintWidgets(it)
        }
        // Found now, while they are still on their pages: SectionPages.item below moves each one
        // onto its card, out of the trees the module gate further down disables.
        val moduleMasters = modulePages.mapNotNull { M3.MasterSwitch.find(it) }
        val softwareControlsMasters = softwareControlsPages.mapNotNull { M3.MasterSwitch.find(it) }
        applyWindowMaster(WindowModSettings.getEnabled(context))

        // Not a hook -- root and the package manager do this one -- but it belongs with the rest
        // of what this app changes about other software. Its own page, from its own card, and the
        // read that decides whether this phone even has an updater to block is a root call, so the
        // card is dropped from the grid below once that comes back empty rather than before.
        lateinit var grid: LinearLayout
        val items = mutableListOf<IconifyGrid.Item>()
        val blockUpdatesRow = rootSwitchRow(
            context, m3,
            title = "Block system updates",
            supporting = "Turns off ZTE's updater. An update would take root with it.",
            glyph = Icons.SHIELD,
            available = { OtaUpdates.present(context).isNotEmpty() },
            read = { OtaUpdates.blocked(context) },
            write = { on -> OtaUpdates.setBlocked(context, on) },
            refusedMessage = "Couldn't change the updater",
            onAvailability = { usable ->
                // Either way the grid is laid out again: without an updater to block the card goes,
                // and with one the card was built before this read came back and is still showing
                // the "off" its switch had before it knew any better.
                if (!usable) items.removeAll { it.title == BLOCK_UPDATES }
                IconifyGrid.fill(grid, items)
            }
        )
        val blockUpdatesPage = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(blockUpdatesRow)
        }
        m3.connectRows(blockUpdatesPage)
        m3.tintWidgets(blockUpdatesPage)

        // Everything this tab opens, as Iconify's home cards: the four root tools, then the three
        // hook-backed master switches, each of which opens as its own page.
        val activity = deps.activity()
        items += listOf(
            // Ported from Mahmud0808/ColorBlendr: root, recolours the whole phone.
            IconifyGrid.Item("System theme", "Recolour the whole phone's Material You palette",
                Icons.COLOR_PALETTE, SystemThemeUi.master(activity)) { SystemThemeUi.show(activity) },
            // Root cache cleaning and Google Play Services controls.
            IconifyGrid.Item("Performance mods", "Cache Cleaner and GMS Optimizer",
                Icons.PERF) { PerfTweaksUi.show(activity) },
            SectionPages.item(activity, SectionPages.Entry(
                "Floating windows", "Fixes and a window limit for small windows",
                Icons.WINDOW, windowsPage
            )),
            SectionPages.item(activity, SectionPages.Entry(
                "Icon pack", "Replace app icons everywhere with a pack",
                Icons.ICON_PACK_MASTER, iconPackPage
            )),
            SectionPages.item(activity, SectionPages.Entry(
                "GameAssist", "Tweaks for RedMagic's game space",
                Icons.GAME_ASSIST, gameAssistPage
            )),
            SectionPages.item(activity, SectionPages.Entry(
                "Volume Step Control", "Media-volume levels per button press",
                Icons.SOUND_MODE, volumePage
            )),
            SectionPages.item(activity, SectionPages.Entry(
                "Launcher recents", "Hide a third-party launcher's HOME card",
                Icons.HOME, launcherPage
            )),
            SectionPages.item(activity, SectionPages.Entry(
                BLOCK_UPDATES, "Keep ZTE's updater from taking root with it",
                Icons.SHIELD, blockUpdatesPage
            ))
        )
        grid = IconifyGrid.build(context, items)
        container.addView(grid)

        // One root shell for the whole tab, off the main thread. The same pass writes both settings
        // files out, so they exist for the next boot even if the user never touches a control.
        Thread {
            // The gate first: until this lands the controls are left alone rather than flashed
            // disabled and back, and it shares the tab's one root shell pass.
            val module = ModuleStatus.read()
            container.post {
                // Greyed and untappable, with no explanation of its own: Home's module card is
                // where that is said, and repeating it here put the same paragraph on two screens.
                modulePages.forEach { view ->
                    setTreeEnabled(view, module.healthy)
                    view.alpha = if (module.healthy) 1f else M3.Metrics.disabledAlpha
                }
                // The masters live on the cards now; a disabled one is what stops the card's
                // mirror from writing through it (see SectionPages.item).
                moduleMasters.forEach { setTreeEnabled(it, module.healthy) }
                // These hooks do not depend on the ROM's floating-window hook signatures.
                softwareControlsPages.forEach { view ->
                    setTreeEnabled(view, module.loaded)
                    view.alpha = if (module.loaded) 1f else M3.Metrics.disabledAlpha
                }
                softwareControlsMasters.forEach { setTreeEnabled(it, module.loaded) }
            }

            WindowModSettings.pushToDevice(context)
            GameAssistSettings.pushToDevice(context)
            SoftwareControlsSettings.pushVolume(context)
            SoftwareControlsSettings.pushLauncher(context)
            // Tidies up after the policy_control build. No-op once the key is gone.
            com.elitedarkkaiser.redmagic.StatusBarHide.clearLegacySetting()
            val status = WindowModSettings.readModuleStatus()
            container.post { onStatus.forEach { it(status) } }
        }.start()

        m3.connectRows(container)

        m3.tintWidgets(container)

        return Result(view = container)
    }

    /**
     * Switches every control under [view] on or off, and stops taps reaching them.
     *
     * Disabling the container alone is not enough: a disabled ViewGroup still hands touches to its
     * children, so the switches inside would keep working while looking as though they could not.
     */
    private fun setTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            // Blocks taps on rows whose whole surface is the click target, which is most of them.
            view.isClickable = view.isClickable && enabled
            for (i in 0 until view.childCount) setTreeEnabled(view.getChildAt(i), enabled)
        }
    }

    /**
     * A switch whose state lives on the phone rather than in this app's preferences.
     *
     * Both switches on this tab work the same way: read what the phone actually has, write over
     * root, then read back, because `pm` and `settings` can both refuse. Neither reads a stored
     * preference, so a switch still tells the truth after something else -- the De-Bloat list, a
     * factory reset, another tool -- has changed the same thing behind its back.
     *
     * [read] and [write] both block and are only ever called off the main thread.
     */
    private fun rootSwitchRow(
        context: android.content.Context,
        m3: M3,
        title: String,
        supporting: String,
        glyph: String,
        /** Pass one in to keep a handle on it when the line changes after a read. */
        supportingView: TextView? = null,
        /** False hides the row: a switch that cannot work is worse than no switch. */
        available: () -> Boolean = { true },
        read: () -> Boolean,
        write: (Boolean) -> Unit,
        refusedMessage: String,
        /** Told what [available] answered, on the main thread, once it does. */
        onAvailability: (Boolean) -> Unit = {}
    ): LinearLayout {
        // The main thread by way of the looper, not row.post: a row that lives on a page is not
        // attached to a window until that page is opened, and View.post holds anything queued
        // before then until it is -- which for the availability read below would be never, since
        // what it decides is whether the card that opens the page exists at all.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        // This row is the whole of its feature, so its switch is that feature's master -- which is
        // what puts a copy of it on the card that opens this page. See [M3.MasterSwitch].
        val switch = M3.MasterSwitch.mark(MaterialSwitch(context)) as MaterialSwitch
        val row = m3.row(
            title = title,
            supporting = supporting,
            supportingView = supportingView,
            glyph = glyph,
            trailing = switch
        )

        lateinit var bind: (Boolean) -> Unit

        // Assigning isChecked fires the change listener exactly as a finger does, so the listener
        // comes off first and goes back on after. Everything that moves this switch from code goes
        // through here.
        bind = { checked ->
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = checked
            switch.setOnCheckedChangeListener { _, wanted ->
                com.elitedarkkaiser.redmagic.FreshInstallDefaults.keepUserChoice(context, "block_updates")
                switch.isEnabled = false
                Thread {
                    write(wanted)
                    val landed = read()
                    handler.post {
                        switch.isEnabled = true
                        if (landed != wanted) {
                            bind(landed)
                            Toast.makeText(context, refusedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }

        // Off the main thread: root shells and binder calls, during the tab's own layout pass.
        Thread {
            val usable = available()
            val state = if (usable) read() else false
            handler.post {
                if (!usable) row.visibility = View.GONE else bind(state)
                onAvailability(usable)
            }
        }.start()

        return row
    }
}

