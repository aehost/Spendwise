package com.spendwise.app.presentation.screens.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.spendwise.app.data.gmail.GmailAuthManager
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.api.GmailApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.ChangePasswordRequest
import com.spendwise.app.data.remote.dto.CreateSupportTicketRequest
import com.spendwise.app.data.remote.dto.GmailAccountDto
import com.spendwise.app.data.remote.dto.GmailConnectRequest
import com.spendwise.app.data.remote.dto.LogoutRequest
import com.spendwise.app.data.remote.dto.UpdateSalaryRequest
import com.spendwise.app.data.worker.GmailSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsState(
    val email: String? = null,
    val name: String? = null,
    val salaryAmount: Double? = null,
    val salaryDay: Int? = null,
    val smsScanFromMs: Long = 0L,
    // Gmail — multi-account
    val gmailAccounts: List<GmailAccountDto> = emptyList(),
    val gmailLoading: Boolean = false,
    val gmailError: String? = null,
    val gmailSyncing: Boolean = false,
    // Password change
    val passwordChangeSuccess: Boolean = false,
    val passwordChangeError: String? = null,
    val passwordChanging: Boolean = false,
    // Support ticket
    val ticketSuccess: Boolean = false,
    val ticketError: String? = null,
    val ticketSending: Boolean = false
) {
    val gmailConnected: Boolean get() = gmailAccounts.isNotEmpty()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tokenManager: TokenManager,
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val gmailApi: GmailApi,
    private val gmailAuthManager: GmailAuthManager
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                email         = tokenManager.userEmail,
                name          = tokenManager.userName,
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
            refreshGmailAccounts()
        }
    }

    private fun refreshGmailAccounts() {
        viewModelScope.launch {
            try {
                val resp = gmailApi.getAccounts()
                if (resp.isSuccessful) {
                    val accounts = resp.body()?.data?.accounts ?: emptyList()
                    _state.value = _state.value.copy(gmailAccounts = accounts.filter { it.isActive })
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

    // ── Gmail multi-account ───────────────────────────────────
    fun getGmailSignInIntent(): Intent = gmailAuthManager.getSignInIntent()

    fun onGmailSignInResult(data: Intent?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true, gmailError = null)
            val account = gmailAuthManager.handleSignInResult(data)
            if (account == null) {
                _state.value = _state.value.copy(gmailLoading = false, gmailError = "Google sign-in cancelled or failed")
                return@launch
            }
            try {
                val req = GmailConnectRequest(
                    gmailEmail   = account.email ?: "",
                    accessToken  = account.serverAuthCode ?: account.idToken ?: "",
                    refreshToken = null,
                    tokenExpiry  = null
                )
                val resp = gmailApi.addAccount(req)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _state.value = _state.value.copy(gmailLoading = false, gmailError = null)
                    refreshGmailAccounts()
                } else {
                    _state.value = _state.value.copy(gmailLoading = false, gmailError = "Failed to link Gmail account")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(gmailLoading = false, gmailError = e.message ?: "Network error")
            }
        }
    }

    fun removeGmailAccount(accountId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true, gmailError = null)
            try {
                val resp = gmailApi.removeAccount(accountId)
                if (resp.isSuccessful) {
                    _state.value = _state.value.copy(
                        gmailAccounts = _state.value.gmailAccounts.filter { it.id != accountId },
                        gmailLoading  = false
                    )
                } else {
                    _state.value = _state.value.copy(gmailLoading = false, gmailError = "Failed to remove account")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(gmailLoading = false, gmailError = e.message ?: "Error")
            }
        }
    }

    fun syncGmailNow() {
        _state.value = _state.value.copy(gmailSyncing = true)
        val req = OneTimeWorkRequestBuilder<GmailSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork("gmail_sync_now", ExistingWorkPolicy.REPLACE, req)
        viewModelScope.launch {
            // Optimistically clear syncing flag after 3s; actual result is background
            kotlinx.coroutines.delay(3_000)
            _state.value = _state.value.copy(gmailSyncing = false)
            refreshGmailAccounts()
        }
    }

    fun clearGmailError() {
        _state.value = _state.value.copy(gmailError = null)
    }

    // ── Change password ───────────────────────────────────────
    fun changePassword(currentPw: String, newPw: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(passwordChanging = true, passwordChangeError = null, passwordChangeSuccess = false)
            try {
                val resp = authApi.changePassword(ChangePasswordRequest(currentPw, newPw))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _state.value = _state.value.copy(passwordChanging = false, passwordChangeSuccess = true)
                } else {
                    val err = resp.body()?.error ?: "Failed to change password"
                    _state.value = _state.value.copy(passwordChanging = false, passwordChangeError = err)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(passwordChanging = false, passwordChangeError = e.message ?: "Network error")
            }
        }
    }

    fun clearPasswordState() {
        _state.value = _state.value.copy(passwordChangeSuccess = false, passwordChangeError = null)
    }

    // ── Support ticket ────────────────────────────────────────
    fun createSupportTicket(subject: String, description: String, category: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ticketSending = true, ticketError = null, ticketSuccess = false)
            try {
                val resp = userApi.createSupportTicket(CreateSupportTicketRequest(subject, description, category))
                if (resp.isSuccessful && resp.body()?.success == true) {
                    _state.value = _state.value.copy(ticketSending = false, ticketSuccess = true)
                } else {
                    val err = resp.body()?.error ?: "Failed to create ticket"
                    _state.value = _state.value.copy(ticketSending = false, ticketError = err)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(ticketSending = false, ticketError = e.message ?: "Network error")
            }
        }
    }

    fun clearTicketState() {
        _state.value = _state.value.copy(ticketSuccess = false, ticketError = null)
    }
}
