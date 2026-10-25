package com.girocerto.data.repository

import com.girocerto.domain.model.WorkDay
import com.girocerto.domain.model.WorkPlatform
import java.util.Locale

object WorkExport {
    fun csv(days: List<WorkDay>): String = buildString {
        appendLine("Data;" + WorkPlatform.entries.joinToString(";") { it.label } + ";Recebido;Km reais;Horas reais;Custos;Saldo")
        days.sortedBy { it.date }.forEach { day ->
            val values = WorkPlatform.entries.map { p -> day.entries.firstOrNull { it.platform == p }?.revenue ?: 0.0 } +
                listOf(day.revenue, day.totalKm, day.activeHours, day.cost, day.net)
            appendLine(day.date.toString() + ";" + values.joinToString(";") { String.format(Locale.forLanguageTag("pt-BR"), "%.2f", it) })
        }
    }
}
