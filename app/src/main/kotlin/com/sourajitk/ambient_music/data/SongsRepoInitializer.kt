// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.data

import android.app.Application
import android.util.Log
import com.sourajitk.ambient_music.ui.notification.createUpdateNotificationChannel
import com.sourajitk.ambient_music.util.InstallSourceChecker
import com.sourajitk.ambient_music.util.TileStateUtil
import com.sourajitk.ambient_music.util.UpdateScheduler.scheduleUpdateChecks
import com.sourajitk.ambient_music.widget.WidgetImageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongsRepoInitializer : Application() {
    companion object {
        private const val TAG = "SongsRepoInitializer"
    }

    override fun onCreate() {
        super.onCreate()

        // This runs on every process start (including Android Auto binding the media service), so
        // keep it lean. Update checks are handled by the periodic UpdateWorker and MainActivity,
        // and are irrelevant for Play Store installs.
        createUpdateNotificationChannel(this)
        if (!InstallSourceChecker.isFromPlayStore(this)) {
            scheduleUpdateChecks(this)
        }

        // Load the song links from the local cache instead of trying to access flushed cache.
        SongsRepo.initializeFromCache(this)

        Log.d(TAG, "Initializing SongsRepo & Fetching JSON")
        SongsRepo.initializeAndRefresh(applicationContext) { success, statusMessage ->
            Log.d(TAG, "SongsRepo init finished. Success: $success, Status: $statusMessage")
            if (success) {
                Log.d(TAG, "SongsRepo successfully loaded songs. Requesting tile update.")
                TileStateUtil.requestTileUpdate(applicationContext)

                // Refresh widget images
                CoroutineScope(Dispatchers.IO).launch {
                    WidgetImageManager.refreshWidgetImages(applicationContext)
                }
            }
        }
    }
}
