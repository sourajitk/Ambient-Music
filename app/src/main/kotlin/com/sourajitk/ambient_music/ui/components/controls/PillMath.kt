// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.components.controls

import androidx.compose.ui.util.lerp

/*
 * The geometry of a row of cells with one selection pill that travels between them (the floating nav bar, a segmented row): where
 * each cell is, which cell a finger is over, and where the pill sits when its centre is at a given x. Pure, so it can be tested.
 */

/** A visible cell's horizontal extent, in the cell row's own coordinates. */
internal class PillSpan(val index: Int, val left: Float, val right: Float) {
    val center: Float get() = (left + right) / 2f
}

/** The cell under [x]; past either end, the first or last cell. Cells are contiguous. */
internal fun spanAt(spans: List<PillSpan>, x: Float): PillSpan? = spans.firstOrNull { x < it.right } ?: spans.lastOrNull()

/** Where a pill centred on [x] sits: it glides from cell to cell and stops at the outer two. */
internal fun pillAt(spans: List<PillSpan>, x: Float): Pair<Float, Float> {
    val first = spans.first()
    val last = spans.last()
    if (x <= first.center) return first.left to first.right
    if (x >= last.center) return last.left to last.right
    val i = spans.indexOfLast { it.center <= x }
    val a = spans[i]
    val b = spans[i + 1]
    val t = (x - a.center) / (b.center - a.center)
    return lerp(a.left, b.left, t) to lerp(a.right, b.right, t)
}

/**
 * The cells that are showing, as spans in the row's own coordinates, in order. [cellBounds] maps a cell's index to (x in the window,
 * width) as each cell reported it; a cell that has no width yet, or that [include] refuses (one that is not there at the moment), is left out.
 */
internal fun visibleSpans(cellBounds: Map<Int, Pair<Float, Float>>, rowRootX: Float, include: (Int) -> Boolean): List<PillSpan> = cellBounds.entries
    .filter { (index, bounds) -> bounds.second > 1f && include(index) }
    .sortedBy { it.key }
    .map { PillSpan(it.key, it.value.first - rowRootX, it.value.first - rowRootX + it.value.second) }
