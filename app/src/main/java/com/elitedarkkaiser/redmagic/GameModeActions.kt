package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.widget.Toast

internal object GameModeActions {

    fun startServiceSilentlyIfPermitted(context: Context) {
        // Several services restart this one after they finish with the LEDs. The master switch on
        // Lighting has to hold across all of them, or switching Game Mode off would last only until
        // the next call or charge ended.
        if (!MasterSwitches.gameModeEnabled(context)) return
        if (PermissionActions.hasUsageStatsPermission(context)) {
            context.startService(Intent(context, GameModeService::class.java))
        }
    }

    fun startServiceIfPermitted(context: Context) {
        if (!PermissionActions.hasUsageStatsPermission(context)) {
            Toast.makeText(
                context,
                "Grant Usage Access to enable Game Mode",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        context.startService(Intent(context, GameModeService::class.java))
    }

    fun buildProfile(
        fanEnabled: Boolean,
        fanLevel: Int,
        pumpEnabled: Boolean,
        pumpProfile: String,
        fanLedEnabled: Boolean,
        fanLedEffect: String,
        fanLedColor: Int,
        logoLedEnabled: Boolean,
        logoLedEffect: String,
        logoLedColor: Int,
        shoulderLedEnabled: Boolean,
        shoulderLedEffect: String,
        shoulderLedColor: Int
    ): GameModeProfile {
        return GameModeProfile(
            fanEnabled = fanEnabled,
            fanLevel = fanLevel,
            pumpEnabled = pumpEnabled,
            pumpProfile = pumpProfile,
            fanLedEnabled = fanLedEnabled,
            fanLedEffect = fanLedEffect,
            fanLedColor = fanLedColor,
            logoLedEnabled = logoLedEnabled,
            logoLedEffect = logoLedEffect,
            logoLedColor = logoLedColor,
            shoulderLedEnabled = shoulderLedEnabled,
            shoulderLedEffect = shoulderLedEffect,
            shoulderLedColor = shoulderLedColor
        )
    }

    fun saveProfile(
        context: Context,
        profile: GameModeProfile,
        persistProfile: (GameModeProfile) -> Unit,
        onSaved: () -> Unit = {}
    ) {
        persistProfile(profile)
        Toast.makeText(context, "Game profile saved", Toast.LENGTH_SHORT).show()
        onSaved()
    }


    fun updatePumpProfile(
        value: String,
        onProfileChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onProfileChanged(value)
        refreshButtons()
    }


    fun updateLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onEffectChanged(value)
        refreshButtons()
    }

    fun updateLedColor(
        id: Int,
        currentEffect: String,
        onColorChanged: (Int) -> Unit,
        onEffectChanged: (String) -> Unit,
        refreshColorDots: () -> Unit,
        refreshEffectButtons: () -> Unit
    ) {
        onColorChanged(id)
        if (currentEffect.startsWith("preset:")) {
            onEffectChanged("steady")
        }
        refreshColorDots()
        refreshEffectButtons()
    }

    fun applyLedPreset(
        value: String,
        onEffectChanged: (String) -> Unit,
        onColorChanged: (Int) -> Unit,
        refreshEffectButtons: () -> Unit,
        refreshColorDots: () -> Unit,
        refreshPresetBubbles: () -> Unit
    ) {
        onEffectChanged("preset:$value")
        onColorChanged(-1)
        refreshEffectButtons()
        refreshColorDots()
        refreshPresetBubbles()
    }

    fun updateLogoLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit
    ) {
        onEffectChanged(value)
    }

    fun updateLogoLedColor(
        id: Int,
        onColorChanged: (Int) -> Unit,
        refreshColorDots: () -> Unit
    ) {
        onColorChanged(id)
        refreshColorDots()
    }


    fun updateShoulderLedEffect(
        value: String,
        onEffectChanged: (String) -> Unit,
        refreshButtons: () -> Unit
    ) {
        onEffectChanged(value)
        refreshButtons()
    }

    fun updateShoulderLedColor(
        id: Int,
        onColorChanged: (Int) -> Unit,
        refreshColorDots: () -> Unit
    ) {
        onColorChanged(id)
        refreshColorDots()
    }
    fun applyProfileNow(
        profile: GameModeProfile,
        applyFanLed: (String, Int) -> Unit
    ) {
        if (profile.fanEnabled) {
            HardwareController.setFanLevel(profile.fanLevel)
        } else {
            HardwareController.enableFan(false)
        }

        if (profile.pumpEnabled) {
            HardwareController.setPumpProfile(profile.pumpProfile)
        } else {
            HardwareController.enablePump(false)
        }

        if (profile.fanLedEnabled) {
            applyFanLed(profile.fanLedEffect, profile.fanLedColor)
        } else {
            HardwareController.setFanLedEnabled(false)
        }

        if (profile.logoLedEnabled) {
            HardwareController.setLogoLedEffect(profile.logoLedEffect, profile.logoLedColor)
        } else {
            HardwareController.setLogoLedEnabled(false)
        }

        if (profile.shoulderLedEnabled) {
            HardwareController.setShoulderLedEffect(profile.shoulderLedEffect, profile.shoulderLedColor)
        } else {
            HardwareController.setShoulderLedEnabled(false)
        }
    }

}
