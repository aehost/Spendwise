package com.spendwise.app.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.GoalsApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.ContributeGoalRequest
import com.spendwise.app.data.remote.dto.CreateGoalRequest
import com.spendwise.app.data.remote.dto.FinancialGoalDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.ceil

data class GoalPlan(
    val monthsToAchieve: Int,
    val monthlyNeeded: Double,
    val isOnTrack: Boolean,
    val percentComplete: Float,
    val milestones: List<Pair<Int, Double>>  // (month, amount)
)

data class GoalsState(
    val isLoading: Boolean = true,
    val goals: List<FinancialGoalDto> = emptyList(),
    val salary: Double = 0.0,
    val savingsRate: Int = 0,
    val totalMonthlyNeeded: Double = 0.0,
    val availableMonthlySavings: Double = 0.0,
    val error: String? = null,
    val successMessage: String? = null
) {
    val shortTermGoals: List<FinancialGoalDto>
        get() = goals.filter { !it.isCompleted && isShortTerm(it) }
    val longTermGoals: List<FinancialGoalDto>
        get() = goals.filter { !it.isCompleted && !isShortTerm(it) }
    val completedGoals: List<FinancialGoalDto>
        get() = goals.filter { it.isCompleted }

    private fun isShortTerm(goal: FinancialGoalDto): Boolean {
        val deadline = goal.deadline?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
        }
        if (deadline != null) {
            val monthsLeft = (deadline.year - LocalDate.now().year) * 12 +
                             (deadline.monthValue - LocalDate.now().monthValue)
            return monthsLeft <= 12
        }
        val monthly = goal.monthlyTarget.takeIf { it > 0 } ?: (salary * 0.1)
        val remaining = goal.targetAmount - goal.currentAmount
        if (monthly <= 0 || remaining <= 0) return true
        return ceil(remaining / monthly).toInt() <= 12
    }
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalsApi: GoalsApi,
    private val userApi: UserApi,
    private val analyticsApi: AnalyticsApi
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsState())
    val state: StateFlow<GoalsState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val goalsDeferred     = async { goalsApi.getGoals() }
                val salaryDeferred    = async { userApi.getSalary() }
                val dashboardDeferred = async { analyticsApi.getDashboard() }

                val goals     = goalsDeferred.await()
                val salary    = salaryDeferred.await()
                val dashboard = dashboardDeferred.await()

                val salaryAmt    = salary.body()?.data?.amount ?: 0.0
                val savingsRate  = dashboard.body()?.data?.savingsRate ?: 0
                val goalsList    = if (goals.isSuccessful) goals.body()?.data ?: emptyList() else emptyList()
                val available    = salaryAmt * savingsRate / 100.0
                val totalNeeded  = goalsList.filter { !it.isCompleted }
                    .sumOf { it.monthlyTarget.takeIf { t -> t > 0 } ?: 0.0 }

                _state.value = GoalsState(
                    isLoading              = false,
                    goals                  = goalsList,
                    salary                 = salaryAmt,
                    savingsRate            = savingsRate,
                    totalMonthlyNeeded     = totalNeeded,
                    availableMonthlySavings = available
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load goals")
            }
        }
    }

    fun createGoal(
        title: String,
        description: String,
        targetAmount: Double,
        deadline: String?,
        icon: String,
        monthlyTarget: Double
    ) {
        viewModelScope.launch {
            try {
                val resp = goalsApi.createGoal(
                    CreateGoalRequest(
                        title         = title,
                        description   = description,
                        targetAmount  = targetAmount,
                        deadline      = deadline,
                        icon          = icon,
                        monthlyTarget = monthlyTarget
                    )
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _state.value = _state.value.copy(successMessage = "Goal created!")
                    load()
                } else {
                    _state.value = _state.value.copy(error = resp.body()?.error ?: "Failed to create goal")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Network error")
            }
        }
    }

    fun contributeToGoal(goalId: String, amount: Double, note: String = "") {
        viewModelScope.launch {
            try {
                val resp = goalsApi.contributeToGoal(goalId, ContributeGoalRequest(amount, note))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _state.value = _state.value.copy(successMessage = "Contribution added!")
                    load()
                } else {
                    _state.value = _state.value.copy(error = resp.body()?.error ?: "Failed to contribute")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Network error")
            }
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            try {
                goalsApi.deleteGoal(goalId)
                _state.value = _state.value.copy(
                    goals = _state.value.goals.filter { it.id != goalId }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Failed to delete goal")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }

    /** Compute a personalised achievement plan for a single goal. */
    fun computePlan(goal: FinancialGoalDto): GoalPlan {
        val s = _state.value
        val remaining   = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
        val monthly     = goal.monthlyTarget.takeIf { it > 0 }
            ?: (s.salary * s.savingsRate / 100.0 * 0.3)
        val monthsNeeded = if (monthly > 0) ceil(remaining / monthly).toInt() else 999

        val deadline = goal.deadline?.let {
            runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
        }
        val monthsTillDeadline = deadline?.let {
            ((it.year - LocalDate.now().year) * 12 + (it.monthValue - LocalDate.now().monthValue))
                .coerceAtLeast(0)
        }
        val isOnTrack = monthsTillDeadline == null || monthsNeeded <= monthsTillDeadline
        val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f

        // Build milestone steps: 25%, 50%, 75%, 100%
        val milestones = listOf(0.25, 0.50, 0.75, 1.0).mapNotNull { pctTarget ->
            val amtAtMilestone = goal.targetAmount * pctTarget
            if (amtAtMilestone > goal.currentAmount && monthly > 0) {
                val mLeft = ceil((amtAtMilestone - goal.currentAmount) / monthly).toInt()
                Pair(mLeft, amtAtMilestone)
            } else null
        }

        return GoalPlan(
            monthsToAchieve = monthsNeeded,
            monthlyNeeded   = monthly,
            isOnTrack       = isOnTrack,
            percentComplete = pct,
            milestones      = milestones
        )
    }
}
