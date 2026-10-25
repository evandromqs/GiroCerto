package com.girocerto.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitários de formatação no padrão brasileiro (pt-BR).
 */
object Formatters {

    private val ptBrLocale = Locale.forLanguageTag("pt-BR")

    fun formatCurrency(value: Double): String {
        return String.format(ptBrLocale, "R$ %.2f", value)
    }

    fun formatKm(value: Double): String {
        return String.format(ptBrLocale, "%.1f km", value)
    }

    fun formatPerKm(value: Double): String {
        return String.format(ptBrLocale, "R$ %.2f/km", value)
    }

    fun formatPerHour(value: Double): String {
        return String.format(ptBrLocale, "R$ %.2f/h", value)
    }

    fun formatOfferPerHour(value: Double, durationMinutes: Int): String =
        if (durationMinutes > 0) formatPerHour(value) else "Tempo não informado"

    fun formatDuration(durationMinutes: Int): String =
        if (durationMinutes > 0) "$durationMinutes min" else "Tempo não informado"

    fun formatPercent(value: Double): String {
        return String.format(ptBrLocale, "%.1f%%", value)
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBrLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", ptBrLocale)
        return sdf.format(Date(timestamp))
    }
}



