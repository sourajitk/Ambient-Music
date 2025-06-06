package com.sourajitk.ambient_music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
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
  private var isPlaylistSet = false

  companion object {
    const val ACTION_TOGGLE_PLAYBACK_QS = "com.sourajitk.ambient_music.ACTION_TOGGLE_PLAYBACK_QS"
    const val ACTION_SKIP_TO_NEXT = "com.sourajitk.ambient_music.ACTION_SKIP_TO_NEXT"
    const val ACTION_STOP_SERVICE = "com.sourajitk.ambient_music.ACTION_STOP_SERVICE"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_CHANNEL_ID = "MusicPlaybackChannel"
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
        repeatMode = Player.REPEAT_MODE_ALL

        addListener(
          object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
              Log.d(TAG, "Player status changed ig: $isPlayingValue")
              isServiceCurrentlyPlaying = isPlayingValue
              updateNotification()
              requestTileUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
              Log.d(TAG, "Player.Listener.onPlaybackStateChanged: $playbackState")
              var performGeneralPostUpdate = true
              when (playbackState) {
                Player.STATE_IDLE -> {
                  if (isServiceCurrentlyPlaying) {
                    isServiceCurrentlyPlaying = false
                  }
                }
                Player.STATE_BUFFERING -> {
                  Log.d(TAG, "Apple servers are being slow (buffering)")
                  performGeneralPostUpdate = false
                }
                Player.STATE_ENDED -> {
                  Log.d(TAG, "Idling")
                }
                Player.STATE_READY -> {
                  Log.d(TAG, "Apple sent the data.")
                }
              }
              if (performGeneralPostUpdate) {
                updateNotification()
                requestTileUpdate()
              }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
              super.onMediaItemTransition(mediaItem, reason)
              val newIndex = this@apply.currentMediaItemIndex
              SongRepo.selectTrack(newIndex)
              Log.i(
                TAG,
                "CurrIndex: $newIndex Title: ${mediaItem?.mediaMetadata?.title} Reason: $reason"
              )
              updateNotification()
            }

            @OptIn(UnstableApi::class)
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
              super.onMediaMetadataChanged(mediaMetadata)
              Log.d(TAG, "Player.Listener.onMediaMetadataChanged: Title: ${mediaMetadata.title}")
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

    mediaSession = MediaSession.Builder(this, exoPlayer!!).setId("AmbientMusicMediaSession").build()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_TOGGLE_PLAYBACK_QS -> {
        if (SongRepo.songs.isEmpty()) {
          isServiceCurrentlyPlaying = false
          requestTileUpdate()
          stopSelf()
          return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        togglePlayback()
      }
      ACTION_SKIP_TO_NEXT -> {
        Log.i(TAG, "ACTION_SKIP_TO_NEXT received.")
        if (SongRepo.songs.isEmpty() || exoPlayer == null) {
          Log.w(TAG, "ACTION_SKIP_TO_NEXT: Get some songs lol rn null.")
          if (isServiceCurrentlyPlaying) exoPlayer?.stop() else requestTileUpdate()
        } else {
          startForeground(NOTIFICATION_ID, createNotification())
          exoPlayer?.seekToNextMediaItem()
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

  private fun prepareAndSetPlaylist() {
    Log.d(TAG, "prepareAndSetPlaylist called.")
    val allSongData = SongRepo.songs
    if (allSongData.isEmpty()) {
      Log.w(TAG, "Received nothing from JSON can't prepare playlist.")
      isServiceCurrentlyPlaying = false
      updateNotification()
      requestTileUpdate()
      isPlaylistSet = false
      return
    }

    val mediaItems =
      allSongData.map { songData ->
        MediaItem.Builder()
          .setUri(songData.url)
          .setMediaId(songData.url) // Use URL as a unique ID
          .setMediaMetadata(
            MediaMetadata.Builder().setTitle(songData.title).setArtist(songData.artist).build()
          )
          .build()
      }

    // Start from current index in repo
    val startIndex = SongRepo.currentTrackIndex
    exoPlayer?.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
    exoPlayer?.prepare()
    // Playback will be started by togglePlayback if it was called to initiate
    isPlaylistSet = true
    Log.i(TAG, "Playlist with ${mediaItems.size} items set. Starting at index $startIndex.")
  }

  private fun togglePlayback() {
    if (exoPlayer == null || mediaSession == null) {
      Log.e(TAG, "Null playback state. Re-initializing...")
      initializePlayerAndSession()
      if (exoPlayer == null || mediaSession == null) {
        Log.e(TAG, "Re-initialization failed. Aborting.")
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
      // If player is idle, or stopped, or playlist not set, (re)set the full playlist and start.
      if (
        exoPlayer!!.playbackState == Player.STATE_IDLE ||
          exoPlayer!!.playbackState == Player.STATE_ENDED ||
          !isPlaylistSet ||
          exoPlayer?.currentMediaItem == null
      ) {
        Log.d(
          TAG,
          "togglePlayback: Player idle, ended, or playlist not set. Preparing full playlist."
        )
        prepareAndSetPlaylist()
      }
      exoPlayer?.play()
      Log.d(TAG, "togglePlayback: Play command issued.")
    }
  }

  private fun stopPlaybackAndReleaseSession() {
    Log.d(TAG, "stopPlaybackAndReleaseSession called.")
    exoPlayer?.stop()
    exoPlayer?.clearMediaItems()
    isPlaylistSet = false
  }

  private fun createNotificationChannel() {
    val serviceChannel =
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Music Playback (Playlist)",
        NotificationManager.IMPORTANCE_LOW
      )
    serviceChannel.description = "Channel for background music playback with playlist controls"
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(serviceChannel)
    Log.d(TAG, "Notification channel created/verified: $NOTIFICATION_CHANNEL_ID")
  }

  @OptIn(UnstableApi::class)
  private fun createNotification(): Notification {
    Log.d(TAG, "createNotification. isServiceCurrentlyPlaying: $isServiceCurrentlyPlaying")
    val currentExoPlayerMediaItem = exoPlayer?.currentMediaItem
    val currentMediaMetadata = currentExoPlayerMediaItem?.mediaMetadata
    val songFromRepo = SongRepo.getCurrentSong() // Used as fallback / initial

    val title =
      currentMediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
        ?: songFromRepo?.title?.takeIf { it.isNotBlank() }
        // Unlikely case but kept this here cuzwhynot
        ?: getString(R.string.qs_tile_notification_title_unknown) // Fallback string

    val artist =
      currentMediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
        ?: songFromRepo?.artist?.takeIf { it.isNotBlank() }
        // Unlikely case but kept this here cuzwhynot
        ?: getString(R.string.qs_tile_notification_artist_unknown) // Fallback string

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
    }
    return builder.build()
  }

  // This function update the MediaSession() notif when we skip or go back tracks
  private fun updateNotification() {
    if (exoPlayer != null && mediaSession != null) {
      val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.notify(NOTIFICATION_ID, createNotification())
      Log.d(TAG, "Notification updated.")
    } else {
      Log.w(TAG, "updateNotification: Player or MediaSession is null.")
    }
  }

  private fun requestTileUpdate() {
    Log.d(TAG, "Update tile state currstate: $isServiceCurrentlyPlaying")
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
    isPlaylistSet = false
    Log.d(TAG, "MusicPlaybackService destroyed and resources released.")
  }
}
