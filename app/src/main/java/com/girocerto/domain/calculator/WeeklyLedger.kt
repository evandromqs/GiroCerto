package com.girocerto.domain.calculator

import com.girocerto.domain.model.WorkDay
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class WeeklyClosing(val start: LocalDate, val revenue: Double, val variableCost: Double,
    val contract: Double, val registeredDays: Int, val usedCurrentContract: Boolean) {
    val net get() = revenue - variableCost - contract
}

object WeeklyLedger {
    fun calculate(date: LocalDate, days: List<WorkDay>, fallbackContract: Double): WeeklyClosing {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val records = days.filter { !it.date.isBefore(start) && it.date.isBefore(start.plusDays(7)) }
        // The first recorded contract in this week remains authoritative after later profile edits.
        val snapshot = records.sortedBy { it.date }.firstNotNullOfOrNull { it.weeklyRent }
        return WeeklyClosing(start, records.sumOf { it.revenue }, records.sumOf { it.totalKm * it.variablePerKm },
            snapshot ?: fallbackContract, records.size, snapshot == null)
    }
}
