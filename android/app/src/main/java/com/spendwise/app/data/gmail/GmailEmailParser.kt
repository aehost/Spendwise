package com.spendwise.app.data.gmail

import com.spendwise.app.domain.model.EmailBillDetection
import com.spendwise.app.domain.model.EmailType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Pure-Kotlin parser for Indian bank / CC statement emails.
 *
 * Detects and classifies:
 *  - CC statements / recurring bills           → EmailType.BILL
 *  - Salary credit alerts                      → EmailType.SALARY_CREDIT
 *  - IMPS credit alerts                        → EmailType.IMPS_CREDIT
 *  - NEFT credit alerts                        → EmailType.NEFT_CREDIT
 *  - UPI / BHIM credit alerts                  → EmailType.UPI_CREDIT
 *  - Credit card payment received confirmations → EmailType.CC_PAYMENT
 *
 * Returns null for irrelevant emails (OTPs, newsletters, promotions, etc.).
 *
 * Supports: HDFC, ICICI, SBI, Axis, Kotak, IndusInd, Yes Bank, IDFC, HSBC,
 * Citibank, Standard Chartered, AmEx, RBL, AU Small Finance, Bajaj, Paytm,
 * PhonePe, GPay.
 */
object GmailEmailParser {

    // ── Amount patterns ───────────────────────────────────────────────────────

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
    /** "credited with Rs X" / "credit of Rs X" / "amount Rs X credited" */
    private val CREDIT_AMOUNT_PATTERN = Regex(
        """(?:credited(?:\s+with)?|credit\s+of|amount\s+(?:of\s+)?(?:Rs\.?|INR|₹)?)[:\s]+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    /** "received Rs X" / "Rs X received" */
    private val RECEIVED_AMOUNT_PATTERN = Regex(
        """(?:received\s+(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?))|(?:(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)[\s\w]*received)""",
        RegexOption.IGNORE_CASE
    )
    /** "payment of Rs X" */
    private val PAYMENT_AMOUNT_PATTERN = Regex(
        """payment\s+of\s+(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // ── Date patterns ─────────────────────────────────────────────────────────

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
        """(?:card|account)\s+(?:ending|no\.?|number)[:\s]*(?:X+|x+)?(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )
    /** "from XYZ" in credit alerts */
    private val SENDER_NAME_PATTERN = Regex(
        """(?:from|by|sender)[:\s]+([A-Z][A-Za-z\s]{2,30})""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_FORMATS = listOf(
        "dd/MM/yyyy", "d/MM/yyyy", "dd-MM-yyyy", "d-MM-yyyy",
        "dd MMM yyyy", "d MMM yyyy", "dd MMMM yyyy", "d MMMM yyyy"
    )

    // ── Bank names ────────────────────────────────────────────────────────────

    private val KNOWN_BANKS = listOf(
        "HDFC", "ICICI", "SBI", "State Bank", "Axis", "Kotak", "IndusInd",
        "Yes Bank", "IDFC First", "IDFC", "HSBC", "Citibank", "Citi",
        "Standard Chartered", "American Express", "AmEx", "RBL", "AU Small Finance",
        "Bajaj Finserv", "OneCard", "Slice", "Paytm", "PhonePe", "GPay", "Google Pay"
    )

    // ── Subject keyword sets per type ─────────────────────────────────────────

    private val SALARY_SUBJECTS = listOf(
        "salary credited", "salary credit", "salary has been", "salary deposited",
        "salary received", "payroll credit", "salary alert"
    )
    private val IMPS_CREDIT_SUBJECTS = listOf(
        "imps credit", "imps received", "money received via imps", "imps inward",
        "credit via imps", "imps cr alert", "imps transaction"
    )
    private val NEFT_CREDIT_SUBJECTS = listOf(
        "neft credit", "neft received", "money received via neft", "neft inward",
        "credit via neft", "neft cr alert", "neft transaction"
    )
    private val UPI_CREDIT_SUBJECTS = listOf(
        "upi credit", "money received", "received via upi", "upi received",
        "upi inward", "bhim credit", "upi payment received", "you received"
    )
    private val CC_PAYMENT_SUBJECTS = listOf(
        "payment received", "payment confirmation", "payment successful",
        "cc payment received", "credit card payment received", "payment credited",
        "your payment of", "payment of rs", "payment acknowledged"
    )
    private val BILL_SUBJECTS = listOf(
        "statement", "due", "outstanding", "minimum due", "total due",
        "payment reminder", "payment due", "credit card", "account statement",
        "billing", "invoice", "bill generated", "bill payment"
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parse a single email into an [EmailBillDetection], or null if the email
     * is not a financial email worth tracking.
     *
     * @param subject    Email subject line
     * @param body       Email body or snippet (plain text)
     * @param emailDate  Date the email was received
     */
    fun parse(subject: String, body: String, emailDate: String): EmailBillDetection? {
        val combined = "$subject\n$body"
        val type     = classifyEmail(subject) ?: return null
        val bank     = detectBank(combined)
        val card     = CARD_LAST4.find(combined)?.groupValues?.get(1)

        return when (type) {
            EmailType.SALARY_CREDIT -> parseSalaryCredit(combined, bank, emailDate, subject)
            EmailType.IMPS_CREDIT   -> parseCreditAlert(combined, bank, emailDate, subject, "IMPS", type)
            EmailType.NEFT_CREDIT   -> parseCreditAlert(combined, bank, emailDate, subject, "NEFT", type)
            EmailType.UPI_CREDIT    -> parseCreditAlert(combined, bank, emailDate, subject, "UPI", type)
            EmailType.CC_PAYMENT    -> parseCcPayment(combined, bank, card, emailDate, subject)
            EmailType.BILL          -> parseBill(combined, bank, card, emailDate, subject)
            else                    -> null
        }
    }

    // ── Classification ────────────────────────────────────────────────────────

    private fun classifyEmail(subject: String): EmailType? {
        val s = subject.lowercase()
        return when {
            SALARY_SUBJECTS.any     { s.contains(it) } -> EmailType.SALARY_CREDIT
            IMPS_CREDIT_SUBJECTS.any{ s.contains(it) } -> EmailType.IMPS_CREDIT
            NEFT_CREDIT_SUBJECTS.any{ s.contains(it) } -> EmailType.NEFT_CREDIT
            UPI_CREDIT_SUBJECTS.any { s.contains(it) } -> EmailType.UPI_CREDIT
            CC_PAYMENT_SUBJECTS.any { s.contains(it) } -> EmailType.CC_PAYMENT
            BILL_SUBJECTS.any       { s.contains(it) } -> EmailType.BILL
            else                                        -> null
        }
    }

    // ── Per-type parsers ──────────────────────────────────────────────────────

    private fun parseSalaryCredit(text: String, bank: String?, date: String, subject: String): EmailBillDetection {
        val amount = extractCreditAmount(text)
        val sender = SENDER_NAME_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        return EmailBillDetection(
            billName      = if (bank != null) "$bank Salary Credit" else "Salary Credit",
            amount        = amount,
            dueDate       = null,
            dueDayOfMonth = null,
            statementDate = date,
            cardOrAccount = null,
            bankName      = bank,
            emailSubject  = subject,
            emailDate     = date,
            isMinimumDue  = false,
            emailType     = EmailType.SALARY_CREDIT,
            senderName    = sender
        )
    }

    private fun parseCreditAlert(text: String, bank: String?, date: String, subject: String, method: String, type: EmailType): EmailBillDetection {
        val amount = extractCreditAmount(text)
        val sender = SENDER_NAME_PATTERN.find(text)?.groupValues?.get(1)?.trim()
        val label  = if (bank != null) "$bank $method Credit" else "$method Credit"
        return EmailBillDetection(
            billName      = label,
            amount        = amount,
            dueDate       = null,
            dueDayOfMonth = null,
            statementDate = date,
            cardOrAccount = null,
            bankName      = bank,
            emailSubject  = subject,
            emailDate     = date,
            isMinimumDue  = false,
            emailType     = type,
            senderName    = sender
        )
    }

    private fun parseCcPayment(text: String, bank: String?, card: String?, date: String, subject: String): EmailBillDetection {
        val amount = PAYMENT_AMOUNT_PATTERN.find(text)?.groupValues?.get(1)?.let { parseAmount(it) }
            ?: extractCreditAmount(text)
        val label  = buildBillName(subject, bank, card).let { if (it.isBlank()) "CC Payment" else it }
        return EmailBillDetection(
            billName      = label,
            amount        = amount,
            dueDate       = null,
            dueDayOfMonth = null,
            statementDate = date,
            cardOrAccount = card?.let { "xxxx$it" },
            bankName      = bank,
            emailSubject  = subject,
            emailDate     = date,
            isMinimumDue  = false,
            emailType     = EmailType.CC_PAYMENT
        )
    }

    private fun parseBill(text: String, bank: String?, card: String?, date: String, subject: String): EmailBillDetection {
        val billName = buildBillName(subject, bank, card)
        val (amount, isMinDue) = extractBillAmount(text)
        val dueDate = extractDueDate(text)
        val dueDayOfMonth = DUE_DAY_RECURRING.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: dueDate?.let { try { LocalDate.parse(it).dayOfMonth } catch (_: DateTimeParseException) { null } }
        return EmailBillDetection(
            billName      = billName,
            amount        = amount,
            dueDate       = dueDate,
            dueDayOfMonth = dueDayOfMonth,
            statementDate = date,
            cardOrAccount = card?.let { "xxxx$it" },
            bankName      = bank,
            emailSubject  = subject,
            emailDate     = date,
            isMinimumDue  = isMinDue,
            emailType     = EmailType.BILL
        )
    }

    // ── Amount extraction helpers ─────────────────────────────────────────────

    private fun extractCreditAmount(text: String): Double? =
        CREDIT_AMOUNT_PATTERN.find(text)?.groupValues?.get(1)?.let { parseAmount(it) }
        ?: RECEIVED_AMOUNT_PATTERN.find(text)?.let { m ->
            (m.groupValues.getOrNull(1) ?: m.groupValues.getOrNull(2))?.let { parseAmount(it) }
        }
        ?: AMOUNT_PATTERN.find(text)?.groupValues?.get(1)?.let { parseAmount(it) }

    private fun extractBillAmount(text: String): Pair<Double?, Boolean> {
        TOTAL_DUE_PATTERN.find(text)?.groupValues?.get(1)?.let { return Pair(parseAmount(it), false) }
        MIN_DUE_PATTERN.find(text)?.groupValues?.get(1)?.let { return Pair(parseAmount(it), true) }
        AMOUNT_PATTERN.find(text)?.groupValues?.get(1)?.let { return Pair(parseAmount(it), false) }
        return Pair(null, false)
    }

    private fun parseAmount(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()

    // ── Other helpers ─────────────────────────────────────────────────────────

    private fun detectBank(text: String): String? =
        KNOWN_BANKS.firstOrNull { text.contains(it, ignoreCase = true) }

    private fun buildBillName(subject: String, bank: String?, card: String?): String {
        if (bank != null) {
            return if (card != null) "$bank CC xxxx$card" else "$bank Credit Card"
        }
        return subject
            .replace(Regex("[^A-Za-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
            .trim()
            .ifEmpty { "Email Bill" }
    }

    private fun extractDueDate(text: String): String? {
        val raw = DUE_DATE_SLASH.find(text)?.groupValues?.get(1)
            ?: DUE_DATE_WORD.find(text)?.groupValues?.get(1)
            ?: return null
        for (fmt in DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(fmt))
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {}
        }
        return null
    }
}
