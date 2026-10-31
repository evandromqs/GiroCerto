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
    // Neobrutalist Geometry & Stroke Tokens (R1 / R2 / R4)
    // Standardized uniform rounded corners: CornerCard 12dp, CornerButton 10dp, CornerSmall 8dp
    // ========================================================================
    val BorderThick = 2.5.dp       // Standard Neobrutal card & primary button border
    val BorderAction = 2.0.dp      // Modal action buttons & compact dialog borders
    val BorderThin = 1.5.dp        // Badges, chips, secondary outline
    val ShadowOffset = 4.dp        // Hard drop shadow offset (x = 4.dp, y = 4.dp)
    val ShadowOffsetSmall = 2.dp   // Pressed button / badge hard shadow
    val CornerNone = 0.dp          // Zero radius reference
    val CornerSmall = 8.dp         // Badges, chips, compact action buttons
    val CornerCard = 12.dp         // Neobrutal cards standardized rounded corner
    val CornerButton = 10.dp       // Standard tactile buttons rounded corner
    val MinTouchTarget = 48.dp     // WCAG accessibility minimum touch target

    // ========================================================================
    // Floating Overlay & Modal Tokens (R4)
    // ========================================================================
    val ModalWidthCompact = 280.dp // Compact modal width (fits 272-300dp range)
    val ModalActionSpacing = 8.dp  // Spacing between "Salvar" & "Destino" action buttons
}
