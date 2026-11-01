package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class WindowOwnerHintsTest {
    @Test fun popupWithoutRootUsesRecentEmittingPackage() {
        val hints = WindowOwnerHints()
        hints.observe(42, "com.ubercab.driver", 100)
        assertEquals("com.ubercab.driver", hints.owner(42, null, 500))
        assertNull(hints.owner(43, null, 500))
        assertNull(hints.owner(42, null, 10_101))
    }
    @Test fun explicitOwnerOverridesReusedWindowAndRefreshesEvidence() {
        val hints = WindowOwnerHints()
        hints.observe(42, "com.ubercab.driver", 100)
        assertEquals("com.taxis99", hints.owner(42, "com.taxis99", 200))
        assertEquals("com.taxis99", hints.owner(42, null, 300))
        assertEquals("unrelated.app", hints.owner(42, "unrelated.app", 400))
        assertEquals("unrelated.app", hints.owner(42, null, 500))
    }
    @Test fun missingWindowIdNeverAuthorizesDisplayCapture() {
        val hints = WindowOwnerHints()
        hints.observe(-1, "com.ubercab.driver", 100)
        assertNull(hints.owner(-1, null, 200))
    }
}
