package com.aurapdf.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aurapdf.editor.data.PdfRepository
import com.aurapdf.editor.engine.PdfBoxEngine
import com.aurapdf.editor.model.EditorLaunchAction
import com.aurapdf.editor.model.Screen
import com.aurapdf.editor.ui.screens.EditPdfScreen
import com.aurapdf.editor.ui.screens.HomeScreen
import com.aurapdf.editor.ui.screens.ScannerScreen
import com.aurapdf.editor.ui.screens.ViewPdfScreen
import com.aurapdf.editor.ui.theme.AuraPdfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val incomingUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null

        setContent {
            AuraPdfApp(incomingUri = incomingUri)
        }
    }
}

@Composable
fun AuraPdfApp(incomingUri: Uri? = null) {
    val context = LocalContext.current
    val repository = remember { PdfRepository(context.applicationContext) }
    val engine = remember { PdfBoxEngine(context.applicationContext, repository) }

    var themeMode by remember { mutableStateOf(repository.getThemeMode()) }
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var lastStackDepth by remember { mutableIntStateOf(1) }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            runCatching { repository.importPdfFromUri(incomingUri) }
                .onSuccess { imported ->
                    lastStackDepth = backStack.size
                    backStack.add(Screen.ViewPdf(imported.absolutePath))
                }
        }
    }

    fun navigateTo(screen: Screen) {
        lastStackDepth = backStack.size
        backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            lastStackDepth = backStack.size
            backStack.removeAt(backStack.lastIndex)
        }
    }

    AuraPdfTheme(themeMode = themeMode) {
        val currentScreen = backStack.last()
        val isForward = backStack.size >= lastStackDepth

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val spec = tween<androidx.compose.ui.unit.IntOffset>(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing
                )
                val fadeSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
                if (isForward) {
                    (slideInHorizontally(animationSpec = spec) { it / 4 } + fadeIn(fadeSpec))
                        .togetherWith(slideOutHorizontally(animationSpec = spec) { -it / 6 } + fadeOut(fadeSpec))
                } else {
                    (slideInHorizontally(animationSpec = spec) { -it / 4 } + fadeIn(fadeSpec))
                        .togetherWith(slideOutHorizontally(animationSpec = spec) { it / 4 } + fadeOut(fadeSpec))
                }
            },
            label = "aura_screen_transition"
        ) { target ->
            when (target) {
                is Screen.Home -> {
                    HomeScreen(
                        repository = repository,
                        themeMode = themeMode,
                        onThemeChange = { newMode ->
                            themeMode = newMode
                            repository.setThemeMode(newMode)
                        },
                        onOpenPdfViewer = { path -> navigateTo(Screen.ViewPdf(path)) },
                        onOpenPdfEditor = { path -> navigateTo(Screen.EditPdf(path, EditorLaunchAction.NONE)) },
                        onOpenScanner = { navigateTo(Screen.Scanner) }
                    )
                }

                is Screen.Scanner -> {
                    ScannerScreen(
                        repository = repository,
                        onBack = { navigateBack() },
                        onSavedToRecent = {
                            lastStackDepth = backStack.size
                            backStack.clear()
                            backStack.add(Screen.Home)
                        },
                        onOpenInEditor = { path ->
                            lastStackDepth = backStack.size
                            backStack.removeAt(backStack.lastIndex)
                            backStack.add(Screen.EditPdf(path, EditorLaunchAction.NONE))
                        }
                    )
                }

                is Screen.ViewPdf -> {
                    ViewPdfScreen(
                        pdfPath = target.pdfPath,
                        repository = repository,
                        engine = engine,
                        onBack = { navigateBack() },
                        onOpenEditPdf = { path, action -> navigateTo(Screen.EditPdf(path, action)) },
                        onOpenScanner = { navigateTo(Screen.Scanner) }
                    )
                }

                is Screen.EditPdf -> {
                    EditPdfScreen(
                        pdfPath = target.pdfPath,
                        initialAction = target.initialAction,
                        repository = repository,
                        engine = engine,
                        onBack = { navigateBack() }
                    )
                }
            }
        }
    }
}
