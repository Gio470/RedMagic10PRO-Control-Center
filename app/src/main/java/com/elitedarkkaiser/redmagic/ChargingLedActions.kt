package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent

object ChargingLedActions {
    fun saveProfileAndApplyIfCharging(
        context: Context,
        enabledKey: String,
        effectKey: String,
        colorKey: String,
        enabled: Boolean,
        effect: String,
        color: Int
    ) {
        ChargingLedState.saveProfile(
            context,
            enabledKey,
            effectKey,
            colorKey,
            enabled,
            effect,
            color
        )

        HardwareServiceActions.startChargingMode(context)
        if (ChargingLedState.isEnabled(context) && ChargingLedState.isChargingNow(context)) {
            ChargingLedState.setActive(context, true)
            ChargingLedState.applyChargingProfile(context)
        }
    }
    internal fun showLogoDialog(
        activity: MainActivity,
        deps: ChargingLedProfileDialog.Deps
    ) {
        val profile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.LOGO_ENABLED_KEY,
            ChargingLedState.LOGO_EFFECT_KEY,
            ChargingLedState.LOGO_COLOR_KEY,
            defaultEnabled = false,
            defaultEffect = "steady",
            defaultColor = 1
        )

        ChargingLedProfileDialog.show(
            activity = activity,
            title = "Charging Logo LED",
            subtitle = "Used only while charging.",
            originalEnabled = profile.enabled,
            originalEffect = profile.effect,
            originalColor = profile.color,
            onSave = { enabled, effect, color ->
                saveProfileAndApplyIfCharging(
                    activity,
                    ChargingLedState.LOGO_ENABLED_KEY,
                    ChargingLedState.LOGO_EFFECT_KEY,
                    ChargingLedState.LOGO_COLOR_KEY,
                    enabled,
                    effect,
                    color
                )
            },
            deps = deps
        )
    }

    internal fun showShoulderDialog(
        activity: MainActivity,
        deps: ChargingLedProfileDialog.Deps
    ) {
        val profile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.SHOULDER_ENABLED_KEY,
            ChargingLedState.SHOULDER_EFFECT_KEY,
            ChargingLedState.SHOULDER_COLOR_KEY,
            defaultEnabled = false,
            defaultEffect = "breathe",
            defaultColor = 8
        )

        ChargingLedProfileDialog.show(
            activity = activity,
            title = "Charging Shoulder LEDs",
            subtitle = "Used only while charging.",
            originalEnabled = profile.enabled,
            originalEffect = profile.effect,
            originalColor = profile.color,
            onSave = { enabled, effect, color ->
                saveProfileAndApplyIfCharging(
                    activity,
                    ChargingLedState.SHOULDER_ENABLED_KEY,
                    ChargingLedState.SHOULDER_EFFECT_KEY,
                    ChargingLedState.SHOULDER_COLOR_KEY,
                    enabled,
                    effect,
                    color
                )
            },
            deps = deps
        )
    }

    internal fun showFanDialog(
        activity: MainActivity,
        colorDot: (Int, String, () -> Unit) -> android.view.View,
        colorDotDrawable: (String, Boolean) -> android.graphics.drawable.Drawable,
        fanPresetBubble: (String, String, String, String, String, Boolean, () -> Unit) -> android.view.View
    ) {
        val chargingFanProfile = ChargingLedState.readProfile(
            activity,
            ChargingLedState.FAN_ENABLED_KEY,
            ChargingLedState.FAN_EFFECT_KEY,
            ChargingLedState.FAN_COLOR_KEY,
            defaultEnabled = false,
            defaultEffect = "steady",
            defaultColor = 5
        )
        var chargingFanEnabled = chargingFanProfile.enabled
        var chargingFanEffect = chargingFanProfile.effect
        var chargingFanColor = chargingFanProfile.color
        var chargingFanDialogRefresh: (() -> Unit)? = null

        if (chargingFanEffect.startsWith("preset:")) {
            chargingFanColor = -1
        }

        FanLedDialogUi.showFanLedDialog(
            activity = activity,
            originalEnabled = chargingFanEnabled,
            originalEffect = chargingFanEffect,
            originalColor = chargingFanColor,
            currentEnabled = { chargingFanEnabled },
            currentEffect = { chargingFanEffect },
            currentColor = { chargingFanColor },
            setEnabled = { value -> chargingFanEnabled = value },
            setEffect = { value -> chargingFanEffect = value },
            setColor = { value -> chargingFanColor = value },
            applyPreviewIfEnabled = {
                if (ChargingLedState.isEnabled(activity) && ChargingLedState.isChargingNow(activity)) {
                    saveProfileAndApplyIfCharging(
                        activity,
                        ChargingLedState.FAN_ENABLED_KEY,
                        ChargingLedState.FAN_EFFECT_KEY,
                        ChargingLedState.FAN_COLOR_KEY,
                        chargingFanEnabled,
                        chargingFanEffect,
                        chargingFanColor
                    )
                }
            },
            applySelection = { effect, color ->
                if (effect.startsWith("preset:")) {
                    HardwareController.setFanLedStockPreset(effect.removePrefix("preset:"))
                } else {
                    HardwareController.setFanLedEffect(effect, color)
                }
            },
            disableLed = { HardwareController.setFanLedEnabled(false) },
            saveState = {
                saveProfileAndApplyIfCharging(
                    activity,
                    ChargingLedState.FAN_ENABLED_KEY,
                    ChargingLedState.FAN_EFFECT_KEY,
                    ChargingLedState.FAN_COLOR_KEY,
                    chargingFanEnabled,
                    chargingFanEffect,
                    chargingFanColor
                )
            },
            startFanLedService = {
                HardwareServiceActions.startChargingMode(activity)
            },
            stopFanLedService = {
                HardwareServiceActions.startChargingMode(activity)
            },
            anyLedEnabled = { ChargingLedState.isEnabled(activity) },
            setDialogRefresh = { callback -> chargingFanDialogRefresh = callback },
            deps = FanLedDialogUi.Deps(
                colorDot = colorDot,
                colorDotDrawable = colorDotDrawable
            ),
            cycleSupported = false,
            title = "Charging Fan LED",
            subtitle = "Used only while charging.",
            enableLabel = "Enable for charging mode"
        )
    }

}
