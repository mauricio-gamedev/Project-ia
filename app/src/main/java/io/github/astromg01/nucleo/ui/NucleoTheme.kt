package io.github.astromg01.nucleo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NucleoDarkColors = darkColorScheme(
    primary = Color(0xFFB9AEFF),
    onPrimary = Color(0xFF21175F),
    secondary = Color(0xFF92D9D0),
    background = Color(0xFF0C0C12),
    surface = Color(0xFF171720),
    surfaceVariant = Color(0xFF252532),
    onSurface = Color(0xFFF2F0FA),
    onSurfaceVariant = Color(0xFFC3C0D2),
    error = Color(0xFFFFB4AB),
)

private val NucleoOledColors = darkColorScheme(
    primary = Color(0xFFC6B9FF),
    onPrimary = Color(0xFF20164A),
    primaryContainer = Color(0xFF211A36),
    onPrimaryContainer = Color(0xFFF0EAFF),
    secondary = Color(0xFF9BE7DE),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF082C27),
    onSecondaryContainer = Color(0xFFC9FFF7),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF050505),
    onSurface = Color(0xFFF7F7FA),
    onSurfaceVariant = Color(0xFFBDBDC8),
    surfaceTint = Color(0xFFC6B9FF),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF121212),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color(0xFF050505),
    surfaceContainerHighest = Color(0xFF090909),
    outline = Color(0xFF77717F),
    outlineVariant = Color(0xFF24242A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF410002),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val NucleoLightColors = lightColorScheme(
    primary = Color(0xFF5145A8),
    secondary = Color(0xFF226B63),
)

@Composable
fun NucleoTheme(
    oledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = when {
            oledBlack -> NucleoOledColors
            isSystemInDarkTheme() -> NucleoDarkColors
            else -> NucleoLightColors
        },
        content = content,
    )
}