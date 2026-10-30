// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.ui.home.HomeScreen
import com.sourajitk.ambient_music.ui.offlinemode.DownloadGenresScreen
import com.sourajitk.ambient_music.ui.settings.SettingsScreen
import com.sourajitk.ambient_music.ui.timer.TimerScreen
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// Contains all of our routes
sealed class Screen(val route: String, val label: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home_string, Icons.Default.Home)

    object Settings : Screen("settings", R.string.settings_string, Icons.Default.Settings)

    object Timer : Screen("timer", R.string.timer_string, Icons.Default.Timer)
}

private val tabs = listOf(Screen.Home, Screen.Timer, Screen.Settings)

// Composable to get the string label
@Composable
fun Screen.label(): String = stringResource(id = this.label)

// Hides a page that's fully covered by another from accessibility services.
private fun Modifier.hiddenFromAccessibility(hidden: Boolean): Modifier = if (hidden) clearAndSetSemantics { } else this

@Composable
private fun PageScrim(state: PageMotionState) {
    val isDarkTheme = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.scrimAlpha(isDarkTheme) }
            .background(Color.Black),
    )
}

// The tabs, with Offline Listening opened on top of them like a separate screen: it uses the
// platform's activity open/close animations and its cross-activity predictive back animation (see
// PageMotion.kt). The tabs stay composed underneath, so they're ready to show during the back
// gesture and keep their state (e.g. a running library refresh) while the page is open.
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainAppNavigation(windowSizeClass: WindowSizeClass) {
    var isOfflinePageOpen by rememberSaveable { mutableStateOf(false) }
    val motion = rememberPageMotionState()
    val scope = rememberCoroutineScope()

    // Started undispatched so the animation's first frame is set before the page is first drawn.
    val openOfflinePage = {
        if (!isOfflinePageOpen) {
            isOfflinePageOpen = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) { motion.open() }
        }
    }
    val closeOfflinePage = {
        if (isOfflinePageOpen && !motion.isLeaving) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                motion.close()
                isOfflinePageOpen = false
                motion.reset()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isOfflinePageOpen) Modifier.pageLayer(motion, PageRole.Bottom) else Modifier)
                .hiddenFromAccessibility(isOfflinePageOpen && !motion.isBottomVisible),
        ) {
            MainTabs(
                windowSizeClass = windowSizeClass,
                backEnabled = !isOfflinePageOpen,
                onNavigateToDownloadGenres = openOfflinePage,
            )
        }
        if (isOfflinePageOpen) {
            PageScrim(motion)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageLayer(motion, PageRole.Top),
            ) {
                DownloadGenresScreen(onNavigateBack = closeOfflinePage)
            }
        }
    }

    PageBackHandler(
        enabled = isOfflinePageOpen,
        state = motion,
        onBack = { isOfflinePageOpen = false },
        onBackWithoutGesture = closeOfflinePage,
    )
}

@Composable
private fun MainTabs(windowSizeClass: WindowSizeClass, backEnabled: Boolean, onNavigateToDownloadGenres: () -> Unit) {
    var selectedRoute by rememberSaveable { mutableStateOf(Screen.Home.route) }
    val selected = tabs.first { it.route == selectedRoute }
    // For the snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    val showNavRail = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    val showBottomBar = windowSizeClass.widthSizeClass <= WindowWidthSizeClass.Compact
    val showTopBar = windowSizeClass.widthSizeClass <= WindowWidthSizeClass.Compact

    Row(modifier = Modifier.fillMaxSize()) {
        if (showNavRail) {
            AppNavigationRail(navItems = tabs, currentRoute = selectedRoute, onSelect = { selectedRoute = it.route })
        }

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            bottomBar = {
                if (showBottomBar) {
                    AppBottomNavigationBar(navItems = tabs, currentRoute = selectedRoute, onSelect = { selectedRoute = it.route })
                }
            },
        ) { innerPadding ->
            TabHost(
                selected = selected,
                backEnabled = backEnabled,
                onBackToHome = { selectedRoute = Screen.Home.route },
            ) { tab ->
                TabPage(title = tabTitle(tab), showTopBar = showTopBar) {
                    when (tab) {
                        Screen.Home -> HomeScreen(windowSizeClass, innerPadding = innerPadding)

                        Screen.Timer -> TimerScreen(windowSizeClass, innerPadding = innerPadding)

                        Screen.Settings -> SettingsScreen(
                            snackbarHostState = snackbarHostState,
                            innerPadding = innerPadding,
                            onNavigateToDownloadGenres = onNavigateToDownloadGenres,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tabTitle(tab: Screen): String = when (tab) {
    Screen.Settings -> stringResource(R.string.settings_string)
    Screen.Timer -> stringResource(R.string.timer_string)
    Screen.Home -> stringResource(R.string.app_name)
}

private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210

// Shows the selected tab. Switching tabs uses Material's fade through: the old tab fades out, then
// the new one fades and scales in. Back from another tab returns to Home with the native
// predictive back animation, so Home always stays composed underneath the other tabs.
@Composable
private fun TabHost(
    selected: Screen,
    backEnabled: Boolean,
    onBackToHome: () -> Unit,
    page: @Composable (Screen) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val motion = rememberPageMotionState()
    val fade = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(selected) }
    var outgoing by remember { mutableStateOf<Screen?>(null) }

    LaunchedEffect(selected) {
        if (selected == shown) return@LaunchedEffect
        outgoing = shown
        shown = selected
        fade.snapTo(0f)
        // The new tab stays invisible while it's built the first time.
        awaitFirstDraw()
        fade.animateTo(1f, tween(FADE_OUT_MS + FADE_IN_MS, easing = LinearEasing))
        outgoing = null
    }

    val layers = listOfNotNull(Screen.Home, outgoing, shown).distinct()
    Box(modifier = Modifier.fillMaxSize()) {
        layers.forEach { tab ->
            key(tab.route) {
                val isHidden = tab != shown && tab != outgoing && !motion.isBottomVisible
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (motion.isBottomVisible) {
                                // Back gesture from another tab: Home is the page beneath.
                                when (tab) {
                                    Screen.Home -> motion.applyTo(this, PageRole.Bottom)
                                    shown -> motion.applyTo(this, PageRole.Top)
                                    else -> alpha = 0f
                                }
                            } else {
                                val ms = fade.value * (FADE_OUT_MS + FADE_IN_MS)
                                val fadeIn = ((ms - FADE_OUT_MS) / FADE_IN_MS).coerceIn(0f, 1f)
                                val isFadingIn = tab == shown && outgoing != null
                                val scale = if (isFadingIn) 0.92f + 0.08f * NativeMotion.EmphasizedDecelerate.transform(fadeIn) else 1f
                                transformOrigin = TransformOrigin.Center
                                translationX = 0f
                                translationY = 0f
                                scaleX = scale
                                scaleY = scale
                                clip = false
                                alpha = when {
                                    isFadingIn -> fadeIn
                                    tab == shown -> 1f
                                    tab == outgoing -> 1f - (ms / FADE_OUT_MS).coerceIn(0f, 1f)
                                    else -> 0f
                                }
                            }
                        }
                        .hiddenFromAccessibility(isHidden),
                ) {
                    stateHolder.SaveableStateProvider(tab.route) { page(tab) }
                }
                if (tab == Screen.Home) PageScrim(motion)
            }
        }
    }

    PageBackHandler(
        enabled = backEnabled && shown != Screen.Home,
        state = motion,
        onBack = {
            // Home is already fully shown, so switch to it without a fade.
            shown = Screen.Home
            outgoing = null
            onBackToHome()
        },
        onBackWithoutGesture = onBackToHome,
    )
}

// A tab with its own top bar, so the title moves with its page during transitions like a separate
// screen's would. The Surface makes it opaque and stops touches reaching the tab beneath.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabPage(title: String, showTopBar: Boolean, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            } else {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

// Split the navbar controllers for two form factors: tablets/foldables and phones.
@Composable
fun AppBottomNavigationBar(navItems: List<Screen>, currentRoute: String, onSelect: (Screen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 12.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(11.dp)
                    .height(38.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route

                    FloatingNavBarImpl(
                        screen = screen,
                        isSelected = isSelected,
                        onClick = { onSelect(screen) },
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigationRail(navItems: List<Screen>, currentRoute: String, onSelect: (Screen) -> Unit) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        navItems.forEach { screen ->
            NavigationRailItem(
                icon = { Icon(screen.icon, contentDescription = screen.label()) },
                selected = currentRoute == screen.route,
                onClick = { onSelect(screen) },
            )
        }
    }
}
