package com.sourajitk.ambient_music

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log

class SongsRepoInitializer : Application() {
  private val TAG = "SongsRepoInitializer"

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Initializing SongRepo & Fetching JSON")
    SongRepo.initializeAndRefresh(applicationContext) { success, statusMessage ->
      Log.d(TAG, "SongRepo init finished. Success: $success, Status: $statusMessage")
      if (success) {
        Log.d(TAG, "SongRepo successfully loaded songs. Requesting tile update.")
        TileService.requestListeningState(this, ComponentName(this, CalmQSTileService::class.java))
        TileService.requestListeningState(this, ComponentName(this, ChillQSTileService::class.java))
        TileService.requestListeningState(this, ComponentName(this, SleepQSTileService::class.java))
      }
    }
  }
}
