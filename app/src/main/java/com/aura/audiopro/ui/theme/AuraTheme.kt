package com.aura.audiopro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aura.audiopro.model.ThemeMode

val AuraAccentColors = listOf(
    Color(0xFF1DB954), // Emerald Green
    Color(0xFF8B5CF6), // Royal Violet
    Color(0xFF06B6D4), // Ocean Cyan
    Color(0xFFF43F5E), // Crimson Rose
    Color(0xFFF59E0B), // Amber Gold
    Color(0xFF3B82F6)  // Sapphire Blue
)

@Composable
fun AuraAudioProTheme(
    themeMode: ThemeMode,
    accentIndex: Int,
    content: @Composable () -> Unit
) {
    val accent = AuraAccentColors.getOrElse(accentIndex) { AuraAccentColors[0] }
    val colorScheme = when (themeMode) {
        ThemeMode.AMOLED -> darkColorScheme(
            primary = accent,
            secondary = accent,
            background = Color(0xFF000000),
            surface = Color(0xFF0E121A),
            surfaceVariant = Color(0xFF171E2C),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFF94A3B8)
        )
        ThemeMode.DARK -> darkColorScheme(
            primary = accent,
            secondary = accent,
            background = Color(0xFF0B0F17),
            surface = Color(0xFF151C28),
            surfaceVariant = Color(0xFF1E293B),
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFF94A3B8)
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = accent,
            secondary = accent,
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E8F0),
            onPrimary = Color.White,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
