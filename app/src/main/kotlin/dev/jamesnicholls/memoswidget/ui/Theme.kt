package dev.jamesnicholls.memoswidget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF4F46E5)

private val LightColors = lightColorScheme(
    primary = Brand,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4B0FF),
)

@Composable
fun MemosTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
