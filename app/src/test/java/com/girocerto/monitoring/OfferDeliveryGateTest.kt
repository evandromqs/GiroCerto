package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class OfferDeliveryGateTest {
    private val offer = OfferReaderResult(fare = 20.0, distanceKm = 5.0)

    @Test fun alternatingPlatformsDoNotRepeatAlerts() {
        val gate = OfferDeliveryGate()
        assertTrue(gate.accepts("uber", offer, 0))
        assertTrue(gate.accepts("99", offer, 100))
        assertFalse(gate.accepts("uber", offer, 200))
        assertFalse(gate.accepts("99", offer, 300))
    }

    @Test fun ocrAndAccessibilityShareIdentityButRatingChangesAreDelivered() {
        val gate = OfferDeliveryGate()
        assertTrue(gate.accepts("uber", offer, 0))
        assertFalse(gate.accepts("uber", offer.copy(source = OfferSource.OCR_FALLBACK), 100))
        assertTrue(gate.accepts("uber", offer.copy(passengerRating = 4.5), 200))
    }

    @Test fun continuouslyVisibleOfferDoesNotReopenAfterCardTimeout() {
        val gate = OfferDeliveryGate()
        assertTrue(gate.accepts("uber", offer, 0))
        for (time in 1_500L..120_000L step 1_500L) assertFalse(gate.accepts("uber", offer, time))
        assertTrue(gate.accepts("uber", offer.copy(fare = 25.0), 121_500L))
    }

    @Test fun incompleteReadsDoNotConsumeDeliveryAndExpiryAllowsRedisplay() {
        val gate = OfferDeliveryGate()
        assertFalse(gate.accepts("uber", offer.copy(fare = null), 0))
        assertTrue(gate.accepts("uber", offer, 100))
        assertFalse(gate.accepts("uber", offer, 15_099))
        assertFalse(gate.accepts("uber", offer, 15_100))
        assertTrue(gate.accepts("uber", offer, 30_100))
    }
}
