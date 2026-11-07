package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============================================================================
// Official Giro Certo palette: navy for trust/control, green for positive
// action and white for maximum legibility in a moving vehicle.
// ============================================================================
val BrandNavy = Color(0xFF0B1F3A)
val BrandGreen = Color(0xFF16C784)
val BrandGreenPressed = Color(0xFF0FA66E)

// Compatibility aliases while call sites migrate to semantic names.
val BrandBlue = BrandGreen
val BrandBlueDark = Color(0xFF5EE6B2)
val BrandBluePressed = BrandGreenPressed

// Legacy token names kept for call-site compatibility.
val NeobrutalDarkBlue = BrandNavy
val NeobrutalLightGray = Color(0xFFF4F7F6)
val NeobrutalBlack = Color(0xFF000000)
val NeobrutalWhite = Color(0xFFFFFFFF)
val NeobrutalDarkBackground = BrandNavy
val NeobrutalDarkSurface = Color(0xFF102A46)
val NeobrutalLightBlueTint = Color(0xFFE8F8F2)

// Brand alias
val Navy = BrandNavy

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
