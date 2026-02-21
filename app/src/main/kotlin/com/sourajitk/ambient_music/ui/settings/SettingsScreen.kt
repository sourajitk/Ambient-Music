// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.data.GitHubRelease
import com.sourajitk.ambient_music.data.SongsRepo
import com.sourajitk.ambient_music.ui.dialog.UpdateInfoDialog
import com.sourajitk.ambient_music.util.InstallSourceChecker
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

@SuppressLint("CoroutineCreationDuringComposition", "LocalContextGetResourceValueCall", "UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isFromPlayStore = remember { InstallSourceChecker.isFromPlayStore(context) }

    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var isRefreshingLibrary by remember { mutableStateOf(false) }

    if (updateState is UpdateCheckState.UpdateAvailable) {
        UpdateInfoDialog(
            releaseInfo = (updateState as UpdateCheckState.UpdateAvailable).releaseInfo,
            onDismissRequest = { updateState = UpdateCheckState.Idle },
        )
    }

    Scaffold { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp, top = 0.dp, start = 16.dp, end = 16.dp),
        ) {
            item { CategoryHeader(stringResource(R.string.general_header)) }
            item {
                // Opens App Info
                SettingsScreenCard(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.additional_settings_title),
                    subtitle = stringResource(R.string.additional_settings_body),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent)
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    SettingsScreenCard(
                        icon = Icons.Default.Translate,
                        title = stringResource(R.string.app_language_title),
                        subtitle = stringResource(R.string.app_language_body),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        },
                    )
                }
                item { Spacer(modifier = Modifier.height(2.dp)) }
            }

            item {
                // Refresh Song Library
                val refreshSummary =
                    if (isRefreshingLibrary) {
                        stringResource(R.string.cache_clear_fetching)
                    } else {
                        stringResource(R.string.cache_clear_helper)
                    }
                SettingsScreenCard(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.refresh_song_lib),
                    subtitle = refreshSummary,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                    trailingContent = {
                        if (isRefreshingLibrary) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(30.dp))
                        }
                    },
                    onClick = {
                        if (!isRefreshingLibrary) {
                            scope.launch {
                                isRefreshingLibrary = true
                                var finalStatusMessage = context.getString(R.string.refresh_fail_detail)
                                try {
                                    coroutineScope {
                                        // For better UX, sync summaryText and show.Snackbar
                                        val refreshJob =
                                            async(Dispatchers.IO) {
                                                SongsRepo.initializeAndRefresh(context) { _, statusMessage ->
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
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item { CategoryHeader(stringResource(R.string.about_header_title)) }
            item {
                // Author
                SettingsScreenCard(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.author_title),
                    subtitle = stringResource(R.string.app_author),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    onClick = {
                        val url = "https://sourajitk.github.io/"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = url.toUri()
                        context.startActivity(intent)
                    },
                )
            }

            // Check if the user installed the app from Play Store, if not don't show them the rate
            // button. If they did install it from the Play Store, remove the updater button since
            // it is quite redundant as updates are managed by Play store directly.
            if (isFromPlayStore) {
                item { Spacer(modifier = Modifier.height(2.dp)) }

                item {
                    // Rate the app
                    SettingsScreenCard(
                        icon = Icons.Default.Star,
                        title = stringResource(R.string.rate_app_title),
                        subtitle = stringResource(R.string.rate_app_body),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        onClick = {
                            val packageName = context.packageName
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=$packageName".toUri(),
                            )
                            context.startActivity(marketIntent)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                // Donate
                SettingsScreenCard(
                    icon = Icons.Default.AttachMoney,
                    title = stringResource(R.string.donate_title),
                    subtitle = stringResource(R.string.donate_text),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        val url = "https://www.paypal.com/paypalme/androbotsdev"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = url.toUri()
                        context.startActivity(intent)
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                // Source Code
                SettingsScreenCard(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.source_code_title),
                    subtitle = stringResource(R.string.source_code_body),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        val url = "https://github.com/sourajitk/Ambient-Music"
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = url.toUri()
                        context.startActivity(intent)
                    },
                )
            }

            if (!isFromPlayStore) {
                item { Spacer(modifier = Modifier.height(2.dp)) }

                item {
                    // Updater Preference w/ Logic
                    val updateSummary =
                        when (updateState) {
                            is UpdateCheckState.Checking -> stringResource(R.string.check_for_updates)
                            is UpdateCheckState.UpToDate -> stringResource(R.string.latest_version_helper)
                            else -> stringResource(R.string.check_for_updates_helper)
                        }
                    SettingsScreenCard(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.updates),
                        subtitle = updateSummary,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        trailingContent = {
                            if (updateState is UpdateCheckState.Checking) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        },
                        onClick = {
                            if (updateState !is UpdateCheckState.Checking) {
                                scope.launch {
                                    updateState = UpdateCheckState.Checking
                                    // FOR UX PURPOSES :)
                                    delay(2500)
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
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                // About Section
                SettingsScreenCard(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.version_helper),
                    subtitle = stringResource(R.string.app_version),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                    onClick = {
                        val url = context.getString(R.string.github_latest_rel)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = url.toUri()
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
