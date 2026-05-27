package com.spendwise.app.presentation.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.CreateLoanRequest
import com.spendwise.app.data.remote.dto.LoanDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoansState(val loans: List<LoanDto> = emptyList(), val isLoading: Boolean = true, val showDialog: Boolean = false)

@HiltViewModel
class LoansViewModel @Inject constructor(private val api: UserApi) : ViewModel() {
    private val _state = MutableStateFlow(LoansState())
    val state: StateFlow<LoansState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val r = api.getLoans()
                if (r.isSuccessful) _state.value = _state.value.copy(isLoading = false, loans = r.body()?.data ?: emptyList())
                else _state.value = _state.value.copy(isLoading = false)
            } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false) }
        }
    }

    fun showAddDialog() { _state.value = _state.value.copy(showDialog = true) }
    fun hideDialog()    { _state.value = _state.value.copy(showDialog = false) }

    fun addLoan(name: String, emi: Double, rate: Double, outstanding: Double, months: Int) {
        viewModelScope.launch {
            try { api.createLoan(CreateLoanRequest(name, emi, rate, outstanding, months)); load() }
            catch (_: Exception) {}
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try { api.deleteLoan(id); load() }
            catch (_: Exception) {}
        }
    }
}
