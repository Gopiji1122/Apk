package com.aurapdf.editor.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class PdfThemeMode(val id: String, val label: String) {
    AMOLED("amoled", "AMOLED Black"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromId(id: String?): PdfThemeMode = entries.find { it.id == id } ?: AMOLED
    }
}

private val AmoledScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0C0C0C),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF181818),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF282828)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF121212),
    secondary = Color(0xFFB0B0B0),
    onSecondary = Color(0xFF121212),
    background = Color(0xFF121316),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1B1D22),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF252830),
    onSurfaceVariant = Color(0xFFA0A4AC),
    outline = Color(0xFF333742)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF0F1F3),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDEE2E6)
)

fun schemeFor(mode: PdfThemeMode): ColorScheme = when (mode) {
    PdfThemeMode.AMOLED -> AmoledScheme
    PdfThemeMode.DARK -> DarkScheme
    PdfThemeMode.LIGHT -> LightScheme
}

@Composable
fun AuraPdfTheme(mode: PdfThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(mode), content = content)
}
