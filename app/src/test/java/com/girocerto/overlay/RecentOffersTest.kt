package com.girocerto.overlay

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.VehicleSettings
import org.junit.Assert.*
import org.junit.Test

class RecentOffersTest {
    private fun offer(app: String, fare: Double = 20.0) = RideCalculator.calculate(
        RideOffer(fare, 5.0, 15, appName = app), VehicleSettings())

    @Test fun eachPlatformExpiresIndependently() {
        val queue = RecentOffers(30_000L)
        queue.add(offer("Uber"), 0)
        queue.add(offer("99"), 10_000)
        queue.add(offer("iFood"), 20_000)
        assertEquals(3, queue.active(29_999).size)
        assertEquals(listOf("iFood", "99"), queue.active(30_000).map { it.analysis.offer.appName })
        assertEquals(1, queue.active(40_000).size)
        assertTrue(queue.active(50_000).isEmpty())
    }

    @Test fun repeatDoesNotExtendLifetimeOrResurrectExpiredOffer() {
        val queue = RecentOffers(30_000L)
        val analysis = offer("Uber")
        assertTrue(queue.add(analysis, 0))
        assertFalse(queue.add(analysis, 29_999))
        assertTrue(queue.active(30_000).isEmpty())
        assertFalse(queue.add(analysis, 40_000))
        assertTrue(queue.active(40_000).isEmpty())
        assertTrue(queue.add(offer("Uber", 21.0), 40_001))
        assertEquals(1, queue.active(40_001).size)
    }

    @Test fun newOfferReplacesSamePlatformAndListIsBounded() {
        val queue = RecentOffers(30_000L)
        queue.add(offer("Uber"), 0)
        queue.add(offer("99"), 1)
        queue.add(offer("99Food"), 2)
        assertEquals(2, queue.active(3).size)
        queue.add(offer("iFood"), 4)
        queue.add(offer("inDrive"), 5)
        assertEquals(listOf("inDrive", "iFood", "99Food"), queue.active(6).map { it.analysis.offer.appName })
        queue.clear()
        assertTrue(queue.active(7).isEmpty())
        assertTrue(queue.add(offer("iFood"), 8))
    }

    @Test fun identicalTripCanReturnAfterObservationGapButNotContinuousRetries() {
        val queue = RecentOffers(30_000L)
        val analysis = offer("99")
        assertTrue(queue.add(analysis, 0))
        for (time in listOf(30_000L, 60_000L, 90_000L, 120_000L)) {
            assertFalse(queue.add(analysis, time))
            assertTrue(queue.active(time).isEmpty())
        }
        assertTrue(queue.add(analysis, 240_000L))
        assertEquals(1, queue.active(240_000L).size)
    }
    @Test fun defaultIsFifteenSecondsAndProgressDoesNotReset() {
        val queue = RecentOffers()
        val analysis = offer("Uber")
        queue.add(analysis, 0)
        assertEquals(.5f, queue.active(7500).single().remainingFraction(7500), .001f)
        assertFalse(queue.add(analysis, 8000))
        assertTrue(queue.active(15_000).isEmpty())
        queue.add(offer("99"), 15_000, 30_000)
        assertEquals(.5f, queue.active(30_000).single().remainingFraction(30_000), .001f)
        assertTrue(queue.active(45_000).isEmpty())
    }
}
