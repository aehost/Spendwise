package com.spendwise.app.presentation.screens.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.CashFlowApi
import com.spendwise.app.data.remote.dto.CashFlowDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CashFlowState(val isLoading: Boolean = true, val cashFlow: CashFlowDto? = null, val error: String? = null)

@HiltViewModel
class CashFlowViewModel @Inject constructor(private val api: CashFlowApi) : ViewModel() {
    private val _state = MutableStateFlow(CashFlowState())
    val state: StateFlow<CashFlowState> = _state
    init { load() }
    fun load() {
        viewModelScope.launch {
            _state.value = CashFlowState(isLoading = true)
            try {
                val r = api.getCashFlow()
                if (r.isSuccessful) _state.value = CashFlowState(cashFlow = r.body()?.data)
                else _state.value = CashFlowState(error = r.body()?.error ?: "Failed")
            } catch (e: Exception) { _state.value = CashFlowState(error = e.message) }
        }
    }
}
