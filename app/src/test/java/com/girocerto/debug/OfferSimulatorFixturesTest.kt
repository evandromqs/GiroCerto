package com.girocerto.debug

import com.girocerto.domain.model.VehicleSettings
import com.girocerto.domain.usecase.AnalyzeRideOffer
import org.junit.Assert.assertEquals
import org.junit.Test

class OfferSimulatorFixturesTest {
    @Test
    fun `fixtures cobrem plataformas e resultados declarados`() {
        val analyzer = AnalyzeRideOffer()
        val fixtures = OfferSimulatorFixtures.all

        assertEquals(setOf("Uber", "99", "iFood", "Keeta"), fixtures.map { it.parsedOffer().appName }.toSet())
        fixtures.forEach { fixture ->
            val reading = fixture.reading
            assertEquals("${fixture.name}: ${reading.error} ${reading.missingFields}", true, reading.isComplete)
            assertEquals(fixture.name, fixture.expected, analyzer(fixture.parsedOffer(), VehicleSettings()).trafficLight)
        }
    }
}
