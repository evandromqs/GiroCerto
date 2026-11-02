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
        // R1: Royal blue #1D4ED8 and light gray #F5F7FB, pure black #000000 and pure white #FFFFFF
        assertEquals("Royal Blue primary must be #1D4ED8", Color(0xFF1D4ED8), NeobrutalDarkBlue)
        assertEquals("Light Gray background must be #F5F7FB", Color(0xFFF5F7FB), NeobrutalLightGray)
        assertEquals("Neobrutal Black must be pure #000000", Color(0xFF000000), NeobrutalBlack)
        assertEquals("Neobrutal White must be pure #FFFFFF", Color(0xFFFFFFFF), NeobrutalWhite)
        assertEquals("Dark theme background must be #0B1120", Color(0xFF0B1120), NeobrutalDarkBackground)
        assertEquals("Dark theme surface must be #121B2C", Color(0xFF121B2C), NeobrutalDarkSurface)
        assertEquals("Primary container blue tint must be #EEF2F7", Color(0xFFEEF2F7), NeobrutalLightBlueTint)
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
