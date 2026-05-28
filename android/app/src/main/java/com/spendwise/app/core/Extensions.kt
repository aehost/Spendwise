package com.spendwise.app.core

import org.json.JSONObject
import retrofit2.Response
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Reads the error detail from a failed Retrofit [Response].
 *
 * For non-2xx responses Retrofit sets [Response.body] to null; the actual
 * server payload is in [Response.errorBody]. This extension reads that body,
 * parses the `"error"` field from JSON (matching our ApiResponse envelope),
 * and falls back to "HTTP <code>" so callers always get a meaningful string.
 */
fun <T> Response<T>.apiErrorMessage(): String =
    try {
        val raw = errorBody()?.string()
        if (!raw.isNullOrBlank()) {
            JSONObject(raw).optString("error", "").ifBlank { "Server error (${code()})" }
        } else "Server error (${code()})"
    } catch (_: Exception) {
        "Server error (${code()})"
    }

fun Double.formatCurrency(currencyCode: String = "INR"): String {
    return when (currencyCode) {
        "INR" -> formatINR(this)
        else -> {
            val fmt = NumberFormat.getCurrencyInstance(Locale.US)
            fmt.format(this)
        }
    }
}

private fun formatINR(amount: Double): String {
    val abs = Math.abs(amount)
    val prefix = if (amount < 0) "-₹" else "₹"
    return when {
        abs >= 10_00_00_000 -> "$prefix${String.format("%.1f", abs / 10_00_00_000)}Cr"
        abs >= 1_00_000     -> "$prefix${String.format("%.1f", abs / 1_00_000)}L"
        abs >= 1_000        -> "$prefix${String.format("%.0f", abs)}"
        else                -> "$prefix${String.format("%.0f", abs)}"
    }
}

fun Double.formatFull(currencyCode: String = "INR"): String {
    val fmt = NumberFormat.getInstance(Locale("en", "IN"))
    fmt.maximumFractionDigits = 2
    fmt.minimumFractionDigits = 0
    return "₹${fmt.format(this)}"
}

fun String.toLocalDate(): LocalDate? = try {
    LocalDate.parse(this, DateTimeFormatter.ISO_DATE)
} catch (e: Exception) { null }

fun LocalDate.toDisplayString(): String =
    format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

fun LocalDate.toApiString(): String =
    format(DateTimeFormatter.ISO_DATE)

fun today(): LocalDate = LocalDate.now()
fun todayString(): String = today().toApiString()

/**
 * Converts an ISO date string ("2026-05-28") to a human-friendly label:
 *   "Today", "Yesterday", "28 May", or "28 May 2026" for older dates.
 */
fun String.toFriendlyDate(): String = try {
    val date  = LocalDate.parse(this)
    val now   = LocalDate.now()
    when {
        date == now               -> "Today"
        date == now.minusDays(1)  -> "Yesterday"
        date.year == now.year     ->
            "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
        else                      ->
            "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.year}"
    }
} catch (_: Exception) { this }
