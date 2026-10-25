package com.girocerto.monitoring

import com.girocerto.domain.model.RideOffer

/**
 * Implementação padrão e estável de leitura manual de ofertas.
 * Valida os dados informados diretamente pelo usuário em formulário ou tela.
 */
class ManualOfferReader(
    private val rawFare: String,
    private val rawDistanceKm: String,
    private val rawDurationMinutes: String,
    private val rawPickupDistanceKm: String = "0"
) : OfferReader {

    override val source: OfferSource = OfferSource.MANUAL

    override suspend fun readOffer(): OfferReaderResult {
        val missingFields = mutableListOf<String>()

        val fare = rawFare.trim().replace(",", ".").toDoubleOrNull()
        if (fare == null || fare <= 0.0) {
            missingFields.add("Valor da oferta (R$)")
        }

        val distanceKm = rawDistanceKm.trim().replace(",", ".").toDoubleOrNull()
        if (distanceKm == null || distanceKm <= 0.0) {
            missingFields.add("Distância da corrida (km)")
        }

        val durationMinutes = rawDurationMinutes.trim().toIntOrNull()
        if (durationMinutes == null || durationMinutes <= 0) {
            missingFields.add("Duração estimada (minutos)")
        }

        val pickupDistance = rawPickupDistanceKm.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

        if (missingFields.isNotEmpty()) {
            return OfferReaderResult(
                fare = fare,
                distanceKm = distanceKm,
                durationMinutes = durationMinutes,
                pickupDistanceKm = pickupDistance,
                missingFields = missingFields,
                source = source,
                error = "Preencha corretamente os campos obrigatórios."
            )
        }

        return OfferReaderResult(
            fare = fare,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            pickupDistanceKm = pickupDistance,
            missingFields = emptyList(),
            source = source,
            error = null
        )
    }

    /**
     * Converte o resultado de leitura em um [RideOffer] de domínio quando completo.
     */
    fun toRideOffer(): RideOffer? {
        val fare = rawFare.trim().replace(",", ".").toDoubleOrNull() ?: return null
        val distanceKm = rawDistanceKm.trim().replace(",", ".").toDoubleOrNull() ?: return null
        val durationMinutes = rawDurationMinutes.trim().toIntOrNull() ?: return null
        val pickupDistance = rawPickupDistanceKm.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

        val offer = RideOffer(
            fare = fare,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            pickupDistanceKm = pickupDistance
        )
        return if (offer.isValid()) offer else null
    }
}
