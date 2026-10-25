package com.girocerto.monitoring

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOfferReaderTest {

    @Test
    fun testCompleteManualOfferWithCommas() = runBlocking {
        val reader = ManualOfferReader(
            rawFare = "25,50",
            rawDistanceKm = "12,4",
            rawDurationMinutes = "35",
            rawPickupDistanceKm = "1,8"
        )

        val result = reader.readOffer()
        assertTrue(result.isComplete)
        assertEquals(25.50, result.fare!!, 0.001)
        assertEquals(12.4, result.distanceKm!!, 0.001)
        assertEquals(35, result.durationMinutes)
        assertEquals(1.8, result.pickupDistanceKm!!, 0.001)
        assertTrue(result.missingFields.isEmpty())
        assertNull(result.error)

        val offer = reader.toRideOffer()
        assertNotNull(offer)
        assertEquals(14.2, offer!!.totalDistanceKm, 0.001)
    }

    @Test
    fun testMissingFields() = runBlocking {
        val reader = ManualOfferReader(
            rawFare = "",
            rawDistanceKm = "10.0",
            rawDurationMinutes = "0" // inválido
        )

        val result = reader.readOffer()
        assertFalse(result.isComplete)
        assertTrue(result.missingFields.contains("Valor da oferta (R$)"))
        assertTrue(result.missingFields.contains("Duração estimada (minutos)"))
        assertNotNull(result.error)

        val offer = reader.toRideOffer()
        assertNull(offer)
    }

    @Test
    fun testNegativeOrInvalidText() = runBlocking {
        val reader = ManualOfferReader(
            rawFare = "abc",
            rawDistanceKm = "-5.0",
            rawDurationMinutes = "quinze"
        )

        val result = reader.readOffer()
        assertFalse(result.isComplete)
        assertEquals(3, result.missingFields.size)
    }
}
