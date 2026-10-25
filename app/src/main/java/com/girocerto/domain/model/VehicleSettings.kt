package com.girocerto.domain.model

/**
 * Configurações operacionais e metas financeiras definidas pelo motorista/entregador.
 */
data class VehicleSettings(
    val fuelType: FuelType = FuelType.GASOLINA,
    val fuelPricePerLiter: Double = 5.80,
    val fuelConsumptionKmL: Double = 10.0,
    val additionalCostPerKm: Double = 0.30,
    val minTargetPerKm: Double = 2.00,
    val minTargetPerHour: Double = 35.00,
    val fixedCostPerRide: Double = 0.00,
    val dailyFixedCost: Double = 0.00,
    val isVoiceEnabled: Boolean = true,
    val speechRate: Float = 1.1f,
    val cardDurationSeconds: Int = 18,
    val isFloatingBubbleEnabled: Boolean = true
) {
    /**
     * Valida se os parâmetros de custos e consumo são consistentes para cálculo financeiro.
     */
    fun isValid(): Boolean {
        return fuelPricePerLiter >= 0.0 &&
                fuelConsumptionKmL > 0.0 &&
                additionalCostPerKm >= 0.0 &&
                minTargetPerKm >= 0.0 &&
                minTargetPerHour >= 0.0 &&
                fixedCostPerRide >= 0.0 &&
                dailyFixedCost >= 0.0
    }
}
