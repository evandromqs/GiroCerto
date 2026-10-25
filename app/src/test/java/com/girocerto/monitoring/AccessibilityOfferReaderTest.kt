package com.girocerto.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityOfferReaderTest {
    @Test fun uberEnviosMotoShownInUserScreenshotCalculatesRates() {
        // Transcribed non-personal fields from the reported popup; no addresses or image stored.
        val lines = listOf("2% de bateria restante no celular.", "Envios Moto", "R$ 16,73",
            "4,95 (491)", "Verificado", "29 min (10.7 km)", "Aceitar")
        val result = AccessibilityOfferReader.parse(lines, "com.ubercab.driver")
        assertTrue(result.isComplete)
        assertTrue(OfferOcrContext.permits(lines))
        assertEquals(16.73, result.fare!!, 0.001)
        assertEquals(10.7, result.distanceKm!!, 0.001)
        assertEquals(0.0, result.pickupDistanceKm!!, 0.001)
        assertEquals(29, result.durationMinutes)
        val analysis = com.girocerto.domain.calculator.RideCalculator.calculate(
            com.girocerto.domain.model.RideOffer(fare = result.fare!!, distanceKm = result.distanceKm!!,
                durationMinutes = result.durationMinutes!!), com.girocerto.domain.model.VehicleSettings())
        assertEquals(1.56, analysis.grossPerKm, 0.001)
        assertEquals(34.61, analysis.grossPerHour, 0.001)
    }

    @Test fun deliveryWithoutTimeStillCalculatesPerKm() {
        val result = AccessibilityOfferReader.parse(listOf("Nova entrega", "R$ 18,00", "Distância total 6 km", "Aceitar"), "br.com.ifood.driver.app")
        assertTrue(result.isComplete)
        assertEquals(null, result.durationMinutes)
        assertEquals(6.0, result.distanceKm!!, 0.001)
    }

    @Test fun statedTotalDoesNotAddCollectionDistanceAgain() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 18,00", "Até a coleta 2 km", "Distância total 6 km", "20 min"), "com.didiglobal.food.driver")
        assertTrue(result.isComplete)
        assertEquals(6.0, result.distanceKm!! + result.pickupDistanceKm!!, 0.001)
    }

    @Test fun conflictingTotalsDoNotInventDistance() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 18,00", "Total 6 km", "Total 9 km", "20 min"), "com.didiglobal.food.driver")
        assertFalse(result.isComplete)
    }
    @Test
    fun currencyAndUnitsWithNonBreakingSpaces() {
        val result = AccessibilityOfferReader.parse(listOf("R$\u00A04,95", "3\u202Fkm", "10\u00A0min"), "com.ubercab.driver")
        assertTrue(result.isComplete)
        assertEquals(4.95, result.fare!!, 0.001)
    }

    @Test
    fun testParseUberDriverOffer() {
        val uberScreenDump = listOf(
            "UberX",
            "Nova viagem disponível",
            "R$ 34,90",
            "Embarque a 1,5 km (4 min)",
            "Viagem de 7,2 km • 21 min",
            "Destino", "Rua Haddock Lobo, Cerqueira César",
            "⭐ 4.96"
        )

        val result = AccessibilityOfferReader.parse(uberScreenDump, "com.ubercab.driver")

        assertTrue("A oferta da Uber deve estar completa", result.isComplete)
        assertEquals("Uber", result.appName)
        assertEquals(34.90, result.fare ?: 0.0, 0.01)
        assertEquals(7.2, result.distanceKm ?: 0.0, 0.01)
        assertEquals(1.5, result.pickupDistanceKm ?: 0.0, 0.01)
        assertEquals(25, result.durationMinutes ?: 0) // soma dos tempos (4 min + 21 min)
        assertEquals(4.96, result.passengerRating ?: 0.0, 0.01)
        assertNotNull(result.destination)
    }

    @Test
    fun testParse99MotoristaOffer() {
        val ninetyNineScreenDump = listOf(
            "99Pop",
            "R$ 21,50",
            "600 m até o passageiro",
            "Percurso: 5.8 km",
            "16 min",
            "Destino", "Av. Brigadeiro Faria Lima, 2000",
            "★ 4.88"
        )

        val result = AccessibilityOfferReader.parse(ninetyNineScreenDump, "com.taxis99")

        assertTrue("A oferta da 99 deve estar completa", result.isComplete)
        assertEquals("99", result.appName)
        assertEquals(21.50, result.fare ?: 0.0, 0.01)
        assertEquals(5.8, result.distanceKm ?: 0.0, 0.01)
        assertEquals(0.6, result.pickupDistanceKm ?: 0.0, 0.01) // 600m convertido em 0.6 km
        assertEquals(16, result.durationMinutes ?: 0)
        assertEquals(4.88, result.passengerRating ?: 0.0, 0.01)
        assertEquals("Av. Brigadeiro Faria Lima, 2000", result.destination)
    }

    @Test
    fun testParseInDriveOffer() {
        val inDriveScreenDump = listOf(
            "inDrive Corrida",
            "R$ 18,00",
            "Distância: 4.2 km",
            "12 min",
            "Rua Augusta, Consolação"
        )

        val result = AccessibilityOfferReader.parse(inDriveScreenDump, "sinet.startup.indriver")

        assertTrue("A oferta da inDrive deve estar completa", result.isComplete)
        assertEquals("inDrive", result.appName)
        assertEquals(18.00, result.fare ?: 0.0, 0.01)
        assertEquals(4.2, result.distanceKm ?: 0.0, 0.01)
        assertEquals(12, result.durationMinutes ?: 0)
    }

    @Test
    fun testParseLongDistanceWithHours() {
        val longTripScreenDump = listOf(
            "Uber Viagens",
            "R$ 160,00",
            "85 km",
            "1 h 30 min",
            "Aeroporto Internacional de Guarulhos"
        )

        val result = AccessibilityOfferReader.parse(longTripScreenDump, "com.ubercab.driver")

        assertTrue(result.isComplete)
        assertEquals(160.00, result.fare ?: 0.0, 0.01)
        assertEquals(85.0, result.distanceKm ?: 0.0, 0.01)
        assertEquals(90, result.durationMinutes ?: 0) // 1h30min = 90 min
    }

    @Test
    fun testParseIncompleteScreenWithoutFare() {
        val incompleteDump = listOf(
            "Mapa da cidade",
            "15 km",
            "20 min",
            "Rua das Palmeiras"
        )

        val result = AccessibilityOfferReader.parse(incompleteDump, "com.ubercab.driver")

        assertFalse("Sem tarifa a oferta não deve ser considerada completa", result.isComplete)
        assertTrue(result.missingFields.contains("Valor da oferta"))
    }

    @Test
    fun testParseEmptyList() {
        val result = AccessibilityOfferReader.parse(emptyList(), "com.ubercab.driver")

        assertFalse(result.isComplete)
        assertNotNull(result.error)
    }

    @Test fun decimalPointFareKeepsCents() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 28.50", "10 km", "20 min"), "com.ubercab.driver")
        assertEquals(28.50, result.fare!!, 0.001)
    }

    @Test fun ratingsNeverBecomeFares() {
        for (rating in listOf("⭐ 4,95", "⭐ 4.95", "4.95", "Nota 4,95")) {
            val result = AccessibilityOfferReader.parse(listOf(rating, "10 km", "20 min"), "com.ubercab.driver")
            assertFalse(result.isComplete)
        }
    }

    @Test fun addressLabelsCanBeSeparateNodes() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 25,00", "10 km", "20 min", "Origem", "Rua A, 100", "Destino", "Rua B, 200"), "com.ubercab.driver")
        assertEquals("Rua B, 200", result.destination)
    }

    @Test fun unlabelledAddressesUseDestinationAfterOrigin() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 25,00", "10 km", "20 min", "Rua A, 100", "Rua B, 200"), "com.ubercab.driver")
        assertEquals("Rua B, 200", result.destination)
    }

    @Test fun singleUnlabelledAddressIsNotAssumedToBeDestination() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 25,00", "10 km", "20 min", "Rua A, 100"), "com.ubercab.driver")
        assertEquals(null, result.destination)
    }

    @Test fun real99DriverPackageResolvesPlatform() {
        val result = AccessibilityOfferReader.parse(listOf("R$ 28,50", "10 km", "20 min"), "com.app99.driver")
        assertTrue(result.isComplete)
        assertEquals("99", result.appName)
    }
    @Test fun adjacentSplitCurrencyAndMeasurementTokensAreRead() {
        val result = AccessibilityOfferReader.parse(listOf("R$", "28,50", "7,2", "km", "21", "min"), "com.app99.driver")
        assertTrue(result.isComplete)
        assertEquals(28.50, result.fare!!, 0.001)
        assertEquals(7.2, result.distanceKm!!, 0.001)
        assertEquals(21, result.durationMinutes!!)
    }
    @Test fun splitSmallFareDoesNotBecomeRating() {
        val result = AccessibilityOfferReader.parse(listOf("R$", "4,95", "2", "km", "8", "min"), "com.app99.driver")
        assertTrue(result.isComplete)
        assertEquals(4.95, result.fare!!, 0.001)
        assertEquals(null, result.passengerRating)
    }
    @Test fun splitHoursMinutesAndMetersKeepUnits() {
        val result = AccessibilityOfferReader.parse(listOf("BRL", "90.50", "600", "m", "40", "km", "1", "h", "15", "min"), "com.app99.driver")
        assertTrue(result.isComplete)
        assertEquals(0.6, result.pickupDistanceKm!!, 0.001)
        assertEquals(75, result.durationMinutes!!)
    }
    @Test fun nonAdjacentUnitsAreNotInvented() {
        val result = AccessibilityOfferReader.parse(listOf("R$", "28,50", "7", "outro texto", "km", "20", "outro texto", "min"), "com.app99.driver")
        assertFalse(result.isComplete)
        assertEquals(null, result.distanceKm)
        assertEquals(null, result.durationMinutes)
    }
}
