// SPDX-License-Identifier: MIT
// Copyright (c) 2025 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.ui.home.HomeScreen
import com.sourajitk.ambient_music.ui.settings.SettingsScreen

// Contains all of our routes
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
  object Home : Screen("home", "Home", Icons.Default.Home)

  object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(windowSizeClass: WindowSizeClass) {
  val navController = rememberNavController()
  val navItems = listOf(Screen.Home, Screen.Settings)
  // For the snackbar
  val snackbarHostState = remember { SnackbarHostState() }

  // Get the current navigation back stack entry
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  // Determine the title based on the current route
  val topBarTitle =
    when (currentRoute) {
      Screen.Settings.route -> "Settings"
      else -> stringResource(R.string.app_name) // Default title for Home screen
    }

  // Determine if we should show the navigation rail (for wider screens)
  // or the navigation bar (for phone-sized screens).
  val showNavRail = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

  Row(modifier = Modifier.fillMaxSize()) {
    if (showNavRail) {
      AppNavigationRail(navController = navController, navItems = navItems)
    }

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      // Only needed for compact screens
      topBar = {
        if (!showNavRail) {
          CenterAlignedTopAppBar(title = { Text(topBarTitle) })
        }
      },
      bottomBar = {
        if (!showNavRail) {
          AppBottomNavigationBar(navController = navController, navItems = navItems)
        }
      },
    ) { innerPadding ->
      NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
        composable(Screen.Home.route) { HomeScreen(windowSizeClass) }
        composable(Screen.Settings.route) { SettingsScreen(snackbarHostState = snackbarHostState) }
      }
    }
  }
}

// Split the navbar controllers for two form factors: tablets/foldables and phones.
@Composable
fun AppBottomNavigationBar(navController: NavHostController, navItems: List<Screen>) {
  NavigationBar {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    navItems.forEach { screen ->
      NavigationBarItem(
        icon = { Icon(screen.icon, contentDescription = screen.label) },
        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
        label = { Text(screen.label) },
        onClick = {
          navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
          }
        },
      )
    }
  }
}

@Composable
fun AppNavigationRail(navController: NavHostController, navItems: List<Screen>) {
  NavigationRail(modifier = Modifier.fillMaxHeight()) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    navItems.forEach { screen ->
      NavigationRailItem(
        icon = { Icon(screen.icon, contentDescription = screen.label) },
        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
        onClick = {
          navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
          }
        },
      )
    }
  }
}
