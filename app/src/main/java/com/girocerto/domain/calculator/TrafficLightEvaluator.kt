package com.girocerto.domain.calculator

import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import java.util.Locale

/**
 * Avaliador determinístico da classificação por semáforo de uma oferta de corrida.
 */
object TrafficLightEvaluator {

    data class EvaluationResult(
        val status: TrafficLightStatus,
        val reason: String
    )

    /**
     * Avalia os indicadores financeiros contra as metas do motorista.
     */
    fun evaluate(
        offer: RideOffer,
        settings: VehicleSettings,
        grossPerKm: Double,
        grossPerHour: Double,
        estimatedProfit: Double
    ): EvaluationResult {
        if (!offer.isValid() || !settings.isValid()) {
            return EvaluationResult(
                status = TrafficLightStatus.INVALID,
                reason = "Dados insuficientes ou inconsistentes para avaliação."
            )
        }

        // Se o lucro estimado for nulo ou negativo, é alerta vermelho imediato
        if (estimatedProfit <= 0.0) {
            val lossFormatted = String.format(Locale.GERMANY, "%.2f", kotlin.math.abs(estimatedProfit))
            return EvaluationResult(
                status = TrafficLightStatus.RED,
                reason = if (estimatedProfit == 0.0) {
                    "Margem zero: os custos operacionais empatam com o valor da oferta."
                } else {
                    "Prejuízo operacional estimado de R$ $lossFormatted nesta corrida."
                }
            )
        }

        val targetKm = settings.minTargetPerKm
        val targetHour = settings.minTargetPerHour

        val meetsKm = grossPerKm >= targetKm
        val meetsHour = grossPerHour >= targetHour

        if (offer.durationMinutes == 0) {
            return EvaluationResult(
                status = when {
                    meetsKm -> TrafficLightStatus.GREEN
                    targetKm > 0 && grossPerKm / targetKm >= 0.85 -> TrafficLightStatus.YELLOW
                    else -> TrafficLightStatus.RED
                },
                reason = "Avaliação por km e custos estimados. Tempo não informado; ganho por hora indisponível."
            )
        }

        val kmRatio = if (targetKm > 0) grossPerKm / targetKm else 1.0
        val hourRatio = if (targetHour > 0) grossPerHour / targetHour else 1.0

        val kmFormatted = String.format(Locale.GERMANY, "%.2f", grossPerKm)
        val hourFormatted = String.format(Locale.GERMANY, "%.2f", grossPerHour)
        val profitFormatted = String.format(Locale.GERMANY, "%.2f", estimatedProfit)

        // Classificação VERDE: cumpre ambas as metas com lucro positivo
        if (meetsKm && meetsHour) {
            return EvaluationResult(
                status = TrafficLightStatus.GREEN,
                reason = "Compatível com suas metas (R$ $kmFormatted/km e R$ $hourFormatted/h) e lucro de R$ $profitFormatted."
            )
        }

        // Classificação AMARELO: cumpre uma das metas ou está na faixa de tolerância (>= 85%)
        val nearTolerance = 0.85
        val isNearKm = kmRatio >= nearTolerance
        val isNearHour = hourRatio >= nearTolerance

        if (meetsKm && !meetsHour) {
            val targetHourFormatted = String.format(Locale.GERMANY, "%.2f", targetHour)
            return EvaluationResult(
                status = TrafficLightStatus.YELLOW,
                reason = "Meta de km atingida (R$ $kmFormatted/km), porém ganho/h (R$ $hourFormatted/h) abaixo de R$ $targetHourFormatted/h."
            )
        }

        if (meetsHour && !meetsKm) {
            val targetKmFormatted = String.format(Locale.GERMANY, "%.2f", targetKm)
            return EvaluationResult(
                status = TrafficLightStatus.YELLOW,
                reason = "Meta de hora atingida (R$ $hourFormatted/h), porém ganho/km (R$ $kmFormatted/km) abaixo de R$ $targetKmFormatted/km."
            )
        }

        if (isNearKm && isNearHour) {
            return EvaluationResult(
                status = TrafficLightStatus.YELLOW,
                reason = "Próxima das metas (tolerância de 15%) com lucro estimado positivo de R$ $profitFormatted."
            )
        }

        // Classificação VERMELHO: abaixo de ambas as metas e fora da tolerância
        val targetKmFormatted = String.format(Locale.GERMANY, "%.2f", targetKm)
        val targetHourFormatted = String.format(Locale.GERMANY, "%.2f", targetHour)
        return EvaluationResult(
            status = TrafficLightStatus.RED,
            reason = "Oferta abaixo das metas mínimas configuradas (Meta: R$ $targetKmFormatted/km e R$ $targetHourFormatted/h)."
        )
    }
}
