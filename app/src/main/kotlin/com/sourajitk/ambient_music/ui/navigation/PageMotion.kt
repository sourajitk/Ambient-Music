// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import android.view.RoundedCorner
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// Page transitions that reproduce the platform's own activity animations, so pages inside the app
// move exactly like native Android screens. The values come from AOSP:
// - Opening/closing without a gesture: core/res/res/anim/activity_{open,close}_{enter,exit}.xml
// - Predictive back: WindowManager Shell's (Default)CrossActivityBackAnimation
// They're drawn by Compose, so they're the same on every device and Android version, while the
// back gesture preview itself needs Android 14+.
internal object NativeMotion {
    const val DURATION_MS = 450
    const val MAX_SCALE = 0.9f
    const val SCRIM_ALPHA_LIGHT = 0.2f
    const val SCRIM_ALPHA_DARK = 0.8f
    val ShiftOffset = 96.dp
    val ScreenMargin = 8.dp

    // fast_out_extra_slow_in / Interpolators.EMPHASIZED. It's a two-segment path, built here from
    // its segments (Material's emphasized accelerate and decelerate curves) rescaled into place.
    private val EmphasizedStart = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val EmphasizedEnd = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private const val EMPHASIZED_SPLIT_X = 0.166666f
    private const val EMPHASIZED_SPLIT_Y = 0.4f
    val Emphasized = Easing { x ->
        if (x < EMPHASIZED_SPLIT_X) {
            EMPHASIZED_SPLIT_Y * EmphasizedStart.transform(x / EMPHASIZED_SPLIT_X)
        } else {
            EMPHASIZED_SPLIT_Y + (1f - EMPHASIZED_SPLIT_Y) * EmphasizedEnd.transform((x - EMPHASIZED_SPLIT_X) / (1f - EMPHASIZED_SPLIT_X))
        }
    }
    val EmphasizedDecelerate = EmphasizedEnd

    // android.view.animation.BackGestureInterpolator, applied to the gesture's progress.
    val BackGesture = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    // DecelerateInterpolator(1f), used for the vertical follow.
    val Decelerate = Easing { x -> 1f - (1f - x) * (1f - x) }
}

private enum class Phase { Idle, Opening, Closing, Dragging, Cancelling, Committing }

// Which of the two pages a layer is: the one on top (opened, or being closed) or the one beneath.
internal enum class PageRole { Top, Bottom }

private class Frame(val left: Float, val top: Float, val scale: Float)

private fun lerp(a: Float, b: Float, fraction: Float) = a + (b - a) * fraction
private fun lerp(a: Frame, b: Frame, fraction: Float) = Frame(lerp(a.left, b.left, fraction), lerp(a.top, b.top, fraction), lerp(a.scale, b.scale, fraction))
private val Identity = Frame(0f, 0f, 1f)

// Drives the transition between a page and the one beneath it.
@Stable
internal class PageMotionState(private val view: View) {
    private var phase by mutableStateOf(Phase.Idle)

    // Linear 0-1 timeline of the non-gesture and post-commit animations.
    private val timeline = Animatable(0f)

    // The back gesture's progress as reported by the system (already smoothed by it).
    private val gestureProgress = Animatable(0f)
    private var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    private var startTouchY = 0f
    private var touchDeltaY by mutableFloatStateOf(0f)
    private var committedProgress = 0f
    private var committedDeltaY = 0f

    // True while the page beneath is shown, i.e. not fully covered by the one on top.
    val isBottomVisible: Boolean get() = phase != Phase.Idle

    // True while the top page is animating away, when further back events are swallowed.
    val isLeaving: Boolean get() = phase == Phase.Committing || phase == Phase.Closing

    suspend fun open() {
        phase = Phase.Opening
        timeline.snapTo(0f)
        // The page stays invisible at the animation's first frame while it's built.
        awaitFirstDraw()
        timeline.animateTo(1f, tween(NativeMotion.DURATION_MS, easing = LinearEasing))
        phase = Phase.Idle
    }

    suspend fun close() {
        phase = Phase.Closing
        timeline.snapTo(0f)
        timeline.animateTo(1f, tween(NativeMotion.DURATION_MS, easing = LinearEasing))
    }

    suspend fun startGesture(event: BackEventCompat) {
        timeline.stop()
        swipeEdge = event.swipeEdge
        startTouchY = event.touchY
        touchDeltaY = 0f
        gestureProgress.snapTo(event.progress)
        phase = Phase.Dragging
    }

    suspend fun updateGesture(event: BackEventCompat) {
        touchDeltaY = event.touchY - startTouchY
        gestureProgress.snapTo(event.progress)
    }

    suspend fun cancelGesture() {
        phase = Phase.Cancelling
        gestureProgress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        phase = Phase.Idle
    }

    suspend fun commitGesture() {
        committedProgress = gestureProgress.value
        committedDeltaY = touchDeltaY
        phase = Phase.Committing
        timeline.snapTo(0f)
        timeline.animateTo(1f, tween(NativeMotion.DURATION_MS, easing = LinearEasing))
    }

    // Called once the top page has been removed after closing.
    fun reset() {
        phase = Phase.Idle
    }

    private fun windowCornerRadius(): Float = view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

    // Positions of both pages during the back gesture (CrossActivityBackAnimation's pre-commit
    // phase): the top page shrinks to 90% and moves to 8dp from the right edge (it stays centered
    // when swiping from the right edge), while the page beneath starts 96dp to the left and shrinks
    // in sync. Both follow the finger vertically, up to the 8dp screen margin.
    private fun GraphicsLayerScope.gestureFrame(role: PageRole, progress: Float, deltaY: Float): Frame {
        val width = size.width
        val height = size.height
        val shift = NativeMotion.ShiftOffset.toPx()
        val margin = NativeMotion.ScreenMargin.toPx()
        val p = NativeMotion.BackGesture.transform(progress.coerceIn(0f, 1f))
        val scaleInset = (1f - NativeMotion.MAX_SCALE) / 2f

        val topTarget = Frame(
            left = if (swipeEdge == BackEventCompat.EDGE_RIGHT) width * scaleInset else width * (1f - NativeMotion.MAX_SCALE) - margin,
            top = height * scaleInset,
            scale = NativeMotion.MAX_SCALE,
        )
        val topFrame = lerp(Identity, topTarget, p)

        val heightRatio = min(height / 2f, abs(deltaY)) / (height / 2f)
        val maxShiftY = max(0f, (height - topFrame.scale * height) / 2f - margin)
        val offsetY = maxShiftY * NativeMotion.Decelerate.transform(heightRatio) * sign(deltaY)

        val frame = when (role) {
            PageRole.Top -> topFrame
            PageRole.Bottom -> lerp(Frame(-shift, 0f, 1f), Frame(-shift + width * scaleInset, height * scaleInset, NativeMotion.MAX_SCALE), p)
        }
        return Frame(frame.left, frame.top + offsetY, frame.scale)
    }

    private fun GraphicsLayerScope.apply(frame: Frame, alpha: Float, cornerRadius: Float) {
        transformOrigin = TransformOrigin(0f, 0f)
        translationX = frame.left
        translationY = frame.top
        scaleX = frame.scale
        scaleY = frame.scale
        this.alpha = alpha
        if (cornerRadius > 0f) {
            // The radius is drawn before scaling, so compensate to keep it matching the screen's.
            shape = RoundedCornerShape(cornerRadius / frame.scale)
            clip = true
        } else {
            clip = false
        }
    }

    // Applies this page's transform. Reads animation values here, inside the layer block, so
    // animating only redraws the layers instead of recomposing the pages.
    fun applyTo(scope: GraphicsLayerScope, role: PageRole) = with(scope) {
        val t = timeline.value
        val ms = t * NativeMotion.DURATION_MS
        val shift = NativeMotion.ShiftOffset.toPx()
        when (phase) {
            Phase.Idle -> apply(Identity, if (role == PageRole.Top) 1f else 0f, 0f)

            // activity_open_enter / activity_open_exit
            Phase.Opening -> when (role) {
                PageRole.Top -> apply(Frame(shift * (1f - NativeMotion.Emphasized.transform(t)), 0f, 1f), ((ms - 50f) / 83f).coerceIn(0f, 1f), 0f)
                PageRole.Bottom -> apply(Frame(-shift * NativeMotion.Emphasized.transform(t), 0f, 1f), 1f, 0f)
            }

            // activity_close_exit / activity_close_enter
            Phase.Closing -> when (role) {
                PageRole.Top -> apply(Frame(shift * NativeMotion.Emphasized.transform(t), 0f, 1f), 1f - ((ms - 35f) / 83f).coerceIn(0f, 1f), 0f)
                PageRole.Bottom -> apply(Frame(-shift * (1f - NativeMotion.Emphasized.transform(t)), 0f, 1f), 1f, 0f)
            }

            Phase.Dragging, Phase.Cancelling -> apply(gestureFrame(role, gestureProgress.value, touchDeltaY), 1f, windowCornerRadius())

            // DefaultCrossActivityBackAnimation's post-commit phase: the top page slides a further
            // 96dp right while fading out within the first 20%, and the page beneath grows back
            // to full screen.
            Phase.Committing -> {
                val start = gestureFrame(role, committedProgress, committedDeltaY)
                val e = NativeMotion.Emphasized.transform(t)
                when (role) {
                    PageRole.Top -> apply(lerp(start, Frame(start.left + shift, 0f, 1f), e), max(1f - t * 5f, 0f), windowCornerRadius())
                    PageRole.Bottom -> apply(lerp(start, Identity, e), 1f, windowCornerRadius())
                }
            }
        }
    }

    // Opacity of the black scrim between the two pages: constant during the gesture (the top page
    // still covers it all at the start), then fading out once the gesture is committed.
    fun scrimAlpha(isDarkTheme: Boolean): Float {
        val maxAlpha = if (isDarkTheme) NativeMotion.SCRIM_ALPHA_DARK else NativeMotion.SCRIM_ALPHA_LIGHT
        return when (phase) {
            Phase.Dragging, Phase.Cancelling -> maxAlpha
            Phase.Committing -> maxAlpha * (1f - timeline.value)
            else -> 0f
        }
    }
}

// Waits until the frame that first composes and draws a newly shown page has finished. Building a
// page can take longer than a frame, and an animation started before that loses its first part to
// the long frame, so the page jumps into view. The platform likewise only starts an activity
// transition once the new window has drawn.
internal suspend fun awaitFirstDraw() {
    // The first callback runs in the frame that composes the page, before it's composed.
    withFrameNanos { }
    // The second one runs once that frame has been drawn.
    withFrameNanos { }
}

@Composable
internal fun rememberPageMotionState(): PageMotionState {
    val view = LocalView.current
    return remember(view) { PageMotionState(view) }
}

internal fun Modifier.pageLayer(state: PageMotionState, role: PageRole): Modifier = graphicsLayer { state.applyTo(this, role) }

// Handles back for a page shown on top of another one.
// - With a back gesture (Android 14+), shows the native predictive back preview, then either plays
//   the native post-commit animation and calls onBack, or springs back when cancelled.
// - Without one (back button, older Android), calls onBackWithoutGesture, which plays its own
//   animation.
@Composable
internal fun PageBackHandler(
    enabled: Boolean,
    state: PageMotionState,
    onBack: () -> Unit,
    onBackWithoutGesture: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        if (state.isLeaving) {
            // Already closing; swallow this back so it doesn't reach the page beneath.
            events.collect {}
            return@PredictiveBackHandler
        }
        var started = false
        try {
            events.collect { event ->
                if (!started) {
                    started = true
                    state.startGesture(event)
                }
                state.updateGesture(event)
            }
        } catch (e: CancellationException) {
            if (started) scope.launch { state.cancelGesture() }
            throw e
        }
        if (started) {
            // Run outside the handler's coroutine so a new back event can't cut it short.
            scope.launch {
                state.commitGesture()
                onBack()
                state.reset()
            }
        } else {
            onBackWithoutGesture()
        }
    }
}
