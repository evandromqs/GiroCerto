package com.girocerto.monitoring
import org.junit.Assert.*
import org.junit.Test
class OfferOcrContextTest {
    @Test fun currencyFormattingIsConsistentAcrossTextAndOcr() {
        for (price in listOf("R$\u00a016,73", "R$\u202f16,73", "\u200eR$\u200e 16,73", "R$\n16,73")) {
            val lines = listOf("Envios Moto", price, "29 min (10.7 km)", "Aceitar")
            assertTrue(price, OfferOcrContext.permits(lines))
            val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
            assertTrue(price, parsed.isComplete)
            assertEquals(16.73, parsed.fare!!, .001)
        }
        assertFalse(OfferOcrContext.permits(listOf("Aceitar", "R$\u00a016,73", "R$\u200e 250,00")))
    }
    @Test fun routeAndBalanceAreNotOfferContext() {
        assertFalse(OfferOcrContext.permits(listOf("Seu saldo", "R$ 28,50", "10 km", "20 min")))
        assertFalse(OfferOcrContext.permits(listOf("Rota atual", "R$ 28,50", "10 km", "20 min")))
    }
    @Test fun offerActionsAreRecognized() {
        assertTrue(OfferOcrContext.permits(listOf("Aceitar", "R$ 28,50")))
        assertTrue(OfferOcrContext.permits(listOf("Nova entrega", "Selecionar", "R$ 20,00")))
    }
    @Test fun contextDoesNotSupplyMissingMeasurements() {
        val lines = listOf("Aceitar", "R$ 28,50")
        assertTrue(OfferOcrContext.permits(lines))
        assertFalse(AccessibilityOfferReader.parse(lines, "com.app99.driver").isComplete)
    }
    @Test fun ocrRequiresExplicitOptIn() { assertFalse(CopilotPolicy().ocrEnabled) }

    @Test fun balanceAndDifferentFareAreRejected() {
        assertFalse(OfferOcrContext.permits(listOf("Saldo R$ 150,00", "Aceitar", "R$ 28,50", "10 km", "20 min")))
    }
    @Test fun repeatedSameFareIsUnambiguous() {
        assertTrue(OfferOcrContext.permits(listOf("Aceitar", "R$ 28,50", "R$ 28.50")))
    }
    @Test fun bareNumberIsNotEnoughForOcrFare() {
        assertFalse(OfferOcrContext.permits(listOf("Aceitar", "28,50", "10 km", "20 min")))
    }

    @Test fun syntheticHeaderBalanceExcludedButOfferFareRetained() {
        assertFalse(OfferOcrContext.acceptsLine("Saldo R$ 150,00", 40, 1000))
        assertTrue(OfferOcrContext.acceptsLine("R$ 28,50", 400, 1000))
        val synthetic = listOf("Saldo R$ 150,00" to 40, "Aceitar" to 350, "R$ 28,50" to 400, "10 km" to 450)
        val filtered = synthetic.filter { OfferOcrContext.acceptsLine(it.first, it.second, 1000) }.map { it.first }
        assertTrue(OfferOcrContext.permits(filtered))
    }
    @Test fun includedBonusUnderTheMainFareStaysUnambiguous() {
        assertTrue(OfferOcrContext.permits(listOf("Aceitar (2)", "R$22,58", "Inclui R$4,00 Bônus", "Distância total 7,6 km")))
    }

    @Test fun headerBoundaryAndUnknownBoundsAreConservative() {
        assertFalse(OfferOcrContext.acceptsLine("R$ 20,00", 75, 1000))
        assertTrue(OfferOcrContext.acceptsLine("R$ 20,00", 76, 1000))
        assertTrue(OfferOcrContext.acceptsLine("R$ 20,00", null, 1000))
        assertTrue(OfferOcrContext.acceptsLine("Aceitar", 40, 1000))
        assertFalse(OfferOcrContext.permits(listOf("Aceitar", "R$ 20,00", "R$ 30,00")))
    }
}
