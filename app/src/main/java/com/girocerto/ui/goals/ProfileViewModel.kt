package com.girocerto.ui.goals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.girocerto.app.GiroCertoApp
import com.girocerto.domain.model.ElectricProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileOperation(val busy: Boolean = false, val error: String? = null, val completed: Int = 0)
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GiroCertoApp
    private val _operation = MutableStateFlow(ProfileOperation())
    val operation = _operation.asStateFlow()
    fun save(profile: ElectricProfile) {
        if (_operation.value.busy) return
        _operation.value = _operation.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.workRepository.saveProfile(profile)
                    app.settingsRepository.updateSettings(profile.vehicleSettings(app.settingsRepository.settings.value))
                }
                _operation.value = ProfileOperation(completed = _operation.value.completed + 1)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _operation.value = _operation.value.copy(busy = false, error = "Não foi possível salvar. Confira os avisos de recuperação e tente novamente.") }
        }
    }
}
