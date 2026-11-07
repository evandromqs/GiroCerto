package com.girocerto.domain

import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.model.FuelType
import com.girocerto.domain.model.VehicleSettings
import com.girocerto.domain.repository.DriverProfileRepository
import com.girocerto.domain.repository.VehicleSettingsRepository
import com.girocerto.domain.usecase.SaveDriverProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SaveDriverProfileTest {
    @Test
    fun `salva perfil e sincroniza configuracoes usadas nas ofertas`() = runTest {
        var saved: ElectricProfile? = null
        val profiles = object : DriverProfileRepository {
            override suspend fun saveProfile(profile: ElectricProfile) { saved = profile }
        }
        val initial = VehicleSettings(
            fuelType = FuelType.GASOLINA,
            fuelPricePerLiter = 5.8,
            fuelConsumptionKmL = 10.0,
            additionalCostPerKm = 0.3,
            minTargetPerKm = 2.0,
            minTargetPerHour = 35.0,
            fixedCostPerRide = 0.0
        )
        val settings = FakeVehicleSettingsRepository(initial)
        val profile = ElectricProfile(
            name = "Motorista", weeklyRent = 350.0, chargedPerKm = 0.2,
            extraPerKm = 0.1, workDays = 5, hoursPerDay = 8.0,
            kmPerDay = 120.0, weeklyNetGoal = 1_000.0
        )

        SaveDriverProfile(profiles, settings)(profile)

        assertSame(profile, saved)
        assertEquals(FuelType.ELETRICO, settings.settings.value.fuelType)
        assertEquals(profile.targetPerKm, settings.settings.value.minTargetPerKm, 0.0001)
    }

    private class FakeVehicleSettingsRepository(initial: VehicleSettings) : VehicleSettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<VehicleSettings> = state
        override fun updateSettings(settings: VehicleSettings) { state.value = settings }
    }
}
