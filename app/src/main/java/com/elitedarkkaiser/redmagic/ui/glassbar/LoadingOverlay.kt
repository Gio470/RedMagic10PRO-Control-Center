package com.elitedarkkaiser.redmagic.ui.glassbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

const val LOADING_FADE_OUT_MILLIS = 350

/** Icon-free startup: a themed wordmark, an abstract progress indicator and the real load state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun LoadingOverlay(
    isLoading: () -> Boolean,
    colors: GlassBottomBarColors,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = isGlassBlurSupported(),
    message: () -> String = { "Starting up…" }
) {
    AnimatedVisibility(
        visible = isLoading(),
        modifier = modifier,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(LOADING_FADE_OUT_MILLIS))
    ) {
        Box(
            Modifier.fillMaxSize().background(colors.background)
                .drawBehind {
                    drawCircle(colors.accent.copy(alpha = 0.07f),
                        radius = size.width * 0.65f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 1.1f, size.height * 0.15f))
                    drawCircle(colors.accent.copy(alpha = 0.035f),
                        radius = size.width * 0.8f,
                        center = androidx.compose.ui.geometry.Offset(-size.width * 0.15f, size.height * 0.9f))
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            val reveal = remember { Animatable(0f) }
            LaunchedEffect(Unit) { reveal.animateTo(1f, tween(260, easing = EaseOutCubic)) }
            Column(
                Modifier.widthIn(max = 360.dp).fillMaxWidth().graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 12.dp.toPx()
                }
            ) {
                Text("YOUR CONTROL CENTER", color = colors.accent,
                    fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                Text("RedMagic", color = colors.onSurface, fontSize = 40.sp,
                    lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Text("Control", color = colors.accent, fontSize = 40.sp,
                    lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(24.dp))
                Box(Modifier.width(48.dp).height(3.dp).background(colors.accent,
                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(color = colors.accent, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Crossfade(targetState = message(), animationSpec = tween(180),
                        label = "startup-status") { status ->
                        Text(status, color = colors.onSurfaceVariant,
                            fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            Text("Hardware · Software · Personalization",
                color = colors.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

