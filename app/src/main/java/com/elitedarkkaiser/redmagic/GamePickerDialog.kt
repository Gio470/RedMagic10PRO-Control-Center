package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog

fun showGamePickerDialogUI(
    context: Context,
    onSave: (Set<String>) -> Unit
) {
    val activity = context as? Activity ?: return
    val pm = context.packageManager
    val m3 = M3(context)

    val apps = pm.getInstalledApplications(0)
        .filter { app ->
            app.packageName != context.packageName &&
            app.enabled &&
            (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
            app.loadLabel(pm).toString().trim().isNotEmpty()
        }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }

    val selected = getSavedGamePackagesStorage(context)

    val listView = ListView(context).apply {
        divider = null
        dividerHeight = 0
        clipToPadding = false
        isVerticalScrollBarEnabled = false
        M3Dialog.scrollsInsideSheet(this)
        background = null
    }

    val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = apps[position]
            val isSelected = selected.contains(app.packageName)

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // An M3 two-line list item.
                setPadding(m3.dp(M3.Space.sm), m3.dp(M3.Space.xs), m3.dp(M3.Space.sm), m3.dp(M3.Space.xs))
                minimumHeight = m3.dp(M3.Metrics.listTwoLine)
                background = M3Dialog.rowBackground(context, isSelected)
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val check = CheckBox(context).apply {
                isChecked = isSelected
                isClickable = false
                isFocusable = false
                m3.tintCompoundButton(this)
            }

            val icon = ImageView(context).apply {
                val size = m3.dp(40)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = m3.dp(8)
                }
                setImageDrawable(app.loadIcon(pm))
            }

            val textWrap = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = m3.dp(M3.Metrics.listInset)
                }
            }

            // The row tints itself when selected, so its text has to follow that container's
            // "on" color rather than staying a fixed onSurface.
            val labelColor = if (isSelected) m3.onSecondaryContainer else m3.onSurface
            val pkgColor = if (isSelected) m3.onSecondaryContainer else m3.onSurfaceVariant

            textWrap.addView(TextView(context).apply {
                text = app.loadLabel(pm)
                m3.styleText(this, M3.Type.bodyLarge, labelColor)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            textWrap.addView(TextView(context).apply {
                text = app.packageName
                m3.styleText(this, M3.Type.bodyMedium, pkgColor)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            row.addView(check)
            row.addView(icon)
            row.addView(textWrap)

            row.setOnClickListener {
                if (selected.contains(app.packageName)) {
                    selected.remove(app.packageName)
                } else {
                    selected.add(app.packageName)
                }
                notifyDataSetChanged()
            }

            return row
        }
    }

    listView.adapter = adapter

    val listHolder = M3Dialog.inset(context).apply {
        addView(
            listView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(400))
        )
    }

    val panel = M3Dialog.panel(context).apply {
        addView(M3Dialog.title(context, "Choose Games / Apps"))
        addView(M3Dialog.body(context, "Pick which launchable apps should trigger Game Mode."))
        addView(listHolder)
    }

    lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

    val cancelBtn = M3Dialog.textButton(context, "Cancel") { dialog.dismiss() }
    val saveBtn = M3Dialog.filledButton(context, "Save") {
        setSavedGamePackagesStorage(context, selected)
        onSave(selected)
        Toast.makeText(context, "Saved ${selected.size} apps", Toast.LENGTH_SHORT).show()
        dialog.dismiss()
    }

    panel.addView(M3Dialog.buttonRow(context, cancelBtn, saveBtn))

    dialog = M3Dialog.show(activity, panel)
}
