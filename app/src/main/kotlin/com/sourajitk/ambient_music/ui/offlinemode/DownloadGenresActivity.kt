// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.offlinemode

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.data.offline.GenreDownloader
import com.sourajitk.ambient_music.ui.offlinemode.components.GenreDownloadCard
import com.sourajitk.ambient_music.ui.theme.AmbientMusicTheme
import java.util.Locale

class DownloadGenresActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setTheme(R.style.Theme_AmbientMusic)
        setContent {
            AmbientMusicTheme {
                DownloadGenresScreen(
                    onNavigateBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadGenresScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val genresWithArt = remember {
        SongsRepo.songs
            .filter { !it.genre.isNullOrEmpty() }
            .groupBy { it.genre!!.lowercase(Locale.ROOT) }
            .map { (genre, songs) ->
                val firstSong = songs.firstOrNull { it.albumArtUrl != null } ?: songs.first()
                genre to firstSong.albumArtUrl
            }
    }

    val downloadingGenres by GenreDownloader.downloadingGenres.collectAsState()
    val downloadedGenres by GenreDownloader.downloadedGenres.collectAsState()
    val downloadProgress by GenreDownloader.downloadProgress.collectAsState()

    LaunchedEffect(genresWithArt) {
        GenreDownloader.loadDownloadedStates(context, genresWithArt.map { it.first })
    }

    LaunchedEffect(Unit) {
        GenreDownloader.errorMessages.collect { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.download_genres_title)) },
                navigationIcon = {
                    FilledIconButton(
                        onClick = onNavigateBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(genresWithArt) { index, (genre, albumArtUrl) ->
                val isDownloading = downloadingGenres.contains(genre)
                val isDownloaded = downloadedGenres.contains(genre)
                val status = downloadProgress[genre] ?: GenreDownloader.DownloadStatus()

                val shape = when {
                    genresWithArt.size == 1 -> RoundedCornerShape(28.dp)
                    index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    index == genresWithArt.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                    else -> RoundedCornerShape(4.dp)
                }
                val localArtUri = remember(genre) {
                    SongsRepo.getLocalAlbumArtUri(context, genre)
                }
                val imageModel = localArtUri ?: albumArtUrl

                GenreDownloadCard(
                    genre = genre,
                    albumArtUrl = imageModel,
                    isDownloading = isDownloading,
                    isDownloaded = isDownloaded,
                    status = status,
                    shape = shape,
                    onToggleDownload = { GenreDownloader.toggleDownload(context, genre) },
                    onStopDownload = { GenreDownloader.stopDownload(context, genre) },
                )
            }
        }
    }
}
