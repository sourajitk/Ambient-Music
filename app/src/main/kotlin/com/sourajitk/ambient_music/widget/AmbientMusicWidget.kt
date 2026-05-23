// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.playback.MusicPlaybackService

class AmbientMusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val isPlaying = MusicPlaybackService.isServiceCurrentlyPlaying
        val currentGenre = MusicPlaybackService.currentPlaylistGenre?.lowercase()

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
                .cornerRadius(24.dp)
                .padding(8.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Row 1: Sleep and Chill
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    GenreTile(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        title = "Sleep",
                        subtitle = "Ambient Bedtime",
                        genreKey = "sleep",
                        defaultIconRes = R.drawable.ic_sleep,
                        isActive = isPlaying && currentGenre == "sleep",
                        action = MusicPlaybackService.ACTION_PLAY_GENRE_SLEEP,
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    GenreTile(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        title = "Chill",
                        subtitle = "Laidback Lo-Fi",
                        genreKey = "chill",
                        defaultIconRes = R.drawable.ic_chill,
                        isActive = isPlaying && currentGenre == "chill",
                        action = MusicPlaybackService.ACTION_PLAY_GENRE_CHILL,
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                // Row 2: Productivity and Calm
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    GenreTile(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        title = "Productivity",
                        subtitle = "Momentum Beats",
                        genreKey = "focus",
                        defaultIconRes = R.drawable.ic_focus,
                        isActive = isPlaying && currentGenre == "focus",
                        action = MusicPlaybackService.ACTION_PLAY_GENRE_FOCUS,
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    GenreTile(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        title = "Calm",
                        subtitle = "Peaceful Mind",
                        genreKey = "calm",
                        defaultIconRes = R.drawable.ic_calm,
                        isActive = isPlaying && currentGenre == "calm",
                        action = MusicPlaybackService.ACTION_PLAY_GENRE_CALM,
                    )
                }
            }
        }
    }

    @Composable
    private fun GenreTile(
        modifier: GlanceModifier,
        title: String,
        subtitle: String,
        genreKey: String,
        defaultIconRes: Int,
        isActive: Boolean,
        action: String,
    ) {
        val context = LocalContext.current

        // If this genre is currently playing, the action should toggle (pause)
        val finalAction = if (isActive) MusicPlaybackService.ACTION_TOGGLE_PLAYBACK_QS else action

        val playIntent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = finalAction
            setPackage(context.packageName)
        }

        val albumArt = WidgetImageManager.getGenreImage(context, genreKey)
        val imageProvider = if (albumArt != null) {
            ImageProvider(albumArt)
        } else {
            ImageProvider(defaultIconRes)
        }

        // Outline logic: outer box with white background if active
        Box(
            modifier = modifier
                .background(if (isActive) Color.White else Color.Transparent)
                .cornerRadius(18.dp)
                .padding(if (isActive) 2.dp else 0.dp)
                .cornerRadius(if (isActive) 16.dp else 18.dp) // inner corners
                .background(Color(0xFF2C2C2E))
                .clickable(actionStartService(playIntent))
                .padding(10.dp),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Image(
                        provider = imageProvider,
                        contentDescription = title,
                        modifier = GlanceModifier.size(28.dp).cornerRadius(8.dp),
                        colorFilter = if (albumArt == null) ColorFilter.tint(ColorProvider(Color.White)) else null,
                    )
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = GlanceModifier.fillMaxWidth(),
                ) {
                    Image(
                        provider = ImageProvider(if (isActive) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = if (isActive) "Pause" else "Play",
                        modifier = GlanceModifier.size(10.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = if (isActive) "Pause " else "Play ",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFEBEBF5).copy(alpha = 0.6f)),
                            fontSize = 9.sp,
                        ),
                    )
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF918AFA)),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

class AmbientMusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AmbientMusicWidget()
}
