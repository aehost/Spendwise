package com.spendwise.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.R
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.UserApi
import com.spendwise.app.presentation.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklyDigestWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsApi: AnalyticsApi,
    private val userApi: UserApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_digest"
        private const val TAG = "WeeklyDigestWorker"
        private const val CHANNEL_ID = "spendwise_weekly"
        private const val NOTIF_ID = 1001

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val dashResp = analyticsApi.getDashboard()
            val billsResp = userApi.getBills()
            val dash = dashResp.body()?.data ?: return Result.success()
            val bills = billsResp.body()?.data ?: emptyList()
            val unpaidBills = bills.filter { !it.paidThisMonth }

            val spent = dash.totalSpent.toLong()
            val savings = dash.savings.toLong()
            val savingsRate = dash.savingsRate
            val pending = dash.pendingCount

            val title = "💰 Your Weekly Money Summary"
            val body = buildString {
                append("Spent ₹${String.format("%,d", spent)} this month")
                if (savingsRate > 0) append(" • Saved ${savingsRate}%")
                if (unpaidBills.isNotEmpty()) append("\n⚠️ ${unpaidBills.size} bill${if (unpaidBills.size > 1) "s" else ""} unpaid")
                if (pending > 0) append("\n📋 $pending transaction${if (pending > 1) "s" else ""} need review")
                if (savings < 0) append("\n🚨 Over budget this month!")
            }

            sendNotification(title, body)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly digest failed", e)
            Result.retry()
        }
    }

    private fun sendNotification(title: String, body: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly Digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Weekly spending summary"
            }
        )
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }
}
