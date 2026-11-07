package com.girocerto.domain

import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.domain.usecase.AnalyzeRideOffer
import com.girocerto.domain.usecase.OfferEvaluationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeRideOfferTest {
    private val settings = VehicleSettings(
        fuelPricePerLiter = 6.0,
        fuelConsumptionKmL = 30.0,
        additionalCostPerKm = 0.2,
        minTargetPerKm = 2.0,
        minTargetPerHour = 30.0,
        fixedCostPerRide = 0.0
    )

    @Test
    fun `centraliza calculo classificacao e explicacoes da politica`() {
        val offer = RideOffer(
            fare = 30.0,
            distanceKm = 10.0,
            durationMinutes = 35,
            passengerRating = 4.6,
            addedMinutes = 5
        )

        val analysis = AnalyzeRideOffer()(
            offer = offer,
            settings = settings,
            policy = OfferEvaluationPolicy(minimumPassengerRating = 4.8),
            id = 42L,
            timestamp = 1234L
        )

        assertEquals(42L, analysis.id)
        assertEquals(1234L, analysis.timestamp)
        assertEquals(TrafficLightStatus.GREEN, analysis.trafficLight)
        assertTrue(analysis.decisionReason.contains("margem de 5 min"))
        assertTrue(analysis.decisionReason.contains("Nota do passageiro"))
    }

    @Test
    fun `nao adiciona alertas quando preferencias nao se aplicam`() {
        val analysis = AnalyzeRideOffer()(
            offer = RideOffer(30.0, 10.0, 30, passengerRating = 4.9),
            settings = settings,
            policy = OfferEvaluationPolicy(minimumPassengerRating = 4.8)
        )

        assertFalse(analysis.decisionReason.contains("margem de"))
        assertFalse(analysis.decisionReason.contains("Nota do passageiro"))
    }
}
