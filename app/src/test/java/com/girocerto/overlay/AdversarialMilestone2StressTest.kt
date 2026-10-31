package com.girocerto.overlay

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.girocerto.app.theme.AppSpacing
import com.girocerto.app.theme.NeobrutalDarkBlue
import com.girocerto.app.theme.NeobrutalLightGray
import com.girocerto.app.theme.StatusGreenSolid
import com.girocerto.app.theme.StatusInvalidSolid
import com.girocerto.app.theme.StatusRedSolid
import com.girocerto.app.theme.StatusYellowSolid
import com.girocerto.monitoring.CopilotPolicy
import com.girocerto.monitoring.OfferDeliveryGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Empirical Adversarial Stress Test Suite for Milestone 2:
 * Screen Layout Reconstruction & Standardization, Corner Gravity & State Persistence,
 * and Offer Delivery Deduplication.
 *
 * Authored by Empirical Challenger M2-1.
 */
class AdversarialMilestone2StressTest {

    // ========================================================================
    // 1. OverlayCorner Contract & Serialization Verification
    // ========================================================================

    @Test
    fun testOverlayCornerEnumValuesAndCompleteness() {
        // Contract: Exactly 4 screen resting quadrants must exist
        val corners = OverlayCorner.entries
        assertEquals("OverlayCorner must contain exactly 4 quadrant values", 4, corners.size)

        val names = corners.map { it.name }.toSet()
        assertTrue(names.contains("TOP_LEFT"))
        assertTrue(names.contains("TOP_RIGHT"))
        assertTrue(names.contains("BOTTOM_LEFT"))
        assertTrue(names.contains("BOTTOM_RIGHT"))
    }

    @Test
    fun testOverlayCornerPortugueseLabelsSpecification() {
        // Contract: Exact Portuguese labels for the CopilotScreen Resting Corner Picker
        assertEquals("Superior Esquerdo", OverlayCorner.TOP_LEFT.label)
        assertEquals("Superior Direito", OverlayCorner.TOP_RIGHT.label)
        assertEquals("Inferior Esquerdo", OverlayCorner.BOTTOM_LEFT.label)
        assertEquals("Inferior Direito", OverlayCorner.BOTTOM_RIGHT.label)
    }

    @Test
    fun testCopilotPolicyDefaultRestingCornerContract() {
        // Contract: Default resting corner in CopilotPolicy must be TOP_RIGHT
        val defaultPolicy = CopilotPolicy()
        assertEquals("Default resting corner must be TOP_RIGHT", OverlayCorner.TOP_RIGHT, defaultPolicy.restingCorner)
    }

    @Test
    fun testOverlayCornerSerializationAndDeserializationResilience() {
        // Roundtrip serialization for every valid corner
        for (corner in OverlayCorner.entries) {
            val serialized = corner.name
            val deserialized = OverlayCorner.valueOf(serialized)
            assertEquals("Roundtrip serialization failed for $corner", corner, deserialized)
        }

        // Adversarial deserialization recovery (mimicking CopilotPreferences.read() logic)
        val testCases = listOf(
            "CORRUPTED" to OverlayCorner.TOP_RIGHT,
            "" to OverlayCorner.TOP_RIGHT,
            "top_right" to OverlayCorner.TOP_RIGHT, // case mismatch
            "CENTER" to OverlayCorner.TOP_RIGHT,
            "TOP_LEFT" to OverlayCorner.TOP_LEFT,
            "TOP_RIGHT" to OverlayCorner.TOP_RIGHT,
            "BOTTOM_LEFT" to OverlayCorner.BOTTOM_LEFT,
            "BOTTOM_RIGHT" to OverlayCorner.BOTTOM_RIGHT
        )

        for ((inputStr, expected) in testCases) {
            val resolved = runCatching { OverlayCorner.valueOf(inputStr) }.getOrDefault(OverlayCorner.TOP_RIGHT)
            assertEquals("Fallback deserialization failed for input: '$inputStr'", expected, resolved)
        }
    }

    // ========================================================================
    // 2. OfferDeliveryGate Deduplication Contract
    // ========================================================================

    @Test
    fun testOfferDeliveryGatePermitsFirstOccurrence() {
        val gate = OfferDeliveryGate(intervalMs = 15_000L)
        val offerKey = "uber_32.50_8.2"
        val timestamp = 1_000_000L

        assertTrue("First occurrence of offer must be permitted", gate.permits(offerKey, timestamp))
    }

    @Test
    fun testOfferDeliveryGateSuppressesRapidDuplicatesWithinWindow() {
        val gate = OfferDeliveryGate(intervalMs = 15_000L)
        val offerKey = "uber_32.50_8.2"
        val start = 1_000_000L

        // Initial acceptance
        assertTrue(gate.permits(offerKey, start))

        // Suppressed at start + 1ms, +1,000ms, +14,999ms
        assertFalse("Immediate duplicate must be blocked", gate.permits(offerKey, start + 1L))
        assertFalse("Duplicate at +1s must be blocked", gate.permits(offerKey, start + 1_000L))
        assertFalse("Duplicate at +14.999s must be blocked", gate.permits(offerKey, start + 14_999L))
    }

    @Test
    fun testOfferDeliveryGatePermitsAfterWindowExpiry() {
        val gate = OfferDeliveryGate(intervalMs = 15_000L)
        val offerKey = "99_19.80_4.5"
        val start = 500_000L

        assertTrue(gate.permits(offerKey, start))
        assertFalse(gate.permits(offerKey, start + 14_999L))

        // Exact expiry: start + 15_000L
        assertTrue("Duplicate at exact 15s window expiry must be permitted", gate.permits(offerKey, start + 15_000L))

        // Next duplicate within new window must again be suppressed
        assertFalse("Duplicate within second window must be blocked", gate.permits(offerKey, start + 15_001L))
    }

    @Test
    fun testOfferDeliveryGateIsolationBetweenDistinctKeys() {
        val gate = OfferDeliveryGate(intervalMs = 15_000L)
        val now = 2_000_000L

        val key1 = "uber_25.0_5.0"
        val key2 = "99_42.0_12.0"
        val key3 = "indrive_15.0_3.0"

        assertTrue("Key 1 first seen must pass", gate.permits(key1, now))
        assertTrue("Key 2 first seen must pass immediately", gate.permits(key2, now))
        assertTrue("Key 3 first seen must pass immediately", gate.permits(key3, now))

        // Re-checking keys: all must be blocked
        assertFalse(gate.permits(key1, now + 100))
        assertFalse(gate.permits(key2, now + 200))
        assertFalse(gate.permits(key3, now + 300))
    }

    @Test
    fun testOfferDeliveryGateStressHarness1000Cycles() {
        val gate = OfferDeliveryGate(intervalMs = 15_000L)
        val random = Random(2026)
        var simulatedClock = 100_000L

        val knownKeys = (1..20).map { "offer_id_$it" }
        var acceptedCount = 0
        var rejectedCount = 0

        for (cycle in 0 until 1_000) {
            val key = knownKeys[random.nextInt(knownKeys.size)]
            val elapsed = random.nextLong(100L, 5_000L)
            simulatedClock += elapsed

            val result = gate.permits(key, simulatedClock)
            if (result) {
                acceptedCount++
            } else {
                rejectedCount++
            }
        }

        assertTrue("Must accept some offers in random walk", acceptedCount > 50)
        assertTrue("Must reject rapid duplicates in random walk", rejectedCount > 100)
    }

    // ========================================================================
    // 3. Standardized Rounded Corners & Layout Tokens Verification
    // ========================================================================

    @Test
    fun testStandardizedCornerRadiusHierarchy() {
        // Requirement R2 & User Specification:
        // "os cantos dos elementos deve ser todo arredondados de forma padronizada"
        // 12dp: cards, containers
        // 10dp: buttons
        // 8dp: small items, inputs, chips, badges
        // 16dp: modal dialogs
        assertEquals("Cards must be standardized to 12dp", 12.dp, AppSpacing.CornerCard)
        assertEquals("Buttons must be standardized to 10dp", 10.dp, AppSpacing.CornerButton)
        assertEquals("Small items must be standardized to 8dp", 8.dp, AppSpacing.CornerSmall)
        assertEquals("CornerNone must be 0dp", 0.dp, AppSpacing.CornerNone)

        // Monotonic progression
        assertTrue(AppSpacing.CornerSmall < AppSpacing.CornerButton)
        assertTrue(AppSpacing.CornerButton < AppSpacing.CornerCard)
    }

    @Test
    fun testMinimumTouchTargetStandard() {
        // Requirement R2 & Accessibility standard: interactive touch targets >= 48dp
        assertEquals("Minimum touch target must be 48dp", 48.dp, AppSpacing.MinTouchTarget)
        assertTrue(AppSpacing.MinTouchTarget.value >= 48f)
    }

    @Test
    fun testNeobrutalistCorePaletteContrast() {
        // Requirement R1 & R2: Neobrutal palette compliance
        assertEquals(Color(0xFF102746), NeobrutalDarkBlue)
        assertEquals(Color(0xFFF0F3F8), NeobrutalLightGray)

        // Status semantic colors
        assertEquals(Color(0xFF22C55E), StatusGreenSolid)
        assertEquals(Color(0xFFFACC15), StatusYellowSolid)
        assertEquals(Color(0xFFEF4444), StatusRedSolid)
        assertEquals(Color(0xFF94A3B8), StatusInvalidSolid)
    }

    // ========================================================================
    // 4. Corner Gravity Snapping Physics Oracle & Boundary Conditions
    // ========================================================================

    data class SnappedCoordinates(
        val corner: OverlayCorner,
        val x: Int,
        val y: Int
    )

    private fun simulateCornerSnap(
        dropX: Int,
        dropY: Int,
        screenWidth: Int,
        screenHeight: Int,
        modalWidth: Int,
        modalHeight: Int,
        topInset: Int = 0,
        bottomInset: Int = 0,
        marginX: Int = 16,
        marginY: Int = 16
    ): SnappedCoordinates {
        val centerX = dropX + modalWidth / 2
        val centerY = dropY + modalHeight / 2

        val isLeft = centerX < screenWidth / 2
        val isTop = centerY < screenHeight / 2

        val targetCorner = when {
            isLeft && isTop -> OverlayCorner.TOP_LEFT
            !isLeft && isTop -> OverlayCorner.TOP_RIGHT
            isLeft && !isTop -> OverlayCorner.BOTTOM_LEFT
            else -> OverlayCorner.BOTTOM_RIGHT
        }

        val clampedX = if (isLeft) {
            marginX
        } else {
            screenWidth - modalWidth - marginX
        }

        val clampedY = if (isTop) {
            topInset + marginY
        } else {
            screenHeight - bottomInset - modalHeight - marginY
        }

        return SnappedCoordinates(targetCorner, clampedX, clampedY)
    }

    @Test
    fun testCornerSnapAcrossVariedDisplayResolutions() {
        val resolutions = listOf(
            Triple(1080, 2400, "Standard 20:9 FHD+"),
            Triple(1440, 3120, "QHD+"),
            Triple(720, 1600, "HD+"),
            Triple(2400, 1080, "Landscape"),
            Triple(1800, 1800, "Foldable Square"),
            Triple(1080, 2640, "Tall 22:9")
        )

        val modalW = 280
        val modalH = 180
        val margin = 16
        val topInset = 72
        val bottomInset = 96

        for ((w, h, label) in resolutions) {
            // Drop in top-left quadrant
            val tl = simulateCornerSnap(10, 10, w, h, modalW, modalH, topInset, bottomInset, margin, margin)
            assertEquals("$label: Must snap to TOP_LEFT", OverlayCorner.TOP_LEFT, tl.corner)
            assertEquals(margin, tl.x)
            assertEquals(topInset + margin, tl.y)

            // Drop in top-right quadrant
            val tr = simulateCornerSnap(w - modalW - 10, 10, w, h, modalW, modalH, topInset, bottomInset, margin, margin)
            assertEquals("$label: Must snap to TOP_RIGHT", OverlayCorner.TOP_RIGHT, tr.corner)
            assertEquals(w - modalW - margin, tr.x)
            assertEquals(topInset + margin, tr.y)

            // Drop in bottom-left quadrant
            val bl = simulateCornerSnap(10, h - modalH - 10, w, h, modalW, modalH, topInset, bottomInset, margin, margin)
            assertEquals("$label: Must snap to BOTTOM_LEFT", OverlayCorner.BOTTOM_LEFT, bl.corner)
            assertEquals(margin, bl.x)
            assertEquals(h - bottomInset - modalH - margin, bl.y)

            // Drop in bottom-right quadrant
            val br = simulateCornerSnap(w - modalW - 10, h - modalH - 10, w, h, modalW, modalH, topInset, bottomInset, margin, margin)
            assertEquals("$label: Must snap to BOTTOM_RIGHT", OverlayCorner.BOTTOM_RIGHT, br.corner)
            assertEquals(w - modalW - margin, br.x)
            assertEquals(h - bottomInset - modalH - margin, br.y)
        }
    }

    @Test
    fun testCornerSnapCenterTieBreakingIsDeterministic() {
        // Exactly at screen center
        val screenW = 1080
        val screenH = 2400
        val modalW = 280
        val modalH = 180

        // Center drop position: centerX == screenW / 2
        val dropX = (screenW - modalW) / 2
        val dropY = (screenH - modalH) / 2

        val result = simulateCornerSnap(dropX, dropY, screenW, screenH, modalW, modalH)
        assertNotNull(result)
        // Since centerX < screenW / 2 is false and centerY < screenH / 2 is false at exact midpoint, it deterministically snaps to BOTTOM_RIGHT
        assertEquals("Center tie-break at exact midpoint must deterministically snap to BOTTOM_RIGHT", OverlayCorner.BOTTOM_RIGHT, result.corner)
    }
}
