package com.girocerto.domain.model

/**
 * Tipos de combustível suportados para o perfil operacional do veículo.
 */
enum class FuelType(val displayName: String, val unitLabel: String) {
    GASOLINA("Gasolina", "R$/Litro"),
    ETANOL("Etanol", "R$/Litro"),
    GNV("GNV", "R$/m³"),
    ELETRICO("Eletricidade", "R$/kWh")
}
