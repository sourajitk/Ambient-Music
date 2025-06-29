package com.sourajitk.ambient_music.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.sourajitk.ambient_music.R

@Composable
fun SettingsScreen() {
  val context = LocalContext.current

  var isDarkMode by remember { mutableStateOf(false) }

  LazyColumn {
    item {
      PreferenceItem(
        icon = Icons.Default.Settings,
        title = "Addtional App Settings",
        summary = "Open system settings to manage permissions",
        onClick = {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          val uri = Uri.fromParts("package", context.packageName, null)
          intent.data = uri
          context.startActivity(intent)
        },
      )
    }
    // Dark Mode
    item {
      PreferenceItem(
        icon = Icons.Default.DarkMode,
        title = "Dark Mode",
        summary = "Enable dark theme throughout the app",
        onClick = { isDarkMode = !isDarkMode },
        trailingContent = { Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it }) },
      )
    }
    // Author
    item {
      PreferenceItem(
        icon = Icons.Default.Person,
        title = "Author",
        summary = "Sourajit Karmakar",
        onClick = {
          val url = "https://github.com/sourajitk/"
          val intent = Intent(Intent.ACTION_VIEW)
          intent.data = url.toUri()
          context.startActivity(intent)
        },
      )
    }
    // Source Code
    item {
      PreferenceItem(
        icon = Icons.Default.Code,
        title = "Source Code",
        summary = "View the project on GitHub",
        onClick = {
          val url = "https://github.com/sourajitk/Ambient-Music"
          val intent = Intent(Intent.ACTION_VIEW)
          intent.data = url.toUri()
          context.startActivity(intent)
        },
      )
    }
    // About Section
    item {
      PreferenceItem(
        icon = Icons.Default.Info,
        title = "Version",
        summary = stringResource(R.string.app_version),
        onClick = {
          val url = "https://github.com/sourajitk/Ambient-Music/releases/latest"
          val intent = Intent(Intent.ACTION_VIEW)
          intent.data = url.toUri()
          context.startActivity(intent)
        },
      )
    }
  }
}
