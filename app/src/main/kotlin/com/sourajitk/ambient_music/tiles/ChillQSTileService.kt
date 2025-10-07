// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.playback.MusicPlaybackService
import com.sourajitk.ambient_music.util.TileStateUtil

class ChillQSTileService : TileService() {

    companion object {
        private const val TAG = "ChillQSTileService"
    }

    private val myGenre = "chill"

    override fun onTileAdded() {
        super.onTileAdded()
        TileStateUtil.setTileAdded(applicationContext, this::class.java.name, true)
        updateTileVisualsBasedOnServiceState()
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Chill Tile: Start")
        updateTileVisualsBasedOnServiceState()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Chill Tile: Stop")
    }

    override fun onClick() {
        super.onClick()
        if (SongsRepo.songs.isEmpty()) {
            Log.w(TAG, "No songs in parsed JSON")
            updateTileVisualsBasedOnServiceState(forceUnavailable = false)
            return
        }
        val isPlaying = MusicPlaybackService.isServiceCurrentlyPlaying
        val activeGenre = MusicPlaybackService.currentPlaylistGenre
        val isMyGenreActiveNow = isPlaying && myGenre.equals(activeGenre, ignoreCase = true)

        if (isMyGenreActiveNow) {
            applyVisuals(isMyGenreActive = false, forceUnavailable = false)
            val intent =
                Intent(this, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_TOGGLE_PLAYBACK_QS
                }
            startForegroundService(intent)
        } else {
            applyVisuals(isMyGenreActive = true, forceUnavailable = false)
            val intent =
                Intent(this, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_PLAY_GENRE_CHILL
                }
            startForegroundService(intent)
        }
    }

    /** Fetches state from MusicPlaybackService and updates tile visuals. */
    private fun updateTileVisualsBasedOnServiceState(forceUnavailable: Boolean = false) {
        val isPlaying = MusicPlaybackService.Companion.isServiceCurrentlyPlaying
        val activeGenre = MusicPlaybackService.Companion.currentPlaylistGenre
        val isMyGenreActive = isPlaying && myGenre.equals(activeGenre, ignoreCase = true)
        applyVisuals(isMyGenreActive, forceUnavailable)
    }

    /** Applies the visual changes to the tile based on the given playback state of THIS genre. */
    private fun applyVisuals(isMyGenreActive: Boolean, forceUnavailable: Boolean) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label_chill)
        if (forceUnavailable) {
            tile.subtitle = getString(R.string.qs_subtitle_no_songs)
            tile.state = Tile.STATE_UNAVAILABLE
            tile.icon = Icon.createWithResource(this, R.drawable.ic_music_unavailable)
        } else {
            if (isMyGenreActive) {
                tile.subtitle = getString(R.string.qs_subtitle_playing)
                tile.state = Tile.STATE_ACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_chill)
            } else {
                tile.subtitle = getString(R.string.qs_subtitle_paused)
                tile.state = Tile.STATE_INACTIVE
                tile.icon = Icon.createWithResource(this, R.drawable.ic_chill)
            }
        }
        tile.updateTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        TileStateUtil.setTileAdded(applicationContext, this::class.java.name, false)
        Log.d(TAG, "onTileRemoved")
    }
}
