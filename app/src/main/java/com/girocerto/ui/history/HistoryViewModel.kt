package com.girocerto.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.repository.RideHistoryRepository
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel(
    private val repository: RideHistoryRepository
) : ViewModel() {
    val rides: StateFlow<List<RideAnalysis>> = repository.rides
    val loading: StateFlow<Boolean> = repository.loading
    val loadError: StateFlow<String?> = repository.loadError

    fun retryLoad() { repository.retryLoad() }
    suspend fun deleteRide(id: Long) = repository.deleteRide(id)
    suspend fun clearHistory() = repository.clearHistory()

    class Factory(private val repository: RideHistoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(repository) as T
        }
    }
}

/** Regra pura, reutilizavel em testes e independente de Compose/Android. */
object HistoryFilter {
    fun apply(
        rides: List<RideAnalysis>,
        platform: String?,
        periodStartMillis: Long
    ): List<RideAnalysis> = rides.filter { ride ->
        (platform.isNullOrBlank() || ride.offer.appName == platform) &&
            ride.timestamp >= periodStartMillis
    }
}
