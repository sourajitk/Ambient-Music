// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.sourajitk.ambient_music.tiles.CalmQSTileService
import com.sourajitk.ambient_music.tiles.ChillQSTileService
import com.sourajitk.ambient_music.tiles.FocusQSTileService
import com.sourajitk.ambient_music.tiles.SerenityQSTileService
import com.sourajitk.ambient_music.tiles.SleepQSTileService
import com.sourajitk.ambient_music.widget.AmbientMusicWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TileStateUtil {
    private const val TILE_PREFS_NAME = "tile_state_prefs"

    private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(TILE_PREFS_NAME, Context.MODE_PRIVATE)

    fun setTileAdded(context: Context, tileClassName: String, isAdded: Boolean) {
        getPrefs(context).edit { putBoolean(tileClassName, isAdded) }
    }

    fun isTileAdded(context: Context, tileClassName: String): Boolean = getPrefs(context).getBoolean(tileClassName, false)

    fun requestTileUpdate(context: Context) {
        val tileServices =
            listOf(
                CalmQSTileService::class.java,
                ChillQSTileService::class.java,
                SleepQSTileService::class.java,
                FocusQSTileService::class.java,
                SerenityQSTileService::class.java,
                SleepTimerService::class.java,
            )

        tileServices.forEach { serviceClass ->
            TileService.requestListeningState(context, ComponentName(context, serviceClass))
        }

        // Also update the Home Screen widget
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AmbientMusicWidget().updateAll(context)
                Log.d("TileStateUtil", "Widget updateAll called successfully")
            } catch (e: Exception) {
                Log.e("TileStateUtil", "Failed to update widget: ${e.message}")
            }
        }
    }
}
