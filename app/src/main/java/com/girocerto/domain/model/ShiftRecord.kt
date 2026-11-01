package com.girocerto.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ShiftRecord(
    val date: LocalDate = LocalDate.now(),
    val startTime: String,
    val startKm: Double? = null,
    val endTime: String? = null,
    val endKm: Double? = null,
    val isActive: Boolean = true
) {
    val durationHours: Double? get() {
        if (endTime == null) return null
        return try {
            val start = LocalTime.parse(startTime)
            val end = LocalTime.parse(endTime)
            val minutes = java.time.Duration.between(start, end).toMinutes()
            val total = if (minutes >= 0) minutes else minutes + 1440
            total / 60.0
        } catch (_: Exception) { null }
    }

    val totalOdometerKm: Double? get() {
        return if (startKm != null && endKm != null && endKm >= startKm) {
            endKm - startKm
        } else null
    }

    companion object {
        val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        fun currentTimeString(): String = LocalTime.now().format(timeFormatter)
    }
}
