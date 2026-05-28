package com.spendwise.app.presentation.screens.iou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.IouApi
import com.spendwise.app.data.remote.dto.CreateIouRequest
import com.spendwise.app.data.remote.dto.IouEntryDto
import com.spendwise.app.data.remote.dto.IouSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class IouState(
    val isLoading: Boolean = true,
    val entries: List<IouEntryDto> = emptyList(),
    val summaries: List<IouSummaryDto> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class IouViewModel @Inject constructor(private val api: IouApi) : ViewModel() {
    private val _state = MutableStateFlow(IouState())
    val state: StateFlow<IouState> = _state

    init { loadAll() }

    private fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val entriesDeferred  = async { api.getEntries(settled = false) }
                val summaryDeferred  = async { api.getSummary() }
                val entries  = entriesDeferred.await()
                val summaries = summaryDeferred.await()
                _state.value = IouState(
                    isLoading = false,
                    entries   = if (entries.isSuccessful) entries.body()?.data ?: emptyList() else emptyList(),
                    summaries = if (summaries.isSuccessful) summaries.body()?.data ?: emptyList() else emptyList()
                )
            } catch (e: Exception) { _state.value = IouState(isLoading = false, error = e.message) }
        }
    }

    fun loadEntries(settled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val r = api.getEntries(settled)
                _state.value = _state.value.copy(isLoading = false, entries = r.body()?.data ?: emptyList())
            } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false) }
        }
    }

    fun create(contactName: String, amount: Double, direction: String, description: String?) {
        viewModelScope.launch {
            try {
                api.create(CreateIouRequest(contactName, amount, direction, description, LocalDate.now().toString()))
                loadAll()
            } catch (_: Exception) {}
        }
    }

    fun settle(id: String) {
        viewModelScope.launch {
            try {
                api.update(id, mapOf("is_settled" to true))
                loadAll()
            } catch (_: Exception) {}
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                api.delete(id)
                _state.value = _state.value.copy(entries = _state.value.entries.filter { it.id != id })
            } catch (_: Exception) {}
        }
    }
}
