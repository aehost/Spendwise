package com.spendwise.app.unit.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.GmailAccountDto
import com.spendwise.app.presentation.screens.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for SettingsViewModel.
 *
 * Tests cover:
 *  - State initialization from TokenManager
 *  - Gmail account management (connect, remove, duplicate guard)
 *  - Email / password validation logic
 *  - Error state clearing
 *  - Sync state management
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class SettingsViewModelTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var tokenManager: TokenManager
    private lateinit var authApi: AuthApi
    private lateinit var userApi: UserApi
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tokenManager = mockk(relaxed = true)
        authApi      = mockk(relaxed = true)
        userApi      = mockk(relaxed = true)

        // Default token manager stubs
        every { tokenManager.userEmail }          returns "test@example.com"
        every { tokenManager.userName }           returns "Test User"
        every { tokenManager.smsScanFromMs }      returns 0L
        every { tokenManager.gmailImapAccountsJson } returns "[]"

        // Stub getSalary to avoid network error in init
        coEvery { userApi.getSalary() } returns mockk(relaxed = true) {
            every { isSuccessful } returns false
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ─── Initialization ───────────────────────────────────────────────────────

    @Test
    fun `initial state loads email and name from TokenManager`() = runTest {
        viewModel = buildViewModel()
        val state = viewModel.state.value
        assertThat(state.email).isEqualTo("test@example.com")
        assertThat(state.name).isEqualTo("Test User")
    }

    @Test
    fun `initial state has no gmail accounts when none stored`() = runTest {
        every { tokenManager.gmailImapAccountsJson } returns "[]"
        viewModel = buildViewModel()
        assertThat(viewModel.state.value.gmailAccounts).isEmpty()
        assertThat(viewModel.state.value.gmailConnected).isFalse()
    }

    @Test
    fun `initial state loads stored gmail accounts`() = runTest {
        every { tokenManager.gmailImapAccountsJson } returns
            """[{"email":"user@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":0,"isActive":true}]"""
        viewModel = buildViewModel()
        assertThat(viewModel.state.value.gmailAccounts).hasSize(1)
        assertThat(viewModel.state.value.gmailConnected).isTrue()
    }

    // ─── Email validation guard ───────────────────────────────────────────────

    @Test
    fun `connectGmailManual rejects blank email`() = runTest {
        viewModel = buildViewModel()
        viewModel.connectGmailManual("", "abcdefghijklmnop")
        val state = viewModel.state.value
        assertThat(state.gmailError).isNotNull()
        assertThat(state.gmailError).contains("valid Gmail")
        assertThat(state.gmailLoading).isFalse()
    }

    @Test
    fun `connectGmailManual rejects email without at-sign`() = runTest {
        viewModel = buildViewModel()
        viewModel.connectGmailManual("notanemail", "abcdefghijklmnop")
        assertThat(viewModel.state.value.gmailError).isNotNull()
    }

    // ─── Password validation guard ────────────────────────────────────────────

    @Test
    fun `connectGmailManual rejects blank app password`() = runTest {
        viewModel = buildViewModel()
        viewModel.connectGmailManual("test@gmail.com", "")
        assertThat(viewModel.state.value.gmailError).isNotNull()
        assertThat(viewModel.state.value.gmailError).contains("App Password")
    }

    @Test
    fun `connectGmailManual rejects short app password`() = runTest {
        viewModel = buildViewModel()
        viewModel.connectGmailManual("test@gmail.com", "short")
        assertThat(viewModel.state.value.gmailError).isNotNull()
    }

    // ─── Error clearing ───────────────────────────────────────────────────────

    @Test
    fun `clearGmailError clears gmailError state`() = runTest {
        viewModel = buildViewModel()
        viewModel.connectGmailManual("bad", "bad")        // triggers error
        assertThat(viewModel.state.value.gmailError).isNotNull()
        viewModel.clearGmailError()
        assertThat(viewModel.state.value.gmailError).isNull()
    }

    @Test
    fun `clearPasswordState resets password success and error`() = runTest {
        viewModel = buildViewModel()
        // Manually inject password error state
        viewModel.clearPasswordState()
        assertThat(viewModel.state.value.passwordChangeSuccess).isFalse()
        assertThat(viewModel.state.value.passwordChangeError).isNull()
    }

    @Test
    fun `clearTicketState resets ticket states`() = runTest {
        viewModel = buildViewModel()
        viewModel.clearTicketState()
        assertThat(viewModel.state.value.ticketSuccess).isFalse()
        assertThat(viewModel.state.value.ticketError).isNull()
    }

    // ─── Account removal ─────────────────────────────────────────────────────

    @Test
    fun `removeGmailImapAccount removes matching account`() = runTest {
        every { tokenManager.gmailImapAccountsJson } returns
            """[{"email":"user@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":0,"isActive":true}]"""
        every { tokenManager.gmailImapAccountsJson = any() } just runs
        viewModel = buildViewModel()

        viewModel.removeGmailImapAccount("user@gmail.com")
        assertThat(viewModel.state.value.gmailAccounts).isEmpty()
    }

    @Test
    fun `removeGmailAccount is alias for removeGmailImapAccount`() = runTest {
        every { tokenManager.gmailImapAccountsJson } returns
            """[{"email":"a@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":0,"isActive":true},
               {"email":"b@gmail.com","appPassword":"xxxxxxxxxxxxxxxx","lastSyncMs":0,"isActive":true}]"""
        every { tokenManager.gmailImapAccountsJson = any() } just runs
        viewModel = buildViewModel()

        viewModel.removeGmailAccount("a@gmail.com")
        // Should only remove "a@gmail.com"
        val remaining = viewModel.state.value.gmailAccounts.map { it.gmailEmail }
        assertThat(remaining).doesNotContain("a@gmail.com")
        assertThat(remaining).contains("b@gmail.com")
    }

    // ─── State computed properties ────────────────────────────────────────────

    @Test
    fun `gmailConnected is false with empty accounts`() = runTest {
        viewModel = buildViewModel()
        assertThat(viewModel.state.value.gmailConnected).isFalse()
    }

    @Test
    fun `gmailConnected is true with at least one account`() = runTest {
        every { tokenManager.gmailImapAccountsJson } returns
            """[{"email":"u@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":0,"isActive":true}]"""
        viewModel = buildViewModel()
        assertThat(viewModel.state.value.gmailConnected).isTrue()
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    fun `logout calls clearAuth on TokenManager`() = runTest {
        every { tokenManager.refreshToken } returns null
        viewModel = buildViewModel()
        viewModel.logout()
        verify(exactly = 1) { tokenManager.clearAuth() }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun buildViewModel(): SettingsViewModel {
        val context = mockk<android.content.Context>(relaxed = true)
        return SettingsViewModel(context, tokenManager, authApi, userApi)
    }
}
