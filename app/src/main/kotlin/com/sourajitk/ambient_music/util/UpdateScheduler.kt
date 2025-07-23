// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
  private const val TAG = "UpdateScheduler"

  fun scheduleUpdateChecks(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val updateRequest =
      PeriodicWorkRequestBuilder<UpdateWorker>(36, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(
        "ambient_update_worker",
        ExistingPeriodicWorkPolicy.KEEP,
        updateRequest,
      )
    Log.d(TAG, "Periodic updates scheduled.")
  }
}
