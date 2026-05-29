package com.spendwise.app.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.spendwise.app.data.gmail.GmailImapClient
import com.spendwise.app.data.local.preferences.GmailImapAccount
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.ChangePasswordRequest
import com.spendwise.app.data.remote.dto.CreateSupportTicketRequest
import com.spendwise.app.data.remote.dto.GmailAccountDto
import com.spendwise.app.data.remote.dto.LogoutRequest
import com.spendwise.app.data.remote.dto.UpdateSalaryRequest
import com.spendwise.app.data.worker.GmailImapWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsState(
    val email: String? = null,
    val name: String? = null,
    val salaryAmount: Double? = null,
    val salaryDay: Int? = null,
    val smsScanFromMs: Long = 0L,
    // Gmail IMAP accounts
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
    private val userApi: UserApi
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
            launch {
                try {
                    val salary = userApi.getSalary()
                    if (salary.isSuccessful) _state.value = _state.value.copy(
                        salaryAmount = salary.body()?.data?.amount,
                        salaryDay    = salary.body()?.data?.expectedDay
                    )
                } catch (_: Exception) {}
            }
            // Load IMAP accounts from local storage
            launch {
                val imapAccounts = GmailImapWorker.readAccounts(tokenManager)
                _state.value = _state.value.copy(
                    gmailAccounts = imapAccounts.map { GmailAccountDto(it.email, it.email, null, true) }
                )
            }
        }
    }

    /** Connect a Gmail account via App Password IMAP. Tests the connection first. */
    fun connectGmailManual(email: String, appPassword: String) {
        if (email.isBlank() || !email.contains("@")) {
            _state.value = _state.value.copy(gmailError = "Enter a valid Gmail address")
            return
        }
        if (appPassword.isBlank() || appPassword.length < 8) {
            _state.value = _state.value.copy(gmailError = "Enter a valid App Password (16 characters from Google Account settings)")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(gmailLoading = true, gmailError = null)
            withContext(Dispatchers.IO) {
                try {
                    // Test IMAP connection first
                    GmailImapClient.fetchBankEmailsSince(
                        email.trim(), appPassword.trim(),
                        System.currentTimeMillis() - 3600_000L
                    )
                    val accounts = GmailImapWorker.readAccounts(tokenManager).toMutableList()
                    if (accounts.none { it.email == email.trim().lowercase() }) {
                        accounts.add(GmailImapAccount(email.trim().lowercase(), appPassword.trim(), 0L))
                        GmailImapWorker.writeAccounts(tokenManager, accounts)
                    }
                    _state.value = _state.value.copy(
                        gmailLoading = false,
                        gmailAccounts = accounts.map { GmailAccountDto(it.email, it.email, null, true) }
                    )
                    GmailImapWorker.triggerNow(appContext)
                } catch (e: Exception) {
                    val msg = when {
                        e.message?.contains("Authentication failed", true) == true ->
                            "Authentication failed. Make sure you're using an App Password (not your regular Gmail password).\nGenerate one at myaccount.google.com/apppasswords"
                        e.message?.contains("Connection refused", true) == true ->
                            "Could not connect. Check your internet connection."
                        else -> "Connection failed: ${e.message}"
                    }
                    _state.value = _state.value.copy(gmailLoading = false, gmailError = msg)
                }
            }
        }
    }

    fun removeGmailImapAccount(email: String) {
        val accounts = GmailImapWorker.readAccounts(tokenManager).filter { it.email != email }
        GmailImapWorker.writeAccounts(tokenManager, accounts)
        _state.value = _state.value.copy(
            gmailAccounts = accounts.map { GmailAccountDto(it.email, it.email, null, true) }
        )
    }

    // Keep old removeGmailAccount API for UI compatibility
    fun removeGmailAccount(accountId: String) = removeGmailImapAccount(accountId)

    fun syncGmailNow() {
        _state.value = _state.value.copy(gmailSyncing = true)
        GmailImapWorker.triggerNow(appContext)
        viewModelScope.launch {
            kotlinx.coroutines.delay(3_000)
            _state.value = _state.value.copy(gmailSyncing = false)
        }
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
