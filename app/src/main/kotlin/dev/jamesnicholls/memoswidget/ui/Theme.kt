package dev.jamesnicholls.memoswidget.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Brand = Color(0xFF4F46E5)

private val LightColors = lightColorScheme(
    primary = Brand,
)

private val DarkColors = darkColorScheme(
    // Lighter indigo keeps primary-colored text readable on dark surfaces;
    // the send button overrides to solid Brand with white content.
    primary = Color(0xFFA9B4FF),
)

@Composable
fun MemosTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
