package com.cortextransl.translateonscreen.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cortextransl.translateonscreen.R
import com.cortextransl.translateonscreen.ui.components.AppHeader
import com.cortextransl.translateonscreen.ui.home.HomeScreen
import com.cortextransl.translateonscreen.ui.languages.LanguagePacksScreen
import com.cortextransl.translateonscreen.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val TRANSLATE = "translate"
    const val MORE = "more"
}

private data class Dest(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
fun AppRoot(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    onStartTranslator: (singleApp: Boolean) -> Unit,
    onStopTranslator: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val navController = rememberNavController()
    var showHelp by remember { mutableStateOf(false) }
    val destinations = listOf(
        Dest(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
        Dest(Routes.TRANSLATE, R.string.nav_translate, Icons.Outlined.Translate),
        Dest(Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz)
    )
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                onHelpClick = { showHelp = true },
                onLogoClick = {
                    navController.navigate(Routes.TRANSLATE) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    isServiceRunning = isServiceRunning,
                    hasOverlayPermission = hasOverlayPermission,
                    onStartTranslator = onStartTranslator,
                    onStopTranslator = onStopTranslator,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
            composable(Routes.TRANSLATE) { LanguagePacksScreen() }
            composable(Routes.MORE) {
                SettingsScreen(
                    onOpenLanguages = {
                        navController.navigate(Routes.TRANSLATE) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.help_title)) },
            text = { Text(stringResource(R.string.how_it_works_body)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
