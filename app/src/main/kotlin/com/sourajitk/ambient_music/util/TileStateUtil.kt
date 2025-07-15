package com.sourajitk.ambient_music.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object TileStateUtil {
  private const val TILE_PREFS_NAME = "tile_state_prefs"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(TILE_PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun setTileAdded(context: Context, tileClassName: String, isAdded: Boolean) {
    getPrefs(context).edit { putBoolean(tileClassName, isAdded) }
  }

  fun isTileAdded(context: Context, tileClassName: String): Boolean {
    return getPrefs(context).getBoolean(tileClassName, false)
  }
}
