package com.girocerto.monitoring

internal object OfferScreenContext {
    private val action = Regex("(?i)\\b(aceitar|selecionar|nova entrega|nova corrida|nova viagem|solicitação de viagem)\\b")
    fun hasOfferAction(lines: List<String>) = lines.any { action.containsMatchIn(it) }
}
