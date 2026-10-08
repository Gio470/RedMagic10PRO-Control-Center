package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.PerfTweaks
import com.elitedarkkaiser.redmagic.PerfTweaksPrefs
import com.google.android.material.materialswitch.MaterialSwitch

/** Cache Cleaner and GMS Optimizer, opened from the Software tab. */
object PerfTweaksUi {

    fun show(activity: Activity) {
        val m3 = M3(activity)
        val context: Context = activity

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(M3Dialog.header(
                context,
                "Performance mods",
                supporting = "Cache cleaning and Google Play Services controls.",
                glyph = Icons.PERF
            ))

            addView(m3.groupHeader("Storage"))
            addView(actionRow(
                context, m3,
                title = "Cache Cleaner",
                supporting = "Trims every partition and clears app caches now.",
                glyph = Icons.CACHE_CLEAN,
                buttonLabel = "Clean",
                action = { PerfTweaks.cleanCache() },
                doneMessage = "Cache cleared",
                failedMessage = "Couldn't clean the cache"
            ))
            addView(m3.groupHeader("Google Play Services"))
            addView(warningBody(
                context, m3,
                "GMS Optimizer restricts Google Play Services in the background: notifications, " +
                    "account sync, Find My Device and similar features are likely to be affected " +
                    "while it is on. Switching it off reverses the restrictions it can reverse."
            ))
            addView(rootSwitchRow(
                context, m3,
                title = "GMS Optimizer",
                supporting = "Denies background access, networking and wake locks to Google " +
                    "Play Services and puts it into hibernation.",
                glyph = Icons.GMS,
                read = { PerfTweaksPrefs.getBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER) },
                write = { on ->
                    PerfTweaksPrefs.setBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER, on)
                    PerfTweaks.setGmsOptimizer(context, on)
                },
                refusedMessage = "Couldn't change the GMS Optimizer"
            ))
        }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.scroll(activity, content), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        m3.connectRows(content)
        m3.tintWidgets(content)
        M3Dialog.show(activity, panel)
    }

    /** A plain paragraph, for the warning ahead of GMS Optimizer's own row. */
    private fun warningBody(context: Context, m3: M3, text: String): TextView =
        TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.error)
            // On the list inset, like the subheader above it and the row below it.
            setPadding(m3.dp(M3.Metrics.listInset), 0, m3.dp(M3.Metrics.listInset), m3.dp(M3.Space.xs))
        }

    /**
     * A row with a switch bound to root shell reads/writes, off the main thread.
     *
     * Not [SoftwareTabUi]'s own `rootSwitchRow`: that one re-reads the state after every write to
     * confirm it landed, which fits a toggle backed by the package manager or a settings key --
     * something with a truth of its own to read back. These toggles are backed by this app's own
     * preference (the state a `setprop` or an `appops` call leaves behind is not something to
     * parse back out reliably), so [write] is trusted once it returns, and only failure to run at
     * all is reported.
     */
    private fun rootSwitchRow(
        context: Context,
        m3: M3,
        title: String,
        supporting: String,
        glyph: String,
        read: () -> Boolean,
        write: (Boolean) -> Unit,
        refusedMessage: String
    ): LinearLayout {
        val switch = MaterialSwitch(context)
        val row = m3.row(
            title = title,
            supporting = supporting,
            glyph = glyph,
            trailing = switch
        )

        lateinit var bind: (Boolean) -> Unit
        bind = { checked ->
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = checked
            switch.setOnCheckedChangeListener { _, wanted ->
                switch.isEnabled = false
                Thread {
                    val ok = runCatching { write(wanted) }.isSuccess
                    row.post {
                        switch.isEnabled = true
                        if (!ok) {
                            bind(!wanted)
                            Toast.makeText(context, refusedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }
        bind(read())
        return row
    }

    /** A row with a button that runs a one-shot action off the main thread. */
    private fun actionRow(
        context: Context,
        m3: M3,
        title: String,
        supporting: String,
        glyph: String,
        buttonLabel: String,
        action: () -> Boolean,
        doneMessage: String,
        failedMessage: String
    ): LinearLayout {
        val button = M3Dialog.tonalButton(context, buttonLabel) { }
        val row = m3.row(title = title, supporting = supporting, glyph = glyph, trailing = button)
        button.setOnClickListener {
            // Shown the instant the tap registers, before the root shell is even touched --
            // whether this appears is the one signal that tells apart a click that never reaches
            // this listener from one that does and then goes quiet, which "nothing happens" could
            // otherwise be either of. Remove once the report distinguishes them.
            Toast.makeText(context, "$title: starting…", Toast.LENGTH_SHORT).show()
            button.isEnabled = false
            Thread {
                val ok = runCatching { action() }.getOrDefault(false)
                row.post {
                    button.isEnabled = true
                    Toast.makeText(
                        context, if (ok) doneMessage else failedMessage, Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }
        return row
    }
}

