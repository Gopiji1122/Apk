package com.offlinepdf.studio.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────────── Brand palette: "Ruby & Ink" ─────────────────────────────
private val Ruby = Color(0xFFC8203B)
private val RubyDeep = Color(0xFF8F1230)
private val RubyBright = Color(0xFFE23B57)
private val Teal = Color(0xFF0E9AA7)
private val TealBright = Color(0xFF22C7D6)

private val LightColors = lightColorScheme(
    primary = Ruby,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E4),
    onPrimaryContainer = Color(0xFF4A0612),
    secondary = Color(0xFF2B3445),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EAF2),
    onSecondaryContainer = Color(0xFF111827),
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F4F7),
    onTertiaryContainer = Color(0xFF00363B),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0F1420),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1420),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF5A6377),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = Color(0xFFF1F3F9),
    surfaceContainerHigh = Color(0xFFEBEEF5),
    surfaceContainerHighest = Color(0xFFE5E8F0),
    outline = Color(0xFFC9CEDB),
    outlineVariant = Color(0xFFE3E6EE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A98),
    onPrimary = Color(0xFF5E0A18),
    primaryContainer = Color(0xFF8A1328),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = Color(0xFFC3CBDC),
    onSecondary = Color(0xFF1B2230),
    secondaryContainer = Color(0xFF2A3345),
    onSecondaryContainer = Color(0xFFE6EAF4),
    tertiary = Color(0xFF52D6E2),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF0A4F56),
    onTertiaryContainer = Color(0xFFBCF1F6),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8EBF3),
    surface = Color(0xFF12151D),
    onSurface = Color(0xFFE8EBF3),
    surfaceVariant = Color(0xFF1D222D),
    onSurfaceVariant = Color(0xFF9AA3B7),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF141821),
    surfaceContainer = Color(0xFF181D27),
    surfaceContainerHigh = Color(0xFF1F2430),
    surfaceContainerHighest = Color(0xFF272D3A),
    outline = Color(0xFF3A4152),
    outlineVariant = Color(0xFF262C3A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ───────────────────────────── Typography & Shapes ─────────────────────────────
private val Sans = FontFamily.SansSerif

val StudioTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// ───────────────────────────── Extra semantic colors ─────────────────────────────
@Immutable
data class StudioExtraColors(
    val success: Color,
    val warning: Color,
    val brandStart: Color,
    val brandEnd: Color,
    val scanStart: Color,
    val scanEnd: Color,
    val readerChrome: Color,
    val readerChromeContent: Color,
    val readerCanvasLight: Color,
    val readerCanvasDark: Color,
    val readerCanvasNight: Color
) {
    val brandBrush: Brush get() = Brush.linearGradient(listOf(brandStart, brandEnd))
    val scanBrush: Brush get() = Brush.linearGradient(listOf(scanStart, scanEnd))
}

private val LightExtras = StudioExtraColors(
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    brandStart = RubyBright,
    brandEnd = RubyDeep,
    scanStart = TealBright,
    scanEnd = Color(0xFF0B6E8A),
    readerChrome = Color(0xE6141821),
    readerChromeContent = Color(0xFFF3F4F8),
    readerCanvasLight = Color(0xFFE6E8EE),
    readerCanvasDark = Color(0xFF101319),
    readerCanvasNight = Color(0xFF07080B)
)

private val DarkExtras = LightExtras.copy(
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    brandEnd = Color(0xFF9B1535)
)

val LocalStudioExtras = staticCompositionLocalOf { LightExtras }

// ───────────────────────────── User interaction prefs ─────────────────────────────
@Immutable
data class StudioUiPrefs(
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

val LocalStudioPrefs = compositionLocalOf { StudioUiPrefs() }

/** Consistent motion tokens. Collapses to instant changes when the user disables animations. */
object StudioMotion {
    const val Short = 160
    const val Medium = 260
    const val Long = 420

    @Composable
    @ReadOnlyComposable
    fun <T> spec(durationMillis: Int = Medium, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        if (LocalStudioPrefs.current.animationsEnabled) {
            tween(durationMillis, delayMillis, FastOutSlowInEasing)
        } else {
            snap()
        }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun OfflinePdfStudioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    animationsEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalStudioExtras provides (if (darkTheme) DarkExtras else LightExtras),
        LocalStudioPrefs provides StudioUiPrefs(animationsEnabled, hapticsEnabled)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}

val MaterialTheme.extras: StudioExtraColors
    @Composable @ReadOnlyComposable get() = LocalStudioExtras.current
