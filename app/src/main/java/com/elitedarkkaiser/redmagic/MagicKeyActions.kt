package com.elitedarkkaiser.redmagic

object MagicKeyActions {
    fun readModeLabel(): String {
        val raw = RootShell.execForOutput("settings get system fourth_physical_key_function_value")?.trim().orEmpty()
        return when (raw) {
            "1" -> "Camera"
            "2" -> "GameSpace"
            "3" -> "Sound Mode"
            "4" -> "Flashlight"
            "5" -> "Voice Recorder"
            "16" -> "Launch App"
            "0" -> "Disabled"
            else -> "Unknown"
        }
    }
    fun resolveAppLabel(context: android.content.Context, pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Choose App"
        return try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg
        }
    }

    fun applyStockMode(
        activity: android.app.Activity,
        label: String,
        applyMode: () -> Boolean,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button? = null,
        refreshStatus: () -> Unit
    ) {
        val ok = applyMode()
        if (ok) {
            saveMagicKeyAppPackageStorage(activity, null)
            sliderButton?.text = "MAGIC KEY APP: Choose App"
            statusLabel.text = "Current: $label"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key set to $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to set Magic Key to $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun applyLaunchAppMode(
        activity: android.app.Activity,
        pkg: String,
        label: String,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button,
        refreshStatus: () -> Unit
    ) {
        val ok = HardwareController.setSliderLaunchApp(pkg)
        if (ok) {
            saveMagicKeyAppPackageStorage(activity, pkg)
            sliderButton.text = "MAGIC KEY APP: $label"
            statusLabel.text = "Current: Launch App"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key set to launch $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to set Magic Key app",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun disableMode(
        activity: android.app.Activity,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button? = null,
        refreshStatus: () -> Unit
    ) {
        val ok = HardwareController.disableSliderSystemHandling()
        if (ok) {
            saveMagicKeyAppPackageStorage(activity, null)
            sliderButton?.text = "MAGIC KEY APP: Choose App"
            statusLabel.text = "Current: Disabled"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key disabled",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to disable Magic Key",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

}
