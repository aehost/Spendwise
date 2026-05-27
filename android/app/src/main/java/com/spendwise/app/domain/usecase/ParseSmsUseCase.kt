package com.spendwise.app.domain.usecase

import com.spendwise.app.core.Constants
import javax.inject.Inject

data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val isCredit: Boolean,
    val categorySlug: String,
    val availableBalance: Double?,
    val accountLast4: String?
)

class ParseSmsUseCase @Inject constructor() {

    /** Returns null if this is not a bank transaction SMS. */
    fun parse(body: String): ParsedSms? {
        if (!isBankSms(body)) return null
        val amount = extractAmount(body) ?: return null
        val isCredit = detectIsCredit(body)
        val merchant = extractMerchant(body)
        val category = guessCategory(merchant, body, isCredit)
        val balance  = extractBalance(body)
        val last4    = extractLast4(body)
        return ParsedSms(amount, merchant, isCredit, category, balance, last4)
    }

    private fun isBankSms(body: String) = Constants.BANK_PATTERN.containsMatchIn(body)

    private fun extractAmount(body: String): Double? {
        val patterns = listOf(
            Regex("""(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Amt|Amount)[:\s]+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:debited|credited|deducted)""", RegexOption.IGNORE_CASE),
            Regex("""(?:debited|paid|spent|withdrawn|deposited)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) {
                val s = m.groupValues[1].replace(",", "")
                val d = s.toDoubleOrNull()
                if (d != null && d > 0) return d
            }
        }
        return null
    }

    private fun detectIsCredit(body: String): Boolean {
        val creditPat = Regex("""\b(?:credited|credit|salary|received|refund|cashback|deposit|reversal)\b""", RegexOption.IGNORE_CASE)
        val debitPat  = Regex("""\b(?:debited|debit|deducted|payment|spent|purchase|withdrawn|paid|charged)\b""", RegexOption.IGNORE_CASE)
        val hasCredit = creditPat.containsMatchIn(body)
        val hasDebit  = debitPat.containsMatchIn(body)
        return hasCredit && !hasDebit
    }

    private fun extractMerchant(body: String): String {
        // 1. Parentheses: (Merchant Name)
        Regex("""\(([A-Za-z][A-Za-z0-9\s&'._\-]{1,40})\)""").find(body)?.let { return it.groupValues[1].trim() }

        // 2. spent at / paid at / purchase at / at
        Regex("""(?:spent at|paid at|payment at|purchase at|\bat)\s+([A-Za-z0-9][A-Za-z0-9\s&'._\-]{1,35}?)(?:\s+on\s|\s+for\s|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 3. towards
        Regex("""towards?\s+([A-Za-z0-9][A-Za-z0-9\s&'._\-]{1,35}?)(?:\s+on\s|\s+via\s|\.|,|;|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 4. UPI ID description
        Regex("""UPI[/\-][A-Z0-9]{5,}[/\-][A-Z0-9]{5,}[/\-]([^\n/]{3,40})(?:/|\.|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 5. transferred to
        Regex("""(?:transferred?|trf)\s+to\s+([A-Za-z][A-Za-z0-9\s]{2,30}?)(?:\.|,|\s+on\s|\n|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 6. Employer
        Regex("""(?:Employer|employer)[:\s]+([A-Za-z][^\.\n]{3,40})""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        // 7. for … payment/transfer
        Regex("""for\s+([A-Za-z][A-Za-z0-9\s&'._\-]{2,30}?)\s+(?:payment|transfer|txn)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1].trim() }

        return "Bank Transaction"
    }

    private fun guessCategory(merchant: String, body: String, isCredit: Boolean): String {
        if (isCredit) return "income"
        val text = "${merchant.lowercase()} ${body.lowercase()}"
        return when {
            text.containsAny("swiggy", "zomato", "food", "restaurant", "cafe", "pizza", "burger", "biryani", "dining") -> "food"
            text.containsAny("petrol", "diesel", "fuel", "pump", "hp petro", "iocl", "bharat petro") -> "fuel"
            text.containsAny("amazon", "flipkart", "myntra", "ajio", "nykaa", "mall", "shop", "meesho") -> "shopping"
            text.containsAny("netflix", "hotstar", "spotify", "prime video", "youtube", "cinema", "movie", "pvr", "inox") -> "entertainment"
            text.containsAny("apollo", "pharmacy", "hospital", "clinic", "medical", "health", "doctor", "medplus") -> "health"
            text.containsAny("electricity", "airtel", "jio", "bsnl", "water", "gas", "bill", "bescom", "tneb", "tnedcl") -> "bills"
            text.containsAny("emi", "loan", "rbl", "kotak", "cred", "hdfc", "sbi loan", "axis bank") -> "emi"
            text.containsAny("irctc", "ola", "uber", "rapido", "train", "flight", "travel", "mmt", "makemytrip") -> "travel"
            text.containsAny("salary", "sal", "employer", "payroll", "ctc") -> "income"
            text.containsAny("transfer", "neft", "imps", "rtgs", "mom", "dad", "brother", "sister", "wife", "husband") -> "family"
            text.containsAny("sip", "mutual", "invest", "zerodha", "groww", "angel", "demat", "ppf", "nps") -> "investment"
            else -> "other"
        }
    }

    private fun String.containsAny(vararg tokens: String) = tokens.any { this.contains(it) }

    private fun extractBalance(body: String): Double? {
        val pat = Regex("""(?:Avail\.?\s*Bal|Avl\.?\s*Bal|Available\s*Balance|Balance)[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        return pat.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun extractLast4(body: String): String? {
        val patterns = listOf(
            Regex("""A/C\s*(?:no\.?)?\s*[Xx*]{0,6}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""account\s+(?:ending|no\.?|number)?\s*[Xx*]{0,6}(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""[Xx*]{4}(\d{4})""")
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
