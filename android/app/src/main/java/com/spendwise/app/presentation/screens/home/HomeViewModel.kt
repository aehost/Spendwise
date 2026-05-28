package com.spendwise.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.FinancialAdvisorApi
import com.spendwise.app.data.remote.api.GoalsApi
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.AdvisorInsightDto
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
    val topInsights: List<AdvisorInsightDto> = emptyList(),
    val userName: String? = null,
    // Individual section errors — don't fail whole screen for one bad call
    val dashboardError: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyticsApi: AnalyticsApi,
    private val transactionApi: TransactionApi,
    private val userApi: UserApi,
    private val goalsApi: GoalsApi,
    private val advisorApi: FinancialAdvisorApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, userName = tokenManager.userName)

            // All 5 calls run in parallel; each fails independently
            val dashD    = async { runCatching { analyticsApi.getDashboard() } }
            val txD      = async { runCatching { transactionApi.getTransactions(limit = 10) } }
            val billsD   = async { runCatching { userApi.getBills() } }
            val goalsD   = async { runCatching { goalsApi.getGoals() } }
            val advD     = async { runCatching { advisorApi.getInsights() } }

            val dashR    = dashD.await()
            val txR      = txD.await()
            val billsR   = billsD.await()
            val goalsR   = goalsD.await()
            val advR     = advD.await()

            _state.value = HomeUiState(
                isLoading          = false,
                userName           = tokenManager.userName,
                dashboard          = dashR.getOrNull()?.body()?.data,
                dashboardError     = if (dashR.isFailure || dashR.getOrNull()?.isSuccessful == false)
                    "Could not load dashboard" else null,
                recentTransactions = txR.getOrNull()?.body()?.data?.transactions ?: emptyList(),
                bills              = billsR.getOrNull()?.body()?.data ?: emptyList(),
                goals              = goalsR.getOrNull()?.body()?.data ?: emptyList(),
                // Show only critical + high priority insights on home screen
                topInsights        = advR.getOrNull()?.body()?.data?.insights
                    ?.filter { it.priority in listOf("critical", "high") }
                    ?.take(3)
                    ?: emptyList(),
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            load()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }
}
