// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.glance.appwidget.updateAll
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sourajitk.ambient_music.data.SongsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object WidgetImageManager {
    private const val TAG = "WidgetImageManager"
    private const val FOLDER_NAME = "widget_images"

    suspend fun refreshWidgetImages(context: Context) {
        val genres = listOf("sleep", "chill", "focus", "serenity", "calm")
        val imageLoader = ImageLoader(context)

        var anyChanged = false
        withContext(Dispatchers.IO) {
            val folder = File(context.filesDir, FOLDER_NAME)
            if (!folder.exists()) folder.mkdirs()

            val songs = SongsRepo.songs
            genres.forEach { genre ->
                val song = songs.find { it.genre.equals(genre, ignoreCase = true) }
                val url = song?.albumArtUrl
                if (url != null) {
                    if (downloadAndSaveImage(context, imageLoader, url, genre.lowercase())) {
                        anyChanged = true
                    }
                }
            }
        }

        if (anyChanged) {
            AmbientMusicWidget().updateAll(context)
        }
    }

    private suspend fun downloadAndSaveImage(
        context: Context,
        imageLoader: ImageLoader,
        url: String,
        genre: String,
    ): Boolean {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()

        val result = imageLoader.execute(request)
        if (result is SuccessResult) {
            val drawable = result.drawable
            val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                return saveBitmap(context, bitmap, genre)
            }
        }
        return false
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, genre: String): Boolean {
        try {
            val file = File(File(context.filesDir, FOLDER_NAME), "$genre.png")

            // Resize bitmap to avoid RemoteViews memory limit (max ~15MB total)
            // 200x200 is plenty for a small widget icon
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)

            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            Log.d(TAG, "Saved scaled image for genre: $genre")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image for genre: $genre", e)
        }
        return false
    }

    fun getGenreImage(context: Context, genre: String): Bitmap? = try {
        val file = File(File(context.filesDir, FOLDER_NAME), "${genre.lowercase()}.png")
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load image for genre: $genre", e)
        null
    }
}
