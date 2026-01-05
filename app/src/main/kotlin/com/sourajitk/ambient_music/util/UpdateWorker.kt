// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sourajitk.ambient_music.ui.notification.showUpdateNotification

// A basic coroutine update checker
class UpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            val update = UpdateChecker.checkForUpdate(applicationContext)
            if (update != null) {
                showUpdateNotification(applicationContext, update)
            }
            return Result.success()
        } catch (_: Exception) {
            return Result.failure()
        }
    }
}
