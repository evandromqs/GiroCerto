package com.girocerto.domain.usecase

import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.repository.DriverProfileRepository
import com.girocerto.domain.repository.VehicleSettingsRepository

/** Salva o perfil e mantem as regras de avaliacao de ofertas sincronizadas. */
class SaveDriverProfile(
    private val profiles: DriverProfileRepository,
    private val vehicleSettings: VehicleSettingsRepository
) {
    suspend operator fun invoke(profile: ElectricProfile) {
        require(profile.valid()) { "Perfil do motorista invalido." }
        profiles.saveProfile(profile)
        vehicleSettings.updateSettings(
            profile.vehicleSettings(vehicleSettings.settings.value)
        )
    }
}
