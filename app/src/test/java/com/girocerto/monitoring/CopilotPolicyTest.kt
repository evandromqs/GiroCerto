package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test
import com.girocerto.domain.model.TrafficLightStatus

class CopilotPolicyTest {
    @Test fun passiveDefaultsNeverSaveOrNotify() {
        assertFalse(CopilotPolicy().autoSave)
        assertFalse(CopilotPolicy().captureOfferScreenshot)
        assertFalse(CopilotPolicy().notifyOffers)
    }
    @Test fun disabledCopilotNeverMonitorsOrSpeaks() {
        val policy = CopilotPolicy(enabled = false)
        assertFalse(policy.monitors("com.ubercab.driver"))
        for (status in TrafficLightStatus.entries) assertFalse(policy.speaks(status))
    }
    @Test fun applicationTogglesAreIndependent() {
        val policy = CopilotPolicy(uber = false, ninetyNine = false)
        assertFalse(policy.monitors("com.ubercab.driver"))
        assertFalse(policy.monitors("com.taxis99"))
        assertFalse(policy.monitors("com.app99.driver"))
        assertTrue(CopilotPolicy().monitors("com.app99.driver"))
        assertFalse(policy.monitors("com.didiglobal.driver"))
        assertTrue(policy.monitors("sinet.startup.indriver"))
    }
    @Test fun classificationTogglesAreIndependent() {
        val policy = CopilotPolicy(speakYellow = false)
        assertTrue(policy.speaks(TrafficLightStatus.GREEN))
        assertFalse(policy.speaks(TrafficLightStatus.YELLOW))
        assertTrue(policy.speaks(TrafficLightStatus.RED))
        assertFalse(policy.speaks(TrafficLightStatus.INVALID))
    }
    @Test fun ratingPreferenceOnlyFlagsKnownLowerRatings() {
        val policy = CopilotPolicy(minimumPassengerRating = 4.8)
        assertFalse(policy.isBelowPreferredRating(null))
        assertFalse(policy.isBelowPreferredRating(Double.NaN))
        assertFalse(policy.isBelowPreferredRating(4.8))
        assertFalse(policy.isBelowPreferredRating(5.0))
        assertTrue(policy.isBelowPreferredRating(4.7))
        assertFalse(CopilotPolicy().isBelowPreferredRating(4.0))
    }
}
