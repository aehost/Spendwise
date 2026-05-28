package com.spendwise.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.formatCurrency
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
import java.time.LocalDate
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
    val isRefreshing: Boolean = false,
    // Salary-day smart card
    val isSalaryDay: Boolean = false,
    val salaryDayAmount: Double = 0.0,
    val salaryDaySuggestions: List<String> = emptyList(),
    // Spending streak
    val spendingStreak: Int = 0,
    // Round-up savings
    val roundUpSavings: Double = 0.0
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

            val dash = dashR.getOrNull()?.body()?.data
            val transactions = txR.getOrNull()?.body()?.data?.transactions ?: emptyList()

            // Salary-day detection
            val today         = LocalDate.now()
            val salary        = dash?.salary?.amount ?: 0.0
            val isSalaryDay   = dash?.salary?.expectedDay != null && today.dayOfMonth == dash.salary.expectedDay
            val salaryDaySuggestions = if (isSalaryDay && salary > 0) listOf(
                "💰 Save ${(salary * 0.20).formatCurrency()} (20% rule)",
                "💳 Pay CC bill: ${dash?.ccOutstanding?.formatCurrency() ?: "₹0"}",
                "📊 EMI due: ${dash?.emiTotal?.formatCurrency() ?: "₹0"}"
            ) else emptyList()

            // Spending streak logic
            val nonEssentialCategories = setOf("bills", "emi", "investment", "savings", "income", "salary", "transfer")
            val todayStr = today.toString()
            val todayDebits = transactions.filter { tx ->
                !tx.isCredit && tx.transactionDate == todayStr && tx.categorySlug !in nonEssentialCategories
            }
            val todayNonEssentialSpend = todayDebits.sumOf { it.amount }
            val lastCheckDate = tokenManager.lastSpendStreakCheckDate
            if (lastCheckDate != todayStr) {
                if (todayNonEssentialSpend < 500.0 || todayDebits.isEmpty()) {
                    tokenManager.spendingStreak = tokenManager.spendingStreak + 1
                } else {
                    tokenManager.spendingStreak = 0
                }
                tokenManager.lastSpendStreakCheckDate = todayStr
            }
            val streak = tokenManager.spendingStreak

            // Round-up savings from local storage
            val roundUpSavings = tokenManager.roundUpSavings

            _state.value = HomeUiState(
                isLoading             = false,
                userName              = tokenManager.userName,
                dashboard             = dash,
                dashboardError        = if (dashR.isFailure || dashR.getOrNull()?.isSuccessful == false)
                    "Could not load dashboard" else null,
                recentTransactions    = transactions,
                bills                 = billsR.getOrNull()?.body()?.data ?: emptyList(),
                goals                 = goalsR.getOrNull()?.body()?.data ?: emptyList(),
                // Show only critical + high priority insights on home screen
                topInsights           = advR.getOrNull()?.body()?.data?.insights
                    ?.filter { it.priority in listOf("critical", "high") }
                    ?.take(3)
                    ?: emptyList(),
                isSalaryDay           = isSalaryDay,
                salaryDayAmount       = salary,
                salaryDaySuggestions  = salaryDaySuggestions,
                spendingStreak        = streak,
                roundUpSavings        = roundUpSavings
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
