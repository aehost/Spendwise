package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.BatchTransactionRequest
import com.spendwise.app.data.remote.dto.CreateCreditCardRequest
import com.spendwise.app.data.remote.dto.CreateTransactionRequest
import com.spendwise.app.data.remote.dto.SmsScanTsRequest
import com.spendwise.app.domain.usecase.ParsedBillDue
import com.spendwise.app.domain.usecase.ParseSmsUseCase
import com.spendwise.app.sms.SmsScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * One-shot worker that scans new bank SMS messages since the last run,
 * parses them, and batch-uploads them as transactions to the backend.
 *
 * Triggered immediately by SmsReceiver on every incoming bank SMS so that
 * transactions appear in the app within seconds of the debit/credit notification.
 */
@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsScanner: SmsScanner,
    private val parseSmsUseCase: ParseSmsUseCase,
    private val transactionApi: TransactionApi,
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    /**
     * When a credit-card bill-due SMS is detected:
     *   1. Try to find an existing card by last-4 digits (exact)
     *   2. Try to find an existing card by bank name (any meaningful word match)
     *   3. If no match found → auto-create the card from SMS data so nothing is lost
     *
     * All paths update/create outstanding + min_due + last_four.
     */
    private suspend fun updateCreditCardOutstanding(due: ParsedBillDue) {
        try {
            val resp  = userApi.getCreditCards()
            val cards = resp.body()?.data ?: return

            // ── Step 1: match by last-4 digits ────────────────────
            var matchedCard = due.cardLast4?.let { last4 ->
                cards.firstOrNull { it.lastFour?.takeLast(4) == last4.takeLast(4) }
            }

            // ── Step 2: match by bank name tokens ─────────────────
            // Use ALL meaningful words (≥3 chars, not generic like "bank", "card", "credit")
            if (matchedCard == null && due.bankName != null) {
                val stopWords = setOf("bank", "card", "credit", "the", "ltd", "pvt")
                val tokens = due.bankName.lowercase()
                    .split(Regex("\\s+"))
                    .filter { it.length >= 3 && it !in stopWords }
                matchedCard = cards.firstOrNull { c ->
                    val nameLower = c.name.lowercase()
                    tokens.any { token -> nameLower.contains(token) }
                }
            }

            if (matchedCard != null) {
                // ── Update existing card ───────────────────────────
                val updates = mutableMapOf<String, Any?>("outstanding" to due.outstandingAmount)
                due.minDueAmount?.let { updates["min_due"] = it }
                // Backfill last_four if it was never stored
                if (matchedCard.lastFour == null && due.cardLast4 != null) {
                    updates["last_four"] = due.cardLast4
                }
                userApi.updateCreditCard(matchedCard.id, updates)

            } else {
                // ── Auto-create card — don't lose the data ─────────
                // Build a sensible card name: "Axis Bank CC (XX9156)"
                val bankLabel = due.bankName ?: "Credit"
                val cardName  = buildString {
                    append(bankLabel)
                    append(" CC")
                    due.cardLast4?.let { append(" (XX$it)") }
                }
                // Extract due day from the due date, e.g. "2026-06-01" → 1
                val dueDay = due.dueDate?.let {
                    runCatching { LocalDate.parse(it).dayOfMonth }.getOrElse { 1 }
                } ?: 1

                userApi.createCreditCard(
                    CreateCreditCardRequest(
                        name        = cardName,
                        creditLimit = 0.0,
                        outstanding = due.outstandingAmount,
                        dueDay      = dueDay,
                        minDue      = due.minDueAmount ?: 0.0,
                        lastFour    = due.cardLast4
                    )
                )
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val WORK_NAME = "sms_sync_immediate"

        fun triggerNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, req)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val lastScanMs = tokenManager.smsScanFromMs
            val rawMessages = withContext(Dispatchers.IO) {
                smsScanner.scanSince(lastScanMs)
            }
            if (rawMessages.isEmpty()) return Result.success()

            val transactions = rawMessages.mapNotNull { raw ->
                val parsed = parseSmsUseCase.parse(raw.body) ?: run {
                    // Not a regular transaction — check if it's a CC bill-due reminder
                    // and update the credit card's outstanding balance automatically.
                    val billDue = parseSmsUseCase.parseBillDue(raw.body)
                    if (billDue != null) updateCreditCardOutstanding(billDue)
                    return@mapNotNull null
                }
                CreateTransactionRequest(
                    amount          = parsed.amount,
                    merchant        = parsed.merchant.ifBlank { "Bank Transaction" },
                    categorySlug    = parsed.categorySlug,
                    transactionDate = Instant.ofEpochMilli(raw.dateMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate().toString(),
                    isCredit        = parsed.isCredit,
                    isPending       = false,
                    smsRaw          = raw.body,
                    smsId           = raw.id
                )
            }

            if (transactions.isNotEmpty()) {
                val resp = transactionApi.batchCreate(BatchTransactionRequest(transactions))
                if (!resp.isSuccessful) return Result.retry()
            }

            // Advance the cursor so we don't re-import the same messages next run
            val newTs = rawMessages.maxOf { it.dateMs } + 1L
            tokenManager.smsScanFromMs = newTs
            try { userApi.updateSmsScanTs(SmsScanTsRequest(newTs)) } catch (_: Exception) {}

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
