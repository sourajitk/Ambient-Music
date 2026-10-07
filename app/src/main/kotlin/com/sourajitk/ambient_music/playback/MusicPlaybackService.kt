// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import coil.ImageLoader
import coil.request.ImageRequest
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.SongAsset
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.util.TileStateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaLibraryService() {

    private var exoPlayer: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var isPlaylistSet = false

    private val becomingNoisyReceiver = BecomingNoisyReceiver()
    private val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private var isReceiverRegistered = false

    private var currentAlbumArt: Bitmap? = null
    private lateinit var imageLoader: ImageLoader

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isForegroundService = false

    companion object {
        const val ACTION_TOGGLE_PLAYBACK_QS = "com.sourajitk.ambient_music.ACTION_TOGGLE_PLAYBACK_QS"
        const val ACTION_SKIP_TO_NEXT = "com.sourajitk.ambient_music.ACTION_SKIP_TO_NEXT"
        const val ACTION_STOP_SERVICE = "com.sourajitk.ambient_music.ACTION_STOP_SERVICE"
        const val ACTION_PLAY_GENRE_CHILL = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_CHILL"
        const val ACTION_PLAY_GENRE_CALM = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_CALM"
        const val ACTION_PLAY_GENRE_SLEEP = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_SLEEP"
        const val ACTION_PLAY_GENRE_FOCUS = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_PRODUCTIVITY"
        const val ACTION_PLAY_GENRE_SERENITY = "com.sourajitk.ambient_music.ACTION_PLAY_GENRE_SERENITY"
        const val ACTION_START_IDLE = "com.sourajitk.ambient_music.ACTION_START_IDLE"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "MusicPlaybackChannel"
        private const val TAG = "MusicPlaybackService"
        private const val ROOT_ID = "ambient_music_root_id"
        private const val SUGGESTED_ROOT_ID = "suggested_root_id"
        private const val GENRE_ID_PREFIX = "genre_"

        // How long a browse request from Android Auto waits for the song list on a cold start.
        private const val SONGS_WAIT_TIMEOUT_MS = 5_000L

        @Volatile
        var isServiceCurrentlyPlaying: Boolean = false
            private set

        @Volatile
        var currentPlaylistGenre: String? = null
            private set
    }

    // It tells Media3 to use our manual updateNotification() instead of its internal manager.
    // This stops the MediaSession notification from being recreating every time along with
    // actually using our assets to override the default API provided bitmaps.
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        updateNotification()
    }

    // Override our implementation of mediaLibrarySession.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    // Handle a Noisy receiver (bluetooth disconnection, media from another source, etc.)
    // where the state of the current playback should change ideally to pause.
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
        createNotificationChannel()
        initializePlayerAndSession()

        // Android Auto caches browse results, so tell it whenever the song list changes
        // (e.g. the remote JSON finishes downloading after the first browse request).
        serviceScope.launch {
            SongsRepo.songsFlow.collect {
                val genreCount = buildGenreItems().size
                mediaLibrarySession?.notifyChildrenChanged(ROOT_ID, genreCount, null)
                mediaLibrarySession?.notifyChildrenChanged(SUGGESTED_ROOT_ID, genreCount, null)
                Log.d(TAG, "notifyChildrenChanged: $genreCount genres")
            }
        }
    }

    // Builds the browsable genre list: one playable item per genre, titled by its first song.
    private fun buildGenreItems(): List<MediaItem> = SongsRepo.songs
        .filter { !it.genre.isNullOrEmpty() }
        .distinctBy { it.genre?.lowercase() }
        .map { songData ->
            val genreName = songData.genre?.lowercase() ?: "unknown"
            MediaItem.Builder()
                .setMediaId("$GENRE_ID_PREFIX$genreName")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(songData.title)
                        .setArtist(songData.artist)
                        .setArtworkUri(songData.albumArtUrl?.toUri())
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build(),
                )
                .build()
        }

    private fun buildSongMediaItem(songData: SongAsset): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(songData.title)
            .setArtist(songData.artist)
        songData.albumArtUrl?.let { metadataBuilder.setArtworkUri(it.toUri()) }
        val mediaUri = SongsRepo.getLocalSongUri(this, songData)?.toString() ?: songData.url
        return MediaItem.Builder()
            .setMediaId(songData.url)
            .setUri(mediaUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    // Runs [block] once songs are available. On a cold start (e.g. a fresh install) the song list
    // may still be downloading, so wait briefly instead of answering with an empty result.
    private fun <T> withSongs(block: () -> T): ListenableFuture<T> {
        if (SongsRepo.songs.isNotEmpty()) return Futures.immediateFuture(block())
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                SongsRepo.awaitSongs(SONGS_WAIT_TIMEOUT_MS)
            } finally {
                future.set(block())
            }
        }
        return future
    }

    private fun resolveMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val resolvedItems = mutableListOf<MediaItem>()
        for (item in mediaItems) {
            if (item.mediaId.startsWith(GENRE_ID_PREFIX)) {
                val genre = item.mediaId.removePrefix(GENRE_ID_PREFIX)
                currentPlaylistGenre = genre
                isPlaylistSet = true
                val genreSongs = SongsRepo.songs.filter { it.genre.equals(genre, ignoreCase = true) }
                resolvedItems.addAll(genreSongs.shuffled().map { buildSongMediaItem(it) })
            } else {
                // Android Auto is trying to resume a specific song from Recents/For You.
                // We must find it in the repo and attach the URI!
                SongsRepo.songs.find { it.url == item.mediaId }?.let { resolvedItems.add(buildSongMediaItem(it)) }
            }
        }
        return resolvedItems
    }

    private fun initializePlayerAndSession() {
        Log.d(TAG, "initializePlayerAndSession")
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                        Log.d(TAG, "Player status changed: $isPlayingValue")
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
                        TileStateUtil.requestTileUpdate(applicationContext)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                            updateNotification()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isServiceCurrentlyPlaying = false
                        updateNotification()
                        TileStateUtil.requestTileUpdate(applicationContext)
                    }
                })
            }

        val callback = object : MediaLibrarySession.Callback {
            // Triggered when Android Auto attempts to connect.
            // We grant permissions for browsing and subscribing to the media library.
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                    .add(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)
                    .build()
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    connectionResult.availablePlayerCommands,
                )
            }

            // Provides the root folder for the media browser.
            // This is the "home" folder that Android Auto first looks for.
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> {
                // Catch both "Suggested" and "Recent" queries from Android Auto
                val isSuggestedOrRecent = (params?.isSuggested == true) || (params?.isRecent == true)
                val rootIdToReturn = if (isSuggestedOrRecent) SUGGESTED_ROOT_ID else ROOT_ID
                val rootTitle = if (isSuggestedOrRecent) "For You Recommendations" else "Ambient Music"
                val rootItem = MediaItem.Builder()
                    .setMediaId(rootIdToReturn)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setTitle(rootTitle)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build(),
                    )
                    .build()

                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            // Returns the genre list for both the main root and the "suggested" root.
            @OptIn(UnstableApi::class)
            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                if (parentId != ROOT_ID && parentId != SUGGESTED_ROOT_ID) {
                    return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                }
                // Wrap items in ImmutableList.copyOf to satisfy Media3 requirement and fix type inference error
                return withSongs { LibraryResult.ofItemList(ImmutableList.copyOf(buildGenreItems()), params) }
            }

            // Resolves a single item by ID, used by Android Auto for resumption and search results.
            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String,
            ): ListenableFuture<LibraryResult<MediaItem>> = withSongs {
                val item = if (mediaId.startsWith(GENRE_ID_PREFIX)) {
                    buildGenreItems().find { it.mediaId == mediaId }
                } else {
                    SongsRepo.songs.find { it.url == mediaId }?.let { buildSongMediaItem(it) }
                }
                item?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
            ): ListenableFuture<List<MediaItem>> = withSongs { resolveMediaItems(mediaItems) }
        }
        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer!!, callback)
            .setId("AmbientMusicMediaSession")
            .build()
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
                promoteToForeground(createNotification())
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
                    promoteToForeground(createNotification())
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
                promoteToForeground(createNotification())
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
                promoteToForeground(createNotification())
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
                promoteToForeground(createNotification())
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
                promoteToForeground(createNotification())
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
                promoteToForeground(createNotification())
                playGenre("serenity")
            }

            ACTION_START_IDLE -> {
                promoteToForeground(createNotification())
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Finally, implement a genre picker so we can check against what genre the tile calls for and
     * serve it exactly what is wants. Usage is present in onStartCommand().
     */
    private fun playGenre(genre: String) {
        Log.d(TAG, "Playing genre: $genre")
        currentPlaylistGenre = genre // Set the current genre

        // Update the widget immediately to show the "Loading/Active" state
        TileStateUtil.requestTileUpdate(applicationContext)

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
                val localArtUri = SongsRepo.getLocalAlbumArtUri(this@MusicPlaybackService, songData.genre ?: "")
                if (localArtUri != null) {
                    metadataBuilder.setArtworkUri(localArtUri)
                } else if (songData.albumArtUrl != null) {
                    try {
                        metadataBuilder.setArtworkUri(songData.albumArtUrl.toUri())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse album art URI: ${songData.albumArtUrl}", e)
                    }
                }
                val mediaUri = SongsRepo.getLocalSongUri(this@MusicPlaybackService, songData)?.toString() ?: songData.url
                MediaItem.Builder()
                    .setUri(mediaUri)
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

                val localArtUri = SongsRepo.getLocalAlbumArtUri(this@MusicPlaybackService, songData.genre ?: "")
                if (localArtUri != null) {
                    metadataBuilder.setArtworkUri(localArtUri)
                } else if (songData.albumArtUrl != null) {
                    try {
                        metadataBuilder.setArtworkUri(songData.albumArtUrl.toUri())
                    } catch (e: Exception) {
                        // Catch potential errors if the URL string is malformed
                        Log.e(TAG, "Failed to parse album art URI: ${songData.albumArtUrl}", e)
                    }
                }

                val mediaUri = SongsRepo.getLocalSongUri(this@MusicPlaybackService, songData)?.toString() ?: songData.url
                MediaItem.Builder()
                    .setUri(mediaUri)
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
            exoPlayer?.play()
            Log.d(TAG, "Playing the audio file.")
        }
    }

    private fun stopPlaybackAndReleaseSession() {
        Log.d(TAG, "stopPlaybackAndReleaseSession called.")
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
                .setOngoing(isServiceCurrentlyPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        mediaLibrarySession?.let { session ->
            val mediaStyle =
                MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1)
            builder.setStyle(mediaStyle)
        }
        return builder.build()
    }

    // Only called from paths where starting a foreground service is allowed (startForegroundService
    // intents from tiles/widget, or playback just started).
    private fun promoteToForeground(notification: Notification) {
        startForeground(NOTIFICATION_ID, notification)
        isForegroundService = true
    }

    // Foreground while playing; when paused, drop foreground status but keep the notification so
    // playback can be resumed. Since Media3's own notification handling is bypassed through
    // onUpdateNotification(), this also has to guard against Android 12+ refusing to start a
    // foreground service while the app is in the background.
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (isServiceCurrentlyPlaying) {
            if (isForegroundService) {
                notificationManager.notify(NOTIFICATION_ID, notification)
                return
            }
            try {
                // A service that is only bound (e.g. by Android Auto) must also be started, or it
                // is destroyed as soon as the controller unbinds.
                startForegroundService(Intent(this, MusicPlaybackService::class.java))
                promoteToForeground(notification)
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException extends IllegalStateException.
                Log.w(TAG, "updateNotification: Not allowed to start foreground service.", e)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (isForegroundService) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForegroundService = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service destroying.")
        stopPlaybackAndReleaseSession()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        exoPlayer?.release()
        exoPlayer = null
        serviceScope.cancel()
        isServiceCurrentlyPlaying = false
        isPlaylistSet = false
        isForegroundService = false
        // The paused notification is detached from the service, so remove it explicitly. This has
        // to run after the player is stopped, since stopping triggers a final updateNotification().
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        Log.d(TAG, "MusicPlaybackService destroyed and resources released.")
    }
}
