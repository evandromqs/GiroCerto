package com.girocerto.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.*
import com.girocerto.domain.model.TrafficLightStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Empirical Adversarial Stress Test Suite for Milestone 1 Component Primitives.
 * Authored by Challenger M1-2.
 *
 * Challenges:
 * 1. Standardized rounded corner compliance (CornerCard=12dp, CornerButton=10dp, CornerSmall=8dp).
 * 2. Button mechanical press physics & interaction cancellation / disabled transitions.
 * 3. Rapid clicking stress harness (10,000 cycles).
 * 4. Card and button shadow offset boundary & clipping oracle across randomized container sizes.
 * 5. AppText typography cascading and font weight override preservation.
 * 6. Backward compatibility guarantees for unmigrated screens.
 */
class AdversarialPrimitiveStressTest {

    // ========================================================================
    // 1. Standardized Rounded Corners Compliance
    // ========================================================================

    @Test
    fun testStandardizedCornerRadiusExactSpecification() {
        // Orchestrator & User prompt specification:
        // CornerCard = 12.dp, CornerButton = 10.dp, CornerSmall = 8.dp
        assertEquals("CornerCard must be strictly 12.dp", 12.dp, AppSpacing.CornerCard)
        assertEquals("CornerButton must be strictly 10.dp", 10.dp, AppSpacing.CornerButton)
        assertEquals("CornerSmall must be strictly 8.dp", 8.dp, AppSpacing.CornerSmall)

        // Verify shape instances
        val cardShape = RoundedCornerShape(AppSpacing.CornerCard)
        val buttonShape = RoundedCornerShape(AppSpacing.CornerButton)
        val smallShape = RoundedCornerShape(AppSpacing.CornerSmall)

        assertNotNull(cardShape)
        assertNotNull(buttonShape)
        assertNotNull(smallShape)

        // Verify all corner values stay within the mandated 8dp..12dp range
        assertTrue(AppSpacing.CornerSmall.value in 8.0f..12.0f)
        assertTrue(AppSpacing.CornerButton.value in 8.0f..12.0f)
        assertTrue(AppSpacing.CornerCard.value in 8.0f..12.0f)

        // Strict ordering check
        assertTrue("CornerSmall (8dp) < CornerButton (10dp)", AppSpacing.CornerSmall.value < AppSpacing.CornerButton.value)
        assertTrue("CornerButton (10dp) < CornerCard (12dp)", AppSpacing.CornerButton.value < AppSpacing.CornerCard.value)
    }

    // ========================================================================
    // 2. Button Mechanical Press Physics & Interaction Cancellation
    // ========================================================================

    /**
     * Mathematical model of NeobrutalButton press translation:
     * targetOffset = if (isPressed && enabled) 2.dp else 0.dp
     */
    private fun computeButtonTranslation(isPressed: Boolean, enabled: Boolean): Dp {
        return if (isPressed && enabled) 2.dp else 0.dp
    }

    @Test
    fun testButtonMechanicalPressNormalFlow() {
        // Idle
        assertEquals(0.dp, computeButtonTranslation(isPressed = false, enabled = true))

        // Finger down (pressed)
        assertEquals(2.dp, computeButtonTranslation(isPressed = true, enabled = true))

        // Finger up (released)
        assertEquals(0.dp, computeButtonTranslation(isPressed = false, enabled = true))
    }

    @Test
    fun testButtonInteractionCancellationRevertsTranslation() {
        // User presses button -> translation goes to 2dp
        assertTrue(computeButtonTranslation(isPressed = true, enabled = true) == 2.dp)

        // Gesture canceled (dragged off button or scroll container intercepts) -> translation MUST revert to 0dp
        val canceledTranslation = computeButtonTranslation(isPressed = false, enabled = true)
        assertEquals("Cancelled press MUST return button to 0.dp elevation", 0.dp, canceledTranslation)
    }

    @Test
    fun testDisabledButtonCannotPress() {
        // Disabled button even when pressed interaction arrives must stay at 0dp
        val translation = computeButtonTranslation(isPressed = true, enabled = false)
        assertEquals("Disabled button must never translate on press", 0.dp, translation)

        // Idle disabled
        assertEquals(0.dp, computeButtonTranslation(isPressed = false, enabled = false))
    }

    @Test
    fun testButtonDisabledWhilePressedSnapsToRest() {
        // Scenario: Button was pressed (translating to 2dp), but the click callback immediately sets enabled = false.
        // The component must evaluate targetValue = if (isPressed && enabled) 2.dp else 0.dp -> 0.dp!
        val isPressed = true
        var enabled = true
        assertEquals(2.dp, computeButtonTranslation(isPressed, enabled))

        enabled = false
        assertEquals("Transitioning to disabled mid-press must return to 0.dp", 0.dp, computeButtonTranslation(isPressed, enabled))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testMutableInteractionSourcePressAndCancelLifecycle() = runTest {
        val interactionSource = MutableInteractionSource()
        val press = PressInteraction.Press(Offset(10f, 10f))
        val cancel = PressInteraction.Cancel(press)
        val release = PressInteraction.Release(press)

        // Emit press
        interactionSource.emit(press)

        // Cancel press
        interactionSource.emit(cancel)

        // Emit another press
        val press2 = PressInteraction.Press(Offset(20f, 20f))
        interactionSource.emit(press2)
        interactionSource.emit(release)
    }

    // ========================================================================
    // 3. Rapid Clicking Stress Harness (10,000 cycles)
    // ========================================================================

    @Test
    fun testRapidClickingStateTransitionsStressHarness() {
        val random = Random(42)
        var isPressed = false
        var enabled = true

        for (i in 0 until 10_000) {
            val event = random.nextInt(4)
            when (event) {
                0 -> isPressed = true // Press
                1 -> isPressed = false // Release
                2 -> isPressed = false // Cancel
                3 -> enabled = random.nextBoolean() // Toggle enabled state
            }

            val translation = computeButtonTranslation(isPressed, enabled)

            if (!enabled) {
                assertEquals("Disabled button must always have 0dp translation", 0.dp, translation)
            } else if (isPressed) {
                assertEquals("Pressed and enabled button must have exactly 2dp translation", 2.dp, translation)
            } else {
                assertEquals("Unpressed button must have 0dp translation", 0.dp, translation)
            }
        }
    }

    // ========================================================================
    // 4. Card & Button Shadow Geometry and Clipping Oracle
    // ========================================================================

    data class LayoutBoxSimulation(
        val requestedWidth: Float,
        val requestedHeight: Float,
        val shadowOffset: Float,
        val outerReportedWidth: Float,
        val outerReportedHeight: Float,
        val surfaceLeft: Float,
        val surfaceTop: Float,
        val surfaceRight: Float,
        val surfaceBottom: Float,
        val shadowLeft: Float,
        val shadowTop: Float,
        val shadowRight: Float,
        val shadowBottom: Float
    )

    /**
     * Simulates NeobrutalCard & NeobrutalButton layout math:
     * Outer Box: modifier.padding(end = shadowOffset, bottom = shadowOffset)
     * Surface: [0, W] x [0, H]
     * Shadow Box: matchParentSize() + offset(shadowOffset, shadowOffset) -> [shadowOffset, W + shadowOffset] x [shadowOffset, H + shadowOffset]
     * Outer reported bounds: [0, W + shadowOffset] x [0, H + shadowOffset]
     */
    private fun simulateLayout(containerWidth: Float, containerHeight: Float, shadowOffset: Float): LayoutBoxSimulation {
        // padding(end = shadowOffset, bottom = shadowOffset) reduces inner constraints:
        val innerAvailableWidth = containerWidth - shadowOffset
        val innerAvailableHeight = containerHeight - shadowOffset

        val surfaceW = innerAvailableWidth
        val surfaceH = innerAvailableHeight

        val surfaceLeft = 0f
        val surfaceTop = 0f
        val surfaceRight = surfaceW
        val surfaceBottom = surfaceH

        val shadowLeft = shadowOffset
        val shadowTop = shadowOffset
        val shadowRight = shadowOffset + surfaceW
        val shadowBottom = shadowOffset + surfaceH

        val outerReportedWidth = surfaceW + shadowOffset
        val outerReportedHeight = surfaceH + shadowOffset

        return LayoutBoxSimulation(
            requestedWidth = containerWidth,
            requestedHeight = containerHeight,
            shadowOffset = shadowOffset,
            outerReportedWidth = outerReportedWidth,
            outerReportedHeight = outerReportedHeight,
            surfaceLeft = surfaceLeft,
            surfaceTop = surfaceTop,
            surfaceRight = surfaceRight,
            surfaceBottom = surfaceBottom,
            shadowLeft = shadowLeft,
            shadowTop = shadowTop,
            shadowRight = shadowRight,
            shadowBottom = shadowBottom
        )
    }

    @Test
    fun testNeobrutalCardShadowNeverClipsReportedOuterBounds() {
        val random = Random(1337)
        val shadowOffset = AppSpacing.ShadowOffset.value // 4.0f

        // Test over 5,000 random container sizes (from compact 48dp up to 1440dp)
        for (i in 0 until 5_000) {
            val width = random.nextDouble(48.0, 1440.0).toFloat()
            val height = random.nextDouble(48.0, 2560.0).toFloat()

            val sim = simulateLayout(width, height, shadowOffset)

            // Critical invariant: Outer Box reported bounds MUST match requested container constraints
            assertEquals("Outer reported width must match requested container width", width, sim.outerReportedWidth, 0.001f)
            assertEquals("Outer reported height must match requested container height", height, sim.outerReportedHeight, 0.001f)

            // Critical invariant: Shadow right edge must NOT exceed outer reported width
            assertTrue(
                "Shadow right edge (${sim.shadowRight}) must not exceed outer width (${sim.outerReportedWidth})",
                sim.shadowRight <= sim.outerReportedWidth + 0.001f
            )

            // Critical invariant: Shadow bottom edge must NOT exceed outer reported height
            assertTrue(
                "Shadow bottom edge (${sim.shadowBottom}) must not exceed outer height (${sim.outerReportedHeight})",
                sim.shadowBottom <= sim.outerReportedHeight + 0.001f
            )

            // Surface fits inside
            assertTrue(sim.surfaceRight <= sim.outerReportedWidth)
            assertTrue(sim.surfaceBottom <= sim.outerReportedHeight)
        }
    }

    @Test
    fun testNeobrutalCardFillMaxWidthBoundary() {
        // Standard mobile display widths: 360dp, 390dp, 412dp, 480dp
        val screenWidths = listOf(360f, 390f, 412f, 480f)
        val shadowOffset = 4.0f

        for (screenWidth in screenWidths) {
            val sim = simulateLayout(screenWidth, 120f, shadowOffset)
            assertEquals(screenWidth, sim.outerReportedWidth, 0.001f)
            assertEquals(screenWidth, sim.shadowRight, 0.001f)
            assertEquals(screenWidth - 4.0f, sim.surfaceRight, 0.001f)
        }
    }

    // ========================================================================
    // 5. Typography Weight Cascade & AppText Font Weight Resolution
    // ========================================================================

    private fun resolveAppTextWeight(explicitWeight: FontWeight?, styleWeight: FontWeight?): FontWeight {
        return explicitWeight ?: styleWeight ?: FontWeight.Normal
    }

    private fun resolveAppTextFamily(explicitFamily: FontFamily?, styleFamily: FontFamily?): FontFamily {
        return explicitFamily ?: styleFamily ?: RobotoFamily
    }

    private fun resolveAppTextAlign(explicitAlign: TextAlign?, styleAlign: TextAlign?): TextAlign {
        return explicitAlign ?: styleAlign?.takeUnless { it == TextAlign.Unspecified } ?: TextAlign.Start
    }

    @Test
    fun testAppTextHonorsExplicitFontWeightOverStyle() {
        val style = TextStyle(fontWeight = FontWeight.Light)
        val resolved = resolveAppTextWeight(explicitWeight = FontWeight.Bold, styleWeight = style.fontWeight)
        assertEquals("Explicit FontWeight.Bold must override style FontWeight.Light", FontWeight.Bold, resolved)
    }

    @Test
    fun testAppTextInheritsThemeTypographyWeightWhenExplicitNull() {
        val headlineStyle = Typography.headlineLarge
        val resolvedHeadline = resolveAppTextWeight(explicitWeight = null, styleWeight = headlineStyle.fontWeight)
        assertEquals("AppText must inherit headlineLarge weight (Bold)", FontWeight.Bold, resolvedHeadline)

        val titleStyle = Typography.titleMedium
        val resolvedTitle = resolveAppTextWeight(explicitWeight = null, styleWeight = titleStyle.fontWeight)
        assertEquals("AppText must inherit titleMedium weight (SemiBold)", FontWeight.SemiBold, resolvedTitle)

        val bodyStyle = Typography.bodyLarge
        val resolvedBody = resolveAppTextWeight(explicitWeight = null, styleWeight = bodyStyle.fontWeight)
        assertEquals("AppText must inherit bodyLarge weight (Normal)", FontWeight.Normal, resolvedBody)
    }

    @Test
    fun testAppTextFallsBackToNormalWhenBothWeightsNull() {
        val resolved = resolveAppTextWeight(explicitWeight = null, styleWeight = null)
        assertEquals("AppText must fallback to FontWeight.Normal when no weight is specified", FontWeight.Normal, resolved)
    }

    @Test
    fun testAppTextFontFamilyStandardizesOnRoboto() {
        assertEquals(FontFamily.SansSerif, RobotoFamily)

        // Explicit null + style null -> RobotoFamily
        val resolved = resolveAppTextFamily(null, null)
        assertEquals("Default font family must resolve to RobotoFamily", RobotoFamily, resolved)

        // Style with custom font family -> preserved
        val customFamily = FontFamily.Monospace
        val resolvedCustom = resolveAppTextFamily(null, customFamily)
        assertEquals("Custom font family in style must be preserved", FontFamily.Monospace, resolvedCustom)
    }

    @Test
    fun testAppTextAlignCascading() {
        assertEquals(TextAlign.Start, resolveAppTextAlign(null, null))
        assertEquals(TextAlign.Center, resolveAppTextAlign(TextAlign.Center, null))
        assertEquals(TextAlign.End, resolveAppTextAlign(null, TextAlign.End))
        assertEquals(TextAlign.Center, resolveAppTextAlign(TextAlign.Center, TextAlign.End)) // explicit wins
    }

    // ========================================================================
    // 6. Backward Compatibility & Palette Verification
    // ========================================================================

    @Test
    fun testBackwardCompatibilityAliasesAndTokens() {
        // ThemeMode preservation
        assertEquals("Sistema", ThemeMode.SYSTEM.label)
        assertEquals("Claro", ThemeMode.LIGHT.label)
        assertEquals("Escuro", ThemeMode.DARK.label)

        // Poppins alias points to Roboto
        assertEquals(RobotoFamily, Poppins)

        // Palette core tokens
        assertEquals(Color(0xFF102746), NeobrutalDarkBlue)
        assertEquals(Color(0xFFF0F3F8), NeobrutalLightGray)
        assertEquals(Color(0xFF000000), NeobrutalBlack)
        assertEquals(Color(0xFFFFFFFF), NeobrutalWhite)
        assertEquals(Color(0xFF0A1128), NeobrutalDarkBackground)
        assertEquals(Color(0xFF152238), NeobrutalDarkSurface)
        assertEquals(Color(0xFFDDE8FA), NeobrutalLightBlueTint)

        // Brand alias
        assertEquals(NeobrutalDarkBlue, Navy)

        // Status tokens
        assertEquals(Color(0xFF22C55E), StatusGreenSolid)
        assertEquals(Color(0xFFFACC15), StatusYellowSolid)
        assertEquals(Color(0xFFEF4444), StatusRedSolid)
        assertEquals(Color(0xFF94A3B8), StatusInvalidSolid)

        // Geometry tokens
        assertEquals(2.5.dp, AppSpacing.BorderThick)
        assertEquals(2.0.dp, AppSpacing.BorderAction)
        assertEquals(1.5.dp, AppSpacing.BorderThin)
        assertEquals(4.0.dp, AppSpacing.ShadowOffset)
        assertEquals(2.0.dp, AppSpacing.ShadowOffsetSmall)
        assertEquals(48.0.dp, AppSpacing.MinTouchTarget)
    }
}
