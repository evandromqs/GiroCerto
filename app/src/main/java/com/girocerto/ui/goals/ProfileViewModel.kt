package com.girocerto.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.girocerto.domain.model.ElectricProfile
import com.girocerto.domain.usecase.SaveDriverProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileOperation(val busy: Boolean = false, val error: String? = null, val completed: Int = 0)
class ProfileViewModel(
    private val saveDriverProfile: SaveDriverProfile
) : ViewModel() {
    private val _operation = MutableStateFlow(ProfileOperation())
    val operation = _operation.asStateFlow()
    fun save(profile: ElectricProfile) {
        if (_operation.value.busy) return
        _operation.value = _operation.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { saveDriverProfile(profile) }
                _operation.value = ProfileOperation(completed = _operation.value.completed + 1)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _operation.value = _operation.value.copy(busy = false, error = "Não foi possível salvar. Confira os avisos de recuperação e tente novamente.") }
        }
    }

    class Factory(
        private val saveDriverProfile: SaveDriverProfile
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(saveDriverProfile) as T
        }
    }
}
