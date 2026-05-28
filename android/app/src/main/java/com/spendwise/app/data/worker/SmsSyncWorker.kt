package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.data.remote.dto.BatchTransactionRequest
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
     * When a credit-card bill-due SMS is detected, find the matching credit card
     * by last-4 digits and update its outstanding balance and minimum due.
     */
    private suspend fun updateCreditCardOutstanding(due: ParsedBillDue) {
        try {
            val last4 = due.cardLast4 ?: return
            val resp  = userApi.getCreditCards()
            val card  = resp.body()?.data?.firstOrNull { card ->
                card.lastFour?.takeLast(4) == last4.takeLast(4)
            } ?: return

            val updates = mutableMapOf<String, Any?>(
                "outstanding" to due.outstandingAmount
            )
            due.minDueAmount?.let { updates["min_due"] = it }
            userApi.updateCreditCard(card.id, updates)
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
