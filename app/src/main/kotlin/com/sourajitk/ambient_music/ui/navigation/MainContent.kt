package com.sourajitk.ambient_music.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kyant.expressa.prelude.surfaceContainer
import com.kyant.expressa.ui.Text
import com.kyant.liquidglass.rememberLiquidGlassProviderState
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.ui.home.HomeScreen
import com.sourajitk.ambient_music.ui.settings.SettingsScreen

enum class MainNavTab(val route: String) {
  Songs("home"),
  Settings("settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(windowSizeClass: WindowSizeClass) {
  val navController = rememberNavController()

  val background = surfaceContainer
  val liquidGlassProviderState = rememberLiquidGlassProviderState(background)
  val selectedTab = remember { mutableStateOf(MainNavTab.Songs) }
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  LaunchedEffect(navBackStackEntry) {
    val currentRoute = navBackStackEntry?.destination?.route
    currentRoute?.let { route ->
      MainNavTab.entries.find { it.route == route }?.let { tab -> selectedTab.value = tab }
    }
  }
  val currentRoute = navBackStackEntry?.destination?.route
  val topBarTitle =
    when (currentRoute) {
      Screen.Settings.route -> "Settings"
      else -> stringResource(R.string.app_name)
    }

  val showNavRail = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

  Row(modifier = Modifier.fillMaxSize()) {
    if (showNavRail) {
      AppNavigationRail(navController = navController, selectedTab = selectedTab.value)
    }

    Scaffold(
      topBar = {
        if (!showNavRail) {
          CenterAlignedTopAppBar(title = { androidx.compose.material3.Text(topBarTitle) })
        }
      },
      bottomBar = {
        if (!showNavRail) {
          Column(
            Modifier.padding(horizontal = 80.dp, vertical = 8.dp)
              .fillMaxWidth()
              .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              BottomTabs(
                tabs = MainNavTab.entries,
                selectedTabState = selectedTab,
                liquidGlassProviderState = liquidGlassProviderState,
                background = background,
                modifier = Modifier.weight(1f),
                onTabSelected = { tab ->
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                content = { tab ->
                  when (tab) {
                    MainNavTab.Songs ->
                      BottomTab(
                        icon = {
                          androidx.compose.material3.Icon(
                            painterResource(R.drawable.home_24px),
                            null,
                          )
                        },
                        label = { Text("Songs") },
                      )

                    MainNavTab.Settings ->
                      BottomTab(
                        icon = {
                          androidx.compose.material3.Icon(
                            painterResource(R.drawable.settings_24px),
                            null,
                          )
                        },
                        label = { Text("Settings") },
                      )
                  }
                },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      NavHost(
        navController,
        startDestination = MainNavTab.Songs.route,
        Modifier.padding(innerPadding),
      ) {
        composable(MainNavTab.Songs.route) { HomeScreen(windowSizeClass) }
        composable(MainNavTab.Settings.route) { SettingsScreen() }
      }
    }
  }
}

@Composable
private fun AppNavigationRail(navController: NavHostController, selectedTab: MainNavTab) {
  NavigationRail(modifier = Modifier.fillMaxHeight()) {
    MainNavTab.entries.forEach { tab ->
      NavigationRailItem(
        icon = {
          val iconRes =
            if (tab == MainNavTab.Songs) R.drawable.home_24px else R.drawable.settings_24px
          Icon(painterResource(iconRes), contentDescription = tab.name)
        },
        label = { androidx.compose.material3.Text(tab.name) },
        selected = selectedTab == tab,
        onClick = {
          navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
          }
        },
      )
    }
  }
}
