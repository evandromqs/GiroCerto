package com.girocerto.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.girocerto.data.repository.LedgerDraftStore
import com.girocerto.domain.model.*
import com.girocerto.domain.repository.WorkLedgerRepository
import kotlinx.coroutines.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LedgerOperation(val busy: Boolean = false, val date: LocalDate? = null,
    val message: String? = null, val deleted: Boolean = false)

/** Writes belong to the screen owner, rather than a transient composable. */
class LedgerViewModel(
    val repository: WorkLedgerRepository,
    private val draftStore: LedgerDraftStore
) : ViewModel() {
    private var draftRevision = repository.restoreRevision.value
    private val draftMutex = Mutex()
    private val drafts = mutableStateMapOf<String, List<String>>()
    var draftsLoaded by mutableStateOf(false)
        private set
    init { viewModelScope.launch {
        repository.awaitLoaded()
        draftRevision = repository.restoreRevision.value
        val loaded = draftStore.load(draftRevision)
        if (draftRevision == repository.restoreRevision.value) drafts.putAll(loaded)
        draftsLoaded = true
        repository.restoreRevision.collect { revision ->
            if (revision != draftRevision) {
                draftsLoaded = false; drafts.clear(); draftRevision = revision
                draftMutex.withLock { draftStore.clear(revision) }
                draftsLoaded = true
            }
        }
    } }
    fun draft(date: LocalDate): List<String>? = if (draftRevision == repository.restoreRevision.value) drafts[date.toString()] else null
    fun cacheDraft(date: LocalDate, fields: List<String>) {
        if (fields == drafts[date.toString()]) return
        drafts[date.toString()] = fields.toList()
        val revision = repository.restoreRevision.value
        viewModelScope.launch { draftMutex.withLock {
            if (revision == repository.restoreRevision.value) draftStore.save(revision, date, fields)
        } }
    }
    fun acknowledgeDelete() { _operation.value = _operation.value.copy(deleted = false) }
    private val _operation = MutableStateFlow(LedgerOperation())
    val operation = _operation.asStateFlow()
    fun save(day: WorkDay) = perform(day.date, false) { repository.saveDay(day) }
    fun delete(date: LocalDate) = perform(date, true) { repository.deleteDay(date) }
    private fun perform(date: LocalDate, deleting: Boolean, action: suspend () -> Unit) {
        if (_operation.value.busy) return
        _operation.value = LedgerOperation(busy = true, date = date)
        viewModelScope.launch {
            try {
                action()
                _operation.value = LedgerOperation(date = date, deleted = deleting,
                    message = if (deleting) "Fechamento excluído." else "Dia salvo. Seus ganhos não foram duplicados.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _operation.value = LedgerOperation(date = date, message = "Não foi possível salvar. Confira os avisos de recuperação e tente novamente.") }
        }
    }

    class Factory(
        private val repository: WorkLedgerRepository,
        private val draftStore: LedgerDraftStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LedgerViewModel::class.java))
            return LedgerViewModel(repository, draftStore) as T
        }
    }
}
