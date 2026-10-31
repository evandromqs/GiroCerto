package com.girocerto.e2e.tier1

import com.girocerto.monitoring.AccessibilityOfferReader
import com.girocerto.monitoring.CopilotPolicy
import com.girocerto.monitoring.OfferOcrContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1: Feature Coverage for Requirement R3.
 * Default on-device OCR detection engine, currency/distance/time parsing,
 * top-header earnings exclusion filter, and action trigger validation.
 */
class OcrDefaultActivationE2ETest {

    @Test
    fun testCurrencyParsingBrazilianRealFormats() {
        // Requirement R3: Parse currency across various whitespace and encoding representations
        val variations = listOf(
            "R$\u00a016,73",
            "R$\u202f16,73",
            "\u200eR$\u200e 16,73",
            "R$\n16,73",
            "R$ 16,73"
        )
        for (price in variations) {
            val lines = listOf("Envios Moto", price, "29 min (10.7 km)", "Aceitar")
            assertTrue("OfferOcrContext must accept valid fare representation: $price", OfferOcrContext.permits(lines))
            val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
            assertTrue("Parsed offer must be complete for $price", parsed.isComplete)
            assertEquals("Parsed fare must equal 16.73", 16.73, parsed.fare!!, 0.001)
        }
    }

    @Test
    fun testDistanceParsingWithFractionalKm() {
        // Requirement R3: Parse distance in kilometers with comma or dot notation
        val sampleLinesComma = listOf("UberX", "R$ 22,50", "4,2 km", "12 min", "Aceitar")
        val parsedComma = AccessibilityOfferReader.parse(sampleLinesComma, "com.ubercab.driver")
        assertNotNull("Fare should be parsed", parsedComma.fare)
        assertEquals(22.50, parsedComma.fare!!, 0.001)
        assertEquals(4.2, parsedComma.distanceKm!!, 0.001)

        val sampleLinesDot = listOf("99Pop", "R$ 18.00", "10.5 km", "15 min", "Aceitar")
        val parsedDot = AccessibilityOfferReader.parse(sampleLinesDot, "com.app99.driver")
        assertEquals(18.00, parsedDot.fare!!, 0.001)
        assertEquals(10.5, parsedDot.distanceKm!!, 0.001)
    }

    @Test
    fun testDurationParsingMinutes() {
        // Requirement R3: Parse duration in minutes from offer lines
        val lines = listOf("Entrega", "R$ 12,00", "5,0 km", "25 min", "Aceitar")
        val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
        assertEquals(25, parsed.durationMinutes)
    }

    @Test
    fun testHeaderExclusionSuppressesTopBannerEarnings() {
        // Requirement R3: Lines in top 7.5% of screen (e.g. driver wallet balance) must be suppressed
        val screenHeight = 1000
        val topThreshold = (screenHeight * 0.075).toInt() // 75

        // Within header (< 7.5%) with currency: must be dropped
        assertFalse(
            "Top banner wallet balance at y=40 must be excluded",
            OfferOcrContext.acceptsLine("Saldo R$ 150,00", 40, screenHeight)
        )
        assertFalse(
            "Boundary at y=75 must be excluded",
            OfferOcrContext.acceptsLine("R$ 20,00", topThreshold, screenHeight)
        )

        // Below header (> 7.5%) with currency: must be retained
        assertTrue(
            "Offer fare at y=400 must be accepted",
            OfferOcrContext.acceptsLine("R$ 28,50", 400, screenHeight)
        )
    }

    @Test
    fun testActionKeywordsTriggerPermit() {
        // Requirement R3: Recognizes valid offer action triggers
        val validActionKeywords = listOf("Aceitar", "Nova entrega", "Selecionar", "Nova corrida", "Nova viagem")
        for (action in validActionKeywords) {
            val lines = listOf(action, "R$ 30,00", "8 km", "20 min")
            assertTrue("Action '$action' must permit OCR offer candidate", OfferOcrContext.permits(lines))
        }
    }

    @Test
    fun testNonOfferContextRejected() {
        // Non-offer screens like existing active route or settings should not trigger modal
        val nonOffer1 = listOf("Seu saldo", "R$ 28,50", "10 km", "20 min")
        assertFalse("Balance screen must not be permitted", OfferOcrContext.permits(nonOffer1))

        val nonOffer2 = listOf("Rota atual", "R$ 28,50", "10 km", "20 min")
        assertFalse("Active route screen must not be permitted", OfferOcrContext.permits(nonOffer2))
    }

    @Test
    fun testAmbiguousMultipleFaresRejected() {
        // Multiple conflicting fares without clear single offer must be rejected
        val ambiguousLines = listOf("Aceitar", "R$ 20,00", "R$ 35,00", "5 km")
        assertFalse("Conflicting multiple fares must be rejected as ambiguous", OfferOcrContext.permits(ambiguousLines))
    }
}
