package com.spendwise.app.presentation.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.FinancialAdvisorApi
import com.spendwise.app.data.remote.dto.AdvisorInsightDto
import com.spendwise.app.data.remote.dto.AdvisorContextDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdvisorState(
    val isLoading: Boolean = true,
    val insights: List<AdvisorInsightDto> = emptyList(),
    val context: AdvisorContextDto? = null,
    val engineVersion: String = "",
    val generatedAt: String = "",
    val error: String? = null
)

@HiltViewModel
class AiCoachViewModel @Inject constructor(
    private val api: FinancialAdvisorApi
) : ViewModel() {
    private val _state = MutableStateFlow(AdvisorState())
    val state: StateFlow<AdvisorState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = AdvisorState(isLoading = true)
            try {
                val r = api.getInsights()
                if (r.isSuccessful && r.body()?.success == true) {
                    val data = r.body()?.data
                    _state.value = AdvisorState(
                        isLoading     = false,
                        insights      = data?.insights ?: emptyList(),
                        context       = data?.context,
                        engineVersion = data?.engineVersion ?: "",
                        generatedAt   = data?.generatedAt ?: ""
                    )
                } else {
                    _state.value = AdvisorState(isLoading = false, error = r.body()?.error ?: "Failed to load")
                }
            } catch (e: Exception) {
                _state.value = AdvisorState(isLoading = false, error = e.message ?: "Network error")
            }
        }
    }
}
