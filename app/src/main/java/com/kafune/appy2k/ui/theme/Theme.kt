package com.kafune.appy2k.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanY2k = Color(0xFF7DF9FF)
val PinkY2k = Color(0xFFFF71CE)
val OrangeLed = Color(0xFFFF9C2E)
val Ink = Color(0xFF0A0A12)
val Panel = Color(0xFF14141F)

private val DarkScheme = darkColorScheme(
    primary = CyanY2k,
    onPrimary = Ink,
    secondary = PinkY2k,
    onSecondary = Ink,
    tertiary = OrangeLed,
    background = Ink,
    onBackground = Color(0xFFE8E8F0),
    surface = Panel,
    onSurface = Color(0xFFE8E8F0),
    surfaceVariant = Color(0xFF1E1E2E),
    onSurfaceVariant = Color(0xFFB8B8C8),
)

@Composable
fun Appy2kTheme(content: @Composable () -> Unit) {
    // o app é dark sempre — estética de lan house às 23h
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
