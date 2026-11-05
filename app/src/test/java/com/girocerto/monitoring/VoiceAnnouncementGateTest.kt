package com.girocerto.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAnnouncementGateTest {
    @Test fun `same offer is spoken once while new offers remain audible`() {
        val gate = VoiceAnnouncementGate(retentionMs = 600_000)
        assertTrue(gate.accepts("uber|20|5|10", 1_000))
        assertFalse(gate.accepts("uber|20|5|10", 5_000))
        assertTrue(gate.accepts("uber|24|5|10", 6_000))
    }

    @Test fun `identity can be announced after retention expires`() {
        val gate = VoiceAnnouncementGate(retentionMs = 1_000)
        assertTrue(gate.accepts("keeta|15|3|8", 100))
        assertTrue(gate.accepts("keeta|15|3|8", 1_100))
    }
}
