package com.sourajitk.ambient_music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sourajitk.ambient_music.ui.navigation.MainAppNavigation
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
            Toast.LENGTH_LONG,
          )
          .show()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Basically, this is not a "real" notification, it's for MediaSession.
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    setContent {
      AmbientMusicTheme() {
        MainAppNavigation() // Set the root to our new Navigation Composable
      }
    }
  }
}
