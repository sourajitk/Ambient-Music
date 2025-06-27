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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
  val showAddTilesSection by remember {
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

        // Add persistent prompts for all three tiles
        if (showAddTilesSection) {
          Card(modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp)) {
              Text(
                text = "Add Genre Tiles",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
              )
              Spacer(modifier = Modifier.height(20.dp))
              AddTileRow(
                context = context,
                tileName = "Calm",
                tileComponent = ComponentName(context, CalmQSTileService::class.java),
                tileIconRes = R.drawable.playlist_music // Use a specific icon
              )
              Spacer(modifier = Modifier.height(12.dp))
              AddTileRow(
                context = context,
                tileName = "Chill",
                tileComponent = ComponentName(context, ChillQSTileService::class.java),
                tileIconRes = R.drawable.playlist_music // Use a specific icon
              )
              Spacer(modifier = Modifier.height(12.dp))
              AddTileRow(
                context = context,
                tileName = "Sleep",
                tileComponent = ComponentName(context, SleepQSTileService::class.java),
                tileIconRes = R.drawable.playlist_music // Use a specific icon
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

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

@Composable
private fun AddTileRow(
  context: Context,
  tileName: String,
  tileComponent: ComponentName,
  tileIconRes: Int
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = tileName, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(
      onClick = {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
          tileComponent,
          tileName,
          Icon.createWithResource(context, tileIconRes),
          context.mainExecutor
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
        modifier = Modifier.size(ButtonDefaults.IconSize)
      )
      Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
      Text("Add")
    }
  }
}
