package com.girocerto.domain

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {

    private val defaultSettings = VehicleSettings(
        fuelPricePerLiter = 6.00,
        fuelConsumptionKmL = 10.0,
        additionalCostPerKm = 0.40,
        minTargetPerKm = 2.00,
        minTargetPerHour = 30.00,
        fixedCostPerRide = 1.00
    )

    @Test
    fun testValidRideCalculation() {
        // Oferta: R$ 30,00, 10 km (8 km corrida + 2 km embarque), 30 min (0.5 h)
        // Combustível: (10 / 10) * 6.00 = R$ 6.00
        // Custo Adicional: 10 * 0.40 = R$ 4.00
        // Custo Fixo: R$ 1.00
        // Custo Total: 6.00 + 4.00 + 1.00 = R$ 11.00
        // Lucro: 30.00 - 11.00 = R$ 19.00
        // R$/km: 30.00 / 10 = R$ 3.00/km (meta: 2.00)
        // R$/h: 30.00 / 0.5 = R$ 60.00/h (meta: 30.00)
        // Margem: (19.00 / 30.00) * 100 = 63.33%
        val offer = RideOffer(
            fare = 30.00,
            distanceKm = 8.0,
            durationMinutes = 30,
            pickupDistanceKm = 2.0
        )

        val analysis = RideCalculator.calculate(offer, defaultSettings)

        assertEquals(10.0, analysis.totalDistanceKm, 0.001)
        assertEquals(3.00, analysis.grossPerKm, 0.001)
        assertEquals(60.00, analysis.grossPerHour, 0.001)
        assertEquals(6.00, analysis.fuelCost, 0.001)
        assertEquals(4.00, analysis.additionalOperationalCost, 0.001)
        assertEquals(1.00, analysis.fixedCost, 0.001)
        assertEquals(11.00, analysis.totalCost, 0.001)
        assertEquals(19.00, analysis.estimatedProfit, 0.001)
        assertEquals(63.33, analysis.estimatedMarginPercent, 0.01)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue(analysis.decisionReason.isNotEmpty())
    }

    @Test
    fun testZeroDistanceRide() {
        val offer = RideOffer(fare = 25.0, distanceKm = 0.0, durationMinutes = 20, pickupDistanceKm = 0.0)
        assertFalse(offer.isValid())

        val analysis = RideCalculator.calculate(offer, defaultSettings)
        assertEquals(TrafficLightStatus.INVALID, analysis.trafficLight)
        assertEquals(0.0, analysis.grossPerKm, 0.0)
        assertFalse(analysis.grossPerKm.isNaN())
        assertFalse(analysis.grossPerKm.isInfinite())
    }

    @Test
    fun testZeroDurationRide() {
        val offer = RideOffer(fare = 25.0, distanceKm = 10.0, durationMinutes = 0, pickupDistanceKm = 0.0)
        assertTrue(offer.isValid())

        val analysis = RideCalculator.calculate(offer, defaultSettings)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertEquals(2.5, analysis.grossPerKm, 0.001)
        assertEquals(14.0, analysis.estimatedProfit, 0.001)
        assertTrue(analysis.decisionReason.contains("Tempo não informado"))
        assertEquals(0.0, analysis.grossPerHour, 0.0)
        assertFalse(analysis.grossPerHour.isNaN())
        assertFalse(analysis.grossPerHour.isInfinite())
    }

    @Test
    fun testZeroFareRide() {
        val offer = RideOffer(fare = 0.0, distanceKm = 10.0, durationMinutes = 20)
        assertFalse(offer.isValid())

        val analysis = RideCalculator.calculate(offer, defaultSettings)
        assertEquals(TrafficLightStatus.INVALID, analysis.trafficLight)
    }

    @Test
    fun testNegativeValues() {
        val offer = RideOffer(fare = -15.0, distanceKm = -5.0, durationMinutes = -10, pickupDistanceKm = -2.0)
        assertFalse(offer.isValid())

        val analysis = RideCalculator.calculate(offer, defaultSettings)
        assertEquals(TrafficLightStatus.INVALID, analysis.trafficLight)
        assertEquals(0.0, analysis.estimatedProfit, 0.0)
        assertFalse(analysis.estimatedProfit.isNaN())
    }

    @Test
    fun testInvalidVehicleSettings() {
        val invalidSettings = VehicleSettings(
            fuelPricePerLiter = -5.0,
            fuelConsumptionKmL = 0.0 // divisão por zero evitada
        )
        assertFalse(invalidSettings.isValid())

        val offer = RideOffer(fare = 20.0, distanceKm = 5.0, durationMinutes = 15)
        val analysis = RideCalculator.calculate(offer, invalidSettings)

        assertEquals(TrafficLightStatus.INVALID, analysis.trafficLight)
        assertEquals(0.0, analysis.fuelCost, 0.0)
        assertFalse(analysis.fuelCost.isNaN())
        assertFalse(analysis.fuelCost.isInfinite())
    }

    @Test
    fun testRoundingAndMonetarySanity() {
        val offer = RideOffer(fare = 17.77, distanceKm = 7.33, durationMinutes = 19)
        val analysis = RideCalculator.calculate(offer, defaultSettings)

        assertFalse(analysis.grossPerKm.isNaN())
        assertFalse(analysis.grossPerHour.isNaN())
        assertFalse(analysis.estimatedProfit.isNaN())
        assertFalse(analysis.estimatedMarginPercent.isNaN())

        // Checagem de precisão de 2 casas
        val diff = analysis.estimatedProfit - (kotlin.math.round(analysis.estimatedProfit * 100.0) / 100.0)
        assertEquals(0.0, diff, 0.0001)
    }

    @Test
    fun testNonFiniteSafeguard() {
        assertEquals(0.0, RideCalculator.round2(Double.NaN), 0.0)
        assertEquals(0.0, RideCalculator.round2(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, RideCalculator.round2(Double.NEGATIVE_INFINITY), 0.0)
    }
}
