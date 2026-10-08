/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package com.elitedarkkaiser.redmagic.ui.backgrounds

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Tilt shifts the artwork by about a dozen pixels at full range, so smaller steps land under a pixel
private const val TILT_TARGET_THRESHOLD = 0.02f

// Backgrounds drift slowly enough that a 60 Hz step is indistinguishable from a 120 Hz one,
// and every skipped step is a full-screen repaint the GPU does not have to make.
// The cadence stays the same behind sheets and dialogs, where the backdrop is still on screen
internal const val BACKGROUND_STEP_INTERVAL_MS = 16f

/**
 * Runs [frameLoop] for as long as the host stays resumed, cancelling the moment it stops. A
 * background left ticking behind the lock screen or another activity keeps the frame clock awake
 * and repaints the canvas for nobody. This app's own dialogs (M3Dialog) are plain Dialogs over the
 * same window rather than full-screen Compose content, so there is no "covered" state to gate on
 * beyond the lifecycle itself.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 */
@Composable
fun AnimationFrameEffect(frameLoop: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            frameLoop()
        }
    }
}

/**
 * Frame-based time accumulator that respects a [speedMultiplier].
 * Returns a [State<Float>] that increases every frame by (deltaMs * speedMultiplier).
 * This allows smooth speed changes without restarting animations.
 * The value only steps once per [BACKGROUND_STEP_INTERVAL_MS], so a high refresh rate display
 * does not repaint the backdrop more often than its slow drift can show.
 */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    // targetSpeed is updated every recomposition via SideEffect (composition thread, safe to read in frame callback)
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    AnimationFrameEffect {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeed.floatValue
        // Real elapsed time gates the step, while the scaled time is what the backdrop reads,
        // so changing the speed never changes how often the canvas is invalidated
        var elapsedMs = 0f
        var pendingTime = 0f
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smooth lerp: 2.5/sec ramp — ~0.8s to reach target speed.
                // High enough to feel reactive, low enough to avoid jarring jumps.
                currentSpeed += (targetSpeed.floatValue - currentSpeed) * (delta / 1000f) * 2.5f

                elapsedMs += delta
                pendingTime += delta * currentSpeed
                if (elapsedMs >= BACKGROUND_STEP_INTERVAL_MS) {
                    time.floatValue += pendingTime
                    // Carry the remainder so the step keeps its cadence on 90 Hz panels too
                    elapsedMs -= BACKGROUND_STEP_INTERVAL_MS
                    pendingTime = 0f
                }
            }
        }
    }
    return time
}


/**
 * Fires [onCompleted] exactly once when [patchingCompleted] flips to true.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 */
@Composable
fun CompletionEffect(patchingCompleted: Boolean, onCompleted: () -> Unit) {
    LaunchedEffect(patchingCompleted) {
        if (patchingCompleted) onCompleted()
    }
}

/**
 * Parallax sensor state holder
 */
data class ParallaxState(
    val tiltX: State<Float>,
    val tiltY: State<Float>
)

/**
 * Reusable parallax effect using device accelerometer
 * Returns ParallaxState with current tilt values as State objects
 *
 * @param enableParallax Whether parallax effect is enabled
 * @param sensitivity Multiplier for tilt sensitivity (default 0.3f)
 */
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
    context: Context
): ParallaxState {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    val tiltTarget = remember { MutableStateFlow(Offset.Zero) }

    // A single pair of springs chases the latest target. Starting a fresh pair per sensor event
    // meant a hundred coroutines a second, each cancelling the spring the one before had just begun
    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            tiltTarget.value = Offset.Zero
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
            return@LaunchedEffect
        }

        tiltTarget.collectLatest { target ->
            coroutineScope {
                launch {
                    smoothTiltX.animateTo(
                        targetValue = target.x,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                launch {
                    smoothTiltY.animateTo(
                        targetValue = target.y,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(enableParallax) {
        if (!enableParallax) {
            // Early exit if parallax is disabled
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: // No accelerometer available
            return@DisposableEffect onDispose { }

        // Calibration belongs to one registration, so the baseline resets with the listener itself
        var baselineX = 0f
        var baselineY = 0f
        var isCalibrated = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!isCalibrated) {
                    baselineX = event.values[0]
                    baselineY = event.values[1]
                    isCalibrated = true
                }

                val rawTiltX = event.values[0] - baselineX
                val rawTiltY = -(event.values[1] - baselineY)
                val target = Offset(rawTiltX * sensitivity, rawTiltY * sensitivity)

                // Sensor noise alone would retarget the springs forever, keeping the frame clock
                // awake even with the device flat on a table
                if ((target - tiltTarget.value).getDistance() > TILT_TARGET_THRESHOLD) {
                    tiltTarget.value = target
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // The spring does the smoothing, so a faster stream only produces targets it never reaches
        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Return State objects directly
    return ParallaxState(
        tiltX = smoothTiltX.asState(),
        tiltY = smoothTiltY.asState()
    )
}
