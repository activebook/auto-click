package com.activebook.autoclick.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF111817),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111817),
    surfaceVariant = Color(0xFFE6EFEC),
    onSurfaceVariant = Color(0xFF445250),
    outline = Color(0xFF9AA9A5),
    outlineVariant = Color(0xFFCBD9D5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF04231F),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0B1320),
    secondaryContainer = Color(0xFF273244),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1215),
    onBackground = Color(0xFFE3EBE9),
    surface = Color(0xFF121C1F),
    onSurface = Color(0xFFE3EBE9),
    surfaceVariant = Color(0xFF1E2B2E),
    onSurfaceVariant = Color(0xFFB4C4C1),
    outline = Color(0xFF3F4F52),
    outlineVariant = Color(0xFF2A383B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun AutoClickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
