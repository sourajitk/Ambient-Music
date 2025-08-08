package com.sourajitk.ambient_music.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.playback.MusicPlaybackService
import com.sourajitk.ambient_music.util.SleepTimerManager // IMPORT THE NEW MANAGER

class SleepTimerTileService : TileService() {

  private val TAG = "SleepTimerTileService"

  private val timerPresets = listOf(0, 5, 10, 15, 20, 25, 30)

  override fun onStartListening() {
    super.onStartListening()
    Log.d(TAG, "onStartListening: Tile is now active and listening.")
    updateTile()
  }

  override fun onStopListening() {
    super.onStopListening()
    Log.d(TAG, "onStopListening: Tile is no longer listening.")
  }

  override fun onTileAdded() {
    super.onTileAdded()
    Log.d(TAG, "onTileAdded: Tile was added by the user.")
  }

  override fun onTileRemoved() {
    super.onTileRemoved()
    Log.d(TAG, "onTileRemoved: Tile was removed by the user.")
  }

  override fun onClick() {
    super.onClick()
    Log.d(TAG, "onClick: Tile was clicked.")

    // MODIFIED: Read current timer value from SleepTimerManager
    val currentTimerValue = SleepTimerManager.sleepTimerMinutes
    Log.d(TAG, "onClick: Read current timer value from manager: $currentTimerValue minutes.")

    val currentIndex = timerPresets.indexOf(currentTimerValue)
    Log.d(TAG, "onClick: Current value found at index: $currentIndex")

    val nextIndex = (currentIndex + 1) % timerPresets.size
    val nextTimerValue = timerPresets[nextIndex]
    Log.d(TAG, "onClick: Calculated next index: $nextIndex, next value: $nextTimerValue minutes.")

    // The intent to the service is still correct
    val intent =
      Intent(this, MusicPlaybackService::class.java).apply {
        action = MusicPlaybackService.ACTION_SET_SLEEP_TIMER
        putExtra(MusicPlaybackService.EXTRA_TIMER_DURATION_MINUTES, nextTimerValue)
      }
    startService(intent)
    Log.d(
      TAG,
      "onClick: Sent Intent to MusicPlaybackService with action SET_SLEEP_TIMER and value $nextTimerValue.",
    )

    updateTile(nextTimerValue)
  }

  // MODIFIED: updateTile now reads its default value from SleepTimerManager
  private fun updateTile(currentValue: Int = SleepTimerManager.sleepTimerMinutes) {
    val tile =
      qsTile
        ?: run {
          Log.w(TAG, "updateTile: qsTile was null, cannot update.")
          return
        }
    Log.d(TAG, "updateTile: Updating visuals for value: $currentValue minutes.")

    if (currentValue > 0) {
      tile.state = Tile.STATE_ACTIVE
      tile.label = "Sleep Timer Set"
      tile.subtitle = "$currentValue minutes"
      tile.icon = Icon.createWithResource(this, R.drawable.ic_timer_on)
      Log.d(TAG, "updateTile: Set to ACTIVE state.")
    } else {
      tile.state = Tile.STATE_INACTIVE
      tile.label = "Sleep Timer"
      tile.subtitle = "Off"
      tile.icon = Icon.createWithResource(this, R.drawable.ic_timer_off)
      Log.d(TAG, "updateTile: Set to INACTIVE state.")
    }

    tile.updateTile()
    Log.d(TAG, "updateTile: updateTile() called on the system tile object.")
  }
}