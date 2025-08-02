// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.util

import android.content.Context
import android.util.Log

object InstallSourceChecker {
  private const val PLAY_STORE_PACKAGE = "com.android.vending"

  /** Checks if the app was installed from the Google Play Store. */
  fun isFromPlayStore(context: Context): Boolean {
    return try {
      val installSourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
      installSourceInfo.installingPackageName == PLAY_STORE_PACKAGE
    } catch (e: Exception) {
      // If the package name can't be found or another error occurs, assume it's not from Play Store
      Log.e("AMInstallSource", "No Play Store detected $e")
      false
    }
  }
}
