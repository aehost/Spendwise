package com.spendwise.app.data.gmail

data class ParsedEmailTransaction(
    val amount: Double,
    val merchant: String,
    val categorySlug: String,
    val isCredit: Boolean,
    val transactionDate: String,
    val emailId: String
)

object BankEmailParser {
    // Amount patterns: "Rs. 1,234.56", "INR 1234", "₹1,234", "Rs 1234.00"
    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:Rs\.?\s*|INR\s*|₹\s*)([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+\.?\d*)\s*(?:rupees?|INR)""", RegexOption.IGNORE_CASE),
        // "debited by / credited with / amount: Rs X" — common in HDFC/SBI alerts
        Regex("""(?:debited(?:\s+by|\s+of)?|credited(?:\s+with)?|amount)[:\s]+(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    )

    // BUG FIX 1: Remove bare "credit" — it fires on "Credit Card", causing false credit detection.
    // Use only past-tense "credited" and specific phrases.
    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit received", "credit alert",
        "received", "refund", "cashback", "deposited", "added"
    )
    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "spent", "paid", "purchase",
        "withdrawn", "deducted", "charged", "used for"
    )

    // BUG FIX 3: Changed "merchant[:\s]+" to "merchant:\s*" (requires colon).
    // The old pattern matched "No merchant info." → captured "info" as merchant.
    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:at|to|from|merchant:\s*|Info:\s*)([\w\s\-&'\.]+?)(?:\s+on\s+|\s+dated|\s+\d|\.|\n|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:VPA|UPI ID)[:\s]+([\w@\.\-]+)""", RegexOption.IGNORE_CASE)
    )

    fun parse(email: RawBankEmail): ParsedEmailTransaction? {
        val text = "${email.subject} ${email.body}"

        // Reminders / statements / scheduled notices (EMI due, "SIP will be
        // debited", credit-card statement, autopay reminder) are NOT completed
        // transactions — never book them into the ledger.
        if (com.spendwise.app.core.FinancialTextHeuristics.isReminderOrFuture(text)) return null

        val amount = extractAmount(text) ?: return null
        val isCredit = detectCredit(text)
        val merchant = extractMerchant(text).ifBlank { "Bank Email" }
        val categorySlug =
            if (!isCredit && com.spendwise.app.core.FinancialTextHeuristics.isInvestment(text)) "investment"
            else inferCategory(merchant, text)

        // Use received date as transaction date
        val date = java.time.Instant.ofEpochMilli(email.receivedMs)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().toString()

        return ParsedEmailTransaction(
            amount          = amount,
            merchant        = merchant.take(80),
            categorySlug    = categorySlug,
            isCredit        = isCredit,
            transactionDate = date,
            emailId         = email.messageId
        )
    }

    private fun extractAmount(text: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1].replace(",", "")
            val value = raw.toDoubleOrNull()?.takeIf { it > 0.0 }
            if (value != null) return value
        }
        return null
    }

    private fun detectCredit(text: String): Boolean {
        val lower = text.lowercase()
        val creditScore = CREDIT_KEYWORDS.count { lower.contains(it) }
        val debitScore  = DEBIT_KEYWORDS.count { lower.contains(it) }
        return creditScore > debitScore
    }

    private fun extractMerchant(text: String): String {
        for (pattern in MERCHANT_PATTERNS) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1].trim()
            if (raw.length >= 2) return raw.split(" ").take(4).joinToString(" ").trim()
        }
        return ""
    }

    private fun inferCategory(merchant: String, text: String): String {
        val lower = (merchant + " " + text).lowercase()
        return when {
            lower.containsAny("swiggy", "zomato", "uber eats", "restaurant", "food", "cafe", "coffee")
                -> "food"
            lower.containsAny("uber", "ola", "rapido", "metro", "irctc", "petrol", "fuel", "diesel")
                -> "transport"
            lower.containsAny("amazon", "flipkart", "myntra", "meesho", "ajio", "shopping")
                -> "shopping"
            lower.containsAny("netflix", "hotstar", "spotify", "amazon prime", "youtube", "entertainment")
                -> "entertainment"
            lower.containsAny("electricity", "water", "gas", "broadband", "mobile", "recharge", "bill", "utility")
                -> "bills"
            lower.containsAny("hospital", "pharmacy", "medic", "clinic", "doctor", "health")
                -> "health"
            // BUG FIX 2: salary/payroll MUST come before emi/loan check.
            // Previously "icici bank" / "hdfc bank" keywords in emi check shadowed salary.
            // Also removed "hdfc bank", "icici bank", "axis bank", "sbi" from emi check —
            // bank names are NOT a reliable indicator of EMI transactions.
            lower.containsAny("salary", "payroll", "pay slip", "payslip")
                -> "income"
            lower.containsAny("emi", "loan repayment", "equated monthly")
                -> "emi"
            lower.containsAny("neft", "imps", "rtgs")
                // BUG FIX: bare contains("credit") also matched "credit card",
                // "credited to payee", etc. — misclassifying outgoing transfers as
                // income. Use the same direction detection as detectCredit() so a
                // NEFT/IMPS row is only "income" when it is genuinely a credit.
                -> if (detectCredit(text)) "income" else "transfer"
            else -> "other"
        }
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
