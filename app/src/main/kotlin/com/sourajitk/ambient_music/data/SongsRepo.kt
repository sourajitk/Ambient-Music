// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class SongAsset(
  val url: String,
  val title: String,
  val artist: String,
  val albumArtUrl: String? = null,
  val genre: String? = null,
)

object SongsRepo {
  private const val TAG = "SongsRepoJSONHandler"
  private const val REMOTE_SONGS_URL = "https://downloads.statixos.com/.am-ms/songs.json"
  private const val LOCAL_CACHE_FILE_NAME = "songs_cache.json"

  @Volatile private var internalLoadedSongs: List<SongAsset> = emptyList()
  var currentTrackIndex = 0
    private set

  private val client = OkHttpClient()
  private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  // The initialization step has 2 basic steps: clearing the local cache and fetch from out remote.
  fun initializeAndRefresh(context: Context, onFinished: ((Boolean, String) -> Unit)? = null) {
    Log.d(TAG, "initializeAndRefresh: Starting song data refresh process...")
    CoroutineScope(Dispatchers.IO).launch {
      var finalStatusMessage: String
      var overallSuccess = false

      // Clear Local Cache
      Log.d(TAG, "initializeAndRefresh: Clearing local cache...")
      clearCache(context)

      // Attempt to fetch the JSON from remote
      Log.d(
        TAG,
        "initializeAndRefresh: Attempting to fetch songs from remote URL: $REMOTE_SONGS_URL",
      )
      try {
        val request = Request.Builder().url(REMOTE_SONGS_URL).build()
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val jsonString = response.body.string()
            val loggableJson =
              if (jsonString.length > 500) jsonString.substring(0, 500) + "..." else jsonString
            Log.d(
              TAG,
              "initializeAndRefresh: Successfully fetched JSON string from remote (snippet): $loggableJson",
            )

            val remoteSongs = jsonParser.decodeFromString<List<SongAsset>>(jsonString)
            synchronized(this@SongsRepo) {
              internalLoadedSongs = remoteSongs
              if (
                currentTrackIndex >= internalLoadedSongs.size && internalLoadedSongs.isNotEmpty()
              ) {
                currentTrackIndex = 0
              }
              // Save the newly fetched data to cache
              saveToCache(context, jsonString)
            }
            Log.i(
              TAG,
              "initializeAndRefresh: Successfully parsed and updated ${remoteSongs.size} songs from remote.",
            )
            finalStatusMessage = "Fetched ${remoteSongs.size} songs from remote."
            overallSuccess = true
          } else {
            val errorBody = response.body.string()
            Log.e(
              TAG,
              "initializeAndRefresh: Remote fetch failed: ${response.code} ${response.message}. Error body: $errorBody",
            )
            finalStatusMessage = "Remote fetch failed: ${response.code}."
          }
        }
      } catch (e: IOException) {
        Log.e(TAG, "initializeAndRefresh: IOException during remote fetch: ", e)
        finalStatusMessage = "Network error during fetch."
      } catch (e: SerializationException) {
        Log.e(TAG, "initializeAndRefresh: SerializationException during remote JSON parsing: ", e)
        finalStatusMessage = "Error parsing remote data."
      } catch (e: Exception) {
        Log.e(TAG, "initializeAndRefresh: Generic exception during remote fetch/parsing: ", e)
        finalStatusMessage = "Unexpected error during fetch."
      }

      Log.d(
        TAG,
        "initializeAndRefresh: Process finished. Songs loaded: ${internalLoadedSongs.size}. Final status: $finalStatusMessage",
      )
      withContext(Dispatchers.Main) { onFinished?.invoke(overallSuccess, finalStatusMessage) }
    }
  }

  private fun clearCache(context: Context) {
    try {
      val file = File(context.filesDir, LOCAL_CACHE_FILE_NAME)
      if (file.exists()) {
        if (file.delete()) {
          Log.i(TAG, "clearCache: Successfully deleted cache file: $LOCAL_CACHE_FILE_NAME")
        } else {
          Log.w(TAG, "clearCache: Failed to delete cache file: $LOCAL_CACHE_FILE_NAME")
        }
      } else {
        Log.d(TAG, "clearCache: Cache file did not exist, no need to delete.")
      }
    } catch (e: Exception) {
      Log.e(TAG, "clearCache: Error deleting cache file: ", e)
    }
  }

  private fun saveToCache(context: Context, jsonString: String) {
    try {
      val file = File(context.filesDir, LOCAL_CACHE_FILE_NAME)
      file.writeText(jsonString)
      Log.i(TAG, "saveToCache: Successfully saved songs to cache: $LOCAL_CACHE_FILE_NAME")
    } catch (e: IOException) {
      Log.e(TAG, "saveToCache: Error saving songs to cache: ", e)
    }
  }

  val songs: List<SongAsset>
    get() = synchronized(this@SongsRepo) { internalLoadedSongs }

  fun getCurrentSong(): SongAsset? {
    return synchronized(this@SongsRepo) { internalLoadedSongs.getOrNull(currentTrackIndex) }
  }

  fun selectTrack(index: Int) {
    synchronized(this@SongsRepo) {
      if (index >= 0 && index < internalLoadedSongs.size) {
        currentTrackIndex = index
      }
    }
  }
}
