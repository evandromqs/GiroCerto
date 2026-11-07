package com.girocerto.ui.history

import com.girocerto.domain.model.RideAnalysis
import com.girocerto.domain.model.RideOffer
import com.girocerto.domain.model.TrafficLightStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    @Test fun `combina plataforma e periodo sem depender da interface`() {
        val oldUber = ride(1, "Uber", 100)
        val recentUber = ride(2, "Uber", 300)
        val recentKeeta = ride(3, "Keeta", 400)

        val result = HistoryFilter.apply(listOf(oldUber, recentUber, recentKeeta), "Uber", 200)

        assertEquals(listOf(recentUber), result)
    }

    private fun ride(id: Long, app: String, timestamp: Long) = RideAnalysis(
        id = id, timestamp = timestamp,
        offer = RideOffer(appName = app, fare = 10.0, pickupDistanceKm = 1.0, distanceKm = 2.0, durationMinutes = 10),
        totalDistanceKm = 3.0, grossPerKm = 3.0, grossPerHour = 60.0,
        fuelCost = 0.0, additionalOperationalCost = 0.0, fixedCost = 0.0,
        totalCost = 0.0, estimatedProfit = 10.0, estimatedMarginPercent = 100.0,
        trafficLight = TrafficLightStatus.GREEN, decisionReason = "ok"
    )
}
