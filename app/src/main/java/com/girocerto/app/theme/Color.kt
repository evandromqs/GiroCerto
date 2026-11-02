package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============================================================================
// Single brand color. Light and dark modes use the same hue family; the dark
// tone is a lighter step so it keeps contrast on dark surfaces.
// ============================================================================
val BrandBlue = Color(0xFF1D4ED8)
val BrandBlueDark = Color(0xFF7BA7FF)
val BrandBluePressed = Color(0xFF1E40AF)

// Legacy token names kept for call-site compatibility.
val NeobrutalDarkBlue = BrandBlue
val NeobrutalLightGray = Color(0xFFF5F7FB)
val NeobrutalBlack = Color(0xFF000000)
val NeobrutalWhite = Color(0xFFFFFFFF)
val NeobrutalDarkBackground = Color(0xFF0B1120)
val NeobrutalDarkSurface = Color(0xFF121B2C)
val NeobrutalLightBlueTint = Color(0xFFEEF2F7)

// Brand alias
val Navy = BrandBlue

// ============================================================================
// Semantic Compatibility Tokens
// ============================================================================
val BackgroundDark: Color @Composable get() = MaterialTheme.colorScheme.background
val SurfaceDark: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceVariantDark: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val PrimaryBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
val PrimaryBlueVariant: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val BorderDark: Color @Composable get() = MaterialTheme.colorScheme.outline

private val ColorSchemeDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

// ============================================================================
// Semantic Status Tokens (kept: they carry financial meaning)
// ============================================================================
val GreenPositive: Color @Composable get() = if (ColorSchemeDark) Color(0xFF6EE7B7) else Color(0xFF087443)
val YellowWarning: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDE68A) else Color(0xFF885600)
val RedAlert: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDA4AF) else Color(0xFFB42336)

val StatusGreenSolid = Color(0xFF16A34A)
val StatusYellowSolid = Color(0xFFEAB308)
val StatusRedSolid = Color(0xFFDC2626)
val StatusInvalidSolid = Color(0xFF94A3B8)
