package com.spendwise.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.spendwise.app.data.remote.api.UserApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userApi: UserApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "bill_reminder_daily"
        private const val CHANNEL_ID   = "bill_reminders"
        private const val CHANNEL_NAME = "Bill Reminders"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val bills   = userApi.getBills().body()?.data ?: return Result.success()
            val today   = LocalDate.now()
            val todayDay = today.dayOfMonth

            val dueSoon = bills.filter { bill ->
                if (bill.paidThisMonth) return@filter false
                val daysUntilDue = when {
                    bill.dueDay >= todayDay -> bill.dueDay - todayDay
                    else -> bill.dueDay + today.lengthOfMonth() - todayDay
                }
                daysUntilDue in 0..3
            }

            if (dueSoon.isEmpty()) return Result.success()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                )
            }

            dueSoon.forEachIndexed { i, bill ->
                val daysLeft = when {
                    bill.dueDay >= todayDay -> bill.dueDay - todayDay
                    else -> bill.dueDay + today.lengthOfMonth() - todayDay
                }
                val title = when (daysLeft) {
                    0    -> "⚠️ ${bill.name} due TODAY"
                    1    -> "⏰ ${bill.name} due tomorrow"
                    else -> "📅 ${bill.name} due in $daysLeft days"
                }
                val text = "Amount: ₹${"%,.0f".format(bill.amount)} • Due on ${bill.dueDay}${ordinal(bill.dueDay)}"
                val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(
                        if (daysLeft == 0) NotificationCompat.PRIORITY_HIGH
                        else NotificationCompat.PRIORITY_DEFAULT
                    )
                    .setAutoCancel(true)
                    .build()
                nm.notify(5000 + i, notif)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun ordinal(d: Int) = when {
        d in 11..13 -> "th"
        d % 10 == 1 -> "st"
        d % 10 == 2 -> "nd"
        d % 10 == 3 -> "rd"
        else        -> "th"
    }
}
