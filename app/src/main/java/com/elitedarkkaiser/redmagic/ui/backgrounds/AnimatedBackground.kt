/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package com.elitedarkkaiser.redmagic.ui.backgrounds

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * Types of animated backgrounds available in the app, drawn behind the whole View tree by
 * [AnimatedBackground]. Ported from MorpheApp/morphe-manager's Compose background gallery.
 */
enum class BackgroundType(val displayName: String) {
    CIRCLES("Circles"),
    RINGS("Rings"),
    MESH("Mesh"),
    SPACE("Space"),
    SHAPES("Shapes"),
    SNOW("Snow"),
    GRID("Grid"),
    PARTICLES("Particles"),
    MATRIX("Matrix"),
    NONE("None"),
    RANDOM("Random");

    companion object {
        val DEFAULT = NONE

        /** All types that can be picked when RANDOM is active (excludes NONE and RANDOM itself). */
        val RANDOMIZABLE: List<BackgroundType> = entries.filter { it != NONE && it != RANDOM }
    }
}

/**
 * Animated background with multiple visual styles.
 * Creates subtle floating effects that can be used across all screens.
 *
 * When [type] is [BackgroundType.RANDOM], [resolvedType] must be provided —
 * it holds the already-resolved random type from the ViewModel so the choice
 * stays stable for the current session/interval.
 */
@Composable
@SuppressLint("ModifierParameter")
fun AnimatedBackground(
    type: BackgroundType = BackgroundType.CIRCLES,
    resolvedType: BackgroundType? = null,
    enableParallax: Boolean = true,
    speedMultiplier: () -> Float = { 1f },
    patchingCompleted: () -> Boolean = { false }
) {
    val effectiveType = if (type == BackgroundType.RANDOM) {
        resolvedType ?: BackgroundType.CIRCLES
    } else {
        type
    }

    val resolvedSpeed = speedMultiplier()
    val resolvedPatchingCompleted = patchingCompleted()

    // No background blends across its own layers, so clipping alone is enough. Compositing offscreen
    // would allocate a full-screen render target and re-composite it every frame for nothing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        when (effectiveType) {
            BackgroundType.CIRCLES -> CirclesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.RINGS -> RingsBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MESH -> MeshBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SPACE -> SpaceBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SHAPES -> ShapesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SNOW -> SnowBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.GRID -> GridBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.PARTICLES -> ParticlesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MATRIX -> MatrixBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.NONE -> Unit
            // effectiveType is never RANDOM (resolved above), but the branch is required for exhaustiveness
            BackgroundType.RANDOM -> Unit
        }
    }
}
