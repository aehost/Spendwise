package com.spendwise.app.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.spendwise.app.MainActivity
import com.spendwise.app.R
import com.spendwise.app.core.Constants
import com.spendwise.app.domain.usecase.ParseSmsUseCase
import java.util.concurrent.ConcurrentLinkedQueue

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "spendwise_sms"
        val pendingQueue: ConcurrentLinkedQueue<Map<String, String>> = ConcurrentLinkedQueue()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val parser = ParseSmsUseCase()

        messages.forEach { msg ->
            val body   = msg.messageBody ?: return@forEach
            val sender = msg.originatingAddress ?: ""

            if (!Constants.BANK_PATTERN.containsMatchIn(body)) return@forEach

            val parsed = parser.parse(body)

            // Queue for the app to consume
            pendingQueue.add(mapOf(
                "id"     to System.currentTimeMillis().toString(),
                "body"   to body,
                "sender" to sender,
                "date"   to System.currentTimeMillis().toString(),
                "amount" to (parsed?.amount?.toString() ?: ""),
                "isCredit" to (parsed?.isCredit?.toString() ?: "false")
            ))

            // Show notification
            if (parsed != null) {
                showNotification(context, parsed.amount, parsed.isCredit, sender)
            }
        }
    }

    private fun showNotification(ctx: Context, amount: Double, isCredit: Boolean, sender: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Bank SMS Alerts", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_sms_notification", true)
        }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val direction = if (isCredit) "credited" else "debited"
        val amtStr    = "₹${String.format("%.0f", amount)}"
        val title     = "$amtStr $direction"
        val text      = "From: $sender — Tap to tag in SpendWise"

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
