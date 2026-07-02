package com.breakfree.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AccentGreen = Color(0xFF2ECC71)
private val DarkBackground = Color(0xFF0E0E10)
private val DarkSurface = Color(0xFF1A1A1D)

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    background = DarkBackground,
    surface = DarkSurface
)

private val LightColors = lightColorScheme(
    primary = AccentGreen
)

@Composable
fun BreakFreeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
