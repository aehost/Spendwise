package com.spendwise.app.presentation.screens.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.GoalsApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetWorthState(
    val isLoading: Boolean = true,
    val bankAccounts: List<BankAccountDto> = emptyList(),
    val investments: List<InvestmentDto> = emptyList(),
    val goalSavings: Double = 0.0,
    val creditCards: List<CreditCardDto> = emptyList(),
    val loans: List<LoanDto> = emptyList(),
    val error: String? = null
) {
    val totalBankBalance: Double get() = bankAccounts.sumOf { it.balance }
    val totalInvestments: Double get() = investments.sumOf { it.currentBalance }
    val totalAssets: Double get() = totalBankBalance + totalInvestments + goalSavings
    val totalCcDebt: Double get() = creditCards.sumOf { it.outstanding }
    val totalLoanDebt: Double get() = loans.sumOf { it.outstanding }
    val totalLiabilities: Double get() = totalCcDebt + totalLoanDebt
    val netWorth: Double get() = totalAssets - totalLiabilities
}

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val userApi: UserApi,
    private val goalsApi: GoalsApi
) : ViewModel() {
    private val _state = MutableStateFlow(NetWorthState())
    val state: StateFlow<NetWorthState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val baD    = async { runCatching { userApi.getBankAccounts() } }
            val invD   = async { runCatching { userApi.getInvestments() } }
            val ccD    = async { runCatching { userApi.getCreditCards() } }
            val loanD  = async { runCatching { userApi.getLoans() } }
            val goalsD = async { runCatching { goalsApi.getGoals() } }

            val ba         = baD.await().getOrNull()?.body()?.data ?: emptyList()
            val inv        = invD.await().getOrNull()?.body()?.data ?: emptyList()
            val cc         = ccD.await().getOrNull()?.body()?.data ?: emptyList()
            val loans      = loanD.await().getOrNull()?.body()?.data ?: emptyList()
            val goalSavings = goalsD.await().getOrNull()?.body()?.data?.sumOf { it.currentAmount } ?: 0.0

            _state.value = NetWorthState(
                isLoading    = false,
                bankAccounts = ba,
                investments  = inv,
                goalSavings  = goalSavings,
                creditCards  = cc,
                loans        = loans
            )
        }
    }
}
