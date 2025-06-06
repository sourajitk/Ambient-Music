package com.sourajitk.ambient_music

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class MusicQSTileService : TileService() {

  private val TAG = "MusicQSTileService"

  override fun onTileAdded() {
    super.onTileAdded()
    updateTileVisualsBasedOnServiceState()
  }

  override fun onStartListening() {
    super.onStartListening()
    Log.d(TAG, "Playback Started!")
    updateTileVisualsBasedOnServiceState()
  }

  override fun onStopListening() {
    super.onStopListening()
    Log.d(TAG, "Playback Stopped :(")
  }

  override fun onClick() {
    super.onClick()
    if (SongRepo.songs.isEmpty()) {
      Log.w(TAG, "No songs in parsed JSON")
      updateTileVisualsBasedOnServiceState(forceUnavailable = true)
      return
    }

    val currentlyPlaying = MusicPlaybackService.isServiceCurrentlyPlaying
    Log.d(TAG, "onClick: Current actual service state isPlaying=$currentlyPlaying.")

    val optimisticNextState = !currentlyPlaying
    Log.d(TAG, "onClick: Optimistically updating tile to reflect next state: isPlaying=$optimisticNextState")
    applyVisuals(optimisticNextState, forceUnavailable = false)

    val intent =
      Intent(this, MusicPlaybackService::class.java).apply {
        action = MusicPlaybackService.ACTION_TOGGLE_PLAYBACK_QS
      }
    startForegroundService(intent)
  }

  /** Fetches state from MusicPlaybackService and updates tile visuals. */
  private fun updateTileVisualsBasedOnServiceState(forceUnavailable: Boolean = false) {
    val actualPlayingState = MusicPlaybackService.isServiceCurrentlyPlaying
    applyVisuals(actualPlayingState, forceUnavailable)
  }

  /** Applies the visual changes to the tile based on the given playback state. */
  private fun applyVisuals(isPlayingState: Boolean, forceUnavailable: Boolean) {
    val tile = qsTile ?: return

    tile.label = getString(R.string.qs_tile_main_label)

    if (forceUnavailable || SongRepo.songs.isEmpty()) {
      tile.subtitle = getString(R.string.qs_subtitle_no_songs)
      tile.state = Tile.STATE_UNAVAILABLE
      tile.icon = Icon.createWithResource(this, R.drawable.ic_music_unavailable)
    } else {
      if (isPlayingState) {
        tile.subtitle = getString(R.string.qs_subtitle_playing)
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_pause)
      } else {
        tile.subtitle = getString(R.string.qs_subtitle_paused)
        tile.state = Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_play_arrow)
      }
    }
    tile.updateTile()
  }

  override fun onTileRemoved() {
    super.onTileRemoved()
    Log.d(TAG, "onTileRemoved")
  }
}
