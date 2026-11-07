package com.girocerto.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Deterministic JUnit tests for Milestone 1 Design System Tokens & Typography.
 */
class ThemeTokensUnitTest {

    @Test
    fun verifyNeobrutalistCorePalette() {
        assertEquals("Official navy must be #0B1F3A", Color(0xFF0B1F3A), NeobrutalDarkBlue)
        assertEquals("Official green must be #16C784", Color(0xFF16C784), BrandGreen)
        assertEquals("Light background must be #F4F7F6", Color(0xFFF4F7F6), NeobrutalLightGray)
        assertEquals("Neobrutal Black must be pure #000000", Color(0xFF000000), NeobrutalBlack)
        assertEquals("Neobrutal White must be pure #FFFFFF", Color(0xFFFFFFFF), NeobrutalWhite)
        assertEquals("Dark theme background must be #0B1F3A", Color(0xFF0B1F3A), NeobrutalDarkBackground)
        assertEquals("Dark theme surface must be #102A46", Color(0xFF102A46), NeobrutalDarkSurface)
        assertEquals("Primary container green tint must be #E8F8F2", Color(0xFFE8F8F2), NeobrutalLightBlueTint)
        assertEquals("Brand alias Navy must match NeobrutalDarkBlue", NeobrutalDarkBlue, Navy)
    }

    @Test
    fun verifyThemeModeEnumAndLabels() {
        // Must preserve compatibility with ReleaseUiTest
        val modes = ThemeMode.entries
        assertEquals(2, modes.size)
        assertEquals("Claro", ThemeMode.LIGHT.label)
        assertEquals("Escuro", ThemeMode.DARK.label)
    }

    @Test
    fun verifyRobotoTypographySystem() {
        // R1: Default font family must be Roboto (SansSerif)
        assertEquals(FontFamily.SansSerif, Roboto)
        assertEquals(FontFamily.SansSerif, RobotoFamily)

        assertNotNull(Typography.bodyLarge)
        assertNotNull(Typography.headlineMedium)
        assertNotNull(Typography.titleMedium)
        assertNotNull(Typography.labelLarge)

        assertEquals(FontFamily.SansSerif, Typography.bodyLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, Typography.headlineMedium.fontFamily)
        assertEquals(FontFamily.SansSerif, Typography.titleMedium.fontFamily)

        // Verify hierarchy weights
        assertEquals(FontWeight.Bold, Typography.headlineLarge.fontWeight)
        assertEquals(FontWeight.Bold, Typography.headlineMedium.fontWeight)
        assertEquals(FontWeight.SemiBold, Typography.titleMedium.fontWeight)
        assertEquals(FontWeight.Normal, Typography.bodyLarge.fontWeight)
        assertEquals(FontWeight.Medium, Typography.labelLarge.fontWeight)
    }

    @Test
    fun verifyNeobrutalistSpacingAndGeometryTokens() {
        // R1 & R2: Neobrutal border, shadow, and standardized rounded corners
        assertEquals(1.dp, AppSpacing.BorderThick)
        assertEquals(1.dp, AppSpacing.BorderAction)
        assertEquals(1.dp, AppSpacing.BorderThin)
        assertEquals(0.0.dp, AppSpacing.ShadowOffset)
        assertEquals(0.0.dp, AppSpacing.ShadowOffsetSmall)
        assertEquals(48.0.dp, AppSpacing.MinTouchTarget)
        assertEquals(8.0.dp, AppSpacing.ModalActionSpacing)
        assertEquals(280.0.dp, AppSpacing.ModalWidthCompact)

        // Standardized rounded corners
        assertEquals(16.0.dp, AppSpacing.CornerCard)
        assertEquals(12.0.dp, AppSpacing.CornerButton)
        assertEquals(8.0.dp, AppSpacing.CornerSmall)
        assertEquals(0.0.dp, AppSpacing.CornerNone)

        // Legacy layout tokens
        assertEquals(20.0.dp, AppSpacing.Screen)
        assertEquals(16.0.dp, AppSpacing.Content)
        assertEquals(16.0.dp, AppSpacing.Section)
        assertEquals(12.0.dp, AppSpacing.Gap)
        assertEquals(8.0.dp, AppSpacing.Small)
    }

    @Test
    fun verifySemanticStatusColorTokens() {
        // R1 & R2: High-contrast solid status fills
        assertEquals(Color(0xFF16A34A), StatusGreenSolid)
        assertEquals(Color(0xFFEAB308), StatusYellowSolid)
        assertEquals(Color(0xFFDC2626), StatusRedSolid)
        assertEquals(Color(0xFF94A3B8), StatusInvalidSolid)
    }
}
