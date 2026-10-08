package com.elitedarkkaiser.redmagic

data class GameModeProfile(
        val fanEnabled: Boolean,
        val fanLevel: Int,
        val pumpEnabled: Boolean,
        val pumpProfile: String,
        val fanLedEnabled: Boolean,
        val fanLedEffect: String,
        val fanLedColor: Int,
        val logoLedEnabled: Boolean,
        val logoLedEffect: String,
        val logoLedColor: Int,
        val shoulderLedEnabled: Boolean,
        val shoulderLedEffect: String,
        val shoulderLedColor: Int
    )
