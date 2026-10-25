package com.girocerto.domain.calculator

import com.girocerto.domain.model.VehicleSettings
import kotlin.math.ceil

/** Planning estimate: all kilometres, working hours and configured operating costs count. */
object EarningsGoalCalculator {
    data class Plan(
        val weeklyNetGoal: Double,
        val workingDays: Int,
        val hoursPerDay: Double,
        val kilometresPerDay: Double,
        val ridesPerDay: Int
    )

    data class Estimate(
        val weeklyOperatingCost: Double,
        val requiredWeeklyGross: Double,
        val requiredDailyGross: Double,
        val suggestedPerHour: Double,
        val suggestedPerKm: Double,
        val averageMonthlyNetGoal: Double
    )

    fun calculate(plan: Plan, settings: VehicleSettings): Estimate {
        require(plan.weeklyNetGoal.isFinite() && plan.weeklyNetGoal >= 0) { "Informe uma meta semanal válida." }
        require(plan.workingDays in 1..7) { "Escolha de 1 a 7 dias por semana." }
        require(plan.hoursPerDay.isFinite() && plan.hoursPerDay > 0 && plan.hoursPerDay <= 24) { "Informe horas por dia entre 0 e 24." }
        require(plan.kilometresPerDay.isFinite() && plan.kilometresPerDay > 0) { "Informe uma distância diária maior que zero." }
        require(plan.ridesPerDay > 0) { "Informe pelo menos uma corrida por dia." }
        require(settings.isValid() && listOf(settings.fuelPricePerLiter, settings.fuelConsumptionKmL,
            settings.additionalCostPerKm, settings.fixedCostPerRide, settings.dailyFixedCost).all { it.isFinite() }) {
            "Revise os custos e o consumo nas configurações."
        }
        val dailyCost = plan.kilometresPerDay *
            (settings.fuelPricePerLiter / settings.fuelConsumptionKmL + settings.additionalCostPerKm) +
            settings.dailyFixedCost + plan.ridesPerDay * settings.fixedCostPerRide
        val weeklyCost = dailyCost * plan.workingDays
        val gross = plan.weeklyNetGoal + weeklyCost
        val dailyGross = gross / plan.workingDays
        val hourly = ceil(dailyGross / plan.hoursPerDay * 100) / 100
        val perKm = ceil(dailyGross / plan.kilometresPerDay * 100) / 100
        val monthly = plan.weeklyNetGoal * 52 / 12
        require(listOf(weeklyCost, gross, dailyGross, hourly, perKm, monthly).all { it.isFinite() }) {
            "Os valores informados são altos demais."
        }
        return Estimate(weeklyCost, gross, dailyGross, hourly, perKm, monthly)
    }
}
