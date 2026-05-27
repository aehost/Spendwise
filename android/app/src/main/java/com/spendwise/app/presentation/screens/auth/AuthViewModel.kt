package com.spendwise.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.core.Result
import com.spendwise.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

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
}
