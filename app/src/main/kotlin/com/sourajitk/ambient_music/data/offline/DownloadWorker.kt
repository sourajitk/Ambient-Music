// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sourajitk.ambient_music.data.offline.GenreDownloader

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val genre = inputData.getString("genre") ?: return Result.failure()

        GenreDownloader.setDownloading(genre)
        val success = GenreDownloader.downloadGenreFiles(applicationContext, genre)
        GenreDownloader.clearDownloading(genre)

        return if (success) {
            GenreDownloader.setDownloaded(genre)
            Result.success()
        } else {
            Result.failure()
        }
    }
}
