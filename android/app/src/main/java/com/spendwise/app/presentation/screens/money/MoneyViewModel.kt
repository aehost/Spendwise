package com.spendwise.app.presentation.screens.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.CreateInvestmentRequest
import com.spendwise.app.data.remote.dto.InvestmentDto
import com.spendwise.app.data.remote.dto.SalaryReceivedRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MoneyState(
    val salaryAmount: Double = 0.0,
    val salaryDay: Int = 1,
    val totalSpent: Double = 0.0,
    val investments: List<InvestmentDto> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val userApi: UserApi,
    private val analyticsApi: AnalyticsApi
) : ViewModel() {
    private val _state = MutableStateFlow(MoneyState())
    val state: StateFlow<MoneyState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val salary = userApi.getSalary()
                val investments = userApi.getInvestments()
                val dashboard = analyticsApi.getDashboard()

                _state.value = MoneyState(
                    salaryAmount  = salary.body()?.data?.amount ?: 0.0,
                    salaryDay     = salary.body()?.data?.expectedDay ?: 1,
                    totalSpent    = dashboard.body()?.data?.totalSpent ?: 0.0,
                    investments   = investments.body()?.data ?: emptyList(),
                    isLoading     = false
                )
            } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false) }
        }
    }

    fun markSalaryReceived() {
        viewModelScope.launch {
            try {
                userApi.markSalaryReceived(SalaryReceivedRequest(_state.value.salaryAmount, LocalDate.now().toString(), "Monthly salary"))
                load()
            } catch (_: Exception) {}
        }
    }

    fun addInvestment(name: String, monthly: Double, current: Double) {
        viewModelScope.launch {
            try { userApi.createInvestment(CreateInvestmentRequest(name, monthly, current)); load() }
            catch (_: Exception) {}
        }
    }
}
