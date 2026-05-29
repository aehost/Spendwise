package com.spendwise.app.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.GoalsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class GoalMilestoneWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val goalsApi: GoalsApi,
    private val tokenManager: TokenManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "goal_milestones"
        private const val CHANNEL_ID = "goal_milestones"

        fun triggerCheck(context: Context) {
            val req = OneTimeWorkRequestBuilder<GoalMilestoneWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, req
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val goals = goalsApi.getGoals().body()?.data ?: return@withContext Result.success()
            val gson = Gson()
            val milestoneMap: MutableMap<String, Int> = try {
                gson.fromJson(
                    tokenManager.goalMilestonesJson,
                    object : TypeToken<MutableMap<String, Int>>() {}.type
                ) ?: mutableMapOf()
            } catch (_: Exception) { mutableMapOf() }

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Goal Milestones",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }

            goals.filter { !it.isCompleted && it.targetAmount > 0 }.forEach { goal ->
                val pct = ((goal.currentAmount / goal.targetAmount) * 100).toInt()
                val lastNotified = milestoneMap[goal.id] ?: 0
                val milestones = listOf(25, 50, 75, 100)
                val nextMilestone = milestones.firstOrNull { it > lastNotified && pct >= it }
                if (nextMilestone != null) {
                    val emoji = when (nextMilestone) {
                        25   -> "🌱"
                        50   -> "🌿"
                        75   -> "🌳"
                        else -> "🏆"
                    }
                    val msg = "$emoji You've reached $nextMilestone% of your '${goal.title}' goal! " +
                        "₹${"%,.0f".format(goal.currentAmount)} saved of ₹${"%,.0f".format(goal.targetAmount)}"
                    val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.star_big_on)
                        .setContentTitle("$emoji Goal Milestone Reached!")
                        .setContentText(msg)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(7100 + goal.id.hashCode(), notif)
                    milestoneMap[goal.id] = nextMilestone
                }
            }
            tokenManager.goalMilestonesJson = gson.toJson(milestoneMap)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
