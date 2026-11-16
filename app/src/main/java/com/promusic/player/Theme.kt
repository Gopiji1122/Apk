package com.promusic.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

val Accents: List<Pair<String, Color>> = listOf(
    "Emerald" to Color(0xFF1ED760),
    "Violet" to Color(0xFF9B7BFF),
    "Ocean" to Color(0xFF3FA2FF),
    "Sunset" to Color(0xFFFF8A4C),
    "Rose" to Color(0xFFFF5C9D),
    "Gold" to Color(0xFFFFCF3F)
)

@Composable
fun ProTheme(accent: Color, amoled: Boolean, content: @Composable () -> Unit) {
    val animated by animateColorAsState(accent, tween(500), label = "accent")
    val bg = if (amoled) Color.Black else Color(0xFF0A0A0F)
    val surface = if (amoled) Color(0xFF0E0E10) else Color(0xFF14141C)
    val scheme = darkColorScheme(
        primary = animated,
        onPrimary = Color.Black,
        primaryContainer = animated.copy(alpha = 0.2f),
        onPrimaryContainer = Color.White,
        secondary = animated,
        onSecondary = Color.Black,
        background = bg,
        onBackground = Color.White,
        surface = surface,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF22222E),
        onSurfaceVariant = Color(0xFFA9A9BA),
        outline = Color(0xFF3A3A4A)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
