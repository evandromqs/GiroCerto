package com.girocerto.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// ============================================================================
// Roboto Typography Foundation (Android Native Sans-Serif)
// ============================================================================
val RobotoFamily: FontFamily = FontFamily.SansSerif
val Roboto: FontFamily = RobotoFamily

// Backward-compatibility alias during refactoring
val Poppins: FontFamily = RobotoFamily

private fun roboto(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
    align: TextAlign = TextAlign.Start
) = TextStyle(
    fontFamily = RobotoFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    textAlign = align
)

val Typography = Typography(
    // Display styles (Hero / impact headers)
    displayLarge = roboto(57, 64, FontWeight.Bold, -0.25),
    displayMedium = roboto(45, 52, FontWeight.Bold, 0.0),
    displaySmall = roboto(36, 44, FontWeight.Bold, 0.0),

    // Headlines (Screen titles, major metric values)
    headlineLarge = roboto(32, 40, FontWeight.Bold, 0.0),
    headlineMedium = roboto(28, 36, FontWeight.Bold, 0.0),
    headlineSmall = roboto(24, 32, FontWeight.Bold, 0.0),

    // Titles (Card headers, modal titles, section headers)
    titleLarge = roboto(22, 28, FontWeight.Bold, 0.0),
    titleMedium = roboto(18, 24, FontWeight.SemiBold, 0.15),
    titleSmall = roboto(16, 22, FontWeight.SemiBold, 0.1),

    // Body (Standard reading copy, details, descriptions)
    bodyLarge = roboto(16, 24, FontWeight.Normal, 0.5),
    bodyMedium = roboto(14, 20, FontWeight.Normal, 0.25),
    bodySmall = roboto(12, 16, FontWeight.Normal, 0.4),

    // Labels (Button text, badges, tabs, micro copy)
    labelLarge = roboto(14, 20, FontWeight.Medium, 0.1),
    labelMedium = roboto(12, 16, FontWeight.Medium, 0.5),
    labelSmall = roboto(11, 16, FontWeight.Medium, 0.5)
)
