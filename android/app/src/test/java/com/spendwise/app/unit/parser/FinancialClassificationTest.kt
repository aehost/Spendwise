package com.spendwise.app.unit.parser

import com.google.common.truth.Truth.assertThat
import com.spendwise.app.core.FinancialTextHeuristics
import com.spendwise.app.data.gmail.BankEmailParser
import com.spendwise.app.data.gmail.RawBankEmail
import com.spendwise.app.domain.usecase.ParseSmsUseCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Verifies the core correctness fix: reminders / scheduled / future-dated and
 * due notices (EMI due, "SIP will be debited", autopay scheduled, statements)
 * are NOT booked as real debit/credit transactions — while genuinely completed
 * transactions still are, and investment debits get the right category.
 */
@RunWith(JUnit4::class)
class FinancialClassificationTest {

    private val sms = ParseSmsUseCase()

    private fun email(subject: String, body: String) = RawBankEmail(
        subject = subject, body = body, from = "alerts@hdfcbank.com",
        receivedMs = System.currentTimeMillis(), messageId = "id-1"
    )

    // ─── Heuristics: reminder / future detection ──────────────────────────────

    @Test fun `SIP will-be-debited is a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Your SIP of Rs.5000 will be debited on 05-Jun")).isTrue()
    }

    @Test fun `EMI is-due is a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Reminder: your EMI of Rs.10000 is due on 15-Jun")).isTrue()
    }

    @Test fun `autopay scheduled is a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Rs.499 autopay scheduled for Netflix on 02-Jun")).isTrue()
    }

    @Test fun `mandate registered is a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "E-mandate registered for Rs.5000 towards HDFC MF")).isTrue()
    }

    @Test fun `statement total due is a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Your total amount due is Rs.25000, payment due on 18-Jun")).isTrue()
    }

    @Test fun `completed debit is NOT a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Rs.500 debited from a/c XX1234 at AMAZON on 02-Jun")).isFalse()
    }

    @Test fun `completed credit is NOT a reminder`() {
        assertThat(FinancialTextHeuristics.isReminderOrFuture(
            "Rs.75000 credited to a/c XX1234. Salary for May")).isFalse()
    }

    // ─── Heuristics: investment detection ─────────────────────────────────────

    @Test fun `SIP text is investment`() {
        assertThat(FinancialTextHeuristics.isInvestment("debited towards SIP HDFC Mutual Fund")).isTrue()
    }

    @Test fun `groww text is investment`() {
        assertThat(FinancialTextHeuristics.isInvestment("Rs.2000 debited to Groww")).isTrue()
    }

    @Test fun `grocery text is not investment`() {
        assertThat(FinancialTextHeuristics.isInvestment("Rs.500 debited at More Supermarket")).isFalse()
    }

    // ─── SMS parser: reminders excluded from the ledger ───────────────────────

    @Test fun `SMS SIP reminder is not a transaction`() {
        val r = sms.parse("Your SIP of Rs.5000 will be debited on 05-Jun towards HDFC Mutual Fund")
        assertThat(r).isNull()
    }

    @Test fun `SMS EMI reminder is not a transaction`() {
        val r = sms.parse("Reminder: Your EMI of Rs.10000 is due on 15-Jun for loan XX12")
        assertThat(r).isNull()
    }

    @Test fun `SMS autopay reminder is not a transaction`() {
        val r = sms.parse("Rs.499 autopay scheduled for Netflix on 02-Jun from HDFC a/c")
        assertThat(r).isNull()
    }

    // ─── SMS parser: real transactions still captured ─────────────────────────

    @Test fun `SMS real debit is captured`() {
        val r = sms.parse("Rs.500 debited from a/c XX1234 at AMAZON on 02-Jun")
        assertThat(r).isNotNull()
        assertThat(r!!.isCredit).isFalse()
        assertThat(r.amount).isEqualTo(500.0)
    }

    @Test fun `SMS real SIP debit is captured and categorized as investment`() {
        val r = sms.parse("Rs.5000 debited from a/c XX1234 towards SIP for HDFC Mutual Fund")
        assertThat(r).isNotNull()
        assertThat(r!!.isCredit).isFalse()
        assertThat(r.categorySlug).isEqualTo("investment")
    }

    @Test fun `SMS real salary credit is captured`() {
        val r = sms.parse("Rs.75000 credited to a/c XX1234. Salary for May 2026")
        assertThat(r).isNotNull()
        assertThat(r!!.isCredit).isTrue()
    }

    // ─── Email parser: reminders excluded ─────────────────────────────────────

    @Test fun `email SIP reminder is not a transaction`() {
        val r = BankEmailParser.parse(email(
            "SIP reminder", "Your SIP of Rs.5000 will be debited on 05 Jun 2026"))
        assertThat(r).isNull()
    }

    @Test fun `email statement due is not a transaction`() {
        val r = BankEmailParser.parse(email(
            "HDFC Credit Card Statement", "Total amount due Rs.25,000. Payment due on 18-Jun-2026"))
        assertThat(r).isNull()
    }

    @Test fun `email real debit still captured`() {
        val r = BankEmailParser.parse(email(
            "Transaction alert", "Rs.2,500.00 debited from your HDFC account at FLIPKART"))
        assertThat(r).isNotNull()
        assertThat(r!!.amount).isEqualTo(2500.0)
        assertThat(r.isCredit).isFalse()
    }

    @Test fun `email real SIP debit categorized as investment`() {
        val r = BankEmailParser.parse(email(
            "Investment debit", "Rs.5,000.00 debited from HDFC account towards SIP Mutual Fund"))
        assertThat(r).isNotNull()
        assertThat(r!!.categorySlug).isEqualTo("investment")
    }
}
