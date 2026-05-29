package com.spendwise.app.unit.imap

import com.google.common.truth.Truth.assertThat
import com.spendwise.app.data.gmail.GmailImapClient
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for GmailImapClient utility methods and sender-keyword matching.
 *
 * Note: actual IMAP network calls are not tested here (integration scope).
 * These tests cover the sender-filtering and keyword logic.
 */
@RunWith(JUnit4::class)
class GmailImapClientTest {

    // ─── Bank sender keyword matching ─────────────────────────────────────────

    private fun isBankSender(email: String): Boolean =
        GmailImapClient.BANK_SENDER_KEYWORDS.any { email.lowercase().contains(it) }

    @Test
    fun `recognises HDFC bank sender`() {
        assertThat(isBankSender("alerts@hdfcbank.com")).isTrue()
    }

    @Test
    fun `recognises ICICI bank sender`() {
        assertThat(isBankSender("notify@icicibank.com")).isTrue()
    }

    @Test
    fun `recognises Axis bank sender`() {
        assertThat(isBankSender("alerts@axisbank.com")).isTrue()
    }

    @Test
    fun `recognises SBI card sender`() {
        assertThat(isBankSender("info@sbicard.com")).isTrue()
    }

    @Test
    fun `recognises SBI coin domain sender`() {
        assertThat(isBankSender("alerts@sbi.co.in")).isTrue()
    }

    @Test
    fun `recognises Kotak sender`() {
        assertThat(isBankSender("noreply@kotak.com")).isTrue()
    }

    @Test
    fun `recognises Yes Bank sender`() {
        assertThat(isBankSender("alert@yesbank.in")).isTrue()
    }

    @Test
    fun `recognises IndusInd sender`() {
        assertThat(isBankSender("alerts@indusind.com")).isTrue()
    }

    @Test
    fun `recognises Federal Bank sender`() {
        assertThat(isBankSender("notify@federalbank.co.in")).isTrue()
    }

    @Test
    fun `recognises Paytm Bank sender`() {
        assertThat(isBankSender("alerts@paytmbank.com")).isTrue()
    }

    @Test
    fun `recognises RBL Bank sender`() {
        assertThat(isBankSender("alerts@rblbank.com")).isTrue()
    }

    @Test
    fun `recognises HSBC sender`() {
        assertThat(isBankSender("notify@hsbc.co.in")).isTrue()
    }

    @Test
    fun `recognises Citibank sender`() {
        assertThat(isBankSender("alerts@citibank.com")).isTrue()
    }

    @Test
    fun `recognises IDFC First Bank sender`() {
        assertThat(isBankSender("alerts@idfcfirstbank.com")).isTrue()
    }

    @Test
    fun `rejects random email sender`() {
        assertThat(isBankSender("newsletter@amazon.com")).isFalse()
    }

    @Test
    fun `rejects personal email`() {
        assertThat(isBankSender("friend@gmail.com")).isFalse()
    }

    @Test
    fun `rejects OTP spam email`() {
        assertThat(isBankSender("noreply@truecaller.com")).isFalse()
    }

    @Test
    fun `rejects delivery email`() {
        assertThat(isBankSender("track@delhivery.com")).isFalse()
    }

    // ─── Keyword safety — no false positives on real domains ─────────────────

    @Test
    fun `pnb keyword does not match unrelated pnb domain`() {
        // pnbhfl.com is PNB Housing — acceptable match (related entity)
        // but 'randomsite.com' should not match 'pnb'
        assertThat(isBankSender("support@randomsite.com")).isFalse()
    }

    @Test
    fun `kotak keyword matches kotak domain correctly`() {
        // "@kotak" prefix prevents false positives from substrings
        assertThat(isBankSender("alerts@kotak.com")).isTrue()
        assertThat(isBankSender("alerts@kotak.co.in")).isTrue()
        // "notakotak.io" does NOT contain "@kotak" — no false positive
        assertThat(isBankSender("alerts@notakotak.io")).isFalse()
    }

    @Test
    fun `Standard Chartered full domain matches`() {
        assertThat(isBankSender("alerts@standardchartered.com")).isTrue()
    }

    @Test
    fun `American Express matches`() {
        assertThat(isBankSender("alerts@amex.com")).isTrue()
        assertThat(isBankSender("info@americanexpress.com")).isTrue()
    }
}
