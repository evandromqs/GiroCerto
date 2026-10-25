package com.girocerto.domain.model

import java.time.LocalDate

enum class WorkPlatform(val label: String) {
    UBER("Uber"), UBER_EATS("Uber Eats"), IFOOD("iFood"),
    NINETY_NINE("99"), FOOD_99("99Food"), KEETA("Keeta")
}

data class ElectricProfile(
    val name: String, val weeklyRent: Double, val chargedPerKm: Double,
    val extraPerKm: Double, val workDays: Int, val hoursPerDay: Double,
    val kmPerDay: Double, val weeklyNetGoal: Double
) {
    fun valid() = name.isNotBlank() && name.length <= 60 && workDays in 1..7 &&
        listOf(weeklyRent, chargedPerKm, extraPerKm, weeklyNetGoal).all { it.isFinite() && it in 0.0..1_000_000.0 } &&
        hoursPerDay.isFinite() && hoursPerDay > 0 && hoursPerDay <= 24 &&
        kmPerDay.isFinite() && kmPerDay > 0 && kmPerDay <= 2000
    val variablePerKm get() = chargedPerKm + extraPerKm
    val dailyRent get() = weeklyRent / workDays
    val weeklyCosts get() = weeklyRent + variablePerKm * kmPerDay * workDays
    val dailyGrossGoal get() = (weeklyNetGoal + weeklyCosts) / workDays
    val targetPerKm get() = dailyGrossGoal / kmPerDay
    val targetPerHour get() = dailyGrossGoal / hoursPerDay

    fun vehicleSettings(previous: VehicleSettings): VehicleSettings {
        require(valid())
        return previous.copy(fuelType = FuelType.ELETRICO, fuelPricePerLiter = 0.0,
            fuelConsumptionKmL = 1.0, additionalCostPerKm = variablePerKm,
            fixedCostPerRide = 0.0, dailyFixedCost = dailyRent,
            minTargetPerKm = targetPerKm, minTargetPerHour = targetPerHour)
    }
}

data class PlatformWork(val platform: WorkPlatform, val revenue: Double, val km: Double, val hours: Double) {
    fun valid() = revenue.isFinite() && revenue in 0.0..1_000_000.0 &&
        km.isFinite() && km in 0.0..2000.0 && hours.isFinite() && hours in 0.0..24.0
}

/** One dated closing, separate from offers. Costs are snapshotted on first save. */
data class WorkDay(
    val date: LocalDate, val entries: List<PlatformWork>, val totalKm: Double,
    val activeHours: Double, val fixedCost: Double, val variablePerKm: Double,
    val grossGoal: Double
) {
    fun valid() = entries.map { it.platform }.distinct().size == entries.size && entries.all { it.valid() } &&
        totalKm.isFinite() && totalKm in 0.0..2000.0 && activeHours.isFinite() && activeHours in 0.0..24.0 &&
        listOf(fixedCost, variablePerKm, grossGoal).all { it.isFinite() && it >= 0.0 }
    val revenue get() = entries.sumOf { it.revenue }
    val cost get() = fixedCost + totalKm * variablePerKm
    val net get() = revenue - cost
    val grossPerHour get() = if (activeHours > 0) revenue / activeHours else null
    val grossPerKm get() = if (totalKm > 0) revenue / totalKm else null
}

/** Keep historical or unsaved amounts visible even after deselecting a platform. */
fun platformsForDay(selected: Set<WorkPlatform>, entries: List<PlatformWork>): Set<WorkPlatform> =
    selected + entries.filter { it.revenue != 0.0 || it.km != 0.0 || it.hours != 0.0 }.map { it.platform }
