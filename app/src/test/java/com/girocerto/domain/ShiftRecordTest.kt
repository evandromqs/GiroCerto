package com.girocerto.domain

import com.girocerto.domain.model.ShiftRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftRecordTest {

    @Test
    fun `active shift has null endTime and null totalOdometerKm`() {
        val shift = ShiftRecord(
            date = LocalDate.now(),
            startTime = "08:00",
            startKm = 12500.0,
            isActive = true
        )

        assertTrue(shift.isActive)
        assertNull(shift.endTime)
        assertNull(shift.endKm)
        assertNull(shift.totalOdometerKm)
        assertNull(shift.durationHours)
    }

    @Test
    fun `finished shift computes totalOdometerKm and durationHours correctly`() {
        val shift = ShiftRecord(
            date = LocalDate.now(),
            startTime = "08:00",
            startKm = 12500.0,
            endTime = "12:30",
            endKm = 12585.5,
            isActive = false
        )

        assertFalse(shift.isActive)
        assertEquals(85.5, shift.totalOdometerKm!!, 0.01)
        assertEquals(4.5, shift.durationHours!!, 0.01)
    }

    @Test
    fun `totalOdometerKm is null if endKm lower than startKm`() {
        val shift = ShiftRecord(
            date = LocalDate.now(),
            startTime = "08:00",
            startKm = 12500.0,
            endTime = "09:00",
            endKm = 12400.0,
            isActive = false
        )

        assertNull(shift.totalOdometerKm)
        assertEquals(1.0, shift.durationHours!!, 0.01)
    }
}
