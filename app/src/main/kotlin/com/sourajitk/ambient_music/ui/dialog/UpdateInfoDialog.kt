// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.dialog

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sourajitk.ambient_music.BuildConfig
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease

@Composable
fun UpdateInfoDialog(releaseInfo: GitHubRelease, onDismissRequest: () -> Unit) {
  val context = LocalContext.current
  val currentVersion = BuildConfig.VERSION_NAME

  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = Modifier.widthIn(max = 300.dp),
    icon = { Icon(Icons.Default.Info, contentDescription = "Update Info") },
    title = {
      Text(
        text = "Update Available",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )
    },
    text = {
      Text(
        text = stringResource(R.string.update_available_text, releaseInfo.tagName, currentVersion),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(
        onClick = {
          val browserIntent = Intent(Intent.ACTION_VIEW, releaseInfo.htmlUrl.toUri())
          context.startActivity(browserIntent)
          onDismissRequest()
        }
      ) {
        Text("Go to GitHub")
      }
    },
    dismissButton = { TextButton(onClick = onDismissRequest) { Text("Later") } },
  )
}
