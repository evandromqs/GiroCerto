package com.girocerto.domain.repository

import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.model.WorkDay
import com.girocerto.domain.model.WorkPlatform
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface WorkLedgerRepository : DriverProfileRepository {
    val loading: StateFlow<Boolean>
    val loadError: StateFlow<String?>
    val selectedPlatforms: StateFlow<Set<WorkPlatform>>
    val restoreRevision: StateFlow<Long>
    val profile: StateFlow<ElectricProfile?>
    val days: StateFlow<List<WorkDay>>
    fun retryLoad()
    suspend fun awaitLoaded()
    suspend fun recordAcceptedRide(platform: WorkPlatform, fare: Double, distanceKm: Double)
    suspend fun saveSelectedPlatforms(platforms: Set<WorkPlatform>)
    suspend fun saveDay(day: WorkDay)
    suspend fun deleteDay(date: LocalDate)
    suspend fun backup(): String
    suspend fun restoreBackup(text: String)
}
