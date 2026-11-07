package com.girocerto.domain.repository

import com.girocerto.domain.model.ShiftRecord
import kotlinx.coroutines.flow.StateFlow

interface ShiftTrackerRepository {
    val currentShift: StateFlow<ShiftRecord?>
    fun startShift(time: String = ShiftRecord.currentTimeString(), km: Double? = null): ShiftRecord
    fun endShift(time: String = ShiftRecord.currentTimeString(), km: Double? = null): ShiftRecord?
    fun resetShift()
}
