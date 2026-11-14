package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = PrecisionCyan,
    onPrimary = StudioObsidian,
    primaryContainer = PrecisionCyanContainer,
    onPrimaryContainer = PrecisionCyan,
    secondary = GizmoAmber,
    onSecondary = StudioObsidian,
    secondaryContainer = GizmoAmberContainer,
    onSecondaryContainer = GizmoAmber,
    tertiary = ShaderViolet,
    onTertiary = StudioObsidian,
    tertiaryContainer = ShaderVioletContainer,
    onTertiaryContainer = ShaderViolet,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioSurfaceHighest,
    error = AlertCoral,
    onError = StudioObsidian
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SpriteForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
