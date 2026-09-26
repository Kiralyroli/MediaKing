package com.kiroland.mediacenter.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

val Accent = Color(0xFF4FA3FF)
val Background = Color(0xFF0E1116)
val SurfaceColor = Color(0xFF181D26)
val Success = Color(0xFF5BD18B)
val Danger = Color(0xFFFF6B6B)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00213F),
    background = Background,
    onBackground = Color(0xFFE6E9EF),
    surface = SurfaceColor,
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF232A36),
    onSurfaceVariant = Color(0xFFA9B2C3),
    inverseSurface = Color(0xFFE6E9EF),
    inverseOnSurface = Background,
    error = Danger,
)

@Composable
fun MediaCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors) {
        // Root surface: sets the background and the default content color for plain Text.
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = colors.background, contentColor = colors.onBackground),
            content = { content() },
        )
    }
}
