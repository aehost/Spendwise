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
import com.spendwise.app.data.worker.SmsSyncWorker
import com.spendwise.app.domain.usecase.ParseSmsUseCase

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "spendwise_sms"
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
            if (parsed != null) {
                // Regular transaction — show amount notification
                showNotification(context, parsed.amount, parsed.isCredit, sender)
            } else {
                // Check if it's a CC bill-due reminder and notify the user
                val billDue = parser.parseBillDue(body)
                if (billDue != null) {
                    showBillDueNotification(context, billDue.outstandingAmount, billDue.bankName ?: sender)
                }
            }
        }

        // Kick off background sync — uploads new transactions AND updates CC outstanding
        SmsSyncWorker.triggerNow(context)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Bank SMS Alerts", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
    }

    private fun mainActivityPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_sms_notification", true)
        }
        return PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showNotification(ctx: Context, amount: Double, isCredit: Boolean, sender: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
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
            .setContentIntent(mainActivityPendingIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private fun showBillDueNotification(ctx: Context, outstanding: Double, bankName: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val amtStr = "₹${String.format("%.2f", outstanding)}"
        val title  = "💳 CC Bill Due — $amtStr"
        val text   = "$bankName bill due. Outstanding updated in SpendWise automatically."
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(mainActivityPendingIntent(ctx))
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
