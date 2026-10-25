package com.girocerto.domain

import com.girocerto.domain.calculator.EarningsGoalCalculator
import com.girocerto.domain.model.VehicleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarningsGoalCalculatorTest {
    private val plan = EarningsGoalCalculator.Plan(1000.0, 5, 8.0, 100.0, 10)
    private val settings = VehicleSettings(fuelPricePerLiter = 6.0, fuelConsumptionKmL = 10.0,
        additionalCostPerKm = 0.4, dailyFixedCost = 20.0, fixedCostPerRide = 2.0)

    @Test fun accountsForEveryCostAndRoundsSuggestedRatesUp() {
        val result = EarningsGoalCalculator.calculate(plan, settings)
        assertEquals(700.0, result.weeklyOperatingCost, 0.00001)
        assertEquals(1700.0, result.requiredWeeklyGross, 0.00001)
        assertEquals(340.0, result.requiredDailyGross, 0.00001)
        assertEquals(42.5, result.suggestedPerHour, 0.00001)
        assertEquals(3.4, result.suggestedPerKm, 0.00001)
        assertEquals(1000.0 * 52 / 12, result.averageMonthlyNetGoal, 0.00001)
        val fractional = EarningsGoalCalculator.calculate(plan.copy(weeklyNetGoal = 1000.01), settings)
        assertEquals(42.51, fractional.suggestedPerHour, 0.00001)
        assertEquals(3.41, fractional.suggestedPerKm, 0.00001)
    }

    @Test fun rejectsImpossibleAndNonFiniteInputs() {
        listOf(plan.copy(workingDays = 0), plan.copy(workingDays = 8),
            plan.copy(hoursPerDay = 0.0), plan.copy(hoursPerDay = 25.0),
            plan.copy(kilometresPerDay = Double.NaN), plan.copy(kilometresPerDay = 0.0),
            plan.copy(weeklyNetGoal = Double.POSITIVE_INFINITY), plan.copy(weeklyNetGoal = -1.0),
            plan.copy(ridesPerDay = 0)).forEach {
            assertTrue(runCatching { EarningsGoalCalculator.calculate(it, settings) }.isFailure)
        }
        assertTrue(runCatching { EarningsGoalCalculator.calculate(plan,
            settings.copy(fuelConsumptionKmL = Double.POSITIVE_INFINITY)) }.isFailure)
    }

    @Test fun zeroProfitGoalStillCoversOperatingCosts() {
        val result = EarningsGoalCalculator.calculate(plan.copy(weeklyNetGoal = 0.0), settings)
        assertEquals(result.weeklyOperatingCost, result.requiredWeeklyGross, 0.0)
    }
}
