package com.girocerto.domain.repository

import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.model.VehicleSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Contratos da camada de dominio para o cadastro do motorista.
 *
 * As implementacoes podem usar SharedPreferences, Neon ou qualquer outra fonte
 * sem fazer a interface e os casos de uso dependerem de Android.
 */
interface DriverProfileRepository {
    suspend fun saveProfile(profile: ElectricProfile)
}

interface VehicleSettingsRepository {
    val settings: StateFlow<VehicleSettings>
    fun updateSettings(settings: VehicleSettings)
}
