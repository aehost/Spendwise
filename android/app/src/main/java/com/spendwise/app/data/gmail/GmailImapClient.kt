package com.spendwise.app.data.gmail

import android.util.Log
import java.util.Date
import java.util.Properties
import javax.mail.Folder
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

data class RawBankEmail(
    val subject: String,
    val body: String,
    val from: String,
    val receivedMs: Long,
    val messageId: String
)

object GmailImapClient {
    private const val TAG = "GmailImapClient"
    private const val IMAP_HOST = "imap.gmail.com"
    private const val IMAP_PORT = 993

    // Known Indian bank email senders.
    // BUG FIX: Use "@keyword" prefix for short/ambiguous names (kotak, hsbc, pnb)
    // so "alerts@notakotak.io" does NOT match "@kotak".
    // BUG FIX: Changed "sc.com" → "@sc.com" (prevents "disc.com" false positive).
    val BANK_SENDER_KEYWORDS = listOf(
        "hdfcbank", "icicibank", "axisbank", "sbicard", "sbi.co.in",
        "@kotak", "yesbank", "indusind", "federalbank", "paytmbank",
        "canarabank", "unionbankofindia", "@pnb", "bobcard", "idfcfirstbank",
        "rblbank", "@sc.com", "@hsbc", "citibank",
        // Additional banks often missed
        "amex", "americanexpress", "standardchartered", "aubank", "idbibank"
    )

    fun fetchBankEmailsSince(email: String, appPassword: String, sinceMs: Long): List<RawBankEmail> {
        val results = mutableListOf<RawBankEmail>()
        var store: javax.mail.Store? = null
        var inbox: Folder? = null
        try {
            val props = Properties().apply {
                put("mail.imaps.host", IMAP_HOST)
                put("mail.imaps.port", IMAP_PORT.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "15000")
                put("mail.imaps.timeout", "15000")
                put("mail.store.protocol", "imaps")
            }
            val session = Session.getInstance(props)
            store = session.getStore("imaps")
            store.connect(IMAP_HOST, email, appPassword)

            inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

            val sinceDate = Date(sinceMs.coerceAtLeast(System.currentTimeMillis() - 7 * 24 * 3600_000L))
            val dateTerm = ReceivedDateTerm(ComparisonTerm.GE, sinceDate)

            // Search by date first, filter by sender in code (OR-search over 15+ senders is slow on IMAP).
            // Cap at 300 to prevent OOM on inboxes with thousands of messages.
            val rawMessages = try { inbox.search(dateTerm) } catch (_: Exception) { inbox.messages }
            val messages = rawMessages.takeLast(300)

            for (msg in messages) {
                try {
                    val from = (msg.from?.firstOrNull() as? InternetAddress)?.address?.lowercase() ?: continue
                    val isBankEmail = BANK_SENDER_KEYWORDS.any { keyword -> from.contains(keyword) }
                    if (!isBankEmail) continue

                    val subject = msg.subject ?: ""
                    val body = extractBody(msg)
                    val receivedMs = msg.receivedDate?.time ?: System.currentTimeMillis()
                    val msgId = try {
                        (msg as? com.sun.mail.imap.IMAPMessage)?.messageID
                    } catch (_: Exception) { null }
                        ?: "${from}_${receivedMs}"

                    results.add(RawBankEmail(subject, body, from, receivedMs, msgId))
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading message: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "IMAP connection failed for $email: ${e.message}")
            throw e
        } finally {
            try { inbox?.close(false) } catch (_: Exception) {}
            try { store?.close() } catch (_: Exception) {}
        }
        return results
    }

    private fun extractBody(msg: javax.mail.Message): String {
        return try {
            when {
                msg.isMimeType("text/plain") -> msg.content as? String ?: ""
                msg.isMimeType("text/html") -> (msg.content as? String)?.replace(Regex("<[^>]+>"), " ") ?: ""
                msg.isMimeType("multipart/*") -> extractMultipart(msg.content as javax.mail.Multipart)
                else -> ""
            }
        } catch (_: Exception) { "" }
    }

    private fun extractMultipart(mp: javax.mail.Multipart): String {
        val sb = StringBuilder()
        for (i in 0 until mp.count) {
            try {
                val part = mp.getBodyPart(i)
                when {
                    part.isMimeType("text/plain") -> sb.append(part.content as? String ?: "")
                    part.isMimeType("text/html") -> sb.append((part.content as? String)?.replace(Regex("<[^>]+>"), " ") ?: "")
                    part.isMimeType("multipart/*") -> sb.append(extractMultipart(part.content as javax.mail.Multipart))
                }
            } catch (_: Exception) {}
        }
        return sb.toString()
    }
}
