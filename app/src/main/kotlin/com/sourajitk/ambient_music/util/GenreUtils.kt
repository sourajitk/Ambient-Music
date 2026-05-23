// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sourajitk.ambient_music.R
import java.util.Locale

@Composable
fun getLocalizedGenreName(genre: String): String {
    val resId = when (genre.lowercase(Locale.ROOT)) {
        "calm" -> R.string.tile_label_calm
        "chill" -> R.string.tile_label_chill
        "sleep" -> R.string.tile_label_sleep
        "focus" -> R.string.tile_label_focus
        "serenity" -> R.string.tile_label_serenity
        else -> null
    }
    
    // Some tile labels are like "Ambient Calm", we can strip "Ambient " to just get "Calm" if it's localized nicely, 
    // but returning the full translated string is best here since it's already translated.
    return resId?.let { stringResource(id = it).removePrefix("Ambient ") } 
        ?: genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}
