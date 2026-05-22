// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sourajitk.ambient_music.data.SongsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object GenreDownloader {
    data class DownloadStatus(
        val progress: Float = 0f,
        val completedFiles: Int = 0,
        val totalFiles: Int = 0,
    )

    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadingGenres = MutableStateFlow<Set<String>>(emptySet())
    val downloadingGenres: StateFlow<Set<String>> = _downloadingGenres.asStateFlow()

    private val _downloadedGenres = MutableStateFlow<Set<String>>(emptySet())
    val downloadedGenres: StateFlow<Set<String>> = _downloadedGenres.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadStatus>> = _downloadProgress.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>()
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    fun setDownloading(genre: String) {
        _downloadingGenres.value = _downloadingGenres.value + genre
        _downloadProgress.value = _downloadProgress.value + (genre to DownloadStatus())
    }

    fun clearDownloading(genre: String) {
        _downloadingGenres.value = _downloadingGenres.value - genre
    }

    fun setDownloaded(genre: String) {
        _downloadedGenres.value = _downloadedGenres.value + genre
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    fun loadDownloadedStates(context: Context, genres: List<String>) {
        scope.launch {
            val downloaded = genres.filter { SongsRepo.isGenreDownloaded(context, it) }.toSet()
            _downloadedGenres.value = downloaded
        }
    }

    fun stopDownload(context: Context, genre: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork("download_$genre")
    }

    fun toggleDownload(context: Context, genre: String) {
        val appContext = context.applicationContext
        if (_downloadingGenres.value.contains(genre)) return // Already downloading

        if (_downloadedGenres.value.contains(genre)) {
            // Delete
            scope.launch {
                SongsRepo.deleteGenreDownloads(appContext, genre)
                _downloadedGenres.value = _downloadedGenres.value - genre
                _downloadProgress.value = _downloadProgress.value - genre
            }
        } else {
            // Download
            if (!isInternetAvailable(appContext)) {
                scope.launch {
                    _errorMessages.emit("No internet connection available to start download.")
                }
                return
            }

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("genre" to genre))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag("download_$genre")
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "download_$genre",
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }
    }

    suspend fun downloadGenreFiles(context: Context, genre: String): Boolean {
        val genreSongs = SongsRepo.songs.filter { it.genre.equals(genre, ignoreCase = true) }
        val genreDir = File(context.filesDir, "offline_genres/$genre")
        if (!genreDir.exists()) {
            genreDir.mkdirs()
        }

        val albumArtUrl = genreSongs.firstOrNull()?.albumArtUrl
        if (albumArtUrl != null) {
            val artFile = File(genreDir, "album_art.jpg")
            if (!artFile.exists() || artFile.length() == 0L) {
                try {
                    val request = Request.Builder().url(albumArtUrl).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(artFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        var allSuccess = true
        val totalFiles = genreSongs.size
        var completedFiles = 0

        for (song in genreSongs) {
            if (!currentCoroutineContext().isActive) {
                allSuccess = false
                break
            }
            val fileName = song.url.substringAfterLast("/")
            val finalFile = File(genreDir, fileName)
            val partFile = File(genreDir, "$fileName.part")

            if (finalFile.exists() && finalFile.length() > 0) {
                completedFiles++
                _downloadProgress.value = _downloadProgress.value + (genre to DownloadStatus((completedFiles.toFloat() / totalFiles), completedFiles, totalFiles))
                continue // Already exists
            }

            try {
                var downloadedBytes = if (partFile.exists()) partFile.length() else 0L
                val requestBuilder = Request.Builder().url(song.url)
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
                }
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful || response.code == 206) {
                    val contentLength = response.body?.contentLength() ?: 1L
                    val totalExpectedBytes = downloadedBytes + (if (contentLength > 0) contentLength else 0L)

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(partFile, downloadedBytes > 0).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!currentCoroutineContext().isActive) break
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                val currentFileProgress = if (totalExpectedBytes > 0) downloadedBytes.toFloat() / totalExpectedBytes else 0f
                                val overallProgress = (completedFiles + currentFileProgress) / totalFiles
                                _downloadProgress.value = _downloadProgress.value + (genre to DownloadStatus(overallProgress, completedFiles, totalFiles))
                            }
                        }
                    }
                    if (!currentCoroutineContext().isActive) {
                        // Job was cancelled/stopped, do NOT delete part file so we can resume
                        allSuccess = false
                        break
                    }
                    // Finished this file successfully, rename to final file
                    if (partFile.renameTo(finalFile)) {
                        completedFiles++
                        _downloadProgress.value = _downloadProgress.value + (genre to DownloadStatus(completedFiles.toFloat() / totalFiles, completedFiles, totalFiles))
                    } else {
                        allSuccess = false
                        break
                    }
                } else {
                    allSuccess = false
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
                allSuccess = false
                if (currentCoroutineContext().isActive) {
                    _errorMessages.emit("Network interrupted while downloading $genre.")
                }
                break
            }
        }

        // Removed SongsRepo.deleteGenreDownloads on failure to allow resuming from partial files
        return allSuccess
    }
}
