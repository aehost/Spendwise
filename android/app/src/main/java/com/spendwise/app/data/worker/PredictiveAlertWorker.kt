package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.remote.api.AnalyticsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PredictiveAlertWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsApi: AnalyticsApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "predictive_alert"
        const val CHANNEL_ID = "predictive_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PredictiveAlertWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val dash = analyticsApi.getDashboard().body()?.data ?: return Result.success()
            val today = java.time.LocalDate.now()
            val dayOfMonth = today.dayOfMonth
            val daysInMonth = today.lengthOfMonth()
            if (dayOfMonth < 5) return Result.success() // Not enough data in first 5 days

            val salary = dash.salary.amount
            if (salary <= 0) return Result.success()

            val spent = dash.totalSpent
            val spendRate = spent / dayOfMonth.toDouble()
            val projectedMonthEnd = spendRate * daysInMonth
            val overshootAmount = projectedMonthEnd - salary

            if (overshootAmount > salary * 0.10) {
                // Overshoot by more than 10% of salary — alert
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(
                            CHANNEL_ID, "Spending Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
                        )
                    )
                }
                val daysLeft = daysInMonth - dayOfMonth
                val body = "At your current pace, you'll overshoot your budget by ₹${"%,.0f".format(overshootAmount)} by month-end. " +
                    "You have ₹${"%,.0f".format((salary - spent).coerceAtLeast(0.0))} and $daysLeft days left. " +
                    "Daily limit to stay on track: ₹${"%,.0f".format(((salary - spent) / daysLeft.coerceAtLeast(1)).coerceAtLeast(0.0))}"

                val notif = androidx.core.app.NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Spending Pace Alert")
                    .setContentText(body)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                nm.notify(9001, notif)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
