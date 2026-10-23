package com.nexguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    primary = Color(0xFF3F51B5),
    onPrimary = Color(0xFFFFFFFF),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
)

private val LightColorScheme = lightColorScheme(
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    primary = Color(0xFF1A237E),
    onPrimary = Color(0xFFFFFFFF),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun NexGuardTheme(content: @Composable () -> Unit) {
    val colorScheme = LightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
