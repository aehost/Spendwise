package com.spendwise.app.data.gmail

import com.spendwise.app.domain.model.EmailBillDetection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Pure-Kotlin parser that extracts billing information from Indian bank / CC statement emails.
 *
 * Supports:
 *  - HDFC, ICICI, SBI, Axis, Kotak, IndusInd, Yes Bank, IDFC, HSBC, Citibank,
 *    Standard Chartered, AmEx, RBL, AU Small Finance
 *  - Utility bill payment reminders (electricity, broadband, insurance)
 *  - Generic "total due / minimum due" pattern
 *
 * Returns null for irrelevant emails (newsletters, promotions, OTPs, etc.)
 */
object GmailEmailParser {

    // ── Regex patterns ────────────────────────────────────────────────────────

    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val TOTAL_DUE_PATTERN = Regex(
        """total\s+(?:amount\s+)?due[:\s]+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val MIN_DUE_PATTERN = Regex(
        """minimum\s+(?:amount\s+)?due[:\s]+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_DATE_SLASH = Regex(
        """due\s+(?:date|on|by)[:\s]+(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_DATE_WORD = Regex(
        """due\s+(?:date|on|by)[:\s]+(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{4})""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_DAY_RECURRING = Regex(
        """(?:due|payment)\s+(?:on|by)\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+of\s+(?:every|each)?\s*month""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_LAST4 = Regex(
        """(?:card|account)\s+(?:ending|no\.?|number)[:\s]*(?:X+)?(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    // ── Known bank names ──────────────────────────────────────────────────────

    private val KNOWN_BANKS = listOf(
        "HDFC", "ICICI", "SBI", "State Bank", "Axis", "Kotak", "IndusInd",
        "Yes Bank", "IDFC First", "IDFC", "HSBC", "Citibank", "Citi",
        "Standard Chartered", "American Express", "AmEx", "RBL", "AU Small Finance",
        "Bajaj Finserv", "OneCard", "Slice"
    )

    private val RELEVANT_SUBJECTS = listOf(
        "statement", "due", "outstanding", "minimum due", "total due",
        "payment reminder", "payment due", "credit card", "account statement",
        "billing", "invoice", "bill generated", "bill payment"
    )

    private val DATE_FORMATS = listOf(
        "dd/MM/yyyy", "d/MM/yyyy", "dd-MM-yyyy", "d-MM-yyyy",
        "dd MMM yyyy", "d MMM yyyy", "dd MMMM yyyy", "d MMMM yyyy"
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse a single email into an [EmailBillDetection], or null if the email
     * is not a billing/statement email.
     *
     * @param subject    Email subject line
     * @param body       Email body or snippet (plain text)
     * @param emailDate  Date the email was received (any readable format; stored as-is)
     */
    fun parse(subject: String, body: String, emailDate: String): EmailBillDetection? {
        if (!isRelevantEmail(subject)) return null

        val combined = "$subject\n$body"
        val bank = detectBank(combined)
        val card = CARD_LAST4.find(combined)?.groupValues?.get(1)

        val billName = buildBillName(subject, bank, card)

        // Amount: prefer total due over minimum due over first currency match
        val (amount, isMinDue) = extractAmount(combined)

        val dueDate = extractDueDate(combined)
        val dueDayOfMonth = DUE_DAY_RECURRING.find(combined)?.groupValues?.get(1)?.toIntOrNull()
            ?: dueDate?.let {
                try { LocalDate.parse(it).dayOfMonth } catch (_: DateTimeParseException) { null }
            }

        return EmailBillDetection(
            billName       = billName,
            amount         = amount,
            dueDate        = dueDate,
            dueDayOfMonth  = dueDayOfMonth,
            statementDate  = emailDate,
            cardOrAccount  = card?.let { "xxxx$it" },
            bankName       = bank,
            emailSubject   = subject,
            emailDate      = emailDate,
            isMinimumDue   = isMinDue
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun isRelevantEmail(subject: String): Boolean =
        RELEVANT_SUBJECTS.any { subject.contains(it, ignoreCase = true) }

    private fun detectBank(text: String): String? =
        KNOWN_BANKS.firstOrNull { text.contains(it, ignoreCase = true) }

    private fun buildBillName(subject: String, bank: String?, card: String?): String {
        if (bank != null) {
            return if (card != null) "$bank CC xxxx$card" else "$bank Credit Card"
        }
        // Fallback: strip noise and use cleaned subject
        return subject
            .replace(Regex("[^A-Za-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
            .trim()
            .ifEmpty { "Email Bill" }
    }

    private fun extractAmount(text: String): Pair<Double?, Boolean> {
        TOTAL_DUE_PATTERN.find(text)?.groupValues?.get(1)
            ?.let { return Pair(parseAmount(it), false) }
        MIN_DUE_PATTERN.find(text)?.groupValues?.get(1)
            ?.let { return Pair(parseAmount(it), true) }
        AMOUNT_PATTERN.find(text)?.groupValues?.get(1)
            ?.let { return Pair(parseAmount(it), false) }
        return Pair(null, false)
    }

    private fun parseAmount(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()

    private fun extractDueDate(text: String): String? {
        val raw = DUE_DATE_SLASH.find(text)?.groupValues?.get(1)
            ?: DUE_DATE_WORD.find(text)?.groupValues?.get(1)
            ?: return null

        for (fmt in DATE_FORMATS) {
            try {
                val date = LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(fmt))
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {}
        }
        return null
    }
}
