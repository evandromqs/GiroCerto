package com.girocerto.domain.model

/**
 * Dados de entrada de uma oferta de corrida ou entrega inseridos manualmente ou capturados via leitura automática.
 *
 * @property fare Valor bruto da oferta em Reais (R$).
 * @property distanceKm Distância principal do percurso em km.
 * @property durationMinutes Duração estimada em minutos; zero significa não informada.
 * @property pickupDistanceKm Distância adicional até o ponto de embarque/coleta em km (padrão 0.0).
 * @property appName Nome da plataforma de origem (ex: "Uber", "99", "99Food", "iFood", "inDrive").
 * @property destinationAddress Endereço de destino ou entrega do cliente.
 * @property pickupAddress Endereço de embarque ou ponto de coleta (ex: Restaurante).
 * @property category Categoria do serviço ("Corrida", "Entrega", "Moto", "Flash", "Pop").
 * @property passengerRating Nota do passageiro ou cliente (ex: 4.95), quando visível.
 */
data class RideOffer(
    val fare: Double,
    val distanceKm: Double,
    val durationMinutes: Int,
    val pickupDistanceKm: Double = 0.0,
    val appName: String = "",
    val destinationAddress: String = "",
    val pickupAddress: String = "",
    val category: String = "Corrida",
    val passengerRating: Double? = null
) {
    /**
     * Distância total considerada no cálculo (trajeto + deslocamento de embarque/coleta).
     */
    val totalDistanceKm: Double
        get() = distanceKm + pickupDistanceKm

    /**
     * Duração convertida em horas para cálculo de rendimento por hora.
     */
    val durationHours: Double
        get() = if (durationMinutes > 0) durationMinutes / 60.0 else 0.0

    /**
     * Validação dos dados da oferta.
     */
    fun isValid(): Boolean {
        return fare > 0.0 &&
                distanceKm >= 0.0 &&
                pickupDistanceKm >= 0.0 &&
                totalDistanceKm > 0.0 &&
                durationMinutes >= 0
    }
}
