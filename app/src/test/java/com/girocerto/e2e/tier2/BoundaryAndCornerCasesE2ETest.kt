package com.girocerto.e2e.tier2

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.monitoring.AccessibilityOfferReader
import com.girocerto.monitoring.OfferOcrContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 2: Boundary & Corner Cases.
 * Edge coordinates, display cutouts/insets, zero/negative fares,
 * malformed OCR lines, extreme numbers, and rapid physics recalculations.
 */
class BoundaryAndCornerCasesE2ETest {

    private val standardSettings = VehicleSettings(
        fuelPricePerLiter = 5.50,
        fuelConsumptionKmL = 11.0,
        additionalCostPerKm = 0.45,
        minTargetPerKm = 2.10,
        minTargetPerHour = 36.00,
        fixedCostPerRide = 0.50
    )

    private fun snapToCorner(
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
    ): Pair<Int, Int> {
        val centerX = dropX + modalWidth / 2
        val centerY = dropY + modalHeight / 2

        val isLeft = centerX < screenWidth / 2
        val isTop = centerY < screenHeight / 2

        val clampedMarginX = marginX.coerceAtMost((screenWidth - modalWidth) / 2).coerceAtLeast(0)
        val clampedMarginY = marginY.coerceAtMost((screenHeight - topInset - bottomInset - modalHeight) / 2).coerceAtLeast(0)

        val targetX = if (isLeft) clampedMarginX else screenWidth - modalWidth - clampedMarginX
        val targetY = if (isTop) topInset + clampedMarginY else screenHeight - bottomInset - modalHeight - clampedMarginY

        return Pair(targetX, targetY)
    }

    @Test
    fun testDropAtExactZeroZeroBoundary() {
        // Drop at (0, 0): Center is at (150, 100), well inside TOP_LEFT
        val (x, y) = snapToCorner(
            dropX = 0,
            dropY = 0,
            screenWidth = 1080,
            screenHeight = 2400,
            modalWidth = 300,
            modalHeight = 200,
            topInset = 90,
            bottomInset = 110,
            marginX = 20,
            marginY = 20
        )

        assertEquals("X must be clamped to left margin", 20, x)
        assertEquals("Y must be clamped to topInset + marginY", 90 + 20, y)
    }

    @Test
    fun testDropPastScreenBoundariesNegativeCoordinates() {
        // Dragged outside screen with negative coordinates
        val (x, y) = snapToCorner(
            dropX = -500,
            dropY = -300,
            screenWidth = 1080,
            screenHeight = 2400,
            modalWidth = 300,
            modalHeight = 200,
            topInset = 80,
            bottomInset = 100
        )

        assertEquals("Must clamp to left margin even with negative drop coordinates", 16, x)
        assertEquals("Must clamp to topInset + marginY even with negative drop coordinates", 80 + 16, y)
    }

    @Test
    fun testExtremeDisplayAspectRatiosTallVsCompact() {
        // 21:9 Ultra-tall smartphone (1080x2520)
        val (tallX, tallY) = snapToCorner(
            dropX = 900,
            dropY = 2400,
            screenWidth = 1080,
            screenHeight = 2520,
            modalWidth = 280,
            modalHeight = 180,
            topInset = 120,
            bottomInset = 140
        )
        assertEquals(1080 - 280 - 16, tallX)
        assertEquals(2520 - 140 - 180 - 16, tallY)

        // Compact display (480x800)
        val (compX, compY) = snapToCorner(
            dropX = 400,
            dropY = 700,
            screenWidth = 480,
            screenHeight = 800,
            modalWidth = 272,
            modalHeight = 150,
            topInset = 40,
            bottomInset = 40
        )
        assertEquals(480 - 272 - 16, compX)
        assertEquals(800 - 40 - 150 - 16, compY)
    }

    @Test
    fun testZeroAndNegativeFaresRejected() {
        // Zero fare offer must be invalid
        val zeroOffer = RideOffer(fare = 0.0, distanceKm = 8.0, durationMinutes = 20)
        assertFalse("Zero fare offer cannot be valid", zeroOffer.isValid())
        val zeroAnalysis = RideCalculator.calculate(zeroOffer, standardSettings)
        assertEquals(TrafficLightStatus.INVALID, zeroAnalysis.trafficLight)

        // Negative fare offer must be invalid
        val negativeOffer = RideOffer(fare = -15.50, distanceKm = 8.0, durationMinutes = 20)
        assertFalse("Negative fare offer cannot be valid", negativeOffer.isValid())
        val negAnalysis = RideCalculator.calculate(negativeOffer, standardSettings)
        assertEquals(TrafficLightStatus.INVALID, negAnalysis.trafficLight)
    }

    @Test
    fun testNegativeDistanceAndDurationHandling() {
        val invalidOffer = RideOffer(fare = 25.0, distanceKm = -10.0, durationMinutes = -15)
        assertFalse(invalidOffer.isValid())
        val analysis = RideCalculator.calculate(invalidOffer, standardSettings)
        assertEquals(TrafficLightStatus.INVALID, analysis.trafficLight)
        assertEquals(0.0, analysis.grossPerKm, 0.0)
        assertFalse("Must not produce NaN", analysis.grossPerKm.isNaN())
    }

    @Test
    fun testExtremeOutlierFareNoOverflow() {
        // Extreme ride fare: R$ 99.999,00
        val outlierOffer = RideOffer(fare = 99999.0, distanceKm = 50.0, durationMinutes = 120)
        assertTrue(outlierOffer.isValid())
        val analysis = RideCalculator.calculate(outlierOffer, standardSettings)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue(analysis.estimatedProfit > 90000.0)
        assertFalse(analysis.estimatedProfit.isInfinite())
        assertFalse(analysis.estimatedProfit.isNaN())
    }

    @Test
    fun testMalformedAndGarbledOcrText() {
        // Corrupted OCR text strings
        val noisyLines = listOf("%%%@@#", "R$ NaN", "---", "Aceitar")
        assertFalse("Garbled currency must not permit offer context", OfferOcrContext.permits(noisyLines))

        val emptyLines = emptyList<String>()
        assertFalse("Empty screen lines must not permit offer context", OfferOcrContext.permits(emptyLines))
    }

    @Test
    fun testRapidSuccessiveCornerSnapCalculations() {
        // Verify deterministic behavior over 100 rapid drag updates
        var lastX = -1
        var lastY = -1
        for (i in 0 until 100) {
            val (x, y) = snapToCorner(
                dropX = 600 + (i % 10),
                dropY = 1500 + (i % 10),
                screenWidth = 1080,
                screenHeight = 2400,
                modalWidth = 300,
                modalHeight = 200,
                topInset = 80,
                bottomInset = 100,
                marginX = 16,
                marginY = 16
            )
            if (i > 0) {
                assertEquals("Position should remain stable and deterministic", lastX, x)
                assertEquals("Position should remain stable and deterministic", lastY, y)
            }
            lastX = x
            lastY = y
        }
    }
}
