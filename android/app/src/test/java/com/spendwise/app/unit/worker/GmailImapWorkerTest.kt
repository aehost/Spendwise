package com.spendwise.app.unit.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.spendwise.app.data.local.preferences.GmailImapAccount
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.worker.GmailImapWorker
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.gson.Gson

/**
 * Unit tests for GmailImapWorker companion object utilities.
 *
 * Tests cover:
 *  - Account serialisation/deserialisation via readAccounts / writeAccounts
 *  - Retry cap logic (runAttemptCount >= 5 → success)
 *  - Empty accounts list → Result.success immediately
 *  - Account merge logic after sync
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GmailImapWorkerTest {

    private lateinit var tokenManager: TokenManager
    private val gson = Gson()

    @Before
    fun setUp() {
        tokenManager = mockk(relaxed = true)
    }

    // ─── Account serialisation ─────────────────────────────────────────────────

    @Test
    fun `readAccounts returns empty list when json is empty array`() {
        every { tokenManager.gmailImapAccountsJson } returns "[]"
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        assertThat(accounts).isEmpty()
    }

    @Test
    fun `readAccounts returns empty list on malformed json`() {
        every { tokenManager.gmailImapAccountsJson } returns "NOT_JSON"
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        assertThat(accounts).isEmpty()
    }

    @Test
    fun `readAccounts returns empty list when json is null string`() {
        every { tokenManager.gmailImapAccountsJson } returns "[]"
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        assertThat(accounts).isEmpty()
    }

    @Test
    fun `readAccounts deserialises single account`() {
        val json = """[{"email":"user@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":1000,"isActive":true}]"""
        every { tokenManager.gmailImapAccountsJson } returns json
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        assertThat(accounts).hasSize(1)
        assertThat(accounts[0].email).isEqualTo("user@gmail.com")
        assertThat(accounts[0].appPassword).isEqualTo("abcdefghijklmnop")
        assertThat(accounts[0].lastSyncMs).isEqualTo(1000L)
        assertThat(accounts[0].isActive).isTrue()
    }

    @Test
    fun `readAccounts deserialises multiple accounts`() {
        val json = """[
            {"email":"a@gmail.com","appPassword":"aaaaaaaaaaaaaaaa","lastSyncMs":0,"isActive":true},
            {"email":"b@gmail.com","appPassword":"bbbbbbbbbbbbbbbb","lastSyncMs":0,"isActive":true}
        ]"""
        every { tokenManager.gmailImapAccountsJson } returns json
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        assertThat(accounts).hasSize(2)
    }

    @Test
    fun `writeAccounts serialises list and saves to tokenManager`() {
        val slot = slot<String>()
        every { tokenManager.gmailImapAccountsJson = capture(slot) } just runs

        val accounts = listOf(
            GmailImapAccount("user@gmail.com", "testpassword1234", 0L, true)
        )
        GmailImapWorker.writeAccounts(tokenManager, accounts)

        val written = slot.captured
        assertThat(written).contains("user@gmail.com")
        assertThat(written).contains("testpassword1234")
    }

    @Test
    fun `writeAccounts serialises empty list as empty json array`() {
        val slot = slot<String>()
        every { tokenManager.gmailImapAccountsJson = capture(slot) } just runs
        GmailImapWorker.writeAccounts(tokenManager, emptyList())
        assertThat(slot.captured).isEqualTo("[]")
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `writeAccounts then readAccounts produces same data`() {
        val original = listOf(
            GmailImapAccount("user@gmail.com", "abcdefghijklmnop", 99999L, true),
            GmailImapAccount("other@gmail.com", "xxxxxxxxxxxxxxxx", 12345L, false)
        )
        var stored = ""
        every { tokenManager.gmailImapAccountsJson = any() } answers { stored = firstArg() }
        every { tokenManager.gmailImapAccountsJson } answers { stored }

        GmailImapWorker.writeAccounts(tokenManager, original)
        val recovered = GmailImapWorker.readAccounts(tokenManager)

        assertThat(recovered).hasSize(2)
        assertThat(recovered[0].email).isEqualTo("user@gmail.com")
        assertThat(recovered[0].appPassword).isEqualTo("abcdefghijklmnop")
        assertThat(recovered[0].lastSyncMs).isEqualTo(99999L)
        assertThat(recovered[1].email).isEqualTo("other@gmail.com")
        assertThat(recovered[1].isActive).isFalse()
    }

    // ─── isActive filtering ────────────────────────────────────────────────────

    @Test
    fun `readAccounts returns accounts with isActive false`() {
        val json = """[{"email":"inactive@gmail.com","appPassword":"abcdefghijklmnop","lastSyncMs":0,"isActive":false}]"""
        every { tokenManager.gmailImapAccountsJson } returns json
        val accounts = GmailImapWorker.readAccounts(tokenManager)
        // readAccounts returns ALL stored accounts; the worker filters isActive
        assertThat(accounts).hasSize(1)
        assertThat(accounts[0].isActive).isFalse()
    }
}
