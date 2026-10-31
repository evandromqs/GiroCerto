package com.girocerto.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ============================================================================
// Neobrutalist Core Palette Tokens (Milestone 1 / R1)
// ============================================================================
val NeobrutalDarkBlue = Color(0xFF102746)       // Primary brand dark blue
val NeobrutalLightGray = Color(0xFFF0F3F8)      // Light mode background/neutral
val NeobrutalBlack = Color(0xFF000000)          // Pure black (text, borders, hard drop shadows)
val NeobrutalWhite = Color(0xFFFFFFFF)          // Pure white (card surfaces, dark mode text)
val NeobrutalDarkBackground = Color(0xFF0A1128) // Deep dark mode background
val NeobrutalDarkSurface = Color(0xFF152238)    // Dark mode card surface fill
val NeobrutalLightBlueTint = Color(0xFFDDE8FA)  // Primary container / subtle blue highlight

// Brand alias
val Navy = NeobrutalDarkBlue

// ============================================================================
// Semantic Compatibility Tokens (Preserves 100% backward compatibility)
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
// Semantic Status Tokens (High-contrast for badges & financial KPIs)
// ============================================================================
val GreenPositive: Color @Composable get() = if (ColorSchemeDark) Color(0xFF6EE7B7) else Color(0xFF087443)
val YellowWarning: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDE68A) else Color(0xFF885600)
val RedAlert: Color @Composable get() = if (ColorSchemeDark) Color(0xFFFDA4AF) else Color(0xFFB42336)

// Neobrutalist Solid Status Backgrounds (Pure opaque fills for badges)
val StatusGreenSolid = Color(0xFF22C55E)
val StatusYellowSolid = Color(0xFFFACC15)
val StatusRedSolid = Color(0xFFEF4444)
val StatusInvalidSolid = Color(0xFF94A3B8)
