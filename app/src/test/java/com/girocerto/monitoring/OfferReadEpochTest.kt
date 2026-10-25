package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class OfferReadEpochTest {
    @Test fun replacedOrExpiredOcrCannotPublish() {
        val gate = OfferReadEpoch()
        val first = gate.current("uber")
        assertTrue(gate.accepts("uber", first, 100, 500))
        gate.invalidate("uber")
        assertFalse(gate.accepts("uber", first, 100, 600))
        assertTrue(gate.accepts("99", 0, 100, 600))
        assertFalse(gate.accepts("uber", gate.current("uber"), 100, 2700))
    }
}
