package com.girocerto.app

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
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

    lateinit var shiftRepository: com.girocerto.data.repository.ShiftRepository
        private set

    lateinit var cloudBackup: com.girocerto.data.cloud.CloudBackupManager
        private set

    lateinit var auth: com.girocerto.auth.AuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.girocerto.app.theme.AppearancePreferences.initialize(this)
        appUpdates = com.girocerto.update.AppUpdateManager(this)
        settingsRepository = SettingsRepository(this)
        rideRepository = RideRepository(this, deferLoading = true)
        shiftRepository = com.girocerto.data.repository.ShiftRepository(this)
        workRepository = com.girocerto.data.repository.WorkRepository(this, deferLoading = true)
        cloudBackup = com.girocerto.data.cloud.CloudBackupManager(this, workRepository, rideRepository)
        auth = com.girocerto.auth.AuthManager(this)
        cloudBackup.schedule()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            workRepository.profile.collectLatest { profile ->
                profile?.let { settingsRepository.updateSettings(it.vehicleSettings(settingsRepository.settings.value)) }
            }
        }
    }

    companion object {
        lateinit var instance: GiroCertoApp
            private set
    }
}
