package com.elitedarkkaiser.redmagic

internal object TempFormat {
    fun formatDisplayTempFromF(tempF: Float?, useFahrenheit: Boolean): String {
        if (tempF == null) return "--"
        return if (useFahrenheit) {
            "${tempF.toInt()}°F"
        } else {
            val c = ((tempF - 32f) * 5f / 9f)
            "${c.toInt()}°C"
        }
    }
}
