package com.aurapdf.editor.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class PdfThemeMode(val id: String, val label: String) {
    AMOLED("amoled", "AMOLED Black"),
    DARK("dark", "Acrobat Dark"),
    LIGHT("light", "Acrobat Light");

    companion object {
        fun fromId(id: String?): PdfThemeMode = entries.find { it.id == id } ?: DARK
    }
}

object AcrobatColors {
    val Red = Color(0xFFE52521)
    val EditPink = Color(0xFFE91E63)
    val CommentBlue = Color(0xFF1976D2)
    val SignPurple = Color(0xFF7C4DFF)
    val OrganizeTeal = Color(0xFF00897B)
    val MergeOrange = Color(0xFFF57C00)
    val CompressGreen = Color(0xFF2E7D32)
    val ProtectRed = Color(0xFFD32F2F)
    val ExportIndigo = Color(0xFF3949AB)
    val ReadCyan = Color(0xFF0097A7)
}

private val AmoledScheme = darkColorScheme(
    primary = AcrobatColors.Red,
    onPrimary = Color.White,
    secondary = Color(0xFF42A5F5),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF101012),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1C1C20),
    onSurfaceVariant = Color(0xFFA0A4AE),
    outline = Color(0xFF2C2C32)
)

private val DarkScheme = darkColorScheme(
    primary = AcrobatColors.Red,
    onPrimary = Color.White,
    secondary = Color(0xFF42A5F5),
    onSecondary = Color.Black,
    background = Color(0xFF16171B),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF1F2128),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF2A2D36),
    onSurfaceVariant = Color(0xFFA6ABB6),
    outline = Color(0xFF383C48)
)

private val LightScheme = lightColorScheme(
    primary = AcrobatColors.Red,
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF16171A),
    surface = Color.White,
    onSurface = Color(0xFF16171A),
    surfaceVariant = Color(0xFFEBEEF2),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFD8DCE3)
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
