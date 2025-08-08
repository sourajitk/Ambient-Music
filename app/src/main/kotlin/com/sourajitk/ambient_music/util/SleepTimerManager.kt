package com.sourajitk.ambient_music.util

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import com.sourajitk.ambient_music.playback.MusicPlaybackService

object SleepTimerManager {
  private var countDownTimer: CountDownTimer? = null

  // The static state for the timer now lives here
  @Volatile
  var sleepTimerMinutes: Int = 0
    private set

  fun start(context: Context, durationInMinutes: Int) {
    // If the new duration is 0, it's a cancel command
    if (durationInMinutes <= 0) {
      cancel(context)
      return
    }

    // Cancel any existing timer before starting a new one
    countDownTimer?.cancel()
    sleepTimerMinutes = durationInMinutes

    val durationInMillis = durationInMinutes * 60 * 1000L

    countDownTimer =
      object : CountDownTimer(durationInMillis, 1000) {
          override fun onTick(millisUntilFinished: Long) {
            // Broadcast the remaining time
            val updateIntent =
              Intent(MusicPlaybackService.ACTION_TIMER_TICK).apply {
                putExtra(MusicPlaybackService.EXTRA_TIME_REMAINING_MS, millisUntilFinished)
              }
            context.sendBroadcast(updateIntent)
          }

          override fun onFinish() {
            // When the timer finishes, send an intent to the service to pause playback
            val pauseIntent =
              Intent(context, MusicPlaybackService::class.java).apply {
                action = MusicPlaybackService.ACTION_TOGGLE_PLAYBACK_QS
              }
            context.startService(pauseIntent)

            // Reset state
            countDownTimer = null
            sleepTimerMinutes = 0
            TileStateUtil.requestTileUpdate(context)
          }
        }
        .start()

    TileStateUtil.requestTileUpdate(context)
  }

  fun cancel(context: Context) {
    countDownTimer?.cancel()
    countDownTimer = null
    sleepTimerMinutes = 0

    // Send a final broadcast to clear the UI countdown text
    val cancelIntent =
      Intent(MusicPlaybackService.ACTION_TIMER_TICK).apply {
        putExtra(MusicPlaybackService.EXTRA_TIME_REMAINING_MS, 0L)
      }
    context.sendBroadcast(cancelIntent)

    TileStateUtil.requestTileUpdate(context)
  }
}
