package com.spendwise.app.presentation.screens.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.remote.api.HealthScoreApi
import com.spendwise.app.data.remote.dto.HealthScoreDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthScoreState(val isLoading: Boolean = true, val score: HealthScoreDto? = null, val error: String? = null)

@HiltViewModel
class HealthScoreViewModel @Inject constructor(private val api: HealthScoreApi) : ViewModel() {
    private val _state = MutableStateFlow(HealthScoreState())
    val state: StateFlow<HealthScoreState> = _state
    init { load() }
    fun load() {
        viewModelScope.launch {
            _state.value = HealthScoreState(isLoading = true)
            try {
                val r = api.getHealthScore()
                if (r.isSuccessful) _state.value = HealthScoreState(score = r.body()?.data)
                else _state.value = HealthScoreState(error = r.body()?.error ?: "Failed")
            } catch (e: Exception) { _state.value = HealthScoreState(error = e.message) }
        }
    }
}
