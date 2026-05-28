package com.spendwise.app.domain.usecase

import com.spendwise.app.core.Constants
import com.spendwise.app.domain.merchant.MerchantMatcher
import java.time.LocalDate
import javax.inject.Inject

/**
 * Parsed result for a credit-card bill-due / payment-due reminder SMS.
 * These are NOT transactions — they are reminders to pay your CC bill.
 * We use them to update the credit card's outstanding balance in the app.
 */
data class ParsedBillDue(
    val cardLast4: String?,
    val outstandingAmount: Double,   // total amount due (the statement balance)
    val minDueAmount: Double?,       // minimum amount due
    val dueDate: String?,            // ISO format "YYYY-MM-DD", if parseable
    val bankName: String?
)

/**
 * Extended parsed SMS result.
 * Includes intelligent extraction of card last-4, loan account, UPI VPA,
 * credit card vs bank account detection, auto-classified category, and bill payee.
 */
data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val isCredit: Boolean,
    val categorySlug: String,
    val availableBalance: Double?,
    // Account identifiers
    val accountLast4: String?,       // Last 4 digits of bank account/card
    val cardLast4: String?,          // Credit/debit card last 4 (distinct from bank a/c)
    val loanAccountNo: String?,      // Partial loan account number
    // Account type detection
    val isCreditCard: Boolean,       // True if transaction is on a credit card
    val bankName: String?,           // Detected bank name
    // UPI details
    val upiVpa: String?,             // UPI VPA (e.g. merchant@upi)
    // Bill/payment payee
    val billPayee: String?,          // e.g. BSNL, BESCOM
    // Merchant confidence
    val merchantConfidence: Float,
)

class ParseSmsUseCase @Inject constructor() {

    /**
     * Returns true if this SMS is a credit-card bill-due / payment-due reminder.
     * Such messages contain the outstanding balance but are NOT actual debit/credit
     * transactions — they should be filtered from the transaction pipeline.
     */
    fun isBillDueReminder(body: String): Boolean {
        val duePat = Regex(
            """\b(?:is\s+due|due\s+on|due\s+date|payment\s+due|bill\s+due|""" +
            """minimum\s+(?:amount\s+)?due|min(?:imum)?\s+due|total\s+outstanding|""" +
            """outstanding\s+amount|overdue|amount\s+due|total\s+due)\b""",
            RegexOption.IGNORE_CASE
        )
        return duePat.containsMatchIn(body)
    }

    /**
     * Parses a credit-card bill-due reminder SMS.
     * Returns null if the SMS is not a bill-due reminder.
     */
    fun parseBillDue(body: String): ParsedBillDue? {
        if (!isBankSms(body) || !isBillDueReminder(body)) return null
        val outstanding = extractAmount(body) ?: return null

        // Minimum due: "minimum amount due of INR 206" or "min due Rs.200"
        val minDuePat = Regex(
            """(?:minimum\s+(?:amount\s+)?due|min(?:imum)?\s+due)\s+(?:of\s+)?(?:INR|Rs\.?|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        val minDue = minDuePat.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        // Due date: "due on 01-06-26", "due by 2026-06-01", "due date: 01/06/2026"
        val dueDatePat = Regex(
            """(?:due\s+(?:on|by|date)?[:\s]*)\s*(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})""",
            RegexOption.IGNORE_CASE
        )
        val rawDate = dueDatePat.find(body)?.groupValues?.get(1)
        val dueDate = rawDate?.let { parseBillDate(it) }

        return ParsedBillDue(
            cardLast4         = extractCardLast4(body),
            outstandingAmount = outstanding,
            minDueAmount      = minDue,
            dueDate           = dueDate,
            bankName          = extractBankName(body)
        )
    }

    /** Converts DD-MM-YY / DD-MM-YYYY / DD/MM/YY / DD/MM/YYYY to ISO "YYYY-MM-DD". */
    private fun parseBillDate(raw: String): String? {
        val parts = raw.split(Regex("[-/]"))
        if (parts.size != 3) return null
        val day   = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        var year  = parts[2].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        return runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
    }

    /** Returns null if this is not a bank transaction SMS. */
    fun parse(body: String): ParsedSms? {
        if (!isBankSms(body)) return null
        // Bill-due reminders look like transactions but are not — filter them out.
        if (isBillDueReminder(body)) return null
        val amount = extractAmount(body) ?: return null
        val isCredit   = detectIsCredit(body)
        val isCcTx     = detectCreditCard(body)
        val merchant   = extractMerchant(body)
        val upiVpa     = extractUpiVpa(body)
        val cardLast4  = extractCardLast4(body)
        val acctLast4  = extractAccountLast4(body)
        val loanAcct   = extractLoanAccount(body)
        val balance    = extractBalance(body)
        val bankName   = extractBankName(body)
        val billPayee  = extractBillPayee(body)

        // Use MerchantMatcher for intelligent classification
        val matchedTag = MerchantMatcher.classify(
            merchant.ifBlank { upiVpa?.substringBefore('@') ?: "" },
            body
        )
        // For credit transactions, override category
        val finalCategory = if (isCredit && matchedTag.categorySlug == "other") "income" else matchedTag.categorySlug

        return ParsedSms(
            amount            = amount,
            merchant          = merchant,
            isCredit          = isCredit,
            categorySlug      = finalCategory,
            availableBalance  = balance,
            accountLast4      = acctLast4 ?: cardLast4,
            cardLast4         = cardLast4,
            loanAccountNo     = loanAcct,
            isCreditCard      = isCcTx,
            bankName          = bankName,
            upiVpa            = upiVpa,
            billPayee         = billPayee,
            merchantConfidence = matchedTag.confidence,
        )
    }

    // ── Bank SMS detection ────────────────────────────────────────
    private fun isBankSms(body: String) = Constants.BANK_PATTERN.containsMatchIn(body)

    // ── Amount extraction ─────────────────────────────────────────
    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Amt|Amount)[:\s]+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|deducted)""", RegexOption.IGNORE_CASE),
            Regex("""(?:debited|paid|spent|withdrawn|deposited|charged)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""of\s+(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(body) ?: continue
            val d = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (d != null && d > 0) return d
        }
        return null
    }

    // ── Credit vs Debit ───────────────────────────────────────────
    private fun detectIsCredit(body: String): Boolean {
        val creditPat = Regex("""\b(?:credited|credit|salary|received|refund|cashback|deposit|reversal|added|inward)\b""", RegexOption.IGNORE_CASE)
        val debitPat  = Regex("""\b(?:debited|debit|deducted|payment|spent|purchase|withdrawn|paid|charged|transferred from)\b""", RegexOption.IGNORE_CASE)
        val hasCredit = creditPat.containsMatchIn(body)
        val hasDebit  = debitPat.containsMatchIn(body)
        return hasCredit && !hasDebit
    }

    // ── Credit card vs bank account ───────────────────────────────
    private fun detectCreditCard(body: String): Boolean {
        val ccPat = Regex("""\b(?:credit card|cc|credit a/c|card ending|card no|card\.?\s*[Xx*]{0,4}\d{4}|statement|min(?:imum)? due|due date|outstanding)\b""", RegexOption.IGNORE_CASE)
        return ccPat.containsMatchIn(body)
    }

    // ── Merchant extraction ───────────────────────────────────────
    private fun extractMerchant(body: String): String {
        // 1. Explicit "Merchant:" or "Merchant Name:" label (highest confidence)
        Regex("""[Mm]erchant\s*[:/]\s*([A-Za-z][A-Za-z0-9\s&'._\-]{2,40})""")
            .find(body)?.let { return it.groupValues[1].trim() }

        // 2. Parentheses: (Merchant Name)
        Regex("""\(([A-Za-z][A-Za-z0-9\s&'._\-]{1,40})\)""").find(body)
            ?.let { return it.groupValues[1].trim() }

        // 3. spent at / paid at / purchase at / at <Merchant>
        Regex("""(?:spent at|paid at|payment at|purchase at|purchase of|at)\s+([A-Za-z0-9][A-Za-z0-9\s&'._\-]{1,35}?)(?:\s+on\s|\s+for\s|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 4. towards <Merchant>
        Regex("""towards?\s+([A-Za-z0-9][A-Za-z0-9\s&'._\-]{1,35}?)(?:\s+on\s|\s+via\s|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 5. UPI ref field: UPI/TxnRef/TxnRef/MerchantName or UPI-TxnRef-MerchantName
        Regex("""UPI[/\-][A-Z0-9]{5,}[/\-][A-Z0-9]{5,}[/\-]([^\n/]{3,40})(?:/|\.|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 6. transferred to / trf to <Name> (but not a phone number)
        Regex("""(?:transferred?|trf)\s+to\s+([A-Za-z][A-Za-z\s]{2,30}?)(?:\.|,|\s+on\s|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 7. for <Merchant> <payment|transfer|txn|transaction>
        Regex("""for\s+([A-Za-z][A-Za-z0-9\s&'._\-]{2,30}?)\s+(?:payment|transfer|txn|transaction)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 8. Broad "debited for / paid for / deducted for" <Merchant> before punctuation/INR/RS/balance keywords
        Regex("""(?:debited for|paid for|deducted for|credited for)\s+([A-Za-z][A-Za-z0-9\s&'._\-]{2,35}?)(?:\s+on\s|\s+via\s|\s+INR|\s+Rs\.?|\s+UPI|\s+Avl|\s+Avail|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 9. VPA domain as merchant — but ONLY if domain is not purely numeric (phone-number VPAs)
        val vpa = extractUpiVpa(body)
        if (vpa != null) {
            val domain = vpa.substringBefore('@')
            val domainLetters = domain.replace(Regex("""[0-9._\-]"""), "").trim()
            if (domainLetters.length >= 3) {
                // Domain has meaningful letters (e.g. "paytm", "okaxis", "yesbank")
                return domainLetters.replaceFirstChar { it.uppercase() }
            }
        }

        // 10. "paid to <Name>" or "trf to <Name>" — name may start with capital letter
        Regex("""(?:paid to|pay to|send to)\s+([A-Za-z][A-Za-z\s]{2,30}?)(?:@|\s+via|\s+UPI|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { m ->
                val name = m.groupValues[1].trim()
                if (name.length >= 2) return name
            }

        // 11. EMI / loan-specific fallback
        if (Regex("""\b(?:EMI|loan|mandate)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)) {
            return extractBankName(body)?.let { "$it EMI" } ?: "EMI Payment"
        }

        // 12. For UPI debits with phone-number VPA, use "UPI Transfer"
        if (vpa != null) return "UPI Transfer"

        return "Bank Transaction"
    }

    // ── UPI VPA extraction ────────────────────────────────────────
    fun extractUpiVpa(body: String): String? {
        val pat = Regex("""([a-zA-Z0-9._\-]+@[a-zA-Z0-9._\-]+)""")
        return pat.find(body)?.groupValues?.get(1)
    }

    // ── Card last 4 digits ────────────────────────────────────────
    fun extractCardLast4(body: String): String? {
        val patterns = listOf(
            // "card ending 1234" / "card ending with 1234"
            Regex("""card\s+(?:ending|no\.?|number|ending with)\s*[Xx*\s]{0,8}(\d{4})""", RegexOption.IGNORE_CASE),
            // "HDFC CC XXXX1234"
            Regex("""[A-Z]{2,}\s+(?:CC|CARD)\s+[Xx*]{4}(\d{4})""", RegexOption.IGNORE_CASE),
            // "XXXX XXXX XXXX 1234"
            Regex("""[Xx*]{4}\s*[Xx*]{4}\s*[Xx*]{4}\s*(\d{4})"""),
            // "Credit Card XXXX1234"
            Regex("""credit\s+card\s+(?:[Xx*]{0,8})(\d{4})""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // ── Bank account last 4 ───────────────────────────────────────
    fun extractAccountLast4(body: String): String? {
        val patterns = listOf(
            Regex("""A/C\s*(?:no\.?)?\s*[Xx*]{0,10}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""account\s+(?:ending|no\.?|number)?\s*[Xx*]{0,10}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:Savings|Current|SB|CA)\s+(?:A/C|Acct?\.?)\s*(?:No\.?)?\s*[Xx*]{0,8}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""(?:ac|acct)\s+(?:no\.?)?\s*[Xx*]{0,8}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""[Xx*]{4}(\d{4})"""),
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // ── Loan account number ───────────────────────────────────────
    fun extractLoanAccount(body: String): String? {
        val patterns = listOf(
            // "Loan A/C 1234567890"
            Regex("""loan\s+(?:a/c|account|acct)\.?\s*(?:no\.?)?\s*([A-Z0-9]{4,20})""", RegexOption.IGNORE_CASE),
            // "Loan No. 123456789"
            Regex("""loan\s+(?:no\.?|number)\s*:?\s*([A-Z0-9]{4,20})""", RegexOption.IGNORE_CASE),
            // "EMI for loan 123456789"
            Regex("""emi\s+(?:for|of)\s+(?:loan\s+)?([A-Z0-9]{4,20})""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // ── Available balance ─────────────────────────────────────────
    private fun extractBalance(body: String): Double? {
        val pat = Regex(
            """(?:Avail\.?\s*Bal|Avl\.?\s*Bal|Available\s*(?:Balance|Bal)|Balance|Bal)\s*:?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        return pat.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    // ── Bank name detection ───────────────────────────────────────
    fun extractBankName(body: String): String? {
        val banks = listOf(
            "HDFC Bank", "ICICI Bank", "SBI", "State Bank", "Axis Bank",
            "Kotak Bank", "Kotak Mahindra", "Yes Bank", "IndusInd Bank",
            "Federal Bank", "IDFC FIRST", "IDBI Bank", "PNB", "Bank of Baroda",
            "Canara Bank", "Union Bank", "RBL Bank", "HSBC", "Standard Chartered",
            "DBS Bank", "Citi Bank", "Citibank", "AU Bank", "Ujjivan Bank",
        )
        val lower = body.lowercase()
        return banks.firstOrNull { lower.contains(it.lowercase()) }
    }

    // ── Bill payee extraction ─────────────────────────────────────
    private fun extractBillPayee(body: String): String? {
        val billKeywords = Regex(
            """(?:bill payment|bill pay|utility payment|paid to)\s+(?:for\s+)?([A-Za-z][A-Za-z0-9\s]{2,30}?)(?:\.|,|;|\n|$)""",
            RegexOption.IGNORE_CASE
        )
        return billKeywords.find(body)?.groupValues?.get(1)?.trim()
    }
}
