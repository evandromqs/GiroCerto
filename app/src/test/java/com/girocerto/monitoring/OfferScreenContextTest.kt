package com.girocerto.monitoring
import org.junit.Assert.*
import org.junit.Test
class OfferScreenContextTest {
    @Test fun earningsScreenIsNotAnOffer() { assertFalse(OfferScreenContext.hasOfferAction(listOf("Ganhos", "R$ 50,00", "10 km"))) }
    @Test fun offerActionIsRequired() { assertTrue(OfferScreenContext.hasOfferAction(listOf("Envios Moto", "Aceitar"))) }
}
