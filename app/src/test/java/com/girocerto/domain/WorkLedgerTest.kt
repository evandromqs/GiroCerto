package com.girocerto.domain

import com.girocerto.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkLedgerTest {
    private val profile = ElectricProfile("Ana", 250.0, 0.5, 0.1, 5, 8.0, 100.0, 1000.0)

    @Test fun electricContractCoversWeeklyRentAndAllKilometres() {
        assertTrue(profile.valid())
        assertEquals(550.0, profile.weeklyCosts, 0.001)
        assertEquals(310.0, profile.dailyGrossGoal, 0.001)
        assertEquals(3.1, profile.targetPerKm, 0.001)
        assertEquals(38.75, profile.targetPerHour, 0.001)
    }

    @Test fun overlappingAppsDoNotDoubleCountCostsOrTime() {
        val day = WorkDay(LocalDate.of(2026, 10, 24), listOf(
            PlatformWork(WorkPlatform.UBER, 120.0, 50.0, 4.0),
            PlatformWork(WorkPlatform.IFOOD, 100.0, 40.0, 4.0)),
            100.0, 5.0, 50.0, 0.6, 310.0)
        assertTrue(day.valid())
        assertEquals(110.0, day.cost, 0.001)
        assertEquals(110.0, day.net, 0.001)
        assertEquals(44.0, day.grossPerHour!!, 0.001)
        assertEquals(2.2, day.grossPerKm!!, 0.001)
    }

    @Test fun weeklyCostDoesNotBecomePerRideCostOrFuelCost() {
        val settings = profile.vehicleSettings(VehicleSettings())
        assertEquals(FuelType.ELETRICO, settings.fuelType)
        assertEquals(0.0, settings.fuelPricePerLiter, 0.0)
        assertEquals(0.6, settings.additionalCostPerKm, 0.001)
        assertEquals(50.0, settings.dailyFixedCost, 0.001)
        assertEquals(0.0, settings.fixedCostPerRide, 0.0)
    }

    @Test fun invalidInputsAndDuplicatePlatformsRejected() {
        assertFalse(profile.copy(workDays = 0).valid())
        assertFalse(profile.copy(hoursPerDay = 25.0).valid())
        assertFalse(profile.copy(chargedPerKm = Double.NaN).valid())
        assertFalse(profile.copy(kmPerDay = Double.POSITIVE_INFINITY).valid())
        assertFalse(profile.copy(weeklyRent = -1.0).valid())
        val entry = PlatformWork(WorkPlatform.KEETA, 10.0, 2.0, 1.0)
        assertFalse(WorkDay(LocalDate.now(), listOf(entry, entry), 2.0, 1.0, 0.0, 0.0, 0.0).valid())
    }

    @Test fun zeroActivityHasNoInventedRatesAndKeepsFixedCost() {
        val day = WorkDay(LocalDate.now(), emptyList(), 0.0, 0.0, 50.0, 0.6, 310.0)
        assertTrue(day.valid())
        assertNull(day.grossPerHour)
        assertNull(day.grossPerKm)
        assertEquals(-50.0, day.net, 0.0)
    }
}
