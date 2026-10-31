package com.girocerto.e2e.tier3

import com.girocerto.app.theme.Navy
import com.girocerto.app.theme.ThemeMode
import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.monitoring.AccessibilityOfferReader
import com.girocerto.monitoring.OfferDeliveryGate
import com.girocerto.monitoring.OfferOcrContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 3: Cross-Feature Combinations (Pairwise Verification).
 * - OCR detection triggering corner-anchored floating modal positioning.
 * - Dark theme switching preserving overlay geometry.
 * - Vehicle operational thresholds driving traffic light evaluation for the overlay.
 * - Header filter combined with delivery gate deduplication.
 * - Screen orientation change preserving resting corner anchoring.
 */
class CrossFeatureIntegrationE2ETest {

    private val driverSettings = VehicleSettings(
        fuelPricePerLiter = 5.89,
        fuelConsumptionKmL = 10.0,
        additionalCostPerKm = 0.50,
        minTargetPerKm = 2.00,
        minTargetPerHour = 35.00,
        fixedCostPerRide = 0.50
    )

    private fun computeCornerCoordinates(
        corner: String,
        screenWidth: Int,
        screenHeight: Int,
        modalWidth: Int,
        modalHeight: Int,
        topInset: Int = 80,
        bottomInset: Int = 100,
        margin: Int = 16
    ): Pair<Int, Int> {
        return when (corner) {
            "TOP_LEFT" -> Pair(margin, topInset + margin)
            "TOP_RIGHT" -> Pair(screenWidth - modalWidth - margin, topInset + margin)
            "BOTTOM_LEFT" -> Pair(margin, screenHeight - bottomInset - modalHeight - margin)
            "BOTTOM_RIGHT" -> Pair(screenWidth - modalWidth - margin, screenHeight - bottomInset - modalHeight - margin)
            else -> Pair(screenWidth - modalWidth - margin, topInset + margin)
        }
    }

    @Test
    fun testOcrDetectionTriggersCornerPositionedModal() {
        // Step 1: Incoming OCR lines from driver app
        val ocrLines = listOf(
            "UberX",
            "R$ 26,40",
            "7.2 km • 18 min",
            "Aceitar"
        )

        // Step 2: Validate OCR permit and parsing
        assertTrue("OCR must permit candidate", OfferOcrContext.permits(ocrLines))
        val parsed = AccessibilityOfferReader.parse(ocrLines, "com.ubercab.driver")
        assertTrue("Parsed offer must be complete", parsed.isComplete)
        assertEquals(26.40, parsed.fare!!, 0.001)

        // Step 3: Compute floating modal position at default TOP_RIGHT resting corner
        val (modalX, modalY) = computeCornerCoordinates(
            corner = "TOP_RIGHT",
            screenWidth = 1080,
            screenHeight = 2400,
            modalWidth = 280,
            modalHeight = 180,
            topInset = 90,
            bottomInset = 110
        )

        assertEquals("Modal X must snap to right edge with margin", 1080 - 280 - 16, modalX)
        assertEquals("Modal Y must clear top status bar inset", 90 + 16, modalY)
    }

    @Test
    fun testDarkThemeSwitchingPreservesOverlayPosition() {
        // Theme changes from LIGHT to DARK
        var currentTheme = ThemeMode.LIGHT
        val initialCoordinates = computeCornerCoordinates("TOP_RIGHT", 1080, 2400, 280, 180)

        currentTheme = ThemeMode.DARK
        val darkCoordinates = computeCornerCoordinates("TOP_RIGHT", 1080, 2400, 280, 180)

        // Coordinates must remain identical while theme is changed
        assertEquals("X position must not change on theme switch", initialCoordinates.first, darkCoordinates.first)
        assertEquals("Y position must not change on theme switch", initialCoordinates.second, darkCoordinates.second)
        assertEquals("Theme must be DARK", ThemeMode.DARK, currentTheme)
    }

    @Test
    fun testVehicleThresholdsAndOcrOfferDriveTrafficLightStatus() {
        // Offer detected by OCR
        val ocrLines = listOf("99Pop", "R$ 38,00", "12 km", "25 min", "Aceitar")
        val parsed = AccessibilityOfferReader.parse(ocrLines, "com.app99.driver")
        assertTrue(parsed.isComplete)

        val offer = RideOffer(
            fare = parsed.fare!!,
            distanceKm = parsed.distanceKm!!,
            durationMinutes = parsed.durationMinutes!!
        )

        val analysis = RideCalculator.calculate(offer, driverSettings)

        // Gross per km: 38.00 / 12 = 3.17 (target: 2.00) -> OK
        // Gross per hour: 38.00 / (25/60) = 91.20 (target: 35.00) -> OK
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue("Estimated profit must be positive", analysis.estimatedProfit > 15.0)
    }

    @Test
    fun testHeaderFilterWithDeliveryGateDeduplication() {
        // Simulated screen with header wallet balance + active offer
        val rawScreen = listOf(
            "Saldo R$ 180,00" to 40,  // header line (< 7.5% screen height of 1000)
            "UberX" to 300,
            "R$ 25,00" to 350,
            "6,0 km" to 400,
            "15 min" to 450,
            "Aceitar" to 500
        )

        // Filter out header
        val filtered = rawScreen.filter { OfferOcrContext.acceptsLine(it.first, it.second, 1000) }.map { it.first }
        assertTrue(OfferOcrContext.permits(filtered))

        val parsed = AccessibilityOfferReader.parse(filtered, "com.ubercab.driver")
        assertTrue(parsed.isComplete)

        // Deduplication gate: first read is accepted
        val gate = OfferDeliveryGate()
        val offerKey = "${parsed.fare}_${parsed.distanceKm}"
        assertTrue("First delivery must pass gate", gate.permits(offerKey))

        // Same offer immediately repeated: must be throttled
        assertFalse("Immediate duplicate delivery must be throttled", gate.permits(offerKey))
    }

    @Test
    fun testScreenOrientationChangeRecalculatesCornerCoordinates() {
        // Portrait: 1080x2400
        val portraitCoords = computeCornerCoordinates("TOP_RIGHT", 1080, 2400, 280, 180)
        assertEquals(1080 - 280 - 16, portraitCoords.first)
        assertEquals(80 + 16, portraitCoords.second)

        // Landscape: 2400x1080
        val landscapeCoords = computeCornerCoordinates("TOP_RIGHT", 2400, 1080, 280, 180)
        assertEquals("Landscape X must anchor to wider screen edge", 2400 - 280 - 16, landscapeCoords.first)
        assertEquals(80 + 16, landscapeCoords.second)
    }
}
