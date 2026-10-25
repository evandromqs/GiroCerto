package com.girocerto.domain

import com.girocerto.domain.calculator.TrafficLightEvaluator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficLightEvaluatorTest {

    private val settings = VehicleSettings(
        fuelPricePerLiter = 5.00,
        fuelConsumptionKmL = 10.0,
        additionalCostPerKm = 0.50,
        minTargetPerKm = 2.00,
        minTargetPerHour = 40.00,
        fixedCostPerRide = 0.00
    )

    @Test
    fun testGreenWhenBothTargetsMetAndProfitable() {
        // Oferta: R$ 50.00, 20 km (R$ 2.50/km >= 2.00), 60 min (R$ 50.00/h >= 40.00), Lucro: 30.00
        val offer = RideOffer(fare = 50.0, distanceKm = 20.0, durationMinutes = 60)
        val result = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 2.50,
            grossPerHour = 50.00,
            estimatedProfit = 30.00
        )

        assertEquals(TrafficLightStatus.GREEN, result.status)
        assertTrue(result.reason.contains("Compatível"))
    }

    @Test
    fun testYellowWhenOnlyKmMet() {
        // R$/km = 2.50 (meta 2.00 OK), R$/h = 30.00 (meta 40.00 NÃO OK, < 40), Lucro: 15.00
        val offer = RideOffer(fare = 25.0, distanceKm = 10.0, durationMinutes = 50)
        val result = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 2.50,
            grossPerHour = 30.00,
            estimatedProfit = 15.00
        )

        assertEquals(TrafficLightStatus.YELLOW, result.status)
        assertTrue(result.reason.contains("Meta de km atingida"))
    }

    @Test
    fun testYellowWhenOnlyHourMet() {
        // R$/km = 1.60 (meta 2.00 NÃO OK), R$/h = 48.00 (meta 40.00 OK), Lucro: 10.00
        val offer = RideOffer(fare = 24.0, distanceKm = 15.0, durationMinutes = 30)
        val result = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 1.60,
            grossPerHour = 48.00,
            estimatedProfit = 10.00
        )

        assertEquals(TrafficLightStatus.YELLOW, result.status)
        assertTrue(result.reason.contains("Meta de hora atingida"))
    }

    @Test
    fun testYellowWhenNearBothTargetsWithinTolerance() {
        // Tolerância 15%: metaKm 2.00 -> min 1.70. metaHour 40.00 -> min 34.00
        // R$/km = 1.80, R$/h = 36.00, Lucro: 8.00
        val offer = RideOffer(fare = 18.0, distanceKm = 10.0, durationMinutes = 30)
        val result = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 1.80,
            grossPerHour = 36.00,
            estimatedProfit = 8.00
        )

        assertEquals(TrafficLightStatus.YELLOW, result.status)
        assertTrue(result.reason.contains("Próxima das metas"))
    }

    @Test
    fun testRedWhenLossOrZeroProfit() {
        val offer = RideOffer(fare = 10.0, distanceKm = 15.0, durationMinutes = 20)
        // Lucro negativo
        val resultLoss = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 0.67,
            grossPerHour = 30.00,
            estimatedProfit = -5.00
        )
        assertEquals(TrafficLightStatus.RED, resultLoss.status)
        assertTrue(resultLoss.reason.contains("Prejuízo"))

        // Lucro zero
        val resultZero = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 0.67,
            grossPerHour = 30.00,
            estimatedProfit = 0.00
        )
        assertEquals(TrafficLightStatus.RED, resultZero.status)
        assertTrue(resultZero.reason.contains("Margem zero"))
    }

    @Test
    fun testRedWhenFarBelowBothTargets() {
        // R$/km = 1.20 (< 1.70), R$/h = 24.00 (< 34.00), Lucro: 2.00
        val offer = RideOffer(fare = 12.0, distanceKm = 10.0, durationMinutes = 30)
        val result = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = 1.20,
            grossPerHour = 24.00,
            estimatedProfit = 2.00
        )

        assertEquals(TrafficLightStatus.RED, result.status)
        assertTrue(result.reason.contains("abaixo das metas", ignoreCase = true))
    }

    @Test
    fun testInvalidOfferOrSettings() {
        val invalidOffer = RideOffer(fare = 0.0, distanceKm = -1.0, durationMinutes = 0)
        val result = TrafficLightEvaluator.evaluate(
            offer = invalidOffer,
            settings = settings,
            grossPerKm = 0.0,
            grossPerHour = 0.0,
            estimatedProfit = 0.0
        )

        assertEquals(TrafficLightStatus.INVALID, result.status)
    }
}
