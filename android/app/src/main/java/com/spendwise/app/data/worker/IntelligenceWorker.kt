package com.spendwise.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.remote.api.IntelligenceApi
import com.spendwise.app.data.remote.dto.AutoAddBillEntry
import com.spendwise.app.data.remote.dto.AutoAddBillsRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Weekly background job that:
 *  1. Fetches the intelligence report from the backend
 *  2. Auto-creates any high-confidence (≥80%) recurring bills that aren't tracked yet
 *  3. Shows a notification summary if new bills were found
 *
 * Runs once per week; requires network connectivity.
 */
@HiltWorker
class IntelligenceWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val intelligenceApi: IntelligenceApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME        = "intelligence_weekly"
        const val NOTIF_CHANNEL_ID = "intelligence"
        const val NOTIF_ID         = 1001
        private const val TAG      = "IntelligenceWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<IntelligenceWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val resp = intelligenceApi.getIntelligenceReport()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Intelligence API returned ${resp.code()}")
                return Result.retry()
            }

            val report = resp.body()?.data ?: return Result.success()

            // Auto-add high-confidence recurring bills detected from transaction history
            val candidates = report.recurringBills
                .filter { it.confidence >= 80 && it.dueDayEstimate != null }

            var added = 0
            if (candidates.isNotEmpty()) {
                val entries = candidates.map { s ->
                    AutoAddBillEntry(
                        name   = s.merchant,
                        icon   = categoryIcon(s.categorySlug),
                        amount = s.avgAmount,
                        dueDay = s.dueDayEstimate!!
                    )
                }
                val addResp = intelligenceApi.autoAddBills(AutoAddBillsRequest(entries))
                added = addResp.body()?.data?.added ?: 0
            }

            // Show insight notification (warnings / savings opportunities)
            val alerts = report.insights.filter { it.type in listOf("warning", "alert") }
            if (alerts.isNotEmpty() || added > 0) {
                showNotification(added, alerts.map { it.message })
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }

    private fun showNotification(newBills: Int, alerts: List<String>) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "SpendWise Intelligence",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Financial insights and auto-detected bills" }
            )
        }

        val lines = mutableListOf<String>()
        if (newBills > 0) lines += "🧾 $newBills new recurring bill(s) auto-added"
        lines += alerts.take(2)

        val builder = NotificationCompat.Builder(appContext, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("SpendWise Insights")
            .setContentText(lines.firstOrNull() ?: "New financial insights available")
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    lines.forEach { style.addLine(it) }
                }
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        nm.notify(NOTIF_ID, builder.build())
    }

    private fun categoryIcon(slug: String) = when (slug) {
        "food"          -> "🍔"
        "transport"     -> "🚗"
        "entertainment" -> "🎬"
        "shopping"      -> "🛒"
        "utilities"     -> "💡"
        "health"        -> "💊"
        "education"     -> "📚"
        "travel"        -> "✈️"
        "bills"         -> "📄"
        "streaming"     -> "📺"
        "gym"           -> "🏋️"
        else            -> "💰"
    }
}
