// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.timer

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.tiles.SleepTimerQSTileService
import com.sourajitk.ambient_music.util.TileStateUtil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(windowSizeClass: WindowSizeClass) {
    val context = LocalContext.current
    val isExpandedScreen = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    val bannerModifier =
        if (isExpandedScreen) {
            Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(24.dp))
        } else {
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp))
        }
    var showInfoDialog by remember { mutableStateOf(false) }
    if (showInfoDialog) {
        SleepTimerInfoDialog(onDismiss = { showInfoDialog = false })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.sleep_timer),
                    contentDescription = "Welcome Banner",
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.timer_tile_add_helper),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(25.dp))
            }
            item {
                Card(modifier = bannerModifier, shape = RoundedCornerShape(32.dp)) {
                    Column(modifier = Modifier.padding(vertical = 24.dp, horizontal = 30.dp)) {
                        Text(
                            text = stringResource(R.string.tile_available),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            AddTileRow(
                                context,
                                stringResource(R.string.timer_string),
                                ComponentName(context, SleepTimerQSTileService::class.java),
                                R.drawable.ic_timer_off,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.incompatible_android_version),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                TextButton(onClick = { showInfoDialog = true }) {
                    Text(
                        text = "Learn more about the Sleep Timer.",
                        textDecoration = TextDecoration.Underline,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_sleep_timer)) },
        text = { Text(stringResource(R.string.sleep_timer_tile_add_helper)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sleep_time_info_close))
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AddTileRow(
    context: Context,
    tileName: String,
    tileComponent: ComponentName,
    tileIconRes: Int,
) {
    var isTileAdded by remember {
        mutableStateOf(TileStateUtil.isTileAdded(context, tileComponent.className))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = tileName, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            modifier = Modifier.width(70.dp),
            enabled = !isTileAdded,
            onClick = {
                // The API call itself is now safe because the button to trigger it
                // will only be shown on API 33+
                val statusBarManager =
                    context.getSystemService(StatusBarManager::class.java) as StatusBarManager
                statusBarManager.requestAddTileService(
                    tileComponent,
                    tileName,
                    Icon.createWithResource(context, tileIconRes),
                    context.mainExecutor,
                ) { result ->
                    val message: String
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        message = "'$tileName' tile added!"
                        isTileAdded = true
                    } else {
                        message = "Could not add '$tileName' tile."
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            if (isTileAdded) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "$tileName is added",
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            } else {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Add $tileName tile",
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            }
        }
    }
}
