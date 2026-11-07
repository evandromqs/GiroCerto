package com.girocerto.app

import android.content.Context
import com.girocerto.auth.AuthManager
import com.girocerto.data.cloud.CloudBackupManager
import com.girocerto.data.repository.RideRepository
import com.girocerto.data.repository.SettingsRepository
import com.girocerto.data.repository.ShiftRepository
import com.girocerto.data.repository.WorkRepository
import com.girocerto.update.AppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Composition root do aplicativo.
 *
 * Somente este objeto conhece como as implementacoes concretas sao criadas.
 * As telas e os servicos podem migrar gradualmente para receber apenas as
 * dependencias que utilizam, sem espalhar construtores pelo Application.
 */
internal interface AppContainer {
    val appUpdates: AppUpdateManager
    val workRepository: WorkRepository
    val settingsRepository: SettingsRepository
    val rideRepository: RideRepository
    val shiftRepository: ShiftRepository
    val cloudBackup: CloudBackupManager
    val auth: AuthManager

    fun start()
}

internal class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val appUpdates = AppUpdateManager(appContext)
    override val settingsRepository = SettingsRepository(appContext)
    override val rideRepository = RideRepository(appContext, deferLoading = true)
    override val shiftRepository = ShiftRepository(appContext)
    override val workRepository = WorkRepository(appContext, deferLoading = true)
    override val cloudBackup = CloudBackupManager(appContext, workRepository, rideRepository)
    override val auth = AuthManager(appContext)

    override fun start() {
        cloudBackup.schedule()
        scope.launch {
            workRepository.profile.collectLatest { profile ->
                profile?.let {
                    settingsRepository.updateSettings(
                        it.vehicleSettings(settingsRepository.settings.value)
                    )
                }
            }
        }
    }
}
