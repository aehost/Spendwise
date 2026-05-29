package com.spendwise.app.core

/**
 * Shared heuristics that decide whether a bank SMS / email represents a REAL,
 * completed transaction versus a reminder / scheduled / future-dated notice —
 * and whether a debit is investment-related.
 *
 * Used by BOTH ingestion paths (ParseSmsUseCase for SMS, BankEmailParser for
 * Gmail) so they classify consistently. The whole point: a "your SIP will be
 * debited on the 5th" or "EMI is due on the 15th" message must NEVER become a
 * debit/credit ledger entry — only genuinely completed transactions do.
 */
object FinancialTextHeuristics {

    /**
     * Reminder / future / scheduled / due phrasing.
     *
     * A COMPLETED transaction is written in the past tense ("Rs.500 debited",
     * "spent", "deducted from", "credited") and matches NONE of these. These
     * patterns instead signal something that has not happened yet (a reminder,
     * an autopay/SIP/EMI notice, a statement due date, a mandate registration).
     */
    private val REMINDER_PATTERN = Regex(
        "(?:" +
            // future tense
            "will\\s+be\\s+(?:auto[- ]?)?(?:debited|deducted|charged|paid|processed)|" +
            "to\\s+be\\s+(?:debited|deducted|charged)|" +
            "shall\\s+be\\s+(?:debited|deducted|charged)|" +
            // scheduling
            "(?:is|are)\\s+scheduled|scheduled\\s+(?:for|on)|" +
            // explicit reminder language
            "\\breminder\\b|\\bupcoming\\b|do\\s+not\\s+miss|kindly\\s+pay|please\\s+pay|" +
            // mandate / autopay SET-UP (registration, not execution)
            "(?:e[- ]?)?mandate\\s+(?:registered|created|set\\s+up|successful)|" +
            "auto[- ]?pay\\s+(?:is\\s+)?(?:scheduled|set|registered)|" +
            // due / outstanding (statement & bill reminders)
            "(?:is|payment|amount|total|min(?:imum)?)\\s+due|" +
            "due\\s+(?:on|by|date)|outstanding\\s+(?:amount|balance)|overdue" +
        ")",
        RegexOption.IGNORE_CASE
    )

    /**
     * Investment instruments / platforms. When a DEBIT matches these it is an
     * investment outflow and should be categorized as "investment" rather than
     * being left as "other".
     */
    private val INVESTMENT_PATTERN = Regex(
        "\\b(?:SIP|mutual\\s*fund|\\bMF\\b|folio|\\bNPS\\b|\\bELSS\\b|\\bPPF\\b|" +
            "recurring\\s+deposit|fixed\\s+deposit|zerodha|groww|kuvera|indmoney|" +
            "upstox|smallcase|\\bdemat\\b|et\\s*money|paytm\\s*money)\\b",
        RegexOption.IGNORE_CASE
    )

    /** True when the text is a reminder / future / due notice, NOT a completed transaction. */
    fun isReminderOrFuture(text: String): Boolean = REMINDER_PATTERN.containsMatchIn(text)

    /** True when the text references an investment instrument or platform. */
    fun isInvestment(text: String): Boolean = INVESTMENT_PATTERN.containsMatchIn(text)
}
