// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One cell of the floating nav bar: an icon over an always-visible label. Stateless - the bar draws the selection pill and owns the
 * touch handling, so a cell only renders itself. [selection] (0..1) is the share of this cell the pill covers right now: it drives
 * the accent tint and the filled icon, so selection never depends on colour alone. It is read here, in the cell, so a moving pill
 * redraws four cells and not the bar. The cell is as wide as its label needs (with a minimum touch width), at least 52 dp tall and
 * taller with a large font; the weight is constant so tinting never changes its width and moves the pill's target.
 */
@Composable
internal fun NavBarCell(
    screen: Screen,
    selection: () -> Float,
    isCurrent: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val covered = selection()
    val contentColor = lerp(scheme.onSurface, scheme.primary, covered)

    Column(
        modifier = modifier
            .widthIn(min = 74.dp)
            .heightIn(min = 52.dp)
            // Touch is handled by the bar; this keeps TalkBack's tab role and double-tap.
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = isCurrent
                onClick {
                    onActivate()
                    true
                }
            }
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The outline crossfades into the filled icon as the pill arrives, rather than cutting over at the halfway point - the
        // filled one also grows in very slightly, so it feels like it is filling in rather than replacing the other.
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = screen.icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(23.dp).graphicsLayer { alpha = 1f - covered },
            )
            Icon(
                painter = painterResource(id = screen.iconFilled),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(23.dp).graphicsLayer {
                    alpha = covered
                    scaleX = 0.82f + 0.18f * covered
                    scaleY = 0.82f + 0.18f * covered
                },
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(screen.label),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
