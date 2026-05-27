package com.spendwise.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.LogoutRequest
import com.spendwise.app.data.remote.dto.UpdateSalaryRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val email: String? = null,
    val name: String? = null,
    val salaryAmount: Double? = null,
    val salaryDay: Int? = null,
    val smsScanFromMs: Long = 0L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val userApi: UserApi
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = SettingsState(
                email        = tokenManager.userEmail,
                name         = tokenManager.userName,
                smsScanFromMs = tokenManager.smsScanFromMs
            )
            try {
                val salary = userApi.getSalary()
                if (salary.isSuccessful) {
                    _state.value = _state.value.copy(
                        salaryAmount = salary.body()?.data?.amount,
                        salaryDay    = salary.body()?.data?.expectedDay
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateSalary(amount: Double, day: Int) {
        viewModelScope.launch {
            try { userApi.updateSalary(UpdateSalaryRequest(amount, day)); load() }
            catch (_: Exception) {}
        }
    }

    fun refresh() { load() }

    fun logout() {
        viewModelScope.launch {
            try {
                val refresh = tokenManager.refreshToken
                if (refresh != null) authApi.logout(LogoutRequest(refresh))
            } catch (_: Exception) {}
            tokenManager.clearAuth()
        }
    }
}
