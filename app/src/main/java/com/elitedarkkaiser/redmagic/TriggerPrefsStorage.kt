package com.elitedarkkaiser.redmagic

import android.content.Context

private const val TRIGGER_PREFS_NAME = "triggers"
private const val LEFT_TRIGGER_KEY = "left_trigger"
private const val RIGHT_TRIGGER_KEY = "right_trigger"
private const val HAPTICS_ENABLED_KEY = "haptics_enabled"
private const val INTENT_UNLOCK_RIGHT_TRIGGER_KEY = "intent_unlock_right_trigger"
private const val TRIGGERS_AUTO_START_KEY = "triggers_auto_start"

data class TriggerPrefsSnapshot(
    val triggerEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val leftTriggerAction: String,
    val rightTriggerAction: String,
    val intentUnlockRightTrigger: Boolean,
    val triggersAutoStart: Boolean
)

fun readTriggerPrefsSnapshot(context: Context): TriggerPrefsSnapshot {
    val prefs = context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
    val autoStart = prefs.getBoolean(TRIGGERS_AUTO_START_KEY, false)
    return TriggerPrefsSnapshot(
        triggerEnabled = autoStart,
        hapticsEnabled = prefs.getBoolean(HAPTICS_ENABLED_KEY, false),
        leftTriggerAction = prefs.getString(LEFT_TRIGGER_KEY, "NONE") ?: "NONE",
        rightTriggerAction = prefs.getString(RIGHT_TRIGGER_KEY, "NONE") ?: "NONE",
        intentUnlockRightTrigger = prefs.getBoolean(INTENT_UNLOCK_RIGHT_TRIGGER_KEY, false),
        triggersAutoStart = autoStart
    )
}

fun saveTriggerPrefsStorage(context: Context, profile: HardwareProfile) {
    context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(LEFT_TRIGGER_KEY, profile.leftTriggerAction)
        .putString(RIGHT_TRIGGER_KEY, profile.rightTriggerAction)
        .putBoolean(HAPTICS_ENABLED_KEY, profile.hapticsEnabled)
        .putBoolean(INTENT_UNLOCK_RIGHT_TRIGGER_KEY, profile.intentUnlockRightTrigger)
        .putBoolean(TRIGGERS_AUTO_START_KEY, profile.triggersAutoStart)
        .apply()
}

fun initDefaultTriggerMappingsStorage(context: Context) {
    val prefs = context.getSharedPreferences(TRIGGER_PREFS_NAME, Context.MODE_PRIVATE)
    if (!prefs.contains(LEFT_TRIGGER_KEY)) {
        prefs.edit()
            .putString(LEFT_TRIGGER_KEY, "VOL_DOWN")
            .putString(RIGHT_TRIGGER_KEY, "VOL_UP")
            .apply()
    }
}
