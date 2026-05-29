package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spendwise.app.data.gmail.BankEmailParser
import com.spendwise.app.data.gmail.GmailImapClient
import com.spendwise.app.data.local.preferences.GmailImapAccount
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.TransactionApi
import com.spendwise.app.data.remote.dto.BatchTransactionRequest
import com.spendwise.app.data.remote.dto.CreateTransactionRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class GmailImapWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenManager: TokenManager,
    private val transactionApi: TransactionApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "gmail_imap_sync"
        private val gson = Gson()
        private val listType = object : TypeToken<List<GmailImapAccount>>() {}.type

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<GmailImapWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun triggerNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<GmailImapWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_now", ExistingWorkPolicy.REPLACE, req
            )
        }

        fun readAccounts(tokenManager: TokenManager): List<GmailImapAccount> {
            return try { gson.fromJson(tokenManager.gmailImapAccountsJson, listType) ?: emptyList() }
            catch (_: Exception) { emptyList() }
        }

        fun writeAccounts(tokenManager: TokenManager, accounts: List<GmailImapAccount>) {
            tokenManager.gmailImapAccountsJson = gson.toJson(accounts)
        }
    }

    override suspend fun doWork(): Result {
        // Cap retries at 5 to avoid infinite retry loops for permanent failures (bad password, etc.)
        if (runAttemptCount >= 5) return Result.success()

        val accounts = readAccounts(tokenManager).filter { it.isActive }
        if (accounts.isEmpty()) return Result.success()

        var anyError = false
        val updatedAccounts = accounts.map { account ->
            try {
                val emails = withContext(Dispatchers.IO) {
                    GmailImapClient.fetchBankEmailsSince(account.email, account.appPassword, account.lastSyncMs)
                }
                if (emails.isEmpty()) return@map account.copy(lastSyncMs = System.currentTimeMillis())

                val transactions = emails.mapNotNull { email ->
                    val parsed = BankEmailParser.parse(email) ?: return@mapNotNull null
                    CreateTransactionRequest(
                        amount          = parsed.amount,
                        merchant        = parsed.merchant,
                        categorySlug    = parsed.categorySlug,
                        transactionDate = parsed.transactionDate,
                        isCredit        = parsed.isCredit,
                        isPending       = false,
                        smsRaw          = "${email.subject} | ${email.body.take(200)}",
                        smsId           = "email_${parsed.emailId}"
                    )
                }.distinctBy { it.smsId }

                if (transactions.isNotEmpty()) {
                    val resp = transactionApi.batchCreate(BatchTransactionRequest(transactions))
                    if (!resp.isSuccessful) { anyError = true; return@map account }
                }
                account.copy(lastSyncMs = System.currentTimeMillis())
            } catch (_: Exception) {
                anyError = true
                account
            }
        }

        // Merge: updated active accounts + any accounts that were not in this sync
        val updatedEmails = updatedAccounts.map { it.email }.toSet()
        val allStored = readAccounts(tokenManager)
        val unchanged = allStored.filter { it.email !in updatedEmails }
        writeAccounts(tokenManager, updatedAccounts + unchanged)
        return if (anyError) Result.retry() else Result.success()
    }
}
