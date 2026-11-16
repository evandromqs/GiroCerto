package com.girocerto.monitoring

internal object OfferScreenContext {
    private val action = Regex("(?i)\\b(aceitar|selecionar|confirmar|nova entrega|nova corrida|nova viagem|solicitação de viagem|solicitação de corrida|solicitação de entrega|envios moto|entrega food|pedido|pedidos|uberx|comfort|black|99pop|99moto|99comfort|99plus)\\b")
    fun hasOfferAction(lines: List<String>) = lines.any { action.containsMatchIn(it) }
}
