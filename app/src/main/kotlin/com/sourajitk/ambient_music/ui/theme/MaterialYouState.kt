// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.theme

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.Settings

/*
 * Whether the phone is handing out Material You (wallpaper) colours. Stock Android always does, so only Samsung needs checking:
 * One UI ships with Color palette off, and while it's off the dynamic colour resources still resolve to Samsung's stock blue
 * palette rather than falling back, so the app has to decide for itself to use its own scheme.
 */

// One UI's Color palette switch (1 = on). It isn't a public API, so it's read from any table that holds it and absence is tolerated.
private const val SAMSUNG_PALETTE_SWITCH = "wallpapertheme_state"

fun isMaterialYouOn(context: Context): Boolean {
    if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return true
    // Unreadable means an unknown One UI build; use the app's own colours, since One UI has the palette off out of the box.
    return readSamsungPaletteSwitch(context.contentResolver) ?: false
}

private fun readSamsungPaletteSwitch(resolver: ContentResolver): Boolean? {
    val readers = listOf<(ContentResolver, String) -> String?>(
        Settings.System::getString,
        Settings.Secure::getString,
        Settings.Global::getString,
    )
    for (read in readers) {
        val value = runCatching { read(resolver, SAMSUNG_PALETTE_SWITCH) }.getOrNull()?.trim()?.toIntOrNull()
        if (value != null) return value != 0
    }
    return null
}
