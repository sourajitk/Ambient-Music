// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import coil.ImageLoader
import coil.request.ImageRequest
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.util.TileStateUtil

class MusicPlaybackService :
    Service(),
    AudioManager.OnAudioFocusChangeListener {

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlaylistSet = false

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var wasPausedByFocusLoss = false

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var isReceiverRegistered = false

    private var currentAlbumArt: Bitmap? = null
    private lateinit var imageLoader: ImageLoader

    companion object {
        const val ACTION_TOGGLE_PLAYBACK_QS = "com.sourajitk.ambient_music.ACTION_TOGGLE_PLAYBACK_QS"
        const val ACTION_SKIP_TO_NEXT = "com.sourajitk.ambient_music.ACTION_SKIP_TO_NEXT"
        const val ACTION_STOP_SERVICE = "com.sourajitk.ambient_music.ACTION_STOP_SERVICE"
        const val ACTION_PLAY_GENRE_CHILL = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_CHILL"
        const val ACTION_PLAY_GENRE_CALM = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_CALM"
        const val ACTION_PLAY_GENRE_SLEEP = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_SLEEP"
        const val ACTION_PLAY_GENRE_FOCUS = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_PRODUCTIVITY"
        const val ACTION_PLAY_GENRE_SERENITY = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_SERENITY"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "MusicPlaybackChannel"
        private const val TAG = "MusicPlaybackService"

        @Volatile
        var isServiceCurrentlyPlaying: Boolean = false
            private set

        @Volatile
        var currentPlaylistGenre: String? = null
            private set
    }

    // BroadcastReceiver class to handle the event
    private inner class BecomingNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isServiceCurrentlyPlaying) {
                // Pause playback when audio output changes
                exoPlayer?.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service creating.")
        imageLoader = ImageLoader(this)
        initializePlayerAndSession()
        initializeAudioFocus()
        createNotificationChannel()
    }

    private fun initializeAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        audioFocusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPausedByFocusLoss) {
                    exoPlayer?.play()
                    wasPausedByFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (exoPlayer?.isPlaying == true) {
                    exoPlayer?.pause()
                }
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                if (exoPlayer?.isPlaying == true) {
                    wasPausedByFocusLoss = true
                    exoPlayer?.pause()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
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
                            if (isPlayingValue) {
                                if (!isReceiverRegistered) {
                                    registerReceiver(becomingNoisyReceiver, intentFilter)
                                    isReceiverRegistered = true
                                }
                            } else {
                                if (isReceiverRegistered) {
                                    unregisterReceiver(becomingNoisyReceiver)
                                    isReceiverRegistered = false
                                }
                            }
                            updateNotification()
                            TileStateUtil.requestTileUpdate(applicationContext)
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
                                    Log.d(TAG, "Buffering media")
                                    performGeneralPostUpdate = false
                                }
                                Player.STATE_ENDED -> {
                                    Log.d(TAG, "Idling")
                                }
                                Player.STATE_READY -> {
                                    Log.d(TAG, "Data Received.")
                                }
                            }
                            if (performGeneralPostUpdate) {
                                updateNotification()
                                TileStateUtil.requestTileUpdate(applicationContext)
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            super.onMediaItemTransition(mediaItem, reason)
                            val newIndex = this@apply.currentMediaItemIndex
                            SongsRepo.selectTrack(newIndex)
                            Log.i(
                                TAG,
                                "Current Index: $newIndex Title: ${mediaItem?.mediaMetadata?.title} Reason: $reason",
                            )

                            // Clear old art and fetch new art on transition
                            currentAlbumArt = null
                            mediaItem?.mediaMetadata?.artworkUri?.let { fetchArtworkAsync(it) }
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
                            TileStateUtil.requestTileUpdate(applicationContext)
                        }
                    },
                )
            }

        mediaSession = MediaSession.Builder(this, exoPlayer!!).setId("AmbientMusicMediaSession").build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK_QS -> {
                if (SongsRepo.songs.isEmpty()) {
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                togglePlayback()
            }
            ACTION_SKIP_TO_NEXT -> {
                Log.i(TAG, "ACTION_SKIP_TO_NEXT received.")
                if (SongsRepo.songs.isEmpty() || exoPlayer == null) {
                    Log.w(TAG, "ACTION_SKIP_TO_NEXT: Get some songs lol rn null.")
                    if (isServiceCurrentlyPlaying) {
                        exoPlayer?.stop()
                    } else {
                        TileStateUtil.requestTileUpdate(applicationContext)
                    }
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
            ACTION_PLAY_GENRE_CHILL -> {
                if (SongsRepo.songs.isEmpty()) {
                    Log.w(TAG, "SongsRepo:Genre is empty.")
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                playGenre("chill")
            }
            ACTION_PLAY_GENRE_CALM -> {
                if (SongsRepo.songs.isEmpty()) {
                    Log.w(TAG, "SongsRepo:Genre is empty.")
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                playGenre("calm")
            }
            ACTION_PLAY_GENRE_SLEEP -> {
                if (SongsRepo.songs.isEmpty()) {
                    Log.w(TAG, "SongsRepo:Genre is empty.")
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                playGenre("sleep")
            }
            ACTION_PLAY_GENRE_FOCUS -> {
                if (SongsRepo.songs.isEmpty()) {
                    Log.w(TAG, "SongsRepo:Genre is empty.")
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                playGenre("focus")
            }
            ACTION_PLAY_GENRE_SERENITY -> {
                if (SongsRepo.songs.isEmpty()) {
                    Log.w(TAG, "SongsRepo:Genre is empty.")
                    isServiceCurrentlyPlaying = false
                    TileStateUtil.requestTileUpdate(applicationContext)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, createNotification())
                playGenre("serenity")
            }
        }
        return START_STICKY
    }

    /**
     * Finally, implement a genre picker so we can check against what genre the tile calls for and
     * serve it exactly what is wants. Usage is present in onStartCommand().
     */
    private fun playGenre(genre: String) {
        if (requestAudioFocus()) {
            Log.d(TAG, "Playing genre: $genre")
            currentPlaylistGenre = genre // Set the current genre

            // Avoid mixing up genres regardless of playState and which tile is being clicked.
            val genreSongs = SongsRepo.songs.filter { it.genre.equals(genre, ignoreCase = true) }
            if (genreSongs.isEmpty()) {
                Log.w(TAG, "No songs found for genre: $genre")
                return
            }

            val mediaItems =
                genreSongs.map { songData ->
                    val metadataBuilder =
                        MediaMetadata.Builder().setTitle(songData.title).setArtist(songData.artist)
                    songData.albumArtUrl?.let { metadataBuilder.setArtworkUri(it.toUri()) }
                    MediaItem.Builder()
                        .setUri(songData.url)
                        .setMediaId(songData.url)
                        .setMediaMetadata(metadataBuilder.build())
                        .build()
                }

            exoPlayer?.shuffleModeEnabled = true
            // Start from a random index within the filtered genre list
            val startIndex = genreSongs.indices.random()
            exoPlayer?.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            exoPlayer?.prepare()
            exoPlayer?.play()
            isPlaylistSet = true
        }
    }

    private fun prepareAndSetPlaylist(playRandom: Boolean = false) {
        Log.d(TAG, "prepareAndSetPlaylist called.")
        currentPlaylistGenre = null
        val allSongData = SongsRepo.songs
        if (allSongData.isEmpty()) {
            Log.w(TAG, "Received nothing from JSON can't prepare playlist.")
            isServiceCurrentlyPlaying = false
            updateNotification()
            TileStateUtil.requestTileUpdate(applicationContext)
            isPlaylistSet = false
            return
        }

        val mediaItems =
            allSongData.map { songData ->
                val metadataBuilder =
                    MediaMetadata.Builder().setTitle(songData.title).setArtist(songData.artist)

                if (songData.albumArtUrl != null) {
                    try {
                        metadataBuilder.setArtworkUri(songData.albumArtUrl.toUri())
                    } catch (e: Exception) {
                        // Catch potential errors if the URL string is malformed
                        Log.e(TAG, "Failed to parse album art URI: ${songData.albumArtUrl}", e)
                    }
                }

                MediaItem.Builder()
                    .setUri(songData.url)
                    .setMediaId(songData.url)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
            }

        // Enable shuffle by default
        exoPlayer?.shuffleModeEnabled = true

        // Start from current index in repo
        var startIndex = SongsRepo.currentTrackIndex
        if (playRandom && allSongData.isNotEmpty()) {
            startIndex = allSongData.indices.random()
            // Update SongsRepo so it's in sync
            SongsRepo.selectTrack(startIndex)
        }
        exoPlayer?.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        exoPlayer?.prepare()
        // Playback will be started by togglePlayback if it was called to initiate
        isPlaylistSet = true
        Log.i(TAG, "Playlist with ${mediaItems.size} items set. Starting at index $startIndex.")
    }

    private fun togglePlayback() {
        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer is null in togglePlayback. Aborting.")
            // Attempt recovery
            initializePlayerAndSession()
            prepareAndSetPlaylist()
            return
        }

        if (exoPlayer!!.isPlaying) {
            exoPlayer?.pause()
            Log.d(TAG, "togglePlayback: Pause command issued.")
        } else {
            if (requestAudioFocus()) {
                exoPlayer?.play()
                Log.d(TAG, "Playing the audio file.")
            }
        }
    }

    private fun stopPlaybackAndReleaseSession() {
        Log.d(TAG, "stopPlaybackAndReleaseSession called.")
        abandonAudioFocus()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        isPlaylistSet = false
        currentAlbumArt = null
        currentPlaylistGenre = null
    }

    // Fetch artwork from a URL asynchronously
    private fun fetchArtworkAsync(artworkUri: Uri) {
        val request =
            // Ngl using coil was interesting...
            ImageRequest.Builder(this)
                .data(artworkUri)
                .target(
                    onSuccess = { result: Drawable ->
                        currentAlbumArt = result.toBitmap()
                        // Artwork is loaded, update the notification again to show it
                        updateNotification()
                    },
                    onError = {
                        currentAlbumArt = null
                        updateNotification()
                    },
                )
                .build()
        imageLoader.enqueue(request)
    }

    private fun createNotificationChannel() {
        val serviceChannel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Playback (Playlist)",
                NotificationManager.IMPORTANCE_LOW,
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
        val songFromRepo = SongsRepo.getCurrentSong()

        val title =
            currentMediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
                ?: songFromRepo?.title
                ?: getString(R.string.qs_tile_notification_title_unknown)
        val artist =
            currentMediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                ?: songFromRepo?.artist
                ?: getString(R.string.qs_tile_notification_artist_unknown)

        val builder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(currentAlbumArt)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        mediaSession?.let { session ->
            val mediaStyle =
                MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1)
            builder.setStyle(mediaStyle)
        }
        return builder.build()
    }

    private fun updateNotification() {
        if (exoPlayer != null && mediaSession != null) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Notification updated.")
        } else {
            Log.w(TAG, "updateNotification: Player or MediaSession is null.")
        }
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
