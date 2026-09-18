package com.omni.image.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OmniColorScheme = darkColorScheme(
    primary = GlassPrimary,
    secondary = GlassSecondary,
    tertiary = GlassTertiary,
    background = GlassBackground,
    surface = GlassSurface,
    onPrimary = GlassBackground,
    onSecondary = GlassBackground,
    onBackground = GlassOnBackground,
    onSurface = GlassOnSurface,
    error = GlassError,
    errorContainer = Color(0xFF3B1A1F),
    onError = Color(0xFFFFD7D7),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = GlassOutline,
    outlineVariant = Color(0x59FFFFFF),
    surfaceContainer = GlassSurfaceStrong,
    surfaceContainerLow = Color(0x66FFFFFF),
    surfaceContainerLowest = Color(0x52FFFFFF),
    surfaceContainerHigh = Color(0x8CFFFFFF),
    surfaceContainerHighest = Color(0xA6FFFFFF),
    onSurfaceVariant = GlassMuted
)

private val OmniShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun OmniImageStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmniColorScheme,
        typography = Typography(),
        shapes = OmniShapes,
        content = content
    )
}
