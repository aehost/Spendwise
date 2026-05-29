package com.spendwise.app.unit.parser

import com.google.common.truth.Truth.assertThat
import com.spendwise.app.data.gmail.BankEmailParser
import com.spendwise.app.data.gmail.RawBankEmail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Exhaustive unit tests for BankEmailParser.
 *
 * Tests cover:
 *  - Amount extraction for all supported formats (Rs., INR, ₹, rupees)
 *  - Credit vs debit detection
 *  - Merchant extraction
 *  - Category inference
 *  - Edge cases (no amount, ambiguous, both credit+debit keywords)
 *  - Real-world bank email snippets for HDFC, ICICI, SBI, Axis, Kotak
 */
@RunWith(JUnit4::class)
class BankEmailParserTest {

    private fun email(subject: String, body: String): RawBankEmail = RawBankEmail(
        subject    = subject,
        body       = body,
        from       = "alerts@hdfcbank.com",
        receivedMs = System.currentTimeMillis(),
        messageId  = "test-id-001"
    )

    // ─── Amount extraction ────────────────────────────────────────────────────

    @Test
    fun `parses Rs dot format`() {
        val result = BankEmailParser.parse(email(
            subject = "HDFC Bank: Alert",
            body    = "Rs. 1,234.56 debited from your account"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(1234.56)
    }

    @Test
    fun `parses INR format without dot`() {
        val result = BankEmailParser.parse(email(
            subject = "Transaction Alert",
            body    = "INR 5000 debited from your HDFC account"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(5000.0)
    }

    @Test
    fun `parses rupee symbol`() {
        val result = BankEmailParser.parse(email(
            subject = "Alert",
            body    = "₹12,500 debited for Swiggy order"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(12500.0)
    }

    @Test
    fun `parses rupees word format`() {
        val result = BankEmailParser.parse(email(
            subject = "ICICI Bank Alert",
            body    = "50000 rupees credited to your account"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(50000.0)
    }

    @Test
    fun `parses amount with no decimal`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs 999 debited at Amazon"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(999.0)
    }

    @Test
    fun `parses large amount with commas`() {
        val result = BankEmailParser.parse(email(
            subject = "Salary Credit",
            body    = "₹1,25,000.00 credited to your account"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(125000.0)
    }

    @Test
    fun `returns null when no amount present`() {
        val result = BankEmailParser.parse(email(
            subject = "Card Statement",
            body    = "Please login to view your statement. No amount mentioned."
        ))
        assertThat(result).isNull()
    }

    @Test
    fun `returns null for zero amount`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 0 debited from account"
        ))
        assertThat(result).isNull()
    }

    // ─── Credit / debit detection ─────────────────────────────────────────────

    @Test
    fun `detects debit transaction correctly`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 500 debited from your account at Zomato"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.isCredit).isFalse()
    }

    @Test
    fun `detects credit transaction correctly`() {
        val result = BankEmailParser.parse(email(
            subject = "Credit Alert",
            body    = "Rs. 50,000 credited to your account. Salary received."
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.isCredit).isTrue()
    }

    @Test
    fun `detects refund as credit`() {
        val result = BankEmailParser.parse(email(
            subject = "Refund Alert",
            body    = "Rs. 299 refund credited to your account from Swiggy"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.isCredit).isTrue()
    }

    @Test
    fun `prefers debit when scores equal — safe default`() {
        // Edge: both "debited" and "credited" appear once
        val result = BankEmailParser.parse(email(
            subject = "Mixed Alert",
            body    = "Rs. 100 debited and Rs. 50 credited"
        ))
        assertThat(result).isNotNull()
        // creditScore == debitScore → returns false (debit is safer default)
        assertThat(result!!.isCredit).isFalse()
    }

    @Test
    fun `detects purchase as debit`() {
        val result = BankEmailParser.parse(email(
            subject = "Purchase Alert",
            body    = "INR 1500 purchase at IRCTC on your credit card"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.isCredit).isFalse()
    }

    // ─── Category inference ───────────────────────────────────────────────────

    @Test
    fun `infers food category for Swiggy`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 350 debited at Swiggy"
        ))
        assertThat(result!!.categorySlug).isEqualTo("food")
    }

    @Test
    fun `infers transport category for Uber`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 250 debited for Uber ride"
        ))
        assertThat(result!!.categorySlug).isEqualTo("transport")
    }

    @Test
    fun `infers shopping category for Amazon`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 999 debited at Amazon"
        ))
        assertThat(result!!.categorySlug).isEqualTo("shopping")
    }

    @Test
    fun `infers entertainment for Netflix`() {
        val result = BankEmailParser.parse(email(
            subject = "Subscription Debit",
            body    = "Rs. 499 debited for Netflix subscription"
        ))
        assertThat(result!!.categorySlug).isEqualTo("entertainment")
    }

    @Test
    fun `infers bills category for electricity`() {
        val result = BankEmailParser.parse(email(
            subject = "Bill Payment",
            body    = "Rs. 1,200 debited for electricity bill payment"
        ))
        assertThat(result!!.categorySlug).isEqualTo("bills")
    }

    @Test
    fun `infers health for pharmacy`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 450 debited at Apollo Pharmacy"
        ))
        assertThat(result!!.categorySlug).isEqualTo("health")
    }

    @Test
    fun `infers emi for EMI payment`() {
        val result = BankEmailParser.parse(email(
            subject = "EMI Debit",
            body    = "Rs. 5,000 EMI debited from your account"
        ))
        assertThat(result!!.categorySlug).isEqualTo("emi")
    }

    @Test
    fun `infers income for salary credit`() {
        val result = BankEmailParser.parse(email(
            subject = "Salary Credited",
            body    = "Rs. 80,000 salary credited to your account"
        ))
        assertThat(result!!.categorySlug).isEqualTo("income")
    }

    @Test
    fun `infers other for unknown merchant`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 750 debited from your account"
        ))
        assertThat(result!!.categorySlug).isEqualTo("other")
    }

    // ─── Merchant extraction ──────────────────────────────────────────────────

    @Test
    fun `extracts merchant from at-pattern`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 350 debited at ZOMATO on 2024-01-15"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.merchant.uppercase()).contains("ZOMATO")
    }

    @Test
    fun `extracts UPI VPA as merchant`() {
        val result = BankEmailParser.parse(email(
            subject = "UPI Debit",
            body    = "Rs. 200 debited. VPA: merchant@upi on 15-01-2024"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.merchant).contains("merchant")
    }

    @Test
    fun `uses fallback merchant when extraction fails`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 100 debited. No merchant info."
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.merchant).isNotEmpty()
        assertThat(result.merchant).isEqualTo("Bank Email")
    }

    @Test
    fun `truncates long merchant names to 80 chars`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 100 debited at ${"A".repeat(100)}"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.merchant.length).isAtMost(80)
    }

    // ─── Real-world bank email scenarios ─────────────────────────────────────

    @Test
    fun `HDFC real debit email`() {
        val result = BankEmailParser.parse(email(
            subject = "HDFC Bank: Rs. 2,999.00 debited from a/c XX9876 on 29-05-26",
            body    = "Dear Customer, Rs. 2,999.00 has been debited from your HDFC Bank Account XX9876 on 29-05-2026 at FLIPKART. Available balance is Rs. 45,230.10."
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(2999.0)
        assertThat(result.isCredit).isFalse()
        assertThat(result.categorySlug).isEqualTo("shopping")
    }

    @Test
    fun `ICICI salary credit email`() {
        val result = BankEmailParser.parse(RawBankEmail(
            subject    = "ICICI Bank: Account Credited",
            body       = "Dear Customer, INR 75,000.00 has been credited to your account XX1234. Salary received from ACME CORP on 01-05-2026.",
            from       = "alerts@icicibank.com",
            receivedMs = System.currentTimeMillis(),
            messageId  = "icici-001"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(75000.0)
        assertThat(result.isCredit).isTrue()
        assertThat(result.categorySlug).isEqualTo("income")
    }

    @Test
    fun `SBI UPI debit email`() {
        val result = BankEmailParser.parse(RawBankEmail(
            subject    = "SBI: UPI transaction alert",
            body       = "Dear SBI Customer, Your account XX5678 has been debited with ₹850.00 for UPI transaction to swiggy@yesb. Info: SWIGGY ORDER.",
            from       = "alerts@sbi.co.in",
            receivedMs = System.currentTimeMillis(),
            messageId  = "sbi-001"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(850.0)
        assertThat(result.isCredit).isFalse()
        assertThat(result.categorySlug).isEqualTo("food")
    }

    @Test
    fun `Axis Bank NEFT credit`() {
        val result = BankEmailParser.parse(RawBankEmail(
            subject    = "Axis Bank NEFT Credit",
            body       = "Account credited Rs.15,000 via NEFT from JOHN DOE on 29-05-2026. Ref No: AXBN12345.",
            from       = "alerts@axisbank.com",
            receivedMs = System.currentTimeMillis(),
            messageId  = "axis-001"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(15000.0)
        assertThat(result.isCredit).isTrue()
    }

    @Test
    fun `Kotak credit card purchase`() {
        val result = BankEmailParser.parse(RawBankEmail(
            // Body explicitly says "debited" to ensure debit detection
            subject    = "Kotak Bank: Credit Card Alert",
            body       = "Your Kotak Credit Card XX4321 was debited INR 3,499.00 at AMAZON INDIA on 29-05-2026.",
            from       = "alerts@kotak.com",
            receivedMs = System.currentTimeMillis(),
            messageId  = "kotak-001"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.amount).isEqualTo(3499.0)
        assertThat(result.isCredit).isFalse()
        assertThat(result.categorySlug).isEqualTo("shopping")
    }

    // ─── Transaction date ─────────────────────────────────────────────────────

    @Test
    fun `uses email received date as transaction date`() {
        val receivedMs = 1716940800000L // 2024-05-29
        val result = BankEmailParser.parse(RawBankEmail(
            subject    = "Debit Alert",
            body       = "Rs. 100 debited from account",
            from       = "alerts@hdfcbank.com",
            receivedMs = receivedMs,
            messageId  = "date-test-001"
        ))
        assertThat(result).isNotNull()
        // Date should be derived from receivedMs
        assertThat(result!!.transactionDate).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    // ─── Email ID deduplication ────────────────────────────────────────────────

    @Test
    fun `emailId is propagated for deduplication`() {
        val result = BankEmailParser.parse(email(
            subject = "Debit Alert",
            body    = "Rs. 500 debited from account"
        ))
        assertThat(result).isNotNull()
        assertThat(result!!.emailId).isEqualTo("test-id-001")
    }
}
