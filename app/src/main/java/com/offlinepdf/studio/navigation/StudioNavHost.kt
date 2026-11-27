package com.offlinepdf.studio.navigation

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinepdf.studio.R
import com.offlinepdf.studio.feature.files.FilesScreen
import com.offlinepdf.studio.feature.home.HomeScreen
import com.offlinepdf.studio.feature.reader.PdfEditorScreen
import com.offlinepdf.studio.feature.reader.PdfReaderScreen
import com.offlinepdf.studio.feature.scanner.ScannerScreen
import com.offlinepdf.studio.feature.settings.SettingsScreen
import com.offlinepdf.studio.feature.tools.ToolsScreen
import java.nio.charset.StandardCharsets

private enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    FILES("files", R.string.nav_files, Icons.Outlined.Folder),
    SCAN("scan", R.string.nav_scan, Icons.Outlined.DocumentScanner),
    TOOLS("tools", R.string.nav_tools, Icons.Outlined.Build),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private const val EXTERNAL_READER_ROUTE = "external_reader"
private const val READER_ROUTE = "reader/{uriB64}"
private const val EDITOR_ROUTE = "editor/{uriB64}"

private fun encodeUriB64(uriString: String): String =
    Base64.encodeToString(
        uriString.toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

private fun decodeUriB64(encoded: String): String =
    String(
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        StandardCharsets.UTF_8
    )

@Composable
fun StudioNavHost(
    initialDocumentUri: String? = null,
    onExitExternalViewer: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isExternalLaunch = !initialDocumentUri.isNullOrBlank()
    val startDestination = if (isExternalLaunch) EXTERNAL_READER_ROUTE else TopDestination.HOME.route
    val showBottomBar = !isExternalLaunch && TopDestination.entries.any { it.route == currentRoute }

    fun navigateToTopTab(route: String) {
        if (currentRoute == route) return
        if (route == TopDestination.HOME.route) {
            val popped = navController.popBackStack(TopDestination.HOME.route, inclusive = false)
            if (!popped) {
                navController.navigate(TopDestination.HOME.route) {
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(route) {
                popUpTo(TopDestination.HOME.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun openReaderInApp(uriString: String) {
        navController.navigate("reader/${encodeUriB64(uriString)}")
    }

    fun openEditor(uriString: String) {
        navController.navigate("editor/${encodeUriB64(uriString)}")
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateToTopTab(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = { Text(stringResource(dest.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (showBottomBar) Modifier.padding(innerPadding) else Modifier,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(160)) }
        ) {
            composable(EXTERNAL_READER_ROUTE) {
                BackHandler { onExitExternalViewer() }
                PdfReaderScreen(
                    uriString = initialDocumentUri.orEmpty(),
                    onBack = onExitExternalViewer,
                    onOpenEditor = ::openEditor
                )
            }
            composable(TopDestination.HOME.route) {
                HomeScreen(
                    onOpenDocument = ::openReaderInApp,
                    onNavigateToFiles = { navigateToTopTab(TopDestination.FILES.route) },
                    onNavigateToScanner = { navigateToTopTab(TopDestination.SCAN.route) },
                    onNavigateToTools = { navigateToTopTab(TopDestination.TOOLS.route) }
                )
            }
            composable(TopDestination.FILES.route) {
                FilesScreen(onOpenDocument = ::openReaderInApp)
            }
            composable(TopDestination.SCAN.route) {
                ScannerScreen(onOpenExportedPdf = ::openReaderInApp)
            }
            composable(TopDestination.TOOLS.route) {
                ToolsScreen()
            }
            composable(TopDestination.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = READER_ROUTE,
                arguments = listOf(navArgument("uriB64") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(150)) }
            ) { entry ->
                val encoded = entry.arguments?.getString("uriB64").orEmpty()
                val decodedUri = runCatching { decodeUriB64(encoded) }.getOrDefault("")
                PdfReaderScreen(
                    uriString = decodedUri,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = ::openEditor
                )
            }
            composable(
                route = EDITOR_ROUTE,
                arguments = listOf(navArgument("uriB64") { type = NavType.StringType }),
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
                },
                exitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180))
                }
            ) { entry ->
                val encoded = entry.arguments?.getString("uriB64").orEmpty()
                val decodedUri = runCatching { decodeUriB64(encoded) }.getOrDefault("")
                PdfEditorScreen(
                    uriString = decodedUri,
                    onBackToViewer = { navController.popBackStack() }
                )
            }
        }
    }
}
