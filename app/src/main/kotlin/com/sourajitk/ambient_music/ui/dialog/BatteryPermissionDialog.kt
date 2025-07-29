package com.sourajitk.ambient_music.ui.dialog

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sourajitk.ambient_music.R

@Composable
fun BatteryPermissionDialog(onDismissRequest: () -> Unit) {
  val context = LocalContext.current

  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = Modifier.widthIn(max = 420.dp),
    icon = {
      Icon(
        Icons.Default.Warning,
        contentDescription = "Warning",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.height(37.dp).width(27.dp),
      )
    },
    title = {
      Text(
        text = "Battery Optimization",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )
    },
    text = { Text(text = stringResource(R.string.battery_message), textAlign = TextAlign.Center) },
    confirmButton = {
      TextButton(
        onClick = {
          val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
          context.startActivity(intent)
          onDismissRequest()
        }
      ) {
        Text("Settings")
      }
    },
    dismissButton = { TextButton(onClick = onDismissRequest) { Text("Later") } },
  )
}
