package com.elitedarkkaiser.redmagic.ui.backgrounds

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Determine if a color represents a dark background. */
internal fun Color.isDarkBackground(): Boolean = luminance() < 0.5f
