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

    @Test fun incompleteReadsDoNotConsumeDeliveryAndExpiryAllowsRedisplay() {
        val gate = OfferDeliveryGate()
        assertFalse(gate.accepts("uber", offer.copy(fare = null), 0))
        assertTrue(gate.accepts("uber", offer, 100))
        assertFalse(gate.accepts("uber", offer, 15_099))
        assertTrue(gate.accepts("uber", offer, 15_100))
    }
}
