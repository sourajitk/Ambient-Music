// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kyant.expressa.m3.LocalColorScheme
import com.kyant.expressa.m3.color.ColorScheme
import com.kyant.expressa.overscroll.OffsetOverscrollFactory
import com.kyant.expressa.prelude.onSurface
import com.kyant.expressa.prelude.surfaceContainer
import com.kyant.expressa.ripple.LocalRippleConfiguration
import com.kyant.expressa.ripple.RippleConfiguration
import com.kyant.expressa.ripple.ripple
import com.kyant.expressa.ui.LocalContentColor
import com.sourajitk.ambient_music.data.GitHubRelease
import com.sourajitk.ambient_music.ui.dialog.BatteryPermissionDialog
import com.sourajitk.ambient_music.ui.dialog.UpdateInfoDialog
import com.sourajitk.ambient_music.ui.navigation.MainContent
import com.sourajitk.ambient_music.ui.theme.AmbientMusicTheme
import com.sourajitk.ambient_music.util.InAppUpdateManager
import com.sourajitk.ambient_music.util.UpdateChecker

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

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  @SuppressLint("SourceLockedOrientationActivity")
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    setTheme(R.style.Theme_AmbientMusic)
    InAppUpdateManager.checkForUpdate(this)

    // Basically, this is not a "real" notification, it's for MediaSession.
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    setContent {
      AmbientMusicTheme {
        val context = LocalContext.current
        var updateInfo by remember { mutableStateOf<GitHubRelease?>(null) }
        val windowSizeClass = calculateWindowSizeClass(this)
        LaunchedEffect(key1 = true) {
          val update = UpdateChecker.checkForUpdate(context)
          update?.let { updateInfo = it }
        }
        var showBatteryDialog by remember { mutableStateOf(false) }
        val rippleConfiguration = remember {
          RippleConfiguration(
            rippleAlpha =
              RippleAlpha(
                hoveredAlpha = 2f * 0.08f,
                focusedAlpha = 2f * 0.10f,
                pressedAlpha = 2f * 0.10f,
                draggedAlpha = 2f * 0.16f,
              )
          )
        }
        val overscrollFactory =
          OffsetOverscrollFactory(
            orientation = Orientation.Vertical,
            animationScope = rememberCoroutineScope(),
          )

        CompositionLocalProvider(LocalColorScheme provides ColorScheme.systemDynamic()) {
          CompositionLocalProvider(
            LocalContentColor provides onSurface,
            LocalRippleConfiguration provides rippleConfiguration,
            LocalIndication provides ripple(),
            LocalOverscrollFactory provides overscrollFactory,
          ) {
            val view = LocalView.current
            val background = surfaceContainer
            LaunchedEffect(view, background) {
              view.rootView.background = background.toArgb().toDrawable()
            }
            LaunchedEffect(key1 = true) {
              val sharedPrefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
              // Only check for Android 14's API since it's the only version with the buggy
              // behavior.
              if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                  showBatteryDialog = true
                  sharedPrefs.edit { putBoolean("battery_prompt_shown", true) }
                }
              }
            }

            // Show the dialog if the state is true
            if (showBatteryDialog) {
              BatteryPermissionDialog(onDismissRequest = { showBatteryDialog = false })
            }
            // MainAppNavigation(windowSizeClass = windowSizeClass)
            MainContent(windowSizeClass = windowSizeClass)
            updateInfo?.let { release ->
              UpdateInfoDialog(releaseInfo = release, onDismissRequest = { updateInfo = null })
            }
          }
        }
      }
    }
  }
}
