package com.offlineai.assistant.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.offlineai.assistant.data.AppThemeMode

private val AmoledScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF262626)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF0F0F0),
    onPrimary = Color(0xFF121212),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF121212),
    background = Color(0xFF121316),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1A1C20),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF24272D),
    onSurfaceVariant = Color(0xFFA0A4AC),
    outline = Color(0xFF333740)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F1F3),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFE0E2E6)
)

fun schemeFor(mode: AppThemeMode): ColorScheme = when (mode) {
    AppThemeMode.AMOLED -> AmoledScheme
    AppThemeMode.DARK -> DarkScheme
    AppThemeMode.LIGHT -> LightScheme
}

@Composable
fun OfflineAiTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = schemeFor(mode),
        content = content
    )
}
