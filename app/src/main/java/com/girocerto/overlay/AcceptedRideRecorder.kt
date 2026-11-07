package com.girocerto.overlay

import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.WorkPlatform
import com.girocerto.domain.repository.RideHistoryRepository
import com.girocerto.domain.repository.WorkLedgerRepository

/** Persists an accepted offer in both history and the daily ledger. */
internal class AcceptedRideRecorder(
    private val history: RideHistoryRepository,
    private val ledger: WorkLedgerRepository
) {
    suspend fun record(analysis: RideAnalysis) {
        history.saveRide(analysis)
        ledger.recordAcceptedRide(
            platform = WorkPlatform.fromAppName(analysis.offer.appName),
            fare = analysis.offer.fare,
            distanceKm = analysis.totalDistanceKm
        )
    }
}
