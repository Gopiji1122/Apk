package com.aurapdf.editor.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.aurapdf.editor.model.AppThemeMode

// Signature Adobe Acrobat-inspired Red & Color-Coded Tool Accents
val AcrobatRed = Color(0xFFE52521)
val AcrobatRedDark = Color(0xFFB71C1C)
val ToolEditPink = Color(0xFFE91E63)
val ToolCommentBlue = Color(0xFF1E88E5)
val ToolSignPurple = Color(0xFF8E24AA)
val ToolOrganizeTeal = Color(0xFF00897B)
val ToolMergeOrange = Color(0xFFFB8C00)
val ToolCompressGreen = Color(0xFF43A047)
val ToolProtectRed = Color(0xFFE53935)

private val AcrobatDarkColorScheme = darkColorScheme(
    primary = AcrobatRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1312),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = ToolCommentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A2D42),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = ToolOrganizeTeal,
    onTertiary = Color.White,
    background = Color(0xFF121417),
    onBackground = Color(0xFFF1F3F6),
    surface = Color(0xFF1A1D22),
    onSurface = Color(0xFFF1F3F6),
    surfaceVariant = Color(0xFF242830),
    onSurfaceVariant = Color(0xFFB6BDC8),
    outline = Color(0xFF424854),
    outlineVariant = Color(0xFF2B303A),
    error = Color(0xFFFF5449)
)

private val AmoledPureBlackColorScheme = darkColorScheme(
    primary = AcrobatRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3E0E0D),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = ToolCommentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF112031),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = ToolOrganizeTeal,
    onTertiary = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF0A0B0D),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF15171C),
    onSurfaceVariant = Color(0xFFAAB2BE),
    outline = Color(0xFF32363F),
    outlineVariant = Color(0xFF1C1F26),
    error = Color(0xFFFF5449)
)

private val AcrobatLightColorScheme = lightColorScheme(
    primary = AcrobatRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = ToolCommentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4FF),
    onSecondaryContainer = Color(0xFF001B3E),
    tertiary = ToolOrganizeTeal,
    onTertiary = Color.White,
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFF4D5460),
    outline = Color(0xFF9EA6B2),
    outlineVariant = Color(0xFFDCE0E8),
    error = Color(0xFFBA1A1A)
)

private val AuraTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

@Composable
fun AuraPdfTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val isLight = themeMode == AppThemeMode.ACROBAT_LIGHT
    val colorScheme = when (themeMode) {
        AppThemeMode.ACROBAT_DARK -> AcrobatDarkColorScheme
        AppThemeMode.AMOLED_BLACK -> AmoledPureBlackColorScheme
        AppThemeMode.ACROBAT_LIGHT -> AcrobatLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        content = content
    )
}
