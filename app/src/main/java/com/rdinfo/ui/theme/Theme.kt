package com.rdinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Rot als Primärfarbe – passend zum Screenshot
private val RD_Red = Color(0xFFB71C1C)
private val RD_RedContainer = Color(0xFFFFDAD6)
private val RD_OnRed = Color(0xFFFFFFFF)

private val LightColors: ColorScheme = lightColorScheme(
    primary = RD_Red,
    onPrimary = RD_OnRed,
    primaryContainer = RD_RedContainer,
    onPrimaryContainer = Color(0xFF410002),
    secondary = RD_Red,
    onSecondary = RD_OnRed,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1D1B1E),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1D1B1E),
    surfaceVariant = Color(0xFFECE0E0),
    onSurfaceVariant = Color(0xFF514345)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB4AB),
    onSecondary = Color(0xFF690005),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD7C1C4)
)

@Composable
fun RDInfoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,   // aus Type.kt der Vorlage
        // WICHTIG: KEIN shapes-Parameter hier
        content = content
    )
}
