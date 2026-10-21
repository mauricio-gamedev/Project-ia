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

private val NucleoLightColors = lightColorScheme(
    primary = Color(0xFF5145A8),
    secondary = Color(0xFF226B63),
)

@Composable
fun NucleoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) NucleoDarkColors else NucleoLightColors,
        content = content,
    )
}
