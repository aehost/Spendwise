package com.spendwise.app.core

import com.spendwise.app.BuildConfig

object Constants {
    const val BASE_URL         = BuildConfig.BASE_URL
    const val TOKEN_KEY        = "access_token"
    const val REFRESH_KEY      = "refresh_token"
    const val SETUP_DONE_KEY   = "setup_done"
    const val SMS_SCAN_FROM_MS = "sms_scan_from_ms"
    const val USER_ID_KEY      = "user_id"
    const val USER_EMAIL_KEY   = "user_email"
    const val USER_NAME_KEY    = "user_name"
    const val DB_NAME          = "spendwise.db"

    val CATEGORY_ICONS = mapOf(
        "food" to "🍽️", "fuel" to "⛽", "shopping" to "🛍️",
        "bills" to "💡", "emi" to "🏦", "entertainment" to "🎬",
        "health" to "💊", "travel" to "✈️", "family" to "👨‍👩‍👧",
        "investment" to "📈", "income" to "💰", "savings" to "🏆",
        "waste" to "🗑️", "other" to "📦"
    )

    val CATEGORY_LABELS = mapOf(
        "food" to "Food & Dining", "fuel" to "Fuel",
        "shopping" to "Shopping", "bills" to "Bills & Utilities",
        "emi" to "EMI / Loan", "entertainment" to "Entertainment",
        "health" to "Health", "travel" to "Travel",
        "family" to "Family / Friends", "investment" to "Investment / SIP",
        "income" to "Income / Salary", "savings" to "Savings",
        "waste" to "Wasteful", "other" to "Other"
    )

    val CATEGORIES = CATEGORY_LABELS.keys.toList()

    val BANK_COLORS = listOf(
        "#6C63FF", "#EC4899", "#10B981", "#F59E0B",
        "#3B82F6", "#8B5CF6", "#EF4444", "#06B6D4"
    )

    val BANK_PATTERN = Regex(
        "debited|debit|credited|credit|deducted|" +
        "Rs\\.?\\s*[\\d,]+|INR\\s*[\\d,]+|₹\\s*[\\d,]+|" +
        "Avail\\s*Bal|Avl\\s*Bal|available\\s*balance|" +
        "transaction|payment|spent|purchase|EMI|UPI|A/C|" +
        "account|withdrawn|deposit|refund",
        RegexOption.IGNORE_CASE
    )
}
