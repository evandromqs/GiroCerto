package com.girocerto.domain.calculator

import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import com.girocerto.domain.model.VehicleSettings
import kotlin.math.round

/**
 * Calculadora central de viabilidade financeira e custos operacionais de ofertas de corrida.
 */
object RideCalculator {

    /**
     * Calcula a análise financeira completa para uma oferta e configurações fornecidas.
     * Garante que nenhum valor retornado seja NaN ou Infinity.
     */
    fun calculate(
        offer: RideOffer,
        settings: VehicleSettings,
        id: Long = 0L,
        timestamp: Long = System.currentTimeMillis()
    ): RideAnalysis {
        // Verificação de consistência básica
        if (!offer.isValid() || !settings.isValid()) {
            val eval = TrafficLightEvaluator.evaluate(
                offer = offer,
                settings = settings,
                grossPerKm = 0.0,
                grossPerHour = 0.0,
                estimatedProfit = 0.0
            )

            return RideAnalysis(
                id = id,
                timestamp = timestamp,
                offer = offer,
                totalDistanceKm = sanitize(offer.totalDistanceKm),
                grossPerKm = 0.0,
                grossPerHour = 0.0,
                fuelCost = 0.0,
                additionalOperationalCost = 0.0,
                fixedCost = sanitize(settings.fixedCostPerRide),
                totalCost = 0.0,
                estimatedProfit = 0.0,
                estimatedMarginPercent = 0.0,
                trafficLight = eval.status,
                decisionReason = eval.reason
            )
        }

        val totalDist = sanitize(offer.totalDistanceKm)
        val durationH = sanitize(offer.durationHours)

        // 1. Ganho bruto por km
        val grossPerKm = if (totalDist > 0.0) sanitize(offer.fare / totalDist) else 0.0

        // 2. Ganho bruto por hora
        val grossPerHour = if (durationH > 0.0) sanitize(offer.fare / durationH) else 0.0

        // 3. Custo de combustível = (distância total / consumo em km/L) * preço por litro
        val fuelCost = if (settings.fuelConsumptionKmL > 0.0) {
            sanitize((totalDist / settings.fuelConsumptionKmL) * settings.fuelPricePerLiter)
        } else {
            0.0
        }

        // 4. Custo operacional adicional (pneus, óleo, manutenção preventiva, depreciação)
        val additionalOperationalCost = sanitize(totalDist * settings.additionalCostPerKm)

        // 5. Custo fixo por corrida (se configurado)
        val fixedCost = sanitize(settings.fixedCostPerRide)

        // 6. Custo total estimado
        val totalCost = sanitize(fuelCost + additionalOperationalCost + fixedCost)

        // 7. Lucro líquido estimado = valor ofertado - custo total
        val estimatedProfit = sanitize(offer.fare - totalCost)

        // 8. Margem estimada em percentual
        val estimatedMarginPercent = if (offer.fare > 0.0) {
            sanitize((estimatedProfit / offer.fare) * 100.0)
        } else {
            0.0
        }

        // 9. Classificação visual no semáforo
        val eval = TrafficLightEvaluator.evaluate(
            offer = offer,
            settings = settings,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            estimatedProfit = estimatedProfit
        )

        return RideAnalysis(
            id = id,
            timestamp = timestamp,
            offer = offer,
            totalDistanceKm = round2(totalDist),
            grossPerKm = round2(grossPerKm),
            grossPerHour = round2(grossPerHour),
            fuelCost = round2(fuelCost),
            additionalOperationalCost = round2(additionalOperationalCost),
            fixedCost = round2(fixedCost),
            totalCost = round2(totalCost),
            estimatedProfit = round2(estimatedProfit),
            estimatedMarginPercent = round2(estimatedMarginPercent),
            trafficLight = eval.status,
            decisionReason = eval.reason
        )
    }

    /**
     * Arredonda com 2 casas decimais para consistência monetária.
     */
    fun round2(value: Double): Double {
        val sanitized = sanitize(value)
        return round(sanitized * 100.0) / 100.0
    }

    /**
     * Previne propagação de NaN e Infinity.
     */
    private fun sanitize(value: Double): Double {
        return if (value.isNaN() || value.isInfinite()) 0.0 else value
    }
}
