package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Navy = Color(0xFF102746)
// Compatibility tokens follow the selected theme throughout existing screens.
val BackgroundDark: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceDark: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceVariantDark: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val PrimaryBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryBlueVariant: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val BorderDark: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val ColorSchemeDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

val GreenPositive: Color @Composable get() = if (ColorSchemeDark) Color(0xFF6EE7B7) else Color(0xFF087443)
val YellowWarning: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDE68A) else Color(0xFF885600)
val RedAlert: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDA4AF) else Color(0xFFB42336)
