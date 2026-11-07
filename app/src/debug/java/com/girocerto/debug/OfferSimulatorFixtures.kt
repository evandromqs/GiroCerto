package com.girocerto.debug

import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.monitoring.AccessibilityOfferReader
import com.girocerto.monitoring.OfferReaderResult

data class SimulatedOffer(
    val name: String,
    val expected: TrafficLightStatus,
    val packageName: String,
    val rawLines: List<String>
) {
    val reading: OfferReaderResult
        get() = AccessibilityOfferReader.parse(rawLines, packageName)

    /** Mesmo adaptador mínimo usado pelo coordenador produtivo, sem serviços Android. */
    fun parsedOffer(): RideOffer {
        val result = reading
        check(result.isComplete) { result.error ?: "Leitura incompleta: ${result.missingFields.joinToString()}" }
        return RideOffer(
            fare = checkNotNull(result.fare),
            distanceKm = checkNotNull(result.distanceKm),
            durationMinutes = result.durationMinutes ?: 0,
            pickupDistanceKm = result.pickupDistanceKm ?: 0.0,
            appName = result.appName,
            destinationAddress = result.destination.orEmpty(),
            pickupAddress = result.pickupAddress.orEmpty(),
            passengerRating = result.passengerRating,
            sourcePackage = packageName,
            category = if (result.appName in setOf("iFood", "99Food", "Keeta")) "Entrega" else "Corrida"
        )
    }
}

/** Cenários determinísticos para validar o motor sem abrir apps de terceiros. */
object OfferSimulatorFixtures {
    val all = listOf(
        SimulatedOffer(
            name = "Uber — boa corrida",
            expected = TrafficLightStatus.GREEN,
            packageName = "com.ubercab.driver",
            rawLines = listOf("Nova solicitação", "R$ 32,00", "1,0 km até o embarque", "8,0 km", "24 min", "★ 4,96")
        ),
        SimulatedOffer(
            name = "99 — no limite",
            expected = TrafficLightStatus.YELLOW,
            packageName = "com.app99.driver",
            rawLines = listOf("99Pop", "R$ 18,00", "1,0 km até o embarque", "8,0 km", "35 min", "★ 4,80")
        ),
        SimulatedOffer(
            name = "iFood — não compensa",
            expected = TrafficLightStatus.RED,
            packageName = "br.com.brainweb.ifood.courier",
            rawLines = listOf("Nova entrega", "R$ 7,00", "Total 9,0 km", "35 min")
        ),
        SimulatedOffer(
            name = "Keeta — boa entrega",
            expected = TrafficLightStatus.GREEN,
            packageName = "com.sankuai.sailor.courier",
            rawLines = listOf("Keeta", "R$ 20,00", "Total 5,5 km", "20 min")
        )
    )
}
