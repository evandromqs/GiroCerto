package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============================================================================
// Official Giro Certo High-Voltage Palette:
// Royal/Electric Blue for authority/branding, Neon Lime for high-visibility
// financial action and Pure White for ultra-sharp legibility.
// ============================================================================
val BrandRoyalBlue = Color(0xFF0131CD)
val BrandNeonLime = Color(0xFFB9FC01)
val BrandNeonLimeDark = Color(0xFFA5E200)

// Official aliases
val BrandNavy = BrandRoyalBlue

// Legacy tokens kept for unit test backward-compatibility suite
val NeobrutalDarkBlue = Color(0xFF0B1F3A)
val BrandGreen = Color(0xFF16C784)
val BrandGreenPressed = Color(0xFF0FA36B)
val NeobrutalLightGray = Color(0xFFF4F7F6)
val NeobrutalBlack = Color(0xFF000000)
val NeobrutalWhite = Color(0xFFFFFFFF)
val NeobrutalDarkBackground = Color(0xFF0B1F3A)
val NeobrutalDarkSurface = Color(0xFF102A46)
val NeobrutalLightBlueTint = Color(0xFFE8F8F2)
val Navy = NeobrutalDarkBlue

// Compatibility aliases while call sites migrate to semantic names.
val BrandBlue = BrandRoyalBlue
val BrandBlueDark = BrandNeonLime
val BrandBluePressed = BrandNeonLimeDark

// Semantic base surfaces for contrast
val CockpitDarkBackground = Color(0xFF060B18)
val CockpitDarkSurface = Color(0xFF0E172E)
val CockpitDarkSurfaceVariant = Color(0xFF162345)
val CockpitLightBackground = Color(0xFFF4F6FC)
val CockpitLightSurface = Color(0xFFFFFFFF)
val CockpitLightSurfaceVariant = Color(0xFFE8EDF9)

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
val GreenPositive: Color @Composable get() = if (ColorSchemeDark) BrandNeonLime else Color(0xFF0131CD)
val YellowWarning: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFFD233) else Color(0xFF996B00)
val RedAlert: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFF4D6D) else Color(0xFFC70D2C)

val StatusGreenSolid = Color(0xFF16A34A)
val StatusYellowSolid = Color(0xFFEAB308)
val StatusRedSolid = Color(0xFFDC2626)
val StatusInvalidSolid = Color(0xFF94A3B8)
