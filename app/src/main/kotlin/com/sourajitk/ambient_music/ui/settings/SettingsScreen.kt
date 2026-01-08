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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

@SuppressLint("CoroutineCreationDuringComposition", "LocalContextGetResourceValueCall", "UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    Scaffold { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { CategoryHeader(stringResource(R.string.general_header)) }
            item {
                ExpressiveSettingsCard {
                    // Opens App Info
                    SettingsListItem(
                        icon = Icons.Default.Settings,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.additional_settings_title),
                        summary = stringResource(R.string.additional_settings_body),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", context.packageName, null)
                            intent.data = uri
                            context.startActivity(intent)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SettingsListItem(
                            icon = Icons.Default.Translate,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            title = stringResource(R.string.app_language_title),
                            summary = stringResource(R.string.app_language_body),
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                val uri = Uri.fromParts("package", context.packageName, null)
                                intent.data = uri
                                context.startActivity(intent)
                            },
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    // Refresh Song Library
                    val refreshSummary =
                        if (isRefreshingLibrary) {
                            stringResource(R.string.cache_clear_fetching)
                        } else {
                            stringResource(R.string.cache_clear_helper)
                        }
                    SettingsListItem(
                        icon = Icons.Default.Refresh,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.refresh_song_lib),
                        summary = refreshSummary,
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
                        trailingContent = {
                            if (isRefreshingLibrary) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        },
                    )
                }
            }

            item { CategoryHeader(stringResource(R.string.about_header_title)) }
            item {
                ExpressiveSettingsCard {
                    // Author
                    SettingsListItem(
                        icon = Icons.Default.Person,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.author_title),
                        summary = stringResource(R.string.app_author),
                        onClick = {
                            val url = "https://github.com/sourajitk/"
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = url.toUri()
                            context.startActivity(intent)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // Donate
                    SettingsListItem(
                        icon = Icons.Default.AttachMoney,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.donate_title),
                        summary = stringResource(R.string.donate_text),
                        onClick = {
                            val url = "https://www.paypal.com/paypalme/androbotsdev"
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = url.toUri()
                            context.startActivity(intent)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // Source Code
                    SettingsListItem(
                        icon = Icons.Default.Code,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.source_code_title),
                        summary = stringResource(R.string.source_code_body),
                        onClick = {
                            val url = "https://github.com/sourajitk/Ambient-Music"
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = url.toUri()
                            context.startActivity(intent)
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // Updater Preference w/ Logic
                    val updateSummary =
                        when (updateState) {
                            is UpdateCheckState.Checking -> stringResource(R.string.check_for_updates)
                            is UpdateCheckState.UpToDate -> stringResource(R.string.latest_version_helper)
                            else -> stringResource(R.string.check_for_updates_helper)
                        }
                    SettingsListItem(
                        icon = Icons.Default.Sync,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.updates),
                        summary = updateSummary,
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
                        trailingContent = {
                            if (updateState is UpdateCheckState.Checking) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(30.dp))
                            }
                        },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    // About Section
                    SettingsListItem(
                        icon = Icons.Default.Info,
                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        title = stringResource(R.string.version_helper),
                        summary = stringResource(R.string.app_version),
                        onClick = {
                            val url = context.getString(R.string.github_latest_rel)
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = url.toUri()
                            context.startActivity(intent)
                        },
                    )
                }
            }
            // Hint Section
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.hint_text),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsListItem(
    icon: ImageVector,
    iconContainerColor: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        trailingContent = trailingContent,
        // Color for the box surrounding the main content.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
    )
}
