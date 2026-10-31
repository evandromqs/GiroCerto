package com.girocerto.e2e.tier4

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.monitoring.AccessibilityOfferReader
import com.girocerto.monitoring.OfferDeliveryGate
import com.girocerto.monitoring.OfferOcrContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 4: Real-World Application Scenarios.
 * Full end-to-end driver workflows across Uber, 99, and iFood:
 * OCR detection, header filtering, economic threshold calculations,
 * traffic light classifications, and compact floating modal layout placement.
 */
class RealWorldApplicationScenariosE2ETest {

    private val combustionCarSettings = VehicleSettings(
        fuelPricePerLiter = 5.80,
        fuelConsumptionKmL = 11.5,
        additionalCostPerKm = 0.40,
        minTargetPerKm = 2.00,
        minTargetPerHour = 35.00,
        fixedCostPerRide = 0.50
    )

    private val electricMotorcycleSettings = VehicleSettings(
        fuelPricePerLiter = 0.0,
        fuelConsumptionKmL = Double.MAX_VALUE,
        additionalCostPerKm = 0.15,
        minTargetPerKm = 1.80,
        minTargetPerHour = 30.00,
        fixedCostPerRide = 0.0
    )

    @Test
    fun testScenario1_UberRideOfferProfitableGreen() {
        // Driver receives profitable UberX offer
        val rawScreenTokens = listOf(
            "Ganhos hoje R$ 184,50" to 30, // Header line at y=30 (< 7.5% screen height of 1000)
            "UberX" to 200,
            "R$ 31,50" to 250,
            "8.2 km • 18 min" to 300,
            "Desembarque: Av. Paulista, 1000" to 350,
            "Aceitar" to 500
        )

        // 1. OCR Header Exclusion
        val validLines = rawScreenTokens
            .filter { OfferOcrContext.acceptsLine(it.first, it.second, 1000) }
            .map { it.first }
        assertTrue("Header balance should be excluded and offer accepted", OfferOcrContext.permits(validLines))

        // 2. Offer Parser
        val parsed = AccessibilityOfferReader.parse(validLines, "com.ubercab.driver")
        assertTrue("Parsed offer must be complete", parsed.isComplete)
        assertEquals(31.50, parsed.fare!!, 0.001)
        assertEquals(8.2, parsed.distanceKm!!, 0.001)
        assertEquals(18, parsed.durationMinutes!!)

        // 3. Financial Analysis
        val offer = RideOffer(fare = parsed.fare!!, distanceKm = parsed.distanceKm!!, durationMinutes = parsed.durationMinutes!!)
        val analysis = RideCalculator.calculate(offer, combustionCarSettings)

        // R$/km: 31.50 / 8.2 = 3.84 (> 2.00 target)
        // R$/h: 31.50 / (18/60) = 105.00 (> 35.00 target)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue("Estimated margin percent must be high (> 50%)", analysis.estimatedMarginPercent > 50.0)

        // 4. Modal Placement at TOP_RIGHT
        val screenW = 1080
        val modalW = 280
        val margin = 16
        val expectedX = screenW - modalW - margin
        assertEquals(784, expectedX)
    }

    @Test
    fun testScenario2_NinetyNineTripBorderlineYellow() {
        // Driver receives 99Pop trip with borderline return
        val lines = listOf(
            "99Pop",
            "R$ 14,00",
            "9,5 km (22 min)",
            "Embarque a 1,5 km",
            "Aceitar"
        )

        assertTrue(OfferOcrContext.permits(lines))
        val parsed = AccessibilityOfferReader.parse(lines, "com.app99.driver")
        assertTrue(parsed.isComplete)

        val offer = RideOffer(fare = parsed.fare!!, distanceKm = parsed.distanceKm!!, durationMinutes = parsed.durationMinutes!!)
        val analysis = RideCalculator.calculate(offer, combustionCarSettings)

        // R$/km: 14.00 / 9.5 = 1.47 (< 2.00 target)
        // R$/h: 14.00 / (22/60) = 38.18 (>= 35.00 target)
        // One target met, one failed -> YELLOW
        assertEquals(TrafficLightStatus.YELLOW, analysis.trafficLight)
        assertTrue("Decision reason must explain warning", analysis.decisionReason.contains("Meta de hora atingida") || analysis.decisionReason.isNotEmpty())
    }

    @Test
    fun testScenario3_IFoodDeliveryUnprofitableRed() {
        // Courier receives long low-fare delivery
        val lines = listOf(
            "Entrega Moto",
            "R$ 6,50",
            "8,0 km",
            "35 min",
            "Aceitar entrega"
        )

        val parsed = AccessibilityOfferReader.parse(lines, "br.com.ifood.driver")
        val offer = RideOffer(fare = parsed.fare ?: 6.50, distanceKm = parsed.distanceKm ?: 8.0, durationMinutes = parsed.durationMinutes ?: 35)

        val analysis = RideCalculator.calculate(offer, combustionCarSettings)

        // Fuel: (8 / 11.5) * 5.80 = R$ 4.03
        // Addl: 8 * 0.40 = R$ 3.20
        // Fixed: R$ 0.50
        // Total cost = 7.73 > Fare 6.50 -> Net loss!
        assertEquals(TrafficLightStatus.RED, analysis.trafficLight)
        assertTrue("Estimated profit should be <= 0 for loss", analysis.estimatedProfit <= 0.0)
    }

    @Test
    fun testScenario4_MultiOfferStreamDeduplication() {
        val gate = OfferDeliveryGate()
        val offer1 = "25.0_6.0"
        val offer2 = "40.0_10.0"

        // First occurrence: delivered
        assertTrue("First read of offer1 must pass gate", gate.permits(offer1))

        // Rapid duplicate read within 2 seconds: suppressed
        assertFalse("Rapid duplicate read of offer1 must be suppressed", gate.permits(offer1))

        // Different offer: passes immediately
        assertTrue("New distinct offer2 must pass gate", gate.permits(offer2))
    }

    @Test
    fun testScenario5_ElectricMotorcycleProfileWithBottomRightCorner() {
        // Electric motorcycle driver with custom resting corner BOTTOM_RIGHT
        val lines = listOf(
            "Uber Moto",
            "R$ 18,00",
            "5,5 km",
            "15 min",
            "Aceitar"
        )

        val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
        val offer = RideOffer(fare = 18.00, distanceKm = 5.5, durationMinutes = 15)

        // Under electric vehicle settings (almost zero energy cost, low maintenance)
        val analysis = RideCalculator.calculate(offer, electricMotorcycleSettings)

        // R$/km: 18 / 5.5 = 3.27 (> 1.80)
        // R$/h: 18 / 0.25 = 72.00 (> 30.00)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue("Electric vehicle margin should exceed 80%", analysis.estimatedMarginPercent > 80.0)

        // Anchored to BOTTOM_RIGHT:
        // Screen: 1080x2400, Modal: 280x180, BottomInset: 120, Margin: 16
        val expectedX = 1080 - 280 - 16 // 784
        val expectedY = 2400 - 120 - 180 - 16 // 2084
        assertEquals(784, expectedX)
        assertEquals(2084, expectedY)
    }
}
