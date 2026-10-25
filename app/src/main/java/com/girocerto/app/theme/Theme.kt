package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GiroCertoColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryBlueVariant,
    onPrimaryContainer = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = RedAlert,
    onError = Color.White
)

@Composable
fun GiroCertoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GiroCertoColorScheme,
        typography = Typography,
        content = content
    )
}

