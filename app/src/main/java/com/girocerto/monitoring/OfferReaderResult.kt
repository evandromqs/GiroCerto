package com.girocerto.monitoring

/**
 * Origem de onde os dados da oferta foram extraídos.
 */
enum class OfferSource {
    MANUAL,                     // Entrada manual fornecida pelo motorista
    CLIPBOARD,                  // Dados colados via área de transferência
    NOTIFICATION,               // Notificação do sistema (quando permitido)
    ACCESSIBILITY_SERVICE,      // Extração automática via Serviço de Acessibilidade
    ACCESSIBILITY_EXPERIMENTAL, // Extração experimental legada
    OCR_FALLBACK                // Leitura ótica por captura de tela / OCR
}

/**
 * Resultado tipado de leitura de uma oferta de corrida.
 */
data class OfferReaderResult(
    val fare: Double? = null,
    val distanceKm: Double? = null,
    val durationMinutes: Int? = null,
    val pickupDistanceKm: Double? = null,
    val appName: String = "",
    val destination: String? = null,
    val pickupAddress: String? = null,
    val passengerRating: Double? = null,
    val missingFields: List<String> = emptyList(),
    val source: OfferSource = OfferSource.MANUAL,
    val error: String? = null
) {
    /**
     * Retorna se a leitura possui todos os campos mínimos necessários para o cálculo financeiro.
     */
    val isComplete: Boolean
        get() = fare != null && fare > 0.0 && distanceKm != null && distanceKm > 0.0 && missingFields.isEmpty() && error == null
}
