package com.spendwise.app.presentation.screens.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.DebtPayoffApi
import com.spendwise.app.data.remote.dto.DebtPayoffDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebtPayoffState(val isLoading: Boolean = true, val data: DebtPayoffDto? = null, val error: String? = null)

@HiltViewModel
class DebtPayoffViewModel @Inject constructor(private val api: DebtPayoffApi) : ViewModel() {
    private val _state = MutableStateFlow(DebtPayoffState())
    val state: StateFlow<DebtPayoffState> = _state
    init { load() }
    fun load() {
        viewModelScope.launch {
            _state.value = DebtPayoffState(isLoading = true)
            try {
                val r = api.getDebtPayoff()
                if (r.isSuccessful) _state.value = DebtPayoffState(isLoading = false, data = r.body()?.data)
                else _state.value = DebtPayoffState(isLoading = false, error = r.body()?.error ?: "Failed")
            } catch (e: Exception) { _state.value = DebtPayoffState(isLoading = false, error = e.message) }
        }
    }
}
