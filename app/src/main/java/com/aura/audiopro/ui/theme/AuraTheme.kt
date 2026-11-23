package com.aura.audiopro.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aura.audiopro.model.ThemeMode

/**
 * ProMusicPlayer visual theme & accent palette for Aura Audio Pro.
 * @author Gopal Gorchhiya
 */
val AuraAccentColors = listOf(
    Color(0xFF1ED760), // Emerald
    Color(0xFF9B7BFF), // Violet
    Color(0xFF3FA2FF), // Ocean
    Color(0xFFFF8A4C), // Sunset
    Color(0xFFFF5C9D), // Rose
    Color(0xFFFFCF3F)  // Gold
)

val AuraAccentNames = listOf(
    "Emerald",
    "Violet",
    "Ocean",
    "Sunset",
    "Rose",
    "Gold"
)

@Composable
fun AuraAudioProTheme(
    themeMode: ThemeMode,
    accentIndex: Int,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
        else -> themeMode
    }

    val rawAccent = AuraAccentColors.getOrElse(accentIndex) { AuraAccentColors[0] }
    val primary by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 350),
        label = "pro_accent"
    )

    val colorScheme = when (effectiveMode) {
        ThemeMode.AMOLED -> darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = primary.copy(alpha = 0.20f),
            onPrimaryContainer = primary,
            secondary = primary,
            background = Color(0xFF000000),
            onBackground = Color.White,
            surface = Color(0xFF0E0E10),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF22222E),
            onSurfaceVariant = Color(0xFFA9A9BA),
            outline = Color(0xFF3A3A4A)
        )
        ThemeMode.DARK, ThemeMode.SYSTEM -> darkColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = primary.copy(alpha = 0.20f),
            onPrimaryContainer = primary,
            secondary = primary,
            background = Color(0xFF0A0A0F),
            onBackground = Color.White,
            surface = Color(0xFF14141C),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF22222E),
            onSurfaceVariant = Color(0xFFA9A9BA),
            outline = Color(0xFF3A3A4A)
        )
        ThemeMode.LIGHT -> lightColorScheme(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = primary,
            secondary = primary,
            background = Color(0xFFF6F6FA),
            onBackground = Color(0xFF0A0A0F),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0A0A0F),
            surfaceVariant = Color(0xFFEAEAF2),
            onSurfaceVariant = Color(0xFF5A5A6E),
            outline = Color(0xFFD0D0DE)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
