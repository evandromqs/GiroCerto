package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class DeliveryOfferRulesTest {
    @Test fun ninetyNineExcludesCustomerCashAndConditionalBonus() {
        val lines = listOf("Entrega Food", "R$9,05", "+R$3,00", "Entregar até 19:43",
            "Distância total 7,1 km", "Coletar R$27,29 em dinheiro do cliente", "Aceitar pedido")
        val result = AccessibilityOfferReader.parse(lines, "com.app99.driver")
        assertTrue(result.isComplete)
        assertTrue(OfferOcrContext.permits(lines, "com.app99.driver"))
        assertEquals(9.05, result.fare!!, .001)
        assertEquals(7.1, result.distanceKm!!, .001)
        assertNull(result.durationMinutes) // A delivery deadline is not a trip duration.
        assertEquals(0.0, result.pickupDistanceKm!!, .001)
        assertFalse(AccessibilityOfferReader.parse(lines, "com.ubercab.driver").isComplete)
    }

    @Test fun splitCollectionLabelAndPlusRewardStaySeparateFromFare() {
        val lines = listOf("R$", "9,05", "+", "R$", "3,00", "Entregar até 19:43",
            "Distância total", "7,1 km", "Coletar", "R$27,29", "em dinheiro do cliente", "Aceitar pedido")
        val parsed = AccessibilityOfferReader.parse(lines, "com.app99.driver")
        assertEquals(9.05, parsed.fare!!, .001)
        assertTrue(OfferOcrContext.permits(lines, "com.app99.driver"))
    }

    @Test fun unknownCompetingPricesStillReject99Offer() {
        assertFalse(AccessibilityOfferReader.parse(listOf("R$9,05", "R$27,29", "7,1 km", "Aceitar"), "com.app99.driver").isComplete)
    }

    @Test fun ifoodRouteCardExcludesOtherAppsProfitAndRouteMetrics() {
        val lines = listOf("iFood", "Lucro: R$ 11,86", "5,89 km 22 Min", "Coleta 1", "Entrega 1",
            "R$ 13,63", "Rota para Moto", "Distância total", "5,89 km",
            "Tempo aproximado de rota", "22 min", "Possibilidade de devolução", "Sim", "Rejeitar", "Aceitar (84)")
        val result = AccessibilityOfferReader.parse(lines, "br.com.ifood.driver.app")
        assertTrue(result.isComplete)
        assertTrue(OfferOcrContext.permits(lines, "br.com.ifood.driver.app"))
        assertEquals(13.63, result.fare!!, .001)
        assertEquals(5.89, result.distanceKm!!, .001)
        assertEquals(22, result.durationMinutes)
    }

    @Test fun ifoodColumnLayoutKeepsDistanceAndTime() {
        val lines = listOf("R$ 14,99", "Rota para Moto", "Distância total", "Tempo aproximado de rota",
            "9,99 km", "34 min", "Possibilidade de devolução", "Sim", "Rejeitar", "Aceitar")
        val result = AccessibilityOfferReader.parse(lines, "br.com.ifood.driver")
        assertTrue(result.isComplete)
        assertTrue(OfferOcrContext.permits(lines, "br.com.ifood.driver"))
        assertEquals(14.99, result.fare!!, .001)
        assertEquals(9.99, result.distanceKm!!, .001)
        assertEquals(34, result.durationMinutes)
    }

    @Test fun missingIfoodCardFareCannotUseAnotherOverlaysProfit() {
        val lines = listOf("Lucro: R$11,86", "Rota para Moto", "Distância total 5,89 km", "22 min", "Aceitar")
        assertFalse(AccessibilityOfferReader.parse(lines, "br.com.ifood.driver.app").isComplete)
        assertFalse(OfferOcrContext.permits(lines, "br.com.ifood.driver.app"))
    }

    @Test fun uberInputsAreUnchangedByDeliveryRules() {
        val lines = listOf("Envios Moto", "R$ 8,96", "16 min (4.8 km)", "Aceitar")
        assertSame(lines, DeliveryOfferRules.prepare(lines, "com.ubercab.driver"))
        val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
        assertEquals(8.96, parsed.fare!!, .001)
        assertEquals(4.8, parsed.distanceKm!!, .001)
        assertEquals(16, parsed.durationMinutes)
        assertEquals(OfferOcrContext.permits(lines), OfferOcrContext.permits(lines, "com.ubercab.driver"))
    }

    @Test fun optionalBufferAddsFiveOnlyToKnownUberAnd99Durations() {
        assertEquals(16, CopilotPolicy().adjustedDuration(16, "com.ubercab.driver"))
        val policy = CopilotPolicy(addFiveMinutes = true)
        assertEquals(21, policy.adjustedDuration(16, "com.ubercab.driver"))
        assertEquals(39, policy.adjustedDuration(34, "com.app99.driver"))
        assertEquals(0, policy.adjustedDuration(0, "com.app99.driver"))
        assertEquals(22, policy.adjustedDuration(22, "br.com.ifood.driver.app"))
    }
}
