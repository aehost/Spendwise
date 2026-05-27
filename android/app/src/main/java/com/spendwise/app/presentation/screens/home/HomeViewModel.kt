package com.spendwise.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.Result
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.dto.DashboardDto
import com.spendwise.app.data.remote.dto.TransactionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val dashboard: DashboardDto? = null,
    val recentTransactions: List<TransactionDto> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyticsApi: AnalyticsApi,
    private val transactionApi: TransactionApi
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true)
            try {
                val dashResp = analyticsApi.getDashboard()
                val txResp   = transactionApi.getTransactions(limit = 5)

                val dashboard = if (dashResp.isSuccessful) dashResp.body()?.data else null
                val recent    = if (txResp.isSuccessful)  txResp.body()?.data?.transactions ?: emptyList() else emptyList()

                _state.value = HomeUiState(isLoading = false, dashboard = dashboard, recentTransactions = recent)
            } catch (e: Exception) {
                _state.value = HomeUiState(isLoading = false, error = e.message)
            }
        }
    }
}
