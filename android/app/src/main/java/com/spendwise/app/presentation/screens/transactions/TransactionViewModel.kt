package com.spendwise.app.presentation.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.dto.CreateTransactionRequest
import com.spendwise.app.data.remote.dto.TransactionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionListState(
    val transactions: List<TransactionDto> = emptyList(),
    val isLoading: Boolean = true,
    val totalDebit: Double = 0.0,
    val totalCredit: Double = 0.0,
    val pendingCount: Int = 0,
    val categoryFilter: String? = null,
    val pendingOnly: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val api: TransactionApi
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionListState())
    val state: StateFlow<TransactionListState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val resp = api.getTransactions(
                    limit     = 100,
                    category  = _state.value.categoryFilter,
                    isPending = if (_state.value.pendingOnly) true else null
                )
                if (resp.isSuccessful) {
                    val data = resp.body()?.data
                    val txs  = data?.transactions ?: emptyList()
                    _state.value = _state.value.copy(
                        isLoading    = false,
                        transactions = txs,
                        totalDebit   = txs.filter { !it.isCredit }.sumOf { it.amount },
                        totalCredit  = txs.filter {  it.isCredit }.sumOf { it.amount },
                        pendingCount = txs.count { it.isPending }
                    )
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = "Failed to load")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setFilter(category: String?) {
        _state.value = _state.value.copy(categoryFilter = category, pendingOnly = false)
        load()
    }

    fun togglePending() {
        _state.value = _state.value.copy(pendingOnly = !_state.value.pendingOnly, categoryFilter = null)
        load()
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                api.deleteTransaction(id)
                load()
            } catch (_: Exception) { }
        }
    }

    fun createTransaction(
        amount: Double,
        merchant: String,
        categorySlug: String,
        transactionDate: String,
        isCredit: Boolean,
        note: String
    ) {
        if (amount <= 0 || merchant.isBlank()) return
        viewModelScope.launch {
            try {
                api.createTransaction(
                    CreateTransactionRequest(
                        amount          = amount,
                        merchant        = merchant.trim(),
                        categorySlug    = categorySlug,
                        transactionDate = transactionDate,
                        note            = note.trim(),
                        isCredit        = isCredit,
                        isPending       = false
                    )
                )
                load()
            } catch (_: Exception) {}
        }
    }
}
