package com.spendwise.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AnalyticsApi
import com.spendwise.app.data.remote.api.UserApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyPulseWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val analyticsApi: AnalyticsApi,
    private val userApi: UserApi,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_pulse"
        const val CHANNEL_ID = "daily_pulse"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyPulseWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(computeInitialDelay(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        private fun computeInitialDelay(): Long {
            val now = java.time.LocalDateTime.now()
            var next8AM = now.toLocalDate().atTime(8, 0)
            if (now.hour >= 8) next8AM = next8AM.plusDays(1)
            return java.time.Duration.between(now, next8AM).toMillis().coerceAtLeast(60_000L)
        }
    }

    override suspend fun doWork(): Result {
        val todayStr = java.time.LocalDate.now().toString()
        if (tokenManager.lastDailyPulseDate == todayStr) return Result.success()
        return try {
            val dash = analyticsApi.getDashboard().body()?.data ?: return Result.retry()
            val bills = try { userApi.getBills().body()?.data ?: emptyList() } catch (_: Exception) { emptyList() }
            val today = java.time.LocalDate.now()
            val streak = tokenManager.spendingStreak

            val daysLeft = dash.daysLeft.takeIf { it > 0 } ?: (today.lengthOfMonth() - today.dayOfMonth + 1)
            val dailyBudget = if (daysLeft > 0 && dash.salary.amount > 0) {
                val remaining = dash.salary.amount - dash.totalSpent
                if (remaining > 0) remaining / daysLeft else 0.0
            } else 0.0

            val nextBill = bills.filter { !it.paidThisMonth }
                .minByOrNull {
                    val d = it.dueDay - today.dayOfMonth
                    if (d < 0) d + today.lengthOfMonth() else d
                }

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(CHANNEL_ID, "Daily Pulse", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                )
            }

            val streakText = if (streak > 0) "Streak: $streak days | " else ""
            val billText = nextBill?.let {
                val daysUntil = (it.dueDay - today.dayOfMonth).let { d -> if (d < 0) d + today.lengthOfMonth() else d }
                "${it.name} due in $daysUntil days"
            } ?: ""
            val budgetText = if (dailyBudget > 0) "₹${"%,.0f".format(dailyBudget)}/day budget remaining" else ""

            val title = "SpendWise Morning Pulse"
            val body = buildString {
                append("Spent this month: ₹${"%,.0f".format(dash.totalSpent)}")
                if (budgetText.isNotBlank()) append(" | $budgetText")
                if (streakText.isNotBlank()) append("\n${streakText.trimEnd()}")
                if (billText.isNotBlank()) append(" | $billText")
            }

            val notif = androidx.core.app.NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(9000, notif)

            // Update widget cache so the home-screen widget shows fresh data
            val widgetPrefs = appContext.getSharedPreferences("widget_cache", Context.MODE_PRIVATE)
            widgetPrefs.edit()
                .putFloat("daily_remaining", dailyBudget.toFloat())
                .putFloat("spent_today", dash.totalSpent.toFloat())
                .putInt("streak", streak)
                .apply()
            try {
                val wm = android.appwidget.AppWidgetManager.getInstance(appContext)
                val ids = wm.getAppWidgetIds(
                    android.content.ComponentName(appContext, com.spendwise.app.data.widget.BudgetWidget::class.java)
                )
                val tokenMgr = tokenManager
                ids.forEach { id -> com.spendwise.app.data.widget.BudgetWidget.updateWidget(appContext, wm, id, tokenMgr) }
            } catch (_: Exception) {}

            tokenManager.lastDailyPulseDate = todayStr
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
