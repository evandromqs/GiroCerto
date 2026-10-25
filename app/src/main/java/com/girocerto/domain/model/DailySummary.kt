package com.girocerto.domain.model

/**
 * Resumo consolidado das corridas analisadas em determinado período (ex: no dia de hoje).
 */
data class DailySummary(
    val totalRides: Int = 0,
    val totalEstimatedProfit: Double = 0.0,
    val totalGrossFare: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val averageGrossPerKm: Double = 0.0,
    val greenCount: Int = 0,
    val yellowCount: Int = 0,
    val redCount: Int = 0
)
