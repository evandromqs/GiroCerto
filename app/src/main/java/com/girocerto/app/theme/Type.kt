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

private fun type(size: Int, height: Int, align: TextAlign) = TextStyle(
    fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = size.sp,
    lineHeight = height.sp, letterSpacing = 0.sp, textAlign = align)
val Typography = Typography(
    headlineLarge = type(32, 40, TextAlign.Center), headlineMedium = type(28, 36, TextAlign.Center),
    headlineSmall = type(24, 32, TextAlign.Center), titleLarge = type(22, 30, TextAlign.Center),
    titleMedium = type(18, 26, TextAlign.Center), titleSmall = type(16, 24, TextAlign.Center),
    bodyLarge = type(16, 24, TextAlign.Start), bodyMedium = type(14, 22, TextAlign.Start), bodySmall = type(12, 20, TextAlign.Start),
    labelLarge = type(14, 20, TextAlign.Start), labelMedium = type(12, 18, TextAlign.Start),
    labelSmall = type(11, 16, TextAlign.Start)
)
