package com.breakfree.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.breakfree.app.data.settings.AppTheme

private val White = Color(0xFFFFFFFF)
private val VeryDarkGray = Color(0xFF121212)
private val GrayDark = Color(0xFF1E1E1E)
private val GrayLight = Color(0xFFF2F2F7)
private val CardBackgroundDark = Color(0xFF2C2C2E)
private val CardBackgroundLight = Color(0xFFE5E5EA)

private val DarkColors = darkColorScheme(
    primary = White,
    onPrimary = VeryDarkGray,
    primaryContainer = CardBackgroundDark,
    onPrimaryContainer = White,
    background = VeryDarkGray,
    onBackground = White,
    surface = VeryDarkGray,
    onSurface = White,
    secondary = Color.LightGray,
    onSecondary = VeryDarkGray,
    secondaryContainer = Color(0xFF1C1C1E),
    onSecondaryContainer = White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color.LightGray
)

private val LightColors = lightColorScheme(
    primary = VeryDarkGray,
    onPrimary = White,
    primaryContainer = CardBackgroundLight,
    onPrimaryContainer = VeryDarkGray,
    background = White,
    onBackground = VeryDarkGray,
    surface = White,
    onSurface = VeryDarkGray,
    secondary = Color.DarkGray,
    onSecondary = White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = VeryDarkGray,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color.DarkGray
)

@Composable
fun BreakFreeTheme(
    appTheme: AppTheme = AppTheme.AUTO,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.AUTO -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
