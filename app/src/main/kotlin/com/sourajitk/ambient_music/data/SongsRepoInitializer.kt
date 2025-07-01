package com.sourajitk.ambient_music.data

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.util.Log
import com.sourajitk.ambient_music.tiles.CalmQSTileService
import com.sourajitk.ambient_music.tiles.ChillQSTileService
import com.sourajitk.ambient_music.tiles.SleepQSTileService

class SongsRepoInitializer : Application() {
  private val TAG = "SongsRepoInitializer"

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Initializing SongsRepo & Fetching JSON")
    SongsRepo.initializeAndRefresh(applicationContext) { success, statusMessage ->
      Log.d(TAG, "SongsRepo init finished. Success: $success, Status: $statusMessage")
      if (success) {
        Log.d(TAG, "SongsRepo successfully loaded songs. Requesting tile update.")
        TileService.requestListeningState(this, ComponentName(this, CalmQSTileService::class.java))
        TileService.requestListeningState(this, ComponentName(this, ChillQSTileService::class.java))
        TileService.requestListeningState(this, ComponentName(this, SleepQSTileService::class.java))
      }
    }
  }
}
