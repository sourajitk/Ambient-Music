package com.sourajitk.ambient_music.ui.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sourajitk.ambient_music.ui.home.HomeScreen
import com.sourajitk.ambient_music.ui.settings.SettingsScreen

// Contains all of our routes
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
  object Home : Screen("home", "Home", Icons.Default.Home)

  object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation() {
  val navController = rememberNavController()
  val navItems = listOf(Screen.Home, Screen.Settings)

  Scaffold(
    bottomBar = {
      NavigationBar(modifier = Modifier.height(80.dp)) { // Adjusted height to M3 default
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        navItems.forEach { screen ->
          NavigationBarItem(
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
  ) { innerPadding ->
    NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
      composable(Screen.Home.route) { HomeScreen() }
      composable(Screen.Settings.route) { SettingsScreen() }
    }
  }
}
