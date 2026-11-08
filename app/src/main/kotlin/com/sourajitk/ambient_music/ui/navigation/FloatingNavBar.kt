// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sourajitk.ambient_music.ui.components.controls.pillCell
import com.sourajitk.ambient_music.ui.components.controls.pillContainerColor
import com.sourajitk.ambient_music.ui.components.controls.pillHighlightColor
import com.sourajitk.ambient_music.ui.components.controls.pillScrubbing
import com.sourajitk.ambient_music.ui.components.controls.pillTilt
import com.sourajitk.ambient_music.ui.components.controls.rememberPillScrubber

/**
 * A capsule bar with one selection pill that follows the selection: it stretches toward the new cell (leading edge first) and
 * settles, and you can press the selected cell and drag along the bar to scrub between destinations (the pill is
 * [com.sourajitk.ambient_music.ui.components.controls.PillScrubber]). Sits above the system navigation bar; every cell shows its label.
 */
@Composable
fun FloatingNavBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screens = Screen.entries
    val scrubber = rememberPillScrubber(
        selectedIndex = current.ordinal,
        onSelect = { onSelect(screens[it]) },
    )

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.pillTilt(scrubber.tilt, maxShift = 1.35.dp),
            shape = CircleShape,
            color = pillContainerColor(),
            shadowElevation = 6.dp,
        ) {
            Row(
                // Side padding = vertical padding + the pill's overhang, so its gap to the bar is even.
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp).pillScrubbing(scrubber, pillHighlightColor()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                screens.forEach { screen ->
                    NavBarCell(
                        screen = screen,
                        // Read while the cell draws, so the moving pill redraws the cells, not the whole bar.
                        selection = { scrubber.coverage(screen.ordinal) },
                        isCurrent = screen == current,
                        onActivate = { if (screen != current) onSelect(screen) },
                        modifier = Modifier.pillCell(scrubber, screen.ordinal),
                    )
                }
            }
        }
    }
}
