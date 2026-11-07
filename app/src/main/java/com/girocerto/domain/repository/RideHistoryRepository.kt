package com.girocerto.domain.repository

import com.girocerto.domain.model.RideAnalysis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

interface RideHistoryRepository {
    val rides: StateFlow<List<RideAnalysis>>
    val loading: StateFlow<Boolean>
    val loadError: StateFlow<String?>
    fun retryLoad(): Job
    suspend fun saveRide(analysis: RideAnalysis): RideAnalysis
    suspend fun deleteRide(id: Long)
    suspend fun clearHistory()
    suspend fun backup(): String
    suspend fun restoreBackup(text: String)
}
