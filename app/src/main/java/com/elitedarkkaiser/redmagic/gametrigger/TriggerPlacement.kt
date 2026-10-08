package com.elitedarkkaiser.redmagic.gametrigger

import kotlin.math.roundToInt

object TriggerPlacement {
    fun mapping(display: TriggerDisplayState, orientation: NativeTgkOrientation,
        leftX: Float, leftY: Float, rightX: Float, rightY: Float, radius: Int): NativeTgkOrientationMapping {
        require(display.isValid() && display.orientation == orientation) { "The display is not in the selected orientation" }
        require(radius > 0) { "Invalid target radius" }
        fun rect(x: Float, y: Float): NativeTgkRect {
            val cx = x.roundToInt(); val cy = y.roundToInt()
            val l = (cx - radius).coerceIn(0, display.width - 2)
            val t = (cy - radius).coerceIn(0, display.height - 2)
            return NativeTgkRect(l, t, (cx + radius).coerceIn(l + 1, display.width - 1),
                (cy + radius).coerceIn(t + 1, display.height - 1), display.width, display.height)
        }
        return NativeTgkOrientationMapping(rect(leftX, leftY), rect(rightX, rightY))
    }
}
