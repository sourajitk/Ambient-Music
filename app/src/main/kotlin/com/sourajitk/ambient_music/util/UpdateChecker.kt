// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sourajitk.ambient_music.BuildConfig
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease
import com.sourajitk.ambient_music.data.SongsRepoInitializer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateChecker {
  private val client = OkHttpClient()
  private val jsonParser = Json { ignoreUnknownKeys = true }

  suspend fun checkForUpdate(context: Context): GitHubRelease? {
    return withContext(Dispatchers.IO) {
      val apiUrl = context.getString(R.string.update_url)
      try {
        val request = Request.Builder().url(apiUrl).build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            Log.e("UpdateChecker", "Failed to fetch releases: ${response.code}")
            return@withContext null
          }
          val responseBody = response.body?.string() ?: return@withContext null
          val latestRelease = jsonParser.decodeFromString<GitHubRelease>(responseBody)

          val currentVersion = BuildConfig.VERSION_NAME
          val latestVersion = latestRelease.tag_name.removePrefix("v")
          Log.d(
            "UpdateChecker",
            "Current version: $currentVersion, Latest GitHub release: $latestVersion",
          )
          if (latestVersion > currentVersion) {
            Log.d("UpdateChecker", "New update found: ${latestRelease.tag_name}")
            return@withContext latestRelease
          } else {
            Log.d("UpdateChecker", "App is up to date.")
            return@withContext null
          }
        }
      } catch (e: Exception) {
        Log.e("UpdateChecker", "Error checking for update", e)
        return@withContext null
      }
    }
  }
}
