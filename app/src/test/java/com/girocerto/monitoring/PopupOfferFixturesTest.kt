package com.girocerto.monitoring

import org.junit.Assert.*
import org.junit.Test

class PopupOfferFixturesTest {
    @Test fun uberPopupValuesMatchTheReportedCards() {
        for ((fare, distance, minutes) in listOf(Triple(14.34, 12.4, 34), Triple(4.20, 2.2, 11))) {
            val lines = listOf("Envios Moto", String.format(java.util.Locale.US, "R$ %.2f", fare).replace('.', ','), "$minutes min ($distance km)", "Aceitar")
            val parsed = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
            assertEquals(fare, parsed.fare!!, .001)
            assertEquals(distance, parsed.distanceKm!!, .001)
            assertEquals(minutes, parsed.durationMinutes)
        }
    }
    @Test fun ninetyNinePopupDoesNotBorrowAnotherAppsDurationOrAddIncludedBonus() {
        val parsed = AccessibilityOfferReader.parse(listOf("2 Pedidos na rota", "R$18,22", "Inclui R$4,00 Bônus", "Distância total 7,9 km", "Aceitar (2)"), "com.taxis99")
        assertEquals(18.22, parsed.fare!!, .001)
        assertEquals(7.9, parsed.distanceKm!!, .001)
        assertTrue(parsed.durationMinutes == null || parsed.durationMinutes == 0)
    }
}
