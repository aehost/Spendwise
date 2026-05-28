package com.spendwise.app.presentation.screens.tax

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.TaxApi
import com.spendwise.app.data.remote.dto.TaxEstimateDto
import com.spendwise.app.data.remote.dto.TaxEstimateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaxState(
    val annualSalary: String = "",
    val otherIncome: String = "0",
    val section80c: String = "0",
    val section80d: String = "0",
    val hraExemption: String = "0",
    val nps80ccd: String = "0",
    val homeLoanInterest: String = "0",
    val isLoading: Boolean = false,
    val result: TaxEstimateDto? = null,
    val error: String? = null
)

@HiltViewModel
class TaxPlanningViewModel @Inject constructor(private val api: TaxApi) : ViewModel() {
    private val _state = MutableStateFlow(TaxState())
    val state: StateFlow<TaxState> = _state

    fun update(block: TaxState.() -> TaxState) { _state.value = _state.value.block() }

    fun calculate() {
        val s = _state.value
        val salary = s.annualSalary.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = api.estimateTax(TaxEstimateRequest(
                    annualSalary      = salary,
                    otherIncome       = s.otherIncome.toDoubleOrNull() ?: 0.0,
                    section80c        = s.section80c.toDoubleOrNull() ?: 0.0,
                    section80d        = s.section80d.toDoubleOrNull() ?: 0.0,
                    hraExemption      = s.hraExemption.toDoubleOrNull() ?: 0.0,
                    nps80ccd          = s.nps80ccd.toDoubleOrNull() ?: 0.0,
                    homeLoanInterest  = s.homeLoanInterest.toDoubleOrNull() ?: 0.0,
                ))
                if (r.isSuccessful) _state.value = _state.value.copy(isLoading = false, result = r.body()?.data)
                else _state.value = _state.value.copy(isLoading = false, error = r.body()?.error ?: "Calculation failed")
            } catch (e: Exception) { _state.value = _state.value.copy(isLoading = false, error = e.message) }
        }
    }
}
