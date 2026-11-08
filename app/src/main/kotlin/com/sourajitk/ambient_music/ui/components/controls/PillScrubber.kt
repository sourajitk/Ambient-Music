// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.components.controls

/*
 * One selection pill shared by a row of cells (the floating nav bar, a segmented row): it stretches toward a newly selected cell
 * (leading edge first) and settles, and the selected cell can be pressed and dragged along the row to scrub between cells with
 * haptic ticks, committing on release. The row gives each cell [pillCell], itself [pillScrubbing], and reads how much of a cell the
 * pill covers through [PillScrubber.coverage] - inside a lambda that runs while drawing, so a moving pill never recomposes the row.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The capsule's fill: near-white in light mode, a raised grey in dark and OLED, where "lowest" is pure black and a bar would vanish. */
@Composable
fun pillContainerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
}

/** An airy tint of the accent, for the pill behind the selected cell. */
@Composable
fun pillHighlightColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return scheme.primary.copy(alpha = if (scheme.surface.luminance() < 0.5f) 0.24f else 0.14f)
}

/**
 * The state of one pill row. [selectedIndex], [isVisible] and [onSelect] are refreshed on every composition and read when a gesture
 * happens, so a gesture that outlives a recomposition always acts on the current ones.
 */
@Stable
class PillScrubber internal constructor(
    private val haptic: HapticFeedback,
    internal val tilt: PillTilt,
) {
    /** Where the pill should go while a finger drives it: [snap] jumps there, otherwise it glides. */
    private class Move(val left: Float, val right: Float, val snap: Boolean)

    // Only the newest move matters, so the finger's moves go through one channel and one collector (see [follow]), not a coroutine each.
    private val moves = Channel<Move>(Channel.CONFLATED)

    internal var selectedIndex = 0
    internal var isVisible: (Int) -> Boolean = { true }
    internal var onSelect: (Int) -> Unit = {}

    // Each cell reports (x in the window, width); rowRootX turns that into the row's own coordinates.
    internal val cellBounds = mutableStateMapOf<Int, Pair<Float, Float>>()
    internal var rowRootX by mutableFloatStateOf(0f)

    // While a finger drives the pill: where its centre should be, in row coordinates.
    internal var dragCenter by mutableStateOf<Float?>(null)

    // The cell just released on, held until the parent's selection catches up.
    internal var pendingIndex by mutableStateOf<Int?>(null)
    internal var placed by mutableStateOf(false)
    internal val pillLeft = Animatable(0f)
    internal val pillRight = Animatable(0f)
    internal lateinit var pressScale: State<Float>

    internal fun spans(): List<PillSpan> = visibleSpans(cellBounds, rowRootX, isVisible)

    /** The share (0..1) of cell [index] the pill covers right now. Read it while drawing or inside a lambda that is. */
    fun coverage(index: Int): Float {
        val span = spans().firstOrNull { it.index == index } ?: return 0f
        val overlap = minOf(pillRight.value, span.right) - maxOf(pillLeft.value, span.left)
        return (overlap / (span.right - span.left)).coerceIn(0f, 1f)
    }

    /** Puts the pill under the finger now: a direct jump when it is already close, a stiff glide when far. */
    internal fun moveWithFinger(center: Float, catchUp: Float) {
        val spans = spans()
        if (spans.isEmpty()) return
        val (l, r) = pillAt(spans, center)
        val gap = abs((l + r) / 2f - (pillLeft.value + pillRight.value) / 2f)
        moves.trySend(Move(l, r, snap = gap <= catchUp))
    }

    /** Carries out the finger's moves until cancelled; a newer move replaces one still gliding. */
    internal suspend fun follow() {
        val glide = spring<Float>(dampingRatio = 1f, stiffness = 1500f)
        moves.receiveAsFlow().collectLatest { move ->
            if (move.snap) {
                pillLeft.snapTo(move.left)
                pillRight.snapTo(move.right)
            } else {
                coroutineScope {
                    launch { pillLeft.animateTo(move.left, glide) }
                    launch { pillRight.animateTo(move.right, glide) }
                }
            }
        }
    }

    internal fun tick() = haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
}

/** Remembers the pill of a row whose selected cell is [selectedIndex]; [onSelect] is told the cell the pill lands on. */
@Composable
fun rememberPillScrubber(selectedIndex: Int, isVisible: (Int) -> Boolean = { true }, onSelect: (Int) -> Unit): PillScrubber {
    val tilt = rememberPillTilt()
    val haptic = LocalHapticFeedback.current
    val s = remember { PillScrubber(haptic, tilt) }
    LaunchedEffect(s) { s.follow() }
    s.selectedIndex = selectedIndex
    s.isVisible = isVisible
    s.onSelect = onSelect

    val spans = s.spans()
    val dragging = s.dragCenter != null
    val target: Pair<Float, Float>? = if (spans.isEmpty()) {
        null
    } else {
        s.dragCenter?.let { pillAt(spans, it) }
            ?: spans.firstOrNull { it.index == (s.pendingIndex ?: selectedIndex) }?.let { it.left to it.right }
    }

    LaunchedEffect(s.pendingIndex) {
        if (s.pendingIndex != null) {
            delay(500)
            s.pendingIndex = null
        }
    }
    LaunchedEffect(selectedIndex) { s.pendingIndex = null }

    // The edge the pill is moving toward leads and the other follows, so it stretches out and then contracts onto the new cell.
    LaunchedEffect(target?.first, target?.second, dragging) {
        val (targetLeft, targetRight) = target ?: return@LaunchedEffect
        if (!s.placed) {
            s.pillLeft.snapTo(targetLeft)
            s.pillRight.snapTo(targetRight)
            s.placed = true
            return@LaunchedEffect
        }
        // While a finger is down the gesture moves the pill itself, so it tracks with no lag.
        if (dragging) return@LaunchedEffect
        val movingRight = targetLeft + targetRight > s.pillLeft.value + s.pillRight.value
        val lead = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)
        val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 330f)
        coroutineScope {
            launch { s.pillLeft.animateTo(targetLeft, if (movingRight) trail else lead) }
            launch { s.pillRight.animateTo(targetRight, if (movingRight) lead else trail) }
        }
    }
    s.pressScale = animateFloatAsState(
        targetValue = if (dragging) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "pillPress",
    )
    return s
}

/** Marks a cell of the row: reports where it is so the pill can find it. */
fun Modifier.pillCell(scrubber: PillScrubber, index: Int): Modifier = onGloballyPositioned {
    scrubber.cellBounds[index] = it.positionInRoot().x to it.size.width.toFloat()
}

/** The row itself: draws the pill behind its cells and handles pressing and dragging along it. */
fun Modifier.pillScrubbing(scrubber: PillScrubber, color: Color): Modifier = this
    .onGloballyPositioned { scrubber.rowRootX = it.positionInRoot().x }
    .drawBehind {
        // Overhangs the cell a little each side: a longer flat top and bottom between the round ends.
        val overhang = 3.dp.toPx()
        val left = scrubber.pillLeft.value - overhang
        val right = scrubber.pillRight.value + overhang
        if (scrubber.placed && right > left) {
            scale(scrubber.pressScale.value, pivot = Offset((left + right) / 2f, size.height / 2f)) {
                drawRoundRect(color, Offset(left, 0f), Size(right - left, size.height), CornerRadius(size.height / 2f))
            }
        }
    }
    .pointerInput(scrubber) {
        val slop = viewConfiguration.touchSlop
        val catchUp = 40.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downX = down.position.x
            val startSpan = spanAt(scrubber.spans(), downX) ?: return@awaitEachGesture
            // Pressing the selected cell grabs the pill where it is; pressing another is a tap unless the finger travels, and then
            // the pill catches up to it.
            val grabbed = startSpan.index == scrubber.selectedIndex
            var moved = false
            var center = startSpan.center
            var hover = startSpan.index
            var released = false
            try {
                if (grabbed) {
                    scrubber.dragCenter = center
                    scrubber.tilt.drag(down.position, size)
                }
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    val x = change.position.x
                    if (!moved && abs(x - downX) > slop) moved = true
                    if (grabbed || moved) scrubber.tilt.drag(change.position, size)
                    if (moved) {
                        center = if (grabbed) startSpan.center + (x - downX) else x
                        scrubber.dragCenter = center
                        scrubber.moveWithFinger(center, catchUp)
                        change.consume()
                        val under = spanAt(scrubber.spans(), center)?.index ?: hover
                        if (under != hover) {
                            hover = under
                            scrubber.tick()
                        }
                    }
                }
                if (released) {
                    val index = if (moved) spanAt(scrubber.spans(), center)?.index ?: startSpan.index else startSpan.index
                    scrubber.pendingIndex = index
                    if (index != scrubber.selectedIndex) scrubber.onSelect(index)
                }
            } finally {
                scrubber.dragCenter = null
                scrubber.tilt.release()
            }
        }
    }
