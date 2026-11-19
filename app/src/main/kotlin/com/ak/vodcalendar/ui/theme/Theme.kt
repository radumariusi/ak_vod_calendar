package com.ak.vodcalendar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val Accent = Color(0xFFE50914)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF181B21),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF242832),
    onSurfaceVariant = Color(0xFFB0B4BC),
)

@Composable
fun AkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
