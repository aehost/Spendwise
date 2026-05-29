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
        Regex("""([\d,]+\.?\d*)\s*(?:rupees?|INR)""", RegexOption.IGNORE_CASE)
    )

    private val DEBIT_KEYWORDS = listOf("debited", "debit", "spent", "paid", "purchase", "withdrawn", "deducted", "charged")
    private val CREDIT_KEYWORDS = listOf("credited", "credit", "received", "refund", "cashback", "deposited", "added")

    // Merchant extraction: "at MERCHANT" or "to MERCHANT" or "at merchant NAME"
    private val MERCHANT_PATTERNS = listOf(
        Regex("""(?:at|to|from|merchant[:\s]+|Info:\s*)([\w\s\-&'\.]+?)(?:\s+on\s+|\s+dated|\s+\d|\.|\n|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:VPA|UPI ID)[:\s]+([\w@\.\-]+)""", RegexOption.IGNORE_CASE)
    )

    fun parse(email: RawBankEmail): ParsedEmailTransaction? {
        val text = "${email.subject} ${email.body}"

        val amount = extractAmount(text) ?: return null
        val isCredit = detectCredit(text)
        val merchant = extractMerchant(text).ifBlank { "Bank Email" }
        val categorySlug = inferCategory(merchant, text)

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
            return raw.toDoubleOrNull()?.takeIf { it > 0.0 }
        }
        return null
    }

    private fun detectCredit(text: String): Boolean {
        val lower = text.lowercase()
        val creditScore = CREDIT_KEYWORDS.count { lower.contains(it) }
        val debitScore = DEBIT_KEYWORDS.count { lower.contains(it) }
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
            lower.containsAny("swiggy", "zomato", "uber eats", "restaurant", "food", "cafe", "coffee") -> "food"
            lower.containsAny("uber", "ola", "rapido", "metro", "irctc", "petrol", "fuel", "diesel") -> "transport"
            lower.containsAny("amazon", "flipkart", "myntra", "meesho", "ajio", "shopping") -> "shopping"
            lower.containsAny("netflix", "hotstar", "spotify", "amazon prime", "youtube", "entertainment") -> "entertainment"
            lower.containsAny("electricity", "water", "gas", "broadband", "mobile", "recharge", "bill", "utility") -> "bills"
            lower.containsAny("hospital", "pharmacy", "medic", "clinic", "doctor", "health") -> "health"
            lower.containsAny("emi", "loan", "hdfc bank", "icici bank", "axis bank", "sbi") -> "emi"
            lower.containsAny("salary", "neft", "imps", "rtgs") -> if (text.lowercase().contains("credit")) "income" else "transfer"
            else -> "other"
        }
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }
}
