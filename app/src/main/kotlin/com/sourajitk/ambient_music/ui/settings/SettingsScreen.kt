// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.ui.dialog.UpdateInfoDialog
import com.sourajitk.ambient_music.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed class UpdateCheckState {
  object Idle : UpdateCheckState()

  object Checking : UpdateCheckState()

  object UpToDate : UpdateCheckState()

  data class UpdateAvailable(val releaseInfo: GitHubRelease) : UpdateCheckState()
}

@Composable
fun SettingsScreen(snackbarHostState: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
  var isRefreshingLibrary by remember { mutableStateOf(false) }

  if (updateState is UpdateCheckState.UpdateAvailable) {
    UpdateInfoDialog(
      releaseInfo = (updateState as UpdateCheckState.UpdateAvailable).releaseInfo,
      onDismissRequest = { updateState = UpdateCheckState.Idle },
    )
  }
  LazyColumn {
    item { CategoryHeader("General") }
    // Opens App Info
    item {
      PreferenceItem(
        icon = Icons.Default.Settings,
        title = "Additional App Settings",
        summary = "Open system settings to manage permissions",
        onClick = {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          val uri = Uri.fromParts("package", context.packageName, null)
          intent.data = uri
          context.startActivity(intent)
        },
      )
    }
    // Refresh Song Library
    item {
      val summaryText =
        if (isRefreshingLibrary) "Fetching new songs..."
        else "Tap to clear cache and fetch latest songs"
      PreferenceItem(
        icon = Icons.Default.Refresh,
        title = "Refresh Song Library",
        summary = summaryText,
        onClick = {
          if (!isRefreshingLibrary) {
            scope.launch {
              isRefreshingLibrary = true
              var finalStatusMessage = "Refresh failed"
              try {
                coroutineScope {
                  // For better UX, sync summaryText and show.Snackbar
                  val refreshJob =
                    async(Dispatchers.IO) {
                      SongsRepo.initializeAndRefresh(context) { success, statusMessage ->
                        finalStatusMessage = statusMessage
                      }
                    }
                  // This delay is purely for UX purposes.
                  delay(1500)
                  refreshJob.await()
                }
              } finally {
                // Ensure the refresh state is set back to false for the message to change
                isRefreshingLibrary = false
                snackbarHostState.showSnackbar(message = finalStatusMessage)
              }
            }
          }
        },
        trailingContent = {
          if (isRefreshingLibrary) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
          }
        },
      )
    }
    item { CategoryHeader("About") }
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
    // Donate
    item {
      PreferenceItem(
        icon = Icons.Default.AttachMoney,
        title = "Donate",
        summary = "Support me for more projects to come!",
        onClick = {
          val url = "https://www.paypal.com/paypalme/androbotsdev"
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
    // Updater Preference w/ Logic
    item {
      val summaryText =
        when (updateState) {
          is UpdateCheckState.Checking -> "Checking for updates..."
          is UpdateCheckState.UpToDate -> "You have the latest version!"
          else -> "Tap to check for updates"
        }
      // Actual updater logic
      PreferenceItem(
        icon = Icons.Default.Sync,
        title = "Updates",
        summary = summaryText,
        onClick = {
          if (updateState !is UpdateCheckState.Checking) {
            scope.launch {
              updateState = UpdateCheckState.Checking
              // FOR UX PURPOSES :)
              delay(1500)
              val update = UpdateChecker.checkForUpdate(context)
              updateState =
                if (update != null) {
                  UpdateCheckState.UpdateAvailable(update)
                } else {
                  UpdateCheckState.UpToDate
                }
            }
          }
        },
        trailingContent = {
          if (updateState is UpdateCheckState.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
          }
        },
      )
    }
    // About Section
    item {
      val url = stringResource(R.string.github_latest_rel)
      PreferenceItem(
        icon = Icons.Default.Info,
        title = "Version",
        summary = stringResource(R.string.app_version),
        onClick = {
          val intent = Intent(Intent.ACTION_VIEW)
          intent.data = url.toUri()
          context.startActivity(intent)
        },
      )
    }
    // Hint Section
    item {
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "Hint: Tapping on some settings, open some links 😉",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
    }
  }
}

@Composable
private fun CategoryHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
  )
}
