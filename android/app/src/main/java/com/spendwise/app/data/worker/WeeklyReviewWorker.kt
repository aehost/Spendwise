package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AnalyticsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WeeklyReviewWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsApi: AnalyticsApi,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_review"
        const val CHANNEL_ID = "weekly_review"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val today = java.time.LocalDate.now().toString()
            if (tokenManager.lastWeeklyReviewDate == today) return Result.success()

            val dash = analyticsApi.getDashboard().body()?.data ?: return Result.retry()
            val savingsRate = dash.savingsRate
            val grade = when {
                savingsRate >= 30 -> "A"
                savingsRate >= 20 -> "B"
                savingsRate >= 10 -> "C"
                else -> "D"
            }

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        CHANNEL_ID, "Weekly Review", android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }

            val body = "This week: spent ₹${"%,.0f".format(dash.totalSpent)} | saved ₹${"%,.0f".format(dash.savings)} | " +
                "savings rate ${dash.savingsRate}% | Grade: $grade" +
                if (dash.burnRate > 0) "\nDaily burn: ₹${"%,.0f".format(dash.burnRate)}" else ""

            val notif = androidx.core.app.NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Your Weekly Financial Review")
                .setContentText(body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(9002, notif)
            tokenManager.lastWeeklyReviewDate = today
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
