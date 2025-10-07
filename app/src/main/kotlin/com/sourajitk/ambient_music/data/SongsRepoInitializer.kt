// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.data

import android.app.Application
import android.util.Log
import com.sourajitk.ambient_music.ui.notification.checkForAppUpdates
import com.sourajitk.ambient_music.ui.notification.createUpdateNotificationChannel
import com.sourajitk.ambient_music.util.TileStateUtil
import com.sourajitk.ambient_music.util.UpdateScheduler.scheduleUpdateChecks

class SongsRepoInitializer : Application() {
  companion object {
    private const val TAG = "SongsRepoInitializer"
  }

  override fun onCreate() {
    super.onCreate()

    createUpdateNotificationChannel(this)
    checkForAppUpdates(this)
    scheduleUpdateChecks(this)

    // Load the song links from the local cache instead of trying to access flushed cache.
    SongsRepo.initializeFromCache(this)

    Log.d(TAG, "Initializing SongsRepo & Fetching JSON")
    SongsRepo.initializeAndRefresh(applicationContext) { success, statusMessage ->
      Log.d(TAG, "SongsRepo init finished. Success: $success, Status: $statusMessage")
      if (success) {
        Log.d(TAG, "SongsRepo successfully loaded songs. Requesting tile update.")
        TileStateUtil.requestTileUpdate(applicationContext)
      }
    }
  }
}
