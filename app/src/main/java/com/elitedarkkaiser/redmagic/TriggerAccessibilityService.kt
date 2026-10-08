package com.elitedarkkaiser.redmagic

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.elitedarkkaiser.redmagic.gametrigger.GameTrigger
import com.elitedarkkaiser.redmagic.gametrigger.TriggerForeground
import com.elitedarkkaiser.redmagic.gametrigger.TriggerRuntimeService

class TriggerAccessibilityService : AccessibilityService() {
    private fun windowPackage(event: AccessibilityEvent?): String? {
        val apps = runCatching {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.mapNotNull { window ->
                window.root?.let { node ->
                    try {
                        node.packageName?.toString()?.let { TriggerForeground.Companion.AppWindow(it,
                            window.isFocused, window.isActive, window.layer) }
                    } finally { @Suppress("DEPRECATION") node.recycle() }
                }
            }
        }.getOrDefault(emptyList())
        return TriggerForeground.fromWindows(apps,
            event?.takeIf { it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }?.packageName?.toString())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = windowPackage(event) ?: return
        if (getSavedGamePackagesStorage(this).contains(pkg)) {
            startService(Intent(this, GameModeService::class.java).putExtra("foreground_pkg", pkg))
        }
        TriggerRuntimeService.accessibilityHint(this, pkg)
    }

    // Accessibility can be rebound independently; root owns the native mapping lifecycle.
    override fun onInterrupt() = Unit
    override fun onServiceConnected() {
        super.onServiceConnected()
        TriggerRuntimeService.sync(this)
    }

    private fun prefs() = getSharedPreferences("triggers", Context.MODE_PRIVATE)

    private fun getAction(key: String): String {
        return prefs().getString(key, "NONE") ?: "NONE"
    }

    private fun runRoot(cmd: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        } catch (_: Throwable) {
        }
    }

    private fun performAction(action: String) {
        when (action) {
            "VOL_UP" -> runRoot("input keyevent 24")
            "VOL_DOWN" -> runRoot("input keyevent 25")
            "MEDIA_PLAY_PAUSE" -> runRoot("input keyevent 85")
            "MEDIA_NEXT" -> runRoot("input keyevent 87")
            "MEDIA_PREVIOUS" -> runRoot("input keyevent 88")
            "NONE" -> Unit
            else -> Unit
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (GameTrigger.ownsTriggers() || !readTriggerPrefsSnapshot(this).triggerEnabled) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_F7 -> {
                performAction(getAction("left_trigger"))
                true
            }

            KeyEvent.KEYCODE_F8 -> {
                performAction(getAction("right_trigger"))
                true
            }

            else -> false
        }
    }
}
