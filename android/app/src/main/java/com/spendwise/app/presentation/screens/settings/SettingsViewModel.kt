package com.spendwise.app.presentation.screens.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.google.android.gms.auth.UserRecoverableAuthException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val deviceGoogleAccounts: List<String> = emptyList(),
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

    // Emits a permission-grant Intent when Google needs user approval for Gmail scope.
    // SettingsScreen must collect this and launch it via ActivityResultLauncher.
    private val _gmailAuthIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val gmailAuthIntent: SharedFlow<Intent> = _gmailAuthIntent

    // Stores the email pending connection until the auth grant completes
    private var pendingGmailEmail: String? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                email         = tokenManager.userEmail,
                name          = tokenManager.userName,
                smsScanFromMs = tokenManager.smsScanFromMs
            )
            launch {
                try {
                    val salary = userApi.getSalary()
                    if (salary.isSuccessful) _state.value = _state.value.copy(
                        salaryAmount = salary.body()?.data?.amount,
                        salaryDay    = salary.body()?.data?.expectedDay
                    )
                } catch (_: Exception) {}
            }
            launch { refreshGmailAccounts() }
            launch { loadDeviceAccounts() }
        }
    }

    private suspend fun refreshGmailAccounts() {
        try {
            val resp = gmailApi.getAccounts()
            if (resp.isSuccessful) {
                _state.value = _state.value.copy(
                    gmailAccounts = resp.body()?.data?.accounts?.filter { it.isActive } ?: emptyList()
                )
            }
        } catch (_: Exception) {}
    }

    private suspend fun loadDeviceAccounts() {
        val accounts = withContext(Dispatchers.IO) {
            gmailAuthManager.getDeviceGoogleAccounts()
        }
        _state.value = _state.value.copy(deviceGoogleAccounts = accounts)
    }

    /** Called when user picks / types a Gmail address to connect. */
    fun connectGmail(gmailEmail: String) {
        if (gmailEmail.isBlank() || !gmailEmail.contains("@")) {
            _state.value = _state.value.copy(gmailError = "Enter a valid Gmail address")
            return
        }
        val email = gmailEmail.trim().lowercase()
        pendingGmailEmail = email
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true, gmailError = null)

            // Try to obtain a real OAuth token from the device account manager.
            // This verifies the account exists on this device and grants read access.
            val tokenResult = withContext(Dispatchers.IO) {
                runCatching { gmailAuthManager.getTokenOrThrow(email) ?: "" }
            }

            when {
                tokenResult.isSuccess -> {
                    doConnectGmail(email, tokenResult.getOrNull() ?: "")
                }
                tokenResult.exceptionOrNull() is UserRecoverableAuthException -> {
                    // User must grant Gmail read permission — launch the system dialog
                    val authIntent = (tokenResult.exceptionOrNull() as UserRecoverableAuthException).intent
                    if (authIntent != null) {
                        _gmailAuthIntent.emit(authIntent)
                        _state.value = _state.value.copy(gmailLoading = false)
                    } else {
                        // No intent to show — connect with empty token as fallback
                        doConnectGmail(email, "")
                    }
                }
                else -> {
                    // Account not found on device or non-recoverable error.
                    // Connect with empty token — GmailSyncWorker will acquire it at sync time.
                    doConnectGmail(email, "")
                }
            }
        }
    }

    /** Called after the user grants Gmail scope via the system auth dialog. */
    fun retryConnectAfterGmailAuth() {
        val email = pendingGmailEmail ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true, gmailError = null)
            val token = withContext(Dispatchers.IO) { gmailAuthManager.getTokenBlocking(email) ?: "" }
            doConnectGmail(email, token)
        }
    }

    private suspend fun doConnectGmail(gmailEmail: String, oauthToken: String) {
        try {
            val resp = gmailApi.addAccount(
                GmailConnectRequest(
                    gmailEmail   = gmailEmail,
                    accessToken  = oauthToken,
                    refreshToken = null,
                    tokenExpiry  = null
                )
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                refreshGmailAccounts()
                _state.value = _state.value.copy(gmailLoading = false)
                triggerImmediateSync()
            } else {
                val err = resp.body()?.error ?: "Failed to add Gmail account"
                _state.value = _state.value.copy(gmailLoading = false, gmailError = err)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(gmailLoading = false, gmailError = e.message ?: "Network error")
        }
    }

    fun removeGmailAccount(accountId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true)
            try {
                gmailApi.removeAccount(accountId)
                _state.value = _state.value.copy(
                    gmailAccounts = _state.value.gmailAccounts.filter { it.id != accountId },
                    gmailLoading  = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(gmailLoading = false, gmailError = e.message)
            }
        }
    }

    fun syncGmailNow() {
        _state.value = _state.value.copy(gmailSyncing = true)
        triggerImmediateSync()
        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000)
            refreshGmailAccounts()
            _state.value = _state.value.copy(gmailSyncing = false)
        }
    }

    private fun triggerImmediateSync() {
        val req = OneTimeWorkRequestBuilder<GmailSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork("gmail_sync_now", ExistingWorkPolicy.REPLACE, req)
    }

    fun clearGmailError() { _state.value = _state.value.copy(gmailError = null) }

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
                val r = tokenManager.refreshToken
                if (r != null) authApi.logout(LogoutRequest(r))
            } catch (_: Exception) {}
            tokenManager.clearAuth()
        }
    }

    fun changePassword(currentPw: String, newPw: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(passwordChanging = true, passwordChangeError = null, passwordChangeSuccess = false)
            try {
                val resp = authApi.changePassword(ChangePasswordRequest(currentPw, newPw))
                if (resp.isSuccessful && resp.body()?.success == true)
                    _state.value = _state.value.copy(passwordChanging = false, passwordChangeSuccess = true)
                else
                    _state.value = _state.value.copy(passwordChanging = false, passwordChangeError = resp.body()?.error ?: "Failed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(passwordChanging = false, passwordChangeError = e.message ?: "Network error")
            }
        }
    }

    fun clearPasswordState() { _state.value = _state.value.copy(passwordChangeSuccess = false, passwordChangeError = null) }

    fun createSupportTicket(subject: String, description: String, category: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(ticketSending = true, ticketError = null, ticketSuccess = false)
            try {
                val resp = userApi.createSupportTicket(CreateSupportTicketRequest(subject, description, category))
                if (resp.isSuccessful && resp.body()?.success == true)
                    _state.value = _state.value.copy(ticketSending = false, ticketSuccess = true)
                else
                    _state.value = _state.value.copy(ticketSending = false, ticketError = resp.body()?.error ?: "Failed")
            } catch (e: Exception) {
                _state.value = _state.value.copy(ticketSending = false, ticketError = e.message ?: "Network error")
            }
        }
    }

    fun clearTicketState() { _state.value = _state.value.copy(ticketSuccess = false, ticketError = null) }
}
