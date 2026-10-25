package com.girocerto.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.girocerto.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

val Poppins = FontFamily(Font(R.font.poppins_medium, FontWeight.Medium))

private fun type(size: Int, height: Int) = TextStyle(
    fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = size.sp,
    lineHeight = height.sp, letterSpacing = 0.sp, textAlign = TextAlign.Justify)
val Typography = Typography(
    headlineLarge = type(32, 40), headlineMedium = type(28, 36),
    headlineSmall = type(24, 32), titleLarge = type(22, 30),
    titleMedium = type(18, 26), titleSmall = type(16, 24),
    bodyLarge = type(16, 24), bodyMedium = type(14, 22), bodySmall = type(12, 20),
    labelLarge = type(14, 20), labelMedium = type(12, 18),
    labelSmall = type(11, 16)
)
