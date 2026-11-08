// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sourajitk.ambient_music.R

/**
 * The top-level destinations of the floating nav bar. [icon] and [iconFilled] are res/drawable vectors (outlined and filled); the
 * filled one fades in as the selection pill arrives.
 */
enum class Screen(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val iconFilled: Int,
) {
    Home("home", R.string.home_string, R.drawable.ic_nav_home, R.drawable.ic_nav_home_filled),
    Timer("timer", R.string.timer_string, R.drawable.ic_nav_timer, R.drawable.ic_nav_timer_filled),
    Settings("settings", R.string.settings_string, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_filled),
}

// Composable to get the string label
@Composable
fun Screen.label(): String = stringResource(id = this.label)
