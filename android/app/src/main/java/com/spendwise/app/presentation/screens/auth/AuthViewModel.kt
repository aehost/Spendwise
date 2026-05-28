package com.spendwise.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.Result
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.dto.ForgotPasswordRequest
import com.spendwise.app.data.remote.dto.ResetPasswordRequest
import com.spendwise.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

data class ForgotPwState(
    val step: Int = 0,           // 0 = email entry, 1 = OTP+new-password entry, 2 = done
    val otp: String? = null,     // OTP returned by server (demo mode)
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val authApi: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    private val _forgotState = MutableStateFlow(ForgotPwState())
    val forgotState: StateFlow<ForgotPwState> = _forgotState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            when (val r = repo.login(email.trim(), password)) {
                is Result.Success -> _state.value = AuthUiState(success = true)
                is Result.Error   -> _state.value = AuthUiState(error = r.message)
                else -> {}
            }
        }
    }

    fun register(email: String, password: String, name: String) {
        viewModelScope.launch {
            if (password.length < 8) { _state.value = AuthUiState(error = "Password must be at least 8 characters"); return@launch }
            _state.value = AuthUiState(isLoading = true)
            when (val r = repo.register(email.trim(), password, name.trim())) {
                is Result.Success -> _state.value = AuthUiState(success = true)
                is Result.Error   -> _state.value = AuthUiState(error = r.message)
                else -> {}
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    // ── Forgot password ───────────────────────────────────────

    fun sendForgotOtp(email: String) {
        viewModelScope.launch {
            _forgotState.value = ForgotPwState(isLoading = true)
            try {
                val resp = authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val otp = resp.body()?.data?.otp
                    _forgotState.value = ForgotPwState(step = 1, otp = otp)
                } else {
                    val err = resp.body()?.error ?: parseError(resp) ?: "Failed to send reset code"
                    _forgotState.value = ForgotPwState(error = err)
                }
            } catch (e: Exception) {
                _forgotState.value = ForgotPwState(error = e.message ?: "Network error")
            }
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            if (newPassword.length < 8) { _forgotState.value = _forgotState.value.copy(error = "Password must be at least 8 characters"); return@launch }
            _forgotState.value = _forgotState.value.copy(isLoading = true, error = null)
            try {
                val resp = authApi.resetPassword(ResetPasswordRequest(email.trim(), otp.trim(), newPassword))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _forgotState.value = ForgotPwState(step = 2, successMessage = "Password reset! Please log in.")
                } else {
                    val err = resp.body()?.error ?: parseError(resp) ?: "Invalid or expired code"
                    _forgotState.value = _forgotState.value.copy(isLoading = false, error = err)
                }
            } catch (e: Exception) {
                _forgotState.value = _forgotState.value.copy(isLoading = false, error = e.message ?: "Network error")
            }
        }
    }

    fun resetForgotState() { _forgotState.value = ForgotPwState() }

    private fun parseError(resp: Response<*>): String? = try {
        val raw = resp.errorBody()?.string()
        if (raw.isNullOrBlank()) null else JSONObject(raw).optString("error").ifBlank { null }
    } catch (_: Exception) { null }
}
