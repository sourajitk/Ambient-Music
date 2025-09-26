// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.sourajitk.ambient_music.MainActivity
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.util.TileStateUtil.requestTileUpdate
import java.util.concurrent.TimeUnit

class SleepTimerService : Service() {

  private var countDownTimer: CountDownTimer? = null
  private lateinit var audioManager: AudioManager

  companion object {
    const val ACTION_START = "com.sourajitk.ambient_music.timerservice.ACTION_START"
    const val ACTION_STOP = "com.sourajitk.ambient_music.timerservice.ACTION_STOP"
    const val EXTRA_DURATION_MS = "duration_ms"
    private const val NOTIFICATION_ID = 2
    private const val CHANNEL_ID = "SleepTimerChannel"
    private const val TAG = "SleepTimer"

    @Volatile
    var currentTimerMinutes: Int = 0
      private set
  }

  override fun onCreate() {
    super.onCreate()
    audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        if (duration > 0) {
          currentTimerMinutes = TimeUnit.MILLISECONDS.toMinutes(duration).toInt()
          startTimer(duration)
        }
      }
      ACTION_STOP -> {
        stopTimerAndService()
      }
    }
    return START_NOT_STICKY
  }

  private fun startTimer(durationInMillis: Long) {
    countDownTimer?.cancel()
    startForeground(NOTIFICATION_ID, createNotification(getString(R.string.sleep_timer_active)))

    countDownTimer =
      object : CountDownTimer(durationInMillis, 1000) {
          override fun onTick(millisUntilFinished: Long) {
            val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) + 1
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
            Log.d(TAG, "Timer Tick: ${String.format("%02d:%02d", minutes, seconds)} remaining.")
            val notificationText =
              resources.getQuantityString(
                R.plurals.timer_notification_subtitle,
                minutesLeft.toInt(),
                minutesLeft.toInt(),
              )
            updateNotification(notificationText)
          }

          override fun onFinish() {
            Log.d(TAG, "Timer finished. Sending media PAUSE command.")
            sendMediaPauseCommand()
            stopTimerAndService()
          }
        }
        .start()
  }

  private fun stopTimerAndService() {
    countDownTimer?.cancel()
    currentTimerMinutes = 0
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    requestTileUpdate(this)
  }

  private fun sendMediaPauseCommand() {
    val eventTime = System.currentTimeMillis()
    val downEvent =
      KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
    audioManager.dispatchMediaKeyEvent(downEvent)
    val upEvent =
      KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
    audioManager.dispatchMediaKeyEvent(upEvent)
  }

  private fun createNotification(text: String): Notification {
    val notificationIntent = Intent(this, MainActivity::class.java)
    val pendingIntent =
      PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

    val stopIntent = Intent(this, SleepTimerService::class.java).apply { action = ACTION_STOP }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        0,
        stopIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.sleep_timer_active))
      .setContentText(text)
      .setSmallIcon(R.drawable.ic_timer_on)
      .setContentIntent(pendingIntent)
      .addAction(R.drawable.ic_timer_off, getString(R.string.timer_stop), stopPendingIntent)
      .build()
  }

  private fun updateNotification(text: String) {
    val notification = createNotification(text)
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  private fun createNotificationChannel() {
    val serviceChannel =
      NotificationChannel(
        CHANNEL_ID,
        getString(R.string.timer_string),
        NotificationManager.IMPORTANCE_LOW,
      )
    getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
