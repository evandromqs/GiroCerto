package com.girocerto.domain.usecase

import com.girocerto.domain.calculator.RideCalculator
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.VehicleSettings

/**
 * Preferencias que influenciam a explicacao da oferta, sem misturar Android,
 * armazenamento ou automacao com as regras financeiras.
 */
data class OfferEvaluationPolicy(
    val minimumPassengerRating: Double = 0.0
) {
    fun isBelowPreferredRating(rating: Double?): Boolean =
        minimumPassengerRating > 0.0 && rating != null && rating.isFinite() &&
            rating in 0.0..5.0 && rating < minimumPassengerRating
}

/**
 * Ponto unico de entrada para transformar uma oferta em uma decisao explicavel.
 *
 * Leitores de acessibilidade, OCR, simuladores e entrada manual devem produzir
 * um [RideOffer] e delegar a avaliacao financeira a este caso de uso.
 */
class AnalyzeRideOffer(
    private val calculate: (RideOffer, VehicleSettings, Long, Long) -> RideAnalysis =
        { offer, settings, id, timestamp ->
            RideCalculator.calculate(offer, settings, id, timestamp)
        }
) {
    operator fun invoke(
        offer: RideOffer,
        settings: VehicleSettings,
        policy: OfferEvaluationPolicy = OfferEvaluationPolicy(),
        id: Long = 0L,
        timestamp: Long = System.currentTimeMillis()
    ): RideAnalysis {
        val base = calculate(offer, settings, id, timestamp)
        val notes = buildList {
            if (offer.addedMinutes > 0) add("Tempo inclui margem de ${offer.addedMinutes} min.")
            if (policy.isBelowPreferredRating(offer.passengerRating)) {
                add("Nota do passageiro abaixo da sua preferência. Você decide.")
            }
        }
        return if (notes.isEmpty()) base else base.copy(
            decisionReason = buildString {
                append(base.decisionReason)
                notes.forEach { append(" • ").append(it) }
            }
        )
    }
}
