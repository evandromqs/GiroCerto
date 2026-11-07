package com.girocerto.app

import android.app.Application
import com.girocerto.data.repository.RideRepository
import com.girocerto.data.repository.SettingsRepository
import com.girocerto.data.repository.ShiftRepository
import com.girocerto.data.repository.WorkRepository
import com.girocerto.data.cloud.CloudBackupManager
import com.girocerto.auth.AuthManager
import com.girocerto.update.AppUpdateManager

/**
 * Ponto de entrada do aplicativo e provedor de dependências locais (Service Locator leve).
 */
class GiroCertoApp : Application() {
    internal lateinit var container: AppContainer
        private set

    // Fachada temporaria para manter os consumidores atuais compativeis.
    // Cada fluxo pode ser migrado para injecao por construtor separadamente.
    internal val appUpdates: AppUpdateManager get() = container.appUpdates
    val workRepository: WorkRepository get() = container.workRepository
    val settingsRepository: SettingsRepository get() = container.settingsRepository
    val rideRepository: RideRepository get() = container.rideRepository
    val shiftRepository: ShiftRepository get() = container.shiftRepository
    val cloudBackup: CloudBackupManager get() = container.cloudBackup
    val auth: AuthManager get() = container.auth

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.girocerto.app.theme.AppearancePreferences.initialize(this)
        container = DefaultAppContainer(this)
        container.start()
    }

    companion object {
        lateinit var instance: GiroCertoApp
            private set
    }
}
