package com.sourajitk.ambient_music

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sourajitk.ambient_music.ui.theme.AmbientMusicTheme

class MainActivity : ComponentActivity() {

  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
      if (isGranted) {
        Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(
            this,
            "Notification permission denied. Features may be limited.",
            Toast.LENGTH_LONG
          )
          .show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Basically, this is not a "real" notification, it's for MediaSession.
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    setContent { AmbientMusicTheme { MinimalAppScreen() } }
  }
}

@Composable
// This is basically the Main screen you see when you open the app.
// A better and more streamlined design will be implemented later.
fun MinimalAppScreen() {
  val context = LocalContext.current

  val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
  var showAddTileButton by remember {
    mutableStateOf(!sharedPrefs.getBoolean("tile_prompt_shown", false))
  }

  Scaffold { innerPadding ->
    Surface(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      color = MaterialTheme.colorScheme.background
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Image(
          painter = painterResource(id = R.mipmap.logo),
          contentDescription = null,
          modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(id = R.string.app_name) + " QS Tile",
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = stringResource(R.string.minimal_activity_info),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (showAddTileButton) {
          Button(
            onClick = {
              val statusBarManager = context.getSystemService(StatusBarManager::class.java)
              statusBarManager.requestAddTileService(
                ComponentName(context, MusicQSTileService::class.java),
                context.getString(R.string.qs_tile_main_label),
                Icon.createWithResource(context, R.drawable.ic_music_note),
                context.mainExecutor
              ) { result ->
                val message =
                  when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                      "Tile added successfully!"
                    // This is a case that can actually happen if you uninstall the app
                    // without removing the tile, let's take care of that too...
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                      "Tile was already added."
                    else -> "Tile not added."
                  }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
              }
              // Save that the prompt has been shown to not show it again
              sharedPrefs.edit { putBoolean("tile_prompt_shown", true) }
              // Don't badger the user to add the tile again
              showAddTileButton = false
            },
            modifier = Modifier.fillMaxWidth(0.8f)
          ) {
            Icon(
              imageVector = Icons.Filled.AddCircle,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text("Add Tile to Quick Settings")
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        FilledTonalButton(
          onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", context.packageName, null)
            intent.data = uri
            context.startActivity(intent)
          },
          modifier = Modifier.fillMaxWidth(0.8f)
        ) {
          Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
          )
          Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
          Text("Open App Settings")
        }
      }
    }
  }
}
