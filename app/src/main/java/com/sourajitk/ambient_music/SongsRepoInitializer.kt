package com.sourajitk.ambient_music

import android.app.Application
import android.service.quicksettings.TileService
import android.util.Log
import android.content.ComponentName


class SongsRepoInitializer : Application() {
    private val TAG = "SongsRepoInitializer"
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing SongRepo & Fetching JSON")
        SongRepo.initializeAndRefresh(applicationContext) { success, statusMessage ->
            Log.d(TAG, "SongRepo init finished. Success: $success, Status: $statusMessage")
            if (success) {
                Log.d(TAG, "SongRepo successfully loaded songs. Requesting tile update.")
                TileService.requestListeningState(
                    this,
                    ComponentName(this, MusicQSTileService::class.java)
                )
            }
        }
    }
}