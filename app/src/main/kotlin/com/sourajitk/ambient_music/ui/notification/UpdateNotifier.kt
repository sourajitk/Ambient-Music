// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease
import com.sourajitk.ambient_music.util.InstallSourceChecker
import com.sourajitk.ambient_music.util.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "UpdateNotifier"
private const val UPDATE_NOTIFICATION_CHANNEL_ID = "APP_UPDATES_CHANNEL"
private const val UPDATE_NOTIFICATION_ID = 1001

/** Creates the notification channel for app updates. Calling through --> SongsRepoInitializer */
fun createUpdateNotificationChannel(context: Context) {
  val name = "App Updates"
  val descriptionText = "Notifications for new app versions"
  val importance = NotificationManager.IMPORTANCE_DEFAULT
  val channel =
    NotificationChannel(UPDATE_NOTIFICATION_CHANNEL_ID, name, importance).apply {
      description = descriptionText
    }
  val notificationManager: NotificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  notificationManager.createNotificationChannel(channel)
}

/**
 * Checks for updates in a background coroutine and posts a notification if a new version is found.
 */
fun checkForAppUpdates(context: Context) {
  CoroutineScope(Dispatchers.IO).launch {
    Log.d(TAG, "Checking for app updates in the background...")
    try {
      val update = UpdateChecker.checkForUpdate(context)
      if (update != null) {
        Log.d(TAG, "Update found: ${update.tagName}. Posting notification.")
        showUpdateNotification(context, update)
      } else {
        Log.d(TAG, "No new updates found.")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to check for updates.", e)
    }
  }
}

/** Builds and displays the actual update notification. */
fun showUpdateNotification(context: Context, releaseInfo: GitHubRelease) {
  val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  val wasInstalledFromPlayStore = InstallSourceChecker.isFromPlayStore(context)

  val updateIntent: Intent
  val updateText: String

  if (!wasInstalledFromPlayStore) {
    updateIntent = Intent(Intent.ACTION_VIEW, releaseInfo.htmlUrl.toUri())
    updateText =
      "Version ${releaseInfo.tagName} is now available on GitHub. Tap to open the GitHub Release page."
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        updateIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val notification =
      NotificationCompat.Builder(context, "APP_UPDATES_CHANNEL")
        .setSmallIcon(R.drawable.ic_music_note)
        .setContentTitle("Update Available")
        .setContentText("A new version is ready to install.")
        .setStyle(NotificationCompat.BigTextStyle().bigText(updateText))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
  } else {
    Log.d(TAG, "App installed from Play Store. No notification posted.")
  }
}
