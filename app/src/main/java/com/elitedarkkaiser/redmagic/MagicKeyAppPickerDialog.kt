package com.elitedarkkaiser.redmagic

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog

internal object MagicKeyAppPickerDialog {

    data class MagicKeyAppItem(
        val pkg: String,
        val label: String,
        val launchable: Boolean,
        val icon: Drawable?
    )

    fun show(
        activity: MainActivity,
        targetButton: Button,
        statusLabel: TextView?,
        applyLaunchAppMagicKeyMode: (String, String, TextView, Button) -> Unit
    ) {
        val packageManager = activity.packageManager
        val m3 = M3(activity)

        val allApps = packageManager.getInstalledApplications(0)
            .map { appInfo ->
                val pkg = appInfo.packageName
                val label = try {
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    pkg
                }
                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (_: Throwable) {
                    null
                }
                MagicKeyAppItem(
                    pkg = pkg,
                    label = label,
                    launchable = packageManager.getLaunchIntentForPackage(pkg) != null,
                    icon = icon
                )
            }
            .sortedWith(
                compareBy<MagicKeyAppItem> { it.label.lowercase() }
                    .thenBy { it.pkg.lowercase() }
            )

        if (allApps.isEmpty()) {
            Toast.makeText(activity, "No installed apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val searchInput = M3Dialog.searchField(activity, "Search apps or package names")

        val listView = android.widget.ListView(activity).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            M3Dialog.scrollsInsideSheet(this)
        }

        val filteredApps = ArrayList(allApps)

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = filteredApps.size
            override fun getItem(position: Int): Any = filteredApps[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = filteredApps[position]

                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    // An M3 two-line list item.
                    setPadding(m3.dp(M3.Space.sm), m3.dp(M3.Space.xs), m3.dp(M3.Space.sm), m3.dp(M3.Space.xs))
                    minimumHeight = m3.dp(M3.Metrics.listTwoLine)
                    background = M3Dialog.rowBackground(activity, selected = false)
                }

                val iconView = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(m3.dp(40), m3.dp(40))
                    setImageDrawable(item.icon)
                }

                val textWrap = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(m3.dp(M3.Metrics.listInset), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                textWrap.addView(TextView(activity).apply {
                    text = item.label
                    m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                textWrap.addView(TextView(activity).apply {
                    text = if (item.launchable) item.pkg else "${item.pkg}  •  No launcher activity"
                    m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })

                row.addView(iconView)
                row.addView(textWrap)

                return row
            }
        }

        fun applyFilter(query: String) {
            val q = query.trim().lowercase()
            filteredApps.clear()
            if (q.isEmpty()) {
                filteredApps.addAll(allApps)
            } else {
                filteredApps.addAll(
                    allApps.filter {
                        it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
                    }
                )
            }
            adapter.notifyDataSetChanged()
        }

        listView.adapter = adapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val listHolder = M3Dialog.inset(activity).apply {
            addView(
                listView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(400))
            )
        }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.title(activity, "Choose Magic Key app"))
            addView(M3Dialog.body(activity, "Search by name or package."))
            addView(searchInput)
            addView(M3Dialog.vGap(activity, 12))
            addView(listHolder)
        }

        lateinit var dialog: com.elitedarkkaiser.redmagic.ui.Panel

        val cancelBtn = M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() }
        panel.addView(M3Dialog.buttonRow(activity, cancelBtn))

        dialog = M3Dialog.show(activity, panel)

        listView.setOnItemClickListener { _, _, which, _ ->
            val item = filteredApps[which]
            val status = statusLabel ?: return@setOnItemClickListener

            applyLaunchAppMagicKeyMode(
                item.pkg,
                item.label,
                status,
                targetButton
            )

            if (!item.launchable) {
                Toast.makeText(
                    activity,
                    "${item.label} saved, but it may not open because it has no launcher activity",
                    Toast.LENGTH_LONG
                ).show()
            }

            dialog.dismiss()
        }
    }
}
