package com.spendwise.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.GoalsApi
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.BillDto
import com.spendwise.app.data.remote.dto.DashboardDto
import com.spendwise.app.data.remote.dto.FinancialGoalDto
import com.spendwise.app.data.remote.dto.TransactionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardDto? = null,
    val recentTransactions: List<TransactionDto> = emptyList(),
    val bills: List<BillDto> = emptyList(),
    val goals: List<FinancialGoalDto> = emptyList(),
    val userName: String? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyticsApi: AnalyticsApi,
    private val transactionApi: TransactionApi,
    private val userApi: UserApi,
    private val goalsApi: GoalsApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true, userName = tokenManager.userName)
            try {
                val dashDeferred  = async { analyticsApi.getDashboard() }
                val txDeferred    = async { transactionApi.getTransactions(limit = 8) }
                val billsDeferred = async { userApi.getBills() }
                val goalsDeferred = async { goalsApi.getGoals() }

                val dashResp  = dashDeferred.await()
                val txResp    = txDeferred.await()
                val billsResp = billsDeferred.await()
                val goalsResp = goalsDeferred.await()

                _state.value = HomeUiState(
                    isLoading          = false,
                    userName           = tokenManager.userName,
                    dashboard          = if (dashResp.isSuccessful) dashResp.body()?.data else null,
                    recentTransactions = if (txResp.isSuccessful) txResp.body()?.data?.transactions ?: emptyList() else emptyList(),
                    bills              = if (billsResp.isSuccessful) billsResp.body()?.data ?: emptyList() else emptyList(),
                    goals              = if (goalsResp.isSuccessful) goalsResp.body()?.data ?: emptyList() else emptyList()
                )
            } catch (e: Exception) {
                _state.value = HomeUiState(isLoading = false, error = e.message, userName = tokenManager.userName)
            }
        }
    }
}
