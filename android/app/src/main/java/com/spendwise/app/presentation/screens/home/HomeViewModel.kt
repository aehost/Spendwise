package com.spendwise.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.challenge.DailyChallenge
import com.spendwise.app.data.challenge.DailyChallengeManager
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.FinancialAdvisorApi
import com.spendwise.app.data.remote.api.GoalsApi
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.AdvisorInsightDto
import com.spendwise.app.data.remote.dto.BillDto
import com.spendwise.app.data.remote.dto.CreateTransactionRequest
import com.spendwise.app.data.remote.dto.DashboardDto
import com.spendwise.app.data.remote.dto.FinancialGoalDto
import com.spendwise.app.data.remote.dto.TransactionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val roundUpSavings: Double = 0.0,
    // Emergency Fund
    val emergencyFundMonths: Double = 0.0,
    val emergencyFundTarget: Double = 6.0,
    // Daily Challenge & XP
    val todayChallenge: DailyChallenge? = null,
    val challengeAccepted: Boolean = false,
    val challengeCompleted: Boolean = false,
    val xp: Int = 0,
    val levelName: String = "Saver",
    val xpProgress: Float = 0f
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
            // If called from refresh(), keep the spinner visible instead of showing a full loading screen
            val isRefreshMode = _state.value.isRefreshing
            if (!isRefreshMode) {
                _state.value = _state.value.copy(isLoading = true, userName = tokenManager.userName)
            }

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

            // Emergency fund calculation
            val bankBalance = dash?.bankBalance ?: 0.0
            val monthlyExpenses = dash?.totalSpent?.takeIf { it > 0 }
                ?: (dash?.salary?.amount?.times(0.7) ?: 0.0)
            val emergencyFundMonths = if (monthlyExpenses > 0) bankBalance / monthlyExpenses else 0.0

            // Daily Challenge & XP
            val challenge = DailyChallengeManager.getTodayChallenge(tokenManager)
            val xp = tokenManager.userXp
            val levelName = DailyChallengeManager.getLevelName(tokenManager)
            val (xpInLevel, xpForLevel) = DailyChallengeManager.getXpToNextLevel(tokenManager)
            val xpProgress = if (xpForLevel > 0) (xpInLevel.toFloat() / xpForLevel).coerceIn(0f, 1f) else 0f

            // Use copy() so isRefreshing flows through cleanly; both loading flags
            // are explicitly reset here so the spinner and loading screen both clear.
            _state.value = _state.value.copy(
                isLoading             = false,
                isRefreshing          = false,
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
                roundUpSavings        = roundUpSavings,
                emergencyFundMonths   = emergencyFundMonths,
                emergencyFundTarget   = 6.0,
                todayChallenge        = challenge,
                challengeAccepted     = tokenManager.dailyChallengeAccepted,
                challengeCompleted    = tokenManager.dailyChallengeCompleted,
                xp                    = xp,
                levelName             = levelName,
                xpProgress            = xpProgress
            )
        }
    }

    fun acceptChallenge() {
        tokenManager.dailyChallengeAccepted = true
        DailyChallengeManager.awardXp(tokenManager, 5) // Small XP for accepting
        val challenge = DailyChallengeManager.getTodayChallenge(tokenManager)
        val xp = tokenManager.userXp
        val levelName = DailyChallengeManager.getLevelName(tokenManager)
        val (xpInLevel, xpForLevel) = DailyChallengeManager.getXpToNextLevel(tokenManager)
        val xpProgress = if (xpForLevel > 0) (xpInLevel.toFloat() / xpForLevel).coerceIn(0f, 1f) else 0f
        _state.value = _state.value.copy(
            challengeAccepted = true,
            todayChallenge = challenge,
            xp = xp,
            levelName = levelName,
            xpProgress = xpProgress
        )
    }

    fun completeChallenge() {
        val challenge = DailyChallengeManager.getTodayChallenge(tokenManager)
        if (!tokenManager.dailyChallengeCompleted) {
            tokenManager.dailyChallengeCompleted = true
            DailyChallengeManager.awardXp(tokenManager, challenge.xpReward)
        }
        // Update only XP/challenge state — no network call, no loading screen flash
        val xp = tokenManager.userXp
        val levelName = DailyChallengeManager.getLevelName(tokenManager)
        val (xpInLevel, xpForLevel) = DailyChallengeManager.getXpToNextLevel(tokenManager)
        val xpProgress = if (xpForLevel > 0) (xpInLevel.toFloat() / xpForLevel).coerceIn(0f, 1f) else 0f
        _state.value = _state.value.copy(
            challengeCompleted = true,
            xp = xp,
            levelName = levelName,
            xpProgress = xpProgress
        )
    }

    fun logQuickExpense(amount: Double, merchant: String, categorySlug: String) {
        viewModelScope.launch {
            try {
                val today = LocalDate.now().toString()
                withContext(Dispatchers.IO) {
                    transactionApi.createTransaction(
                        CreateTransactionRequest(
                            amount          = amount,
                            merchant        = merchant.ifBlank { "Quick Expense" },
                            categorySlug    = categorySlug,
                            transactionDate = today,
                            isCredit        = false,
                            isPending       = false
                        )
                    )
                }
                load()
            } catch (_: Exception) {}
        }
    }

    fun refresh() {
        // Guard: don't stack refreshes on top of a load already in progress
        if (_state.value.isLoading || _state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            // load() detects isRefreshing=true and skips the full-screen loader;
            // it clears isRefreshing=false via copy() at the end — no need to reset here.
            load()
        }
    }
}
