package com.spendwise.app.presentation.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.BudgetEntry
import com.spendwise.app.data.remote.dto.UpdateBudgetsRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetItem(
    val slug: String,
    val label: String,
    val icon: String,
    val budget: Double,
    val spent: Double,
) {
    val pct: Float = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining: Double = (budget - spent).coerceAtLeast(0.0)
    val isOver: Boolean = budget > 0 && spent > budget
}

data class BudgetState(
    val isLoading: Boolean = true,
    val items: List<BudgetItem> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val month: Int = LocalDate.now().monthValue,
    val year: Int = LocalDate.now().year,
    val editSlug: String? = null,
    val editAmount: String = "",
    val error: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val userApi: UserApi,
    private val transactionApi: TransactionApi
) : ViewModel() {
    private val _state = MutableStateFlow(BudgetState())
    val state: StateFlow<BudgetState> = _state

    companion object {
        val CATEGORY_DISPLAY = mapOf(
            "food"          to Pair("🍽️", "Food & Dining"),
            "fuel"          to Pair("⛽", "Fuel"),
            "shopping"      to Pair("🛍️", "Shopping"),
            "bills"         to Pair("💡", "Bills & Utilities"),
            "emi"           to Pair("🏦", "EMI / Loan"),
            "entertainment" to Pair("🎬", "Entertainment"),
            "health"        to Pair("💊", "Health"),
            "travel"        to Pair("✈️", "Travel"),
            "family"        to Pair("👨‍👩‍👧", "Family / Friends"),
            "investment"    to Pair("📈", "Investment"),
            "savings"       to Pair("🏆", "Savings"),
            "other"         to Pair("📦", "Other"),
            "transfer"      to Pair("🔄", "Transfer"),
            "income"        to Pair("💰", "Income"),
            "salary"        to Pair("💰", "Salary"),
            "waste"         to Pair("🗑️", "Wasteful")
        )
        val DEFAULT_SLUGS = listOf(
            "food", "fuel", "shopping", "bills", "emi",
            "entertainment", "health", "travel", "family", "investment", "savings", "other"
        )
    }

    init { load() }

    fun load() {
        val m = _state.value.month; val y = _state.value.year
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val budgetsD = async { runCatching { userApi.getBudgets(m, y) } }
            val summaryD = async { runCatching { transactionApi.getSummary(m, y) } }
            val budgets  = budgetsD.await().getOrNull()?.body()?.data?.budgets ?: emptyList()
            val cats     = summaryD.await().getOrNull()?.body()?.data?.byCategory ?: emptyList()
            val spendMap  = cats.associate { it.categorySlug to it.total }
            val budgetMap = budgets.associate { it.categorySlug to it.amount }
            val allSlugs  = (DEFAULT_SLUGS + spendMap.keys + budgetMap.keys).distinct()
            val items = allSlugs.mapNotNull { slug ->
                val meta = CATEGORY_DISPLAY[slug] ?: return@mapNotNull null
                BudgetItem(
                    slug   = slug,
                    label  = meta.second,
                    icon   = meta.first,
                    budget = budgetMap[slug] ?: 0.0,
                    spent  = spendMap[slug] ?: 0.0
                )
            }.sortedWith(
                compareByDescending<BudgetItem> { it.budget > 0 }.thenByDescending { it.spent }
            )
            _state.value = _state.value.copy(
                isLoading   = false,
                items       = items,
                totalBudget = items.sumOf { it.budget },
                totalSpent  = items.sumOf { it.spent }
            )
        }
    }

    fun prevMonth() {
        val cur = LocalDate.of(_state.value.year, _state.value.month, 1).minusMonths(1)
        _state.value = _state.value.copy(month = cur.monthValue, year = cur.year)
        load()
    }

    fun nextMonth() {
        val cur = LocalDate.of(_state.value.year, _state.value.month, 1).plusMonths(1)
        _state.value = _state.value.copy(month = cur.monthValue, year = cur.year)
        load()
    }

    fun showEdit(slug: String, currentBudget: Double) {
        _state.value = _state.value.copy(
            editSlug   = slug,
            editAmount = if (currentBudget > 0) currentBudget.toInt().toString() else ""
        )
    }

    fun hideEdit() { _state.value = _state.value.copy(editSlug = null, editAmount = "") }

    fun onAmountChange(v: String) { _state.value = _state.value.copy(editAmount = v) }

    fun saveBudget() {
        val slug   = _state.value.editSlug ?: return
        val amount = _state.value.editAmount.toDoubleOrNull() ?: 0.0
        val m      = _state.value.month; val y = _state.value.year
        val existing = _state.value.items.associate { it.slug to it.budget }.toMutableMap()
        existing[slug] = amount
        val entries = existing.filter { it.value > 0 }.map { BudgetEntry(it.key, it.value) }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                userApi.updateBudgets(UpdateBudgetsRequest(entries, m, y))
                hideEdit()
                load()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = e.message)
            }
        }
    }
}
