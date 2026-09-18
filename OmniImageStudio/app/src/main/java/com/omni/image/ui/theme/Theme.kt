package com.omni.image.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
    error = GlassError
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
