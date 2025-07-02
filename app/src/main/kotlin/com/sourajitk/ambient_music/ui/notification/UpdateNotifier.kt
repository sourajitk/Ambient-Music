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
        Log.d(TAG, "Update found: ${update.tag_name}. Posting notification.")
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

  // Intent to open the GitHub release page when the notification is tapped
  val intent = Intent(Intent.ACTION_VIEW, releaseInfo.html_url.toUri())
  val pendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  val notification =
    NotificationCompat.Builder(context, "APP_UPDATES_CHANNEL")
      .setSmallIcon(R.drawable.ic_music_note)
      .setContentTitle("Update Available")
      .setContentText("An update to ${releaseInfo.tag_name} is available.")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText(
            "An update to ${releaseInfo.tag_name} is now available. Tap to open the releases page on GitHub."
          )
      )
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

  notificationManager.notify(UPDATE_NOTIFICATION_ID, notification)
}
