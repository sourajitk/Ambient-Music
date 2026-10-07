// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.Context
import android.util.Log
import com.sourajitk.ambient_music.BuildConfig
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object UpdateChecker {
    private val client = OkHttpClient()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    // Compares "major.minor.patch" numerically, ignoring a "v" prefix and any "-suffix" such as the
    // commit hash in versionName. A plain string comparison gets "5.0.10" vs "5.0.9" wrong.
    private fun isNewerVersion(latest: String, current: String): Boolean {
        fun parse(version: String) = version.removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = parse(latest)
        val currentParts = parse(current)
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

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
                    val responseBody = response.body.string()
                    val latestRelease = jsonParser.decodeFromString<GitHubRelease>(responseBody)

                    val currentVersion = BuildConfig.VERSION_NAME
                    val latestVersion = latestRelease.tagName.removePrefix("v")
                    Log.d(
                        "UpdateChecker",
                        "Current version: $currentVersion, Latest GitHub release: $latestVersion",
                    )
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        Log.d("UpdateChecker", "New update found: ${latestRelease.tagName}")
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
