package com.girocerto.app

import android.app.Application
import com.girocerto.data.repository.RideRepository
import com.girocerto.data.repository.SettingsRepository

/**
 * Ponto de entrada do aplicativo e provedor de dependências locais (Service Locator leve).
 */
class GiroCertoApp : Application() {
    internal lateinit var appUpdates: com.girocerto.update.AppUpdateManager
        private set

    lateinit var workRepository: com.girocerto.data.repository.WorkRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var rideRepository: RideRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appUpdates = com.girocerto.update.AppUpdateManager(this)
        settingsRepository = SettingsRepository(this)
        rideRepository = RideRepository(this)
        workRepository = com.girocerto.data.repository.WorkRepository(this)
        // Keep the electric profile authoritative for costs even after an interrupted save.
        workRepository.profile.value?.let {
            settingsRepository.updateSettings(it.vehicleSettings(settingsRepository.settings.value))
        }
    }

    companion object {
        lateinit var instance: GiroCertoApp
            private set
    }
}
