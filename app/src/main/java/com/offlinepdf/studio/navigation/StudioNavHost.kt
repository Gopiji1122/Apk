package com.offlinepdf.studio.navigation

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.offlinepdf.studio.R
import com.offlinepdf.studio.core.designsystem.components.rememberStudioHaptics
import com.offlinepdf.studio.core.designsystem.theme.LocalStudioPrefs
import com.offlinepdf.studio.core.designsystem.theme.extras
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
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    FILES("files", R.string.nav_files, Icons.Outlined.Folder, Icons.Filled.Folder),
    SCAN("scan", R.string.nav_scan, Icons.Outlined.DocumentScanner, Icons.Outlined.DocumentScanner),
    TOOLS("tools", R.string.nav_tools, Icons.Outlined.Build, Icons.Filled.Build),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
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
    val animations = LocalStudioPrefs.current.animationsEnabled

    val isExternalLaunch = !initialDocumentUri.isNullOrBlank()
    val startDestination = if (isExternalLaunch) EXTERNAL_READER_ROUTE else TopDestination.HOME.route
    val isTopLevel = !isExternalLaunch && TopDestination.entries.any { it.route == currentRoute }

    // The scanner hides the bar while the camera / crop / enhance stages are on screen.
    var scannerFullscreen by remember { mutableStateOf(false) }
    val showBottomBar = isTopLevel && !(currentRoute == TopDestination.SCAN.route && scannerFullscreen)

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openReaderInApp(uriString: String) {
        navController.navigate("reader/${encodeUriB64(uriString)}")
    }

    fun openEditor(uriString: String) {
        navController.navigate("editor/${encodeUriB64(uriString)}")
    }

    // Tab changes cross-fade with a tiny scale; detail screens rise / slide in.
    val tabEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        if (animations) fadeIn(tween(240, 70, FastOutSlowInEasing)) + scaleIn(tween(280, 70, FastOutSlowInEasing), 0.97f)
        else EnterTransition.None
    }
    val tabExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        if (animations) fadeOut(tween(110)) else ExitTransition.None
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                StudioBottomBar(currentRoute = currentRoute, onSelect = ::navigateTop)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (isTopLevel) Modifier.padding(innerPadding) else Modifier,
            enterTransition = { tabEnter() },
            exitTransition = { tabExit() },
            popEnterTransition = { tabEnter() },
            popExitTransition = { tabExit() }
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
                    onNavigateToFiles = { navigateTop(TopDestination.FILES.route) },
                    onNavigateToScanner = { navigateTop(TopDestination.SCAN.route) }
                )
            }
            composable(TopDestination.FILES.route) {
                FilesScreen(onOpenDocument = ::openReaderInApp)
            }
            composable(TopDestination.SCAN.route) {
                ScannerScreen(
                    onOpenExportedPdf = ::openReaderInApp,
                    onFullscreenChange = { scannerFullscreen = it }
                )
            }
            composable(TopDestination.TOOLS.route) {
                ToolsScreen(
                    onOpenReader = { navigateTop(TopDestination.FILES.route) },
                    onOpenScanner = { navigateTop(TopDestination.SCAN.route) }
                )
            }
            composable(TopDestination.SETTINGS.route) {
                SettingsScreen()
            }
            composable(
                route = READER_ROUTE,
                arguments = listOf(navArgument("uriB64") { type = NavType.StringType }),
                enterTransition = {
                    if (animations) fadeIn(tween(260)) + scaleIn(tween(320, easing = FastOutSlowInEasing), 0.94f)
                    else EnterTransition.None
                },
                popExitTransition = {
                    if (animations) fadeOut(tween(200)) + scaleOut(tween(240, easing = FastOutSlowInEasing), 0.94f)
                    else ExitTransition.None
                }
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
                    if (animations) slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(240))
                    else EnterTransition.None
                },
                popExitTransition = {
                    if (animations) slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(tween(200))
                    else ExitTransition.None
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

/**
 * Bottom bar with a spring-animated selection pill, filled icons for the active tab and
 * an emphasised centre "Scan" action.
 */
@Composable
private fun StudioBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val haptics = rememberStudioHaptics()
    val items = TopDestination.entries
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(72.dp)
        ) {
            val itemWidth = maxWidth / items.size
            val pillX by animateDpAsState(
                targetValue = itemWidth * selectedIndex + (itemWidth - 58.dp) / 2,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                label = "nav-pill"
            )
            val scanIndex = items.indexOf(TopDestination.SCAN)
            if (selectedIndex != scanIndex) {
                Box(
                    modifier = Modifier
                        .offset(x = pillX, y = 10.dp)
                        .width(58.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, dest ->
                    val selected = index == selectedIndex
                    val isScan = dest == TopDestination.SCAN
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "nav-icon-scale"
                    )
                    Column(
                        modifier = Modifier
                            .offset(x = itemWidth * index)
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!selected) {
                                    haptics.tick()
                                    onSelect(dest.route)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = if (isScan) 6.dp else 10.dp)
                                .height(if (isScan) 40.dp else 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScan) {
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                        .shadow(if (selected) 8.dp else 3.dp, CircleShape, clip = false)
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.extras.scanBrush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        dest.icon,
                                        contentDescription = stringResource(dest.labelRes),
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.icon,
                                    contentDescription = stringResource(dest.labelRes),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                                        .size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(dest.labelRes),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (isScan) 2.dp else 3.dp)
                        )
                    }
                }
            }
        }
    }
}
