package com.elitedarkkaiser.redmagic.ui.components

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.AppTheme
import com.elitedarkkaiser.redmagic.ui.M3

/** Shared widgets, styled on the Material 3 Expressive scale (see ui/M3.kt). */
object UiKit {
    fun titleText(context: Context, text: String): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.headlineSmall, m3.onSurface)
        }
    }

    fun subtitleText(context: Context, text: String): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        }
    }

    fun bodyText(context: Context, text: String): TextView {
        val m3 = M3(context)
        return TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        }
    }

    /** M3 filled card. */
    fun sectionPanel(context: Context): LinearLayout {
        val m3 = M3(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md), m3.dp(M3.Space.md))
            background = m3.cardShape(m3.surfaceContainerHighest, M3.Metrics.groupCorner)
        }
    }

    /** M3 filled tonal button, medium size. */
    fun actionButton(context: Context, text: String, isDanger: Boolean = false): Button =
        M3(context).button(
            text,
            if (isDanger) M3.ButtonKind.Danger else M3.ButtonKind.Tonal,
            M3.ButtonSize.Medium
        )

    @Suppress("unused")
    private val themeRef = AppTheme
}

