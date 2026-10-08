package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkStorage
import java.util.Locale

internal data class GamePickerEntry(val packageName: String, val label: String, val game: Boolean, val added: Boolean)
internal enum class GamePickerFilter(val label: String) { GAMES("Games"), ALL("All apps"), ADDED("Added") }

internal object GamePickerUi {
    internal fun filter(entries: List<GamePickerEntry>, filter: GamePickerFilter, query: String): List<GamePickerEntry> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return entries.filter {
            (filter == GamePickerFilter.ALL || (filter == GamePickerFilter.GAMES && it.game) ||
                (filter == GamePickerFilter.ADDED && it.added)) &&
                (needle.isEmpty() || it.label.lowercase(Locale.ROOT).contains(needle) ||
                    it.packageName.lowercase(Locale.ROOT).contains(needle))
        }
    }

    fun show(activity: Activity, onPicked: (String, String) -> Unit) =
        showApps(activity, onPicked = onPicked)

    fun showApps(activity: Activity, title: String = "Pick a game",
        supporting: String = "Add controls for a game, or customize an app you've already added.",
        initialFilter: GamePickerFilter = GamePickerFilter.GAMES,
        addedPackages: Set<String>? = null,
        onPicked: (String, String) -> Unit) {
        val m3 = M3(activity)
        lateinit var dialog: Panel
        var entries = emptyList<GamePickerEntry>()
        var shown = emptyList<GamePickerEntry>()
        var selection = initialFilter
        var query = ""
        var loading = true
        val filters = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val count = label(activity, "Loading installed apps…", M3.Type.bodySmall, m3.onSurfaceVariant).apply {
            setPadding(m3.dp(4), m3.dp(12), m3.dp(4), m3.dp(8))
        }
        val empty = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(m3.dp(24), m3.dp(24), m3.dp(24), m3.dp(24))
        }
        val emptyTitle = label(activity, "Finding your games", M3.Type.titleMedium, m3.onSurface)
        val emptyBody = label(activity, "Reading installed apps…", M3.Type.bodyMedium, m3.onSurfaceVariant)
            .apply { gravity = Gravity.CENTER; setPadding(0, m3.dp(8), 0, 0) }
        empty.addView(emptyTitle); empty.addView(emptyBody)
        val icons = LruCache<String, Drawable>(48)
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? LinearLayout ?: LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(m3.dp(16), m3.dp(12), m3.dp(12), m3.dp(12))
                    minimumHeight = m3.dp(80)
                    background = m3.cardShape(m3.surfaceContainerHigh, 22)
                    addView(ImageView(activity).apply { contentDescription = null },
                        LinearLayout.LayoutParams(m3.dp(44), m3.dp(44)).apply { marginEnd = m3.dp(12) })
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(activity, "", M3.Type.titleSmall, m3.onSurface).apply {
                            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                        })
                        addView(label(activity, "", M3.Type.bodySmall, m3.onSurfaceVariant).apply {
                            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, m3.dp(4), 0, 0)
                        })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(label(activity, "", M3.Type.labelMedium, m3.primary).apply {
                        gravity = Gravity.CENTER
                        setPadding(m3.dp(10), m3.dp(8), m3.dp(10), m3.dp(8))
                        background = m3.surfaceShape(m3.primaryContainer, 16)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { marginStart = m3.dp(8) })
                }
                val entry = shown[position]
                val icon = icons.get(entry.packageName) ?: runCatching {
                    activity.packageManager.getApplicationIcon(entry.packageName)
                }.getOrElse { activity.getDrawable(android.R.drawable.sym_def_app_icon)!! }
                    .also { icons.put(entry.packageName, it) }
                (row.getChildAt(0) as ImageView).setImageDrawable(icon)
                (row.getChildAt(1) as LinearLayout).apply {
                    (getChildAt(0) as TextView).text = entry.label
                    (getChildAt(1) as TextView).text = entry.packageName
                }
                (row.getChildAt(2) as TextView).apply {
                    text = if (entry.added) "Added" else "+"
                    setTextColor(m3.onPrimaryContainer)
                }
                row.contentDescription = entry.label + if (entry.added) ", already added. Open settings." else ", add game."
                return row
            }
        }
        val list = ListView(activity).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            dividerHeight = m3.dp(8)
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            this.adapter = adapter
            emptyView = empty
            clipToPadding = false
            M3Dialog.scrollsUnderBar(this)
            setOnItemClickListener { _, _, position, _ ->
                shown.getOrNull(position)?.let { entry ->
                    dialog.dismiss()
                    onPicked(entry.packageName, entry.label)
                }
            }
        }
        lateinit var rebuild: () -> Unit
        rebuild = {
            shown = filter(entries, selection, query)
            adapter.notifyDataSetChanged()
            count.text = if (loading) "Loading installed apps…" else
                "${shown.size} ${if (selection == GamePickerFilter.GAMES) "games" else "apps"} · ${entries.count { it.added }} added"
            emptyTitle.text = when {
                loading -> "Finding your games"
                query.isNotBlank() -> "No matching apps"
                selection == GamePickerFilter.ADDED -> "No games added yet"
                selection == GamePickerFilter.GAMES -> "No games detected"
                else -> "No launchable apps found"
            }
            emptyBody.text = when {
                loading -> "Reading installed apps…"
                query.isNotBlank() -> "Try another name or package."
                selection == GamePickerFilter.ALL -> "Install a game, then reopen this picker."
                else -> "Choose All apps to find a game or add any other app."
            }
            filters.removeAllViews()
            GamePickerFilter.entries.forEachIndexed { index, value ->
                filters.addView(m3.button(value.label,
                    if (selection == value) M3.ButtonKind.Filled else M3.ButtonKind.Tonal, onClick = {
                        selection = value; rebuild()
                    }).apply { isEnabled = !loading }, LinearLayout.LayoutParams(0, m3.dp(48), 1f).apply {
                        if (index > 0) marginStart = m3.dp(6)
                    })
            }
        }
        val search = M3Dialog.searchField(activity, "Search name or package").apply {
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(value: Editable?) { query = value?.toString().orEmpty(); rebuild() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }
        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, title, supporting, Icons.APP_LIST))
            addView(search)
            addView(filters)
            addView(count)
            addView(FrameLayout(activity).apply {
                addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        rebuild()
        dialog = M3Dialog.show(activity, panel)
        val app = activity.applicationContext
        Thread({
            val apps = runCatching { installed(app, addedPackages) }.getOrDefault(emptyList())
            activity.runOnUiThread {
                if (!activity.isDestroyed && dialog.isShowing) {
                    entries = apps
                    selection = if (initialFilter == GamePickerFilter.GAMES && apps.none { it.game }) GamePickerFilter.ALL else initialFilter
                    loading = false
                    rebuild()
                }
            }
        }, "trigger-game-picker").start()
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context, addedPackages: Set<String>?): List<GamePickerEntry> {
        val pm = context.packageManager
        val added = addedPackages ?: NativeTgkStorage.readProfiles(context).map { it.packageName }.toSet()
        return pm.getInstalledApplications(0).asSequence()
            .filter { it.packageName != context.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { GamePickerEntry(it.packageName,
                runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName),
                it.category == ApplicationInfo.CATEGORY_GAME || it.flags and ApplicationInfo.FLAG_IS_GAME != 0,
                it.packageName in added) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }.toList()
    }

    private fun label(context: Context, value: String, style: M3.TypeStyle, color: Int) = TextView(context).apply {
        text = value; M3(context).styleText(this, style, color)
    }
}

