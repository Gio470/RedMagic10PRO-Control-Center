package com.elitedarkkaiser.redmagic.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Software tab's icon: `</>` on a monitor.
 *
 * Drawn here rather than taken from material-icons-extended, which has no monitor-with-code glyph
 * — Code is the brackets alone, Terminal is a `>_` prompt, Monitor is an empty screen. Combining
 * them is the whole point of the mark, so it is authored as one vector.
 *
 * Strokes rather than fills, so it reads as line art at the 24dp the bar draws it at, and tints
 * with the rest of the bar (Icon applies its tint over stroke and fill alike).
 */
val SoftwareCodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SoftwareCode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        fun stroke(width: Float, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block
            )
        }

        // The screen.
        stroke(1.9f) {
            moveTo(5f, 3f)
            lineTo(19f, 3f)
            quadTo(21f, 3f, 21f, 5f)
            lineTo(21f, 15f)
            quadTo(21f, 17f, 19f, 17f)
            lineTo(5f, 17f)
            quadTo(3f, 17f, 3f, 15f)
            lineTo(3f, 5f)
            quadTo(3f, 3f, 5f, 3f)
            close()
        }

        // The stand: a neck down from the screen, and the foot it rests on.
        stroke(1.9f) {
            moveTo(12f, 17f)
            lineTo(12f, 20f)
        }
        stroke(1.9f) {
            moveTo(8f, 20.5f)
            lineTo(16f, 20.5f)
        }

        // `</>`, thinner than the frame so it stays legible inside it.
        stroke(1.7f) {
            moveTo(9.4f, 7.6f)
            lineTo(6.6f, 10f)
            lineTo(9.4f, 12.4f)
        }
        stroke(1.7f) {
            moveTo(14.6f, 7.6f)
            lineTo(17.4f, 10f)
            lineTo(14.6f, 12.4f)
        }
        stroke(1.7f) {
            moveTo(13.1f, 7.2f)
            lineTo(10.9f, 12.8f)
        }
    }.build()
}
