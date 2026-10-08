package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.elitedarkkaiser.redmagic.systemtheme.SystemRoles

/** A sample app, recolored from the same roles the system overlay publishes. */
internal class SystemThemePreview(context: Context) : LinearLayout(context) {
    private val m3 = M3(context)
    private val shell = GradientDrawable().apply { cornerRadius = m3.dp(24).toFloat() }
    private val heading = label("RedMagic Control", M3.Type.titleMedium)
    private val caption = label("Your control center", M3.Type.bodySmall)
    private val mode = label("", M3.Type.labelSmall)
    private val tileSurfaces = mutableListOf<GradientDrawable>()
    private val tileTitles = mutableListOf<TextView>()
    private val tileDetails = mutableListOf<TextView>()
    private val tileIcons = mutableListOf<TextView>()
    private val iconSurfaces = mutableListOf<GradientDrawable>()
    private val primaryAction = label("Customize", M3.Type.labelLarge)
    private val secondaryAction = label("Settings", M3.Type.labelLarge)
    private val primaryShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val secondaryShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val nav = LinearLayout(context)
    private val navShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val navLabels = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        background = shell
        setPadding(m3.dp(16), m3.dp(14), m3.dp(16), m3.dp(14))
        // The mode selector outside this sample is interactive; these are illustrative controls.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(heading)
            addView(caption)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mode.setPadding(m3.dp(8), m3.dp(4), m3.dp(8), m3.dp(4))
        header.addView(mode)
        addView(header)

        val tiles = LinearLayout(context)
        listOf(Triple("Cooling", "Fan controls", Icons.FAN),
            Triple("Triggers", "Game layouts", Icons.TRIGGERS)).forEachIndexed { index, (title, detail, glyph) ->
            val fill = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
            val iconFill = GradientDrawable().apply { cornerRadius = m3.dp(12).toFloat() }
            tileSurfaces += fill
            iconSurfaces += iconFill
            tiles.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                background = fill
                setPadding(m3.dp(12), m3.dp(12), m3.dp(12), m3.dp(12))
                addView(TextView(context).apply {
                    text = glyph
                    m3.styleGlyph(this, 17f, m3.primary)
                    gravity = Gravity.CENTER
                    background = iconFill
                    tileIcons += this
                }, LayoutParams(m3.dp(32), m3.dp(32)))
                addView(label(title, M3.Type.titleSmall).also { tileTitles += it },
                    LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = m3.dp(8) })
                addView(label(detail, M3.Type.bodySmall).also { tileDetails += it })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = m3.dp(10)
            })
        }
        addView(tiles, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = m3.dp(12) })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            listOf(primaryAction to primaryShape, secondaryAction to secondaryShape).forEachIndexed { index, (text, fill) ->
                text.gravity = Gravity.CENTER
                text.background = fill
                addView(text, LayoutParams(0, m3.dp(40), 1f).apply {
                    if (index > 0) marginStart = m3.dp(8)
                })
            }
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(12) })

        nav.background = navShape
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.setPadding(m3.dp(4), m3.dp(4), m3.dp(4), m3.dp(4))
        listOf("Home", "Hardware", "Software", "Settings").forEach { name ->
            nav.addView(label(name, M3.Type.labelSmall).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                navLabels += this
            }, LayoutParams(0, m3.dp(32), 1f))
        }
        addView(nav, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(12) })
        hideSampleChildren(this)
    }

    private fun label(value: String, style: M3.TypeStyle) = TextView(context).apply {
        text = value
        m3.styleText(this, style, m3.onSurface)
    }

    private fun hideSampleChildren(parent: ViewGroup) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (child is ViewGroup) hideSampleChildren(child)
        }
    }

    fun render(ramps: Array<IntArray>, dark: Boolean, tintText: Boolean) {
        fun role(name: String): Int {
            val entry = SystemRoles.ROLES.first { it.name == name }
            return ramps[entry.palette][if (dark) entry.darkTone else entry.lightTone]
        }
        val surface = role("background")
        val onSurface = if (tintText) role("on_surface") else if (dark) Color.WHITE else Color.BLACK
        val secondaryText = if (tintText) role("on_surface_variant") else
            ColorUtils.blendARGB(surface, onSurface, 0.72f)
        val primary = role("primary")
        shell.setColor(surface)
        shell.setStroke(m3.dp(1), role("outline_variant"))
        heading.setTextColor(onSurface)
        caption.setTextColor(secondaryText)
        mode.text = if (dark) "Dark" else "Light"
        mode.setTextColor(primary)
        tileSurfaces.forEach { it.setColor(role("surface_variant")) }
        tileTitles.forEach { it.setTextColor(onSurface) }
        tileDetails.forEach { it.setTextColor(secondaryText) }
        tileIcons.forEach { it.setTextColor(role("on_primary_container")) }
        iconSurfaces.forEach { it.setColor(role("primary_container")) }
        primaryShape.setColor(primary)
        primaryAction.setTextColor(role("on_primary"))
        secondaryShape.setColor(role("secondary_container"))
        secondaryAction.setTextColor(role("on_secondary_container"))
        navShape.setColor(role("surface_variant"))
        navLabels.forEachIndexed { index, text ->
            text.setTextColor(if (index == 0) role("on_primary_container") else secondaryText)
            text.background = if (index == 0) GradientDrawable().apply {
                cornerRadius = m3.dp(16).toFloat()
                setColor(role("primary_container"))
            } else null
        }
        contentDescription = "${if (dark) "Dark" else "Light"} app preview. " +
            "Sample cooling and trigger cards, buttons, and navigation using the selected system palette."
    }
}
