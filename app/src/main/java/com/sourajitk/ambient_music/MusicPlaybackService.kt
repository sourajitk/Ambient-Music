package com.sourajitk.ambient_music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper

class MusicPlaybackService : Service() {

  private var exoPlayer: ExoPlayer? = null
  private var mediaSession: MediaSession? = null

  companion object {
    const val ACTION_TOGGLE_PLAYBACK_QS = "com.sourajitk.ambient_music.ACTION_TOGGLE_PLAYBACK_QS"
    const val ACTION_SKIP_TO_NEXT = "com.sourajitk.ambient_music.ACTION_SKIP_TO_NEXT"
    const val ACTION_STOP_SERVICE = "com.sourajitk.ambient_music.ACTION_STOP_SERVICE"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL_ID = "MusicPlaybackChannel_API33_Media_V4"
    private const val TAG = "MusicPlaybackService"

    @Volatile
    var isServiceCurrentlyPlaying: Boolean = false
      private set
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "onCreate: Service creating.")
    initializePlayerAndSession()
    createNotificationChannel()
  }

  private fun initializePlayerAndSession() {
    Log.d(TAG, "initializePlayerAndSession")
    exoPlayer =
      ExoPlayer.Builder(this).build().apply {
        addListener(
          object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
              isServiceCurrentlyPlaying = isPlayingValue
              updateNotification()
              requestTileUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
              Log.d(TAG, "Player.Listener.onPlaybackStateChanged: $playbackState")
              var performGeneralPostUpdate = true

              when (playbackState) {
                Player.STATE_ENDED -> {
                  Log.i(TAG, "Time for the next song now")
                  playNextSong(fromUserAction = false)
                  performGeneralPostUpdate = false
                }
                Player.STATE_IDLE -> {
                  if (isServiceCurrentlyPlaying) {
                    isServiceCurrentlyPlaying = false
                  }
                }
                Player.STATE_BUFFERING -> Log.d(TAG, "Buffering the next song")
                Player.STATE_READY -> Log.d(TAG, "Finished buffering")
              }
              if (performGeneralPostUpdate) {
                updateNotification()
                requestTileUpdate()
              }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
              super.onMediaMetadataChanged(mediaMetadata)
              Log.d(TAG, "Player.Listener.onMediaMetadataChanged:")
              Log.d(TAG, "Title: ${mediaMetadata.title}, Artist: ${mediaMetadata.artist}")
              updateNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
              super.onMediaItemTransition(mediaItem, reason)
              Log.d(TAG, "Player.Listener.onMediaItemTransition: New media item. Reason: $reason")
              updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
              Log.e(TAG, "Player.Listener.ExoPlayer Error: ", error)
              isServiceCurrentlyPlaying = false
              updateNotification()
              requestTileUpdate()
            }
          }
        )
      }

    mediaSession =
      MediaSession.Builder(this, exoPlayer!!)
        .setId("AmbientMusicMediaSession_V_NextBtn") // Unique ID
        .build()
    Log.d(TAG, "MediaSession initialized with callback.")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "onStartCommand received action: ${intent?.action}")
    when (intent?.action) {
      ACTION_TOGGLE_PLAYBACK_QS -> {
        if (SongRepo.songs.isEmpty()) {
          Log.w(TAG, "SongRepo is empty. Cannot play via QS toggle.")
          isServiceCurrentlyPlaying = false
          requestTileUpdate()
          stopSelf()
          return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        togglePlayback()
      }
      ACTION_SKIP_TO_NEXT -> {
        Log.i(TAG, "onStartCommand: ACTION_SKIP_TO_NEXT received.")
        if (SongRepo.songs.isEmpty()) {
          Log.w(TAG, "ACTION_SKIP_TO_NEXT: SongRepo is empty.")
          if (isServiceCurrentlyPlaying) exoPlayer?.stop() else requestTileUpdate()
        } else {
          startForeground(NOTIFICATION_ID, createNotification()) // Ensure service is foreground
          playNextSong(fromUserAction = true)
        }
      }
      ACTION_STOP_SERVICE -> {
        stopPlaybackAndReleaseSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
    return START_STICKY
  }

  private fun togglePlayback() {
    if (exoPlayer == null || mediaSession == null) {
      Log.e(TAG, "togglePlayback: Player or MediaSession is null. Attempting re-initialization.")
      initializePlayerAndSession()
      if (exoPlayer == null || mediaSession == null) {
        Log.e(TAG, "togglePlayback: Re-initialization failed. Aborting.")
        isServiceCurrentlyPlaying = false
        requestTileUpdate()
        stopSelf()
        return
      }
    }

    val playerIsCurrentlyPlaying = exoPlayer!!.isPlaying
    Log.d(TAG, "togglePlayback: Player is currently playing = $playerIsCurrentlyPlaying")

    if (playerIsCurrentlyPlaying) {
      exoPlayer?.pause()
      Log.d(TAG, "togglePlayback: Pause command issued.")
    } else {
      val currentPlaybackState = exoPlayer!!.playbackState
      val currentSongData = SongRepo.getCurrentSong()

      if (currentSongData == null) {
        Log.w(TAG, "No song data available from SongRepo for playback.")
        isServiceCurrentlyPlaying = false
        requestTileUpdate()
        return
      }

      if (
        currentPlaybackState == Player.STATE_IDLE ||
          currentPlaybackState == Player.STATE_ENDED ||
          exoPlayer?.currentMediaItem?.mediaId != currentSongData.url
      ) {
        Log.d(
          TAG,
          "togglePlayback: Preparing MediaItem for URL: ${currentSongData.url} (Title: ${currentSongData.title})"
        )
        val mediaMetadata =
          MediaMetadata.Builder()
            .setTitle(currentSongData.title)
            .setArtist(currentSongData.artist)
            .build()

        val mediaItem =
          MediaItem.Builder()
            .setUri(currentSongData.url)
            .setMediaId(currentSongData.url)
            .setMediaMetadata(mediaMetadata)
            .build()

        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        Log.d(TAG, "togglePlayback: MediaItem set and prepare() called.")
      }
      exoPlayer?.play()
      Log.d(TAG, "togglePlayback: Play command issued.")
    }
  }

  private fun playNextSong(fromUserAction: Boolean) {
    Log.i(TAG, "playNextSong called. From user action: $fromUserAction")
    if (exoPlayer == null) {
      Log.w(TAG, "playNextSong: ExoPlayer is null, cannot proceed.")
      return
    }

    if (SongRepo.songs.isEmpty()) {
      Log.w(TAG, "playNextSong: SongRepo is empty. Nothing to play next.")
      if (exoPlayer!!.isPlaying) exoPlayer?.stop()
      else { // If not playing, ensure state is consistent
        isServiceCurrentlyPlaying = false
        updateNotification()
        requestTileUpdate()
      }
      return
    }

    SongRepo.selectNextTrack()
    val nextSongData = SongRepo.getCurrentSong()

    if (nextSongData != null) {
      Log.i(TAG, "Loading next song via playNextSong: ${nextSongData.title}")
      val mediaMetadata =
        MediaMetadata.Builder().setTitle(nextSongData.title).setArtist(nextSongData.artist).build()
      val nextMediaItem =
        MediaItem.Builder()
          .setUri(nextSongData.url)
          .setMediaId(nextSongData.url)
          .setMediaMetadata(mediaMetadata)
          .build()

      exoPlayer?.setMediaItem(nextMediaItem)
      exoPlayer?.prepare()
      exoPlayer?.play() // Listeners will handle state updates
    } else {
      Log.w(TAG, "playNextSong: No next song data found after selectNextTrack. Stopping playback.")
      if (exoPlayer!!.isPlaying || isServiceCurrentlyPlaying) exoPlayer?.stop()
      else {
        isServiceCurrentlyPlaying = false
        updateNotification()
        requestTileUpdate()
      }
    }
  }

  private fun stopPlaybackAndReleaseSession() {
    Log.d(TAG, "stopPlaybackAndReleaseSession called.")
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
  }

  private fun createNotificationChannel() {
    val serviceChannel =
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Music Playback (Next Button)",
        NotificationManager.IMPORTANCE_LOW
      )
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(serviceChannel)
    Log.d(TAG, "Notification channel created/verified: $NOTIFICATION_CHANNEL_ID")
  }

  @OptIn(UnstableApi::class)
  private fun createNotification(): Notification {
    Log.d(
      TAG,
      "createNotification called. Effective playing state for UI: $isServiceCurrentlyPlaying"
    )
    val exoPlayerMetadata = exoPlayer?.mediaMetadata
    val songFromRepo = SongRepo.getCurrentSong()

    val title =
      exoPlayerMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
        ?: songFromRepo?.title?.takeIf { it.isNotBlank() }
    // ?: getString(R.string.qs_tile_notification_title_unknown)

    val artist =
      exoPlayerMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
        ?: songFromRepo?.artist?.takeIf { it.isNotBlank() }
    // ?: getString(R.string.qs_tile_notification_artist_unknown)

    val playerIsEffectivelyPlaying = isServiceCurrentlyPlaying

    val builder =
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(artist)
        .setSmallIcon(R.drawable.ic_music_note)
        .setOngoing(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    mediaSession?.let { session ->
      val mediaStyle =
        MediaStyleNotificationHelper.MediaStyle(session)
          .setShowActionsInCompactView(0, 1) // Index 0: Play/Pause, Index 1: Skip to Next
      builder.setStyle(mediaStyle)

      // Play/Pause Action (index 0 for compact view)
      val playPauseIcon =
        if (playerIsEffectivelyPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
      val playPauseTitle =
        if (playerIsEffectivelyPlaying) getString(R.string.qs_tile_label_pause)
        else getString(R.string.qs_tile_label_play)
      val playPauseActionIntent =
        Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAYBACK_QS }
      val playPausePendingIntent =
        PendingIntent.getService(
          this,
          101,
          playPauseActionIntent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      builder.addAction(
        NotificationCompat.Action(playPauseIcon, playPauseTitle, playPausePendingIntent)
      )

      // Skip to Next Action (index 1 for compact view)
      val skipToNextIntent =
        Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_SKIP_TO_NEXT }
      PendingIntent.getService(
        this,
        103, // Ensure unique request code
        skipToNextIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )

      val mainActivityIntent =
        Intent(this, MainActivity::class.java) // Or your MinimalEntryActivity
      val contentPendingIntent =
        PendingIntent.getActivity(
          this,
          102,
          mainActivityIntent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      builder.setContentIntent(contentPendingIntent)
    }
    return builder.build()
  }

  private fun updateNotification() {
    if (exoPlayer != null && mediaSession != null) {
      val notificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.notify(NOTIFICATION_ID, createNotification())
      Log.d(TAG, "Notification updated.")
    } else {
      Log.w(TAG, "updateNotification: Player or MediaSession is null.")
    }
  }

  private fun requestTileUpdate() {
    Log.d(
      TAG,
      "Requesting QS Tile update. Current service playing state: $isServiceCurrentlyPlaying"
    )
    TileService.requestListeningState(this, ComponentName(this, MusicQSTileService::class.java))
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "onDestroy: Service destroying.")
    stopPlaybackAndReleaseSession()
    mediaSession?.release()
    mediaSession = null
    exoPlayer?.release()
    exoPlayer = null
    isServiceCurrentlyPlaying = false
    Log.d(TAG, "MusicPlaybackService destroyed and resources released.")
  }
}
