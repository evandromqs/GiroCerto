package com.girocerto.app.theme

import androidx.compose.ui.unit.dp

object AppSpacing {
    // ========================================================================
    // Legacy Screen Layout Tokens (100% backward-compatible with existing screens)
    // ========================================================================
    val Screen = 20.dp
    val Content = 16.dp
    val Section = 16.dp
    val Gap = 12.dp
    val Small = 8.dp

    // ========================================================================
    // Standard 4/8-point Spacing Grid
    // ========================================================================
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Compact = 10.dp
    val Medium = 14.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp

    // ========================================================================
    // Geometry & Stroke Tokens
    // Standardized uniform rounded corners: CornerCard 16dp, CornerButton 12dp, CornerSmall 8dp
    // ========================================================================
    val BorderThick = 1.dp       // Standard card & primary button border
    val BorderAction = 1.dp      // Modal action buttons & compact dialog borders
    val BorderThin = 1.dp        // Badges, chips, secondary outline
    val ShadowOffset = 0.dp        // Flat surfaces: elevation comes from tonal contrast
    val ShadowOffsetSmall = 0.dp   // Flat surfaces: no hard shadow
    val CornerNone = 0.dp          // Zero radius reference
    val CornerSmall = 8.dp         // Badges, chips, compact action buttons
    val CornerCard = 16.dp         // Card rounded corner
    val CornerButton = 12.dp       // Primary button rounded corner
    val MinTouchTarget = 48.dp     // WCAG accessibility minimum touch target

    // ========================================================================
    // Floating Overlay & Modal Tokens (R4)
    // ========================================================================
    val ModalWidthCompact = 280.dp // Compact modal width (fits 272-300dp range)
    val ModalActionSpacing = 8.dp  // Spacing between "Salvar" & "Destino" action buttons
}
