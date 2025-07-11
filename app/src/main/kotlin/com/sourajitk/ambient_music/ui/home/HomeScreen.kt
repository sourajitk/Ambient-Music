// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.home

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.tiles.CalmQSTileService
import com.sourajitk.ambient_music.tiles.ChillQSTileService
import com.sourajitk.ambient_music.tiles.SleepQSTileService

@Composable
fun HomeScreen() {
  val context = LocalContext.current

  val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
  var showAddTilesSection by remember {
    mutableStateOf(!sharedPrefs.getBoolean("tile_prompt_shown", false))
  }

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.welcome_banner),
        contentDescription = "Welcome Banner",
        modifier =
          Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)),
        contentScale = ContentScale.FillWidth,
      )
      Spacer(modifier = Modifier.height(32.dp))
      Text(
        text = stringResource(R.string.minimal_activity_info),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(25.dp))
      if (showAddTilesSection) {
        Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(32.dp)) {
          Column(modifier = Modifier.padding(vertical = 27.dp, horizontal = 16.dp)) {
            Text(
              text = "Available Genre Tiles",
              style = MaterialTheme.typography.titleLarge,
              modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(20.dp))
            AddTileRow(
              context = context,
              tileName = stringResource(R.string.tile_label_calm),
              tileComponent = ComponentName(context, CalmQSTileService::class.java),
              tileIconRes = R.drawable.playlist_music,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AddTileRow(
              context = context,
              tileName = stringResource(R.string.tile_label_chill),
              tileComponent = ComponentName(context, ChillQSTileService::class.java),
              tileIconRes = R.drawable.playlist_music,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AddTileRow(
              context = context,
              tileName = stringResource(R.string.tile_label_sleep),
              tileComponent = ComponentName(context, SleepQSTileService::class.java),
              tileIconRes = R.drawable.playlist_music,
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun AddTileRow(
  context: Context,
  tileName: String,
  tileComponent: ComponentName,
  tileIconRes: Int,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = tileName, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(
      onClick = {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
          tileComponent,
          tileName,
          Icon.createWithResource(context, tileIconRes),
          context.mainExecutor,
        ) { result ->
          val message =
            when (result) {
              StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "'$tileName' tile added!"
              StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                "'$tileName' was already added."
              else -> "Could not add '$tileName' tile."
            }
          Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
      }
    ) {
      Icon(
        imageVector = Icons.Filled.AddCircle,
        contentDescription = "Add $tileName tile",
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
      Text("Add")
    }
  }
}
