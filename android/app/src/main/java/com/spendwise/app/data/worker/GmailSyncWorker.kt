package com.spendwise.app.data.worker

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.spendwise.app.data.gmail.GmailAuthManager
import com.spendwise.app.data.gmail.GmailEmailParser
import com.spendwise.app.data.remote.api.GmailApi
import com.spendwise.app.data.remote.api.IntelligenceApi
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.dto.AutoAddBillEntry
import com.spendwise.app.data.remote.dto.AutoAddBillsRequest
import com.spendwise.app.data.remote.dto.CreateTransactionRequest
import com.spendwise.app.domain.model.EmailBillDetection
import com.spendwise.app.domain.model.EmailType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Daily background job that syncs ALL connected Gmail accounts:
 *
 *  1. Loads connected Gmail accounts from the backend
 *  2. For each account, obtains a fresh OAuth access token via [GoogleAuthUtil.getToken]
 *     (uses Android Account Manager — works for any Google account on the device)
 *  3. Queries Gmail REST API for financial emails (last 30 days)
 *  4. Parses emails with [GmailEmailParser]:
 *       BILL / CC_PAYMENT  → auto-adds / updates recurring bills
 *       SALARY_CREDIT      → creates a credit transaction tagged "salary"
 *       IMPS_CREDIT        → creates a credit transaction tagged "transfer"
 *       NEFT_CREDIT        → creates a credit transaction tagged "transfer"
 *       UPI_CREDIT         → creates a credit transaction tagged "transfer"
 *  5. Updates per-account last-synced timestamp
 *
 * NOTE: [GoogleAuthUtil.getToken] is a blocking call executed on WorkManager's
 * background thread — do NOT call from the main thread.
 */
@HiltWorker
class GmailSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val gmailApi: GmailApi,
    private val intelligenceApi: IntelligenceApi,
    private val transactionApi: TransactionApi,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "gmail_sync_daily"
        private const val TAG = "GmailSyncWorker"
        private const val GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
        private const val OAUTH_SCOPE = "oauth2:${GmailAuthManager.GMAIL_SCOPE}"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<GmailSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        val accountsResp = try { gmailApi.getAccounts() } catch (e: Exception) {
            Log.e(TAG, "Failed to load Gmail accounts", e); return Result.retry()
        }
        val accounts = accountsResp.body()?.data?.accounts
            ?.filter { it.isActive }
            ?: run { Log.d(TAG, "No Gmail accounts connected"); return Result.success() }

        var anySuccess = false
        for (account in accounts) {
            val gmailEmail = account.gmailEmail
            val accountId  = account.id
            try {
                syncAccount(gmailEmail, accountId)
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for $gmailEmail", e)
            }
        }
        return if (anySuccess || accounts.isEmpty()) Result.success() else Result.retry()
    }

    private suspend fun syncAccount(gmailEmail: String, accountId: String) {
        // ── Get a fresh OAuth access token via Android Account Manager ───────
        // GoogleAuthUtil.getToken() is the correct way to get a Gmail API token
        // on Android. It is a blocking call but we're on a background thread.
        val token = try {
            GoogleAuthUtil.getToken(
                appContext,
                Account(gmailEmail, "com.google"),
                OAUTH_SCOPE
            )
        } catch (e: UserRecoverableAuthException) {
            // User needs to re-grant permission — we can't do this from background
            Log.w(TAG, "User action required for $gmailEmail: ${e.message}")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Could not get token for $gmailEmail: ${e.message}")
            return
        }

        val detected = fetchAndParseEmails(token)
        Log.d(TAG, "Gmail sync ($gmailEmail): ${detected.size} financial emails")
        if (detected.isEmpty()) {
            gmailApi.updateAccountSyncTimestamp(accountId)
            return
        }

        // ── Auto-add recurring bills (BILL + CC_PAYMENT types) ────────────────
        val billEntries = detected.mapNotNull { det ->
            if (det.emailType != EmailType.BILL) return@mapNotNull null
            val amount = det.amount ?: return@mapNotNull null
            val dueDay = det.dueDayOfMonth ?: return@mapNotNull null
            if (amount <= 0 || dueDay !in 1..31) return@mapNotNull null
            AutoAddBillEntry(name = det.billName, icon = "📧", amount = amount, dueDay = dueDay)
        }
        if (billEntries.isNotEmpty()) {
            try {
                val r = intelligenceApi.autoAddBills(AutoAddBillsRequest(billEntries))
                Log.d(TAG, "Auto-added ${r.body()?.data?.added ?: 0} bill(s)")
            } catch (e: Exception) { Log.w(TAG, "autoAddBills failed", e) }
        }

        // ── Create credit transactions ─────────────────────────────────────────
        val creditDetections = detected.filter { it.emailType in CREDIT_TYPES && it.amount != null && it.amount > 0 }
        for (det in creditDetections) {
            val (categorySlug, merchant, note) = classifyCredit(det)
            try {
                transactionApi.createTransaction(
                    CreateTransactionRequest(
                        amount          = det.amount!!,
                        merchant        = merchant,
                        categorySlug    = categorySlug,
                        transactionDate = parseDateOrToday(det.emailDate),
                        note            = note,
                        isCredit        = true,
                        isPending       = false
                    )
                )
                Log.d(TAG, "Created ${det.emailType.name} transaction: ₹${det.amount} from $merchant")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create transaction for ${det.billName}", e)
            }
        }

        // ── CC payment tracking (debit from user's perspective) ────────────────
        val ccPayments = detected.filter { it.emailType == EmailType.CC_PAYMENT && it.amount != null && it.amount > 0 }
        for (det in ccPayments) {
            try {
                transactionApi.createTransaction(
                    CreateTransactionRequest(
                        amount          = det.amount!!,
                        merchant        = det.bankName ?: det.billName,
                        categorySlug    = "emi",
                        transactionDate = parseDateOrToday(det.emailDate),
                        note            = "CC payment — ${det.emailSubject.take(60)}",
                        isCredit        = false,
                        isPending       = false
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create CC payment transaction", e)
            }
        }

        gmailApi.updateAccountSyncTimestamp(accountId)
    }

    // ── Gmail REST helpers ────────────────────────────────────────────────────

    private fun fetchAndParseEmails(token: String): List<EmailBillDetection> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val query = URLEncoder.encode(
            "subject:(statement OR \"payment due\" OR \"minimum due\" OR \"total due\" OR " +
            "bill OR outstanding OR \"salary credited\" OR \"salary credit\" OR " +
            "\"IMPS credit\" OR \"NEFT credit\" OR \"UPI credit\" OR \"money received\" OR " +
            "\"payment received\" OR \"amount credited\" OR \"credit alert\") newer_than:30d",
            "UTF-8"
        )
        val listBody = get("$GMAIL_BASE/messages?q=$query&maxResults=75", token) ?: return emptyList()
        val messages = JSONObject(listBody).optJSONArray("messages") ?: return emptyList()

        val results = mutableListOf<EmailBillDetection>()
        for (i in 0 until minOf(messages.length(), 50)) {
            val msgId  = messages.getJSONObject(i).getString("id")
            val msgUrl = "$GMAIL_BASE/messages/$msgId?format=metadata&metadataHeaders=Subject&metadataHeaders=Date"
            val msgBody = get(msgUrl, token) ?: continue
            try {
                val msgJson = JSONObject(msgBody)
                val headers = msgJson.getJSONObject("payload").getJSONArray("headers")
                var subject = ""; var date = today
                for (j in 0 until headers.length()) {
                    val h = headers.getJSONObject(j)
                    when (h.getString("name")) {
                        "Subject" -> subject = h.getString("value")
                        "Date"    -> date    = h.getString("value")
                    }
                }
                val snippet = msgJson.optString("snippet", "")
                GmailEmailParser.parse(subject, snippet, date)?.let { results += it }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message $msgId", e)
            }
        }
        return results
    }

    private fun get(url: String, token: String): String? = try {
        val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").build()
        okHttpClient.newCall(req).execute().use { r -> if (!r.isSuccessful) null else r.body?.string() }
    } catch (e: Exception) { Log.w(TAG, "HTTP GET failed: $url", e); null }

    // ── Credit classification ─────────────────────────────────────────────────

    private data class TxMeta(val categorySlug: String, val merchant: String, val note: String)

    private fun classifyCredit(det: EmailBillDetection): TxMeta {
        val bank   = det.bankName ?: "Bank"
        val sender = det.senderName
        return when (det.emailType) {
            EmailType.SALARY_CREDIT -> TxMeta(
                categorySlug = "salary",
                merchant     = "$bank Salary",
                note         = "Salary credit — ${det.emailSubject.take(60)}"
            )
            EmailType.IMPS_CREDIT  -> TxMeta(
                categorySlug = "transfer",
                merchant     = sender ?: "$bank IMPS",
                note         = "IMPS credit — ${det.emailSubject.take(60)}"
            )
            EmailType.NEFT_CREDIT  -> TxMeta(
                categorySlug = "transfer",
                merchant     = sender ?: "$bank NEFT",
                note         = "NEFT credit — ${det.emailSubject.take(60)}"
            )
            EmailType.UPI_CREDIT   -> TxMeta(
                categorySlug = "transfer",
                merchant     = sender ?: "UPI Credit",
                note         = "UPI credit — ${det.emailSubject.take(60)}"
            )
            else                   -> TxMeta("income", det.billName, det.emailSubject.take(60))
        }
    }

    private fun parseDateOrToday(raw: String): String {
        return try {
            // EmailDate may be in RFC 2822 or ISO format
            LocalDate.parse(raw.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    companion object {
        private val CREDIT_TYPES = setOf(
            EmailType.SALARY_CREDIT, EmailType.IMPS_CREDIT,
            EmailType.NEFT_CREDIT, EmailType.UPI_CREDIT
        )
    }
}
