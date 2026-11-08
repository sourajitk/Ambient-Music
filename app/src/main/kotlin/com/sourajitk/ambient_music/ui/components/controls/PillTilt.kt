// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.components.controls

/*
 * A small parallax for pill bars while a finger scrubs along them: the whole bar eases a couple of dp
 * away from the finger (finger left, bar nudges right; finger up, bar nudges down) and springs back
 * on release. Draw-only, so the bar's touch area and the gesture's own coordinates never move.
 *
 * The finger reports where it is on every pointer event, but only the newest position matters: the reports go into a conflated
 * channel that one long-lived collector drains, so a drag starts no coroutine of its own per event.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.abs

private val FOLLOW: SpringSpec<Offset> = spring(dampingRatio = 1f, stiffness = 600f)
private val SETTLE: SpringSpec<Offset> = spring(dampingRatio = 0.45f, stiffness = 300f)
private val OffsetConverter = TwoWayConverter<Offset, AnimationVector2D>({ AnimationVector2D(it.x, it.y) }, { Offset(it.v1, it.v2) })

internal class PillTilt {
    private class Target(val at: Offset, val spec: SpringSpec<Offset>)

    // Finger position relative to the bar's centre, each axis -1..1.
    private val offset = Animatable(Offset.Zero, OffsetConverter)
    private val targets = Channel<Target>(Channel.CONFLATED)

    val x: Float get() = offset.value.x
    val y: Float get() = offset.value.y

    /** The finger is at [position] inside a bar of [size] (it may be outside the bar). */
    fun drag(position: Offset, size: IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        val nx = ((position.x - size.width / 2f) / (size.width / 2f)).coerceIn(-1f, 1f)
        // Vertical reach is generous: a finger a bar-and-a-half away is already at full tilt.
        val ny = ((position.y - size.height / 2f) / (size.height * 1.5f)).coerceIn(-1f, 1f)
        targets.trySend(Target(Offset(nx, ny), FOLLOW))
    }

    /** The finger lifted: settle back with a small bounce. */
    fun release() {
        if (abs(x) < 0.001f && abs(y) < 0.001f && !offset.isRunning) return
        targets.trySend(Target(Offset.Zero, SETTLE))
    }

    /** Follows the newest target until cancelled; a new one replaces the animation still running toward the last. */
    suspend fun run() {
        targets.receiveAsFlow().collectLatest { offset.animateTo(it.at, it.spec) }
    }
}

@Composable
internal fun rememberPillTilt(): PillTilt {
    val tilt = remember { PillTilt() }
    LaunchedEffect(tilt) { tilt.run() }
    return tilt
}

/** Draws everything after this modifier shifted opposite to the finger, by up to [maxShift]. */
internal fun Modifier.pillTilt(tilt: PillTilt, maxShift: Dp = 1.25.dp): Modifier = drawWithContent {
    val shift = maxShift.toPx()
    translate(-tilt.x * shift, -tilt.y * shift) {
        this@drawWithContent.drawContent()
    }
}
