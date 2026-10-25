package com.girocerto.domain.model

/**
 * Resultado detalhado da avaliação financeira de uma oferta de corrida.
 */
data class RideAnalysis(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val offer: RideOffer,
    val totalDistanceKm: Double,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val fuelCost: Double,
    val additionalOperationalCost: Double,
    val fixedCost: Double,
    val totalCost: Double,
    val estimatedProfit: Double,
    val estimatedMarginPercent: Double,
    val trafficLight: TrafficLightStatus,
    val decisionReason: String
)
