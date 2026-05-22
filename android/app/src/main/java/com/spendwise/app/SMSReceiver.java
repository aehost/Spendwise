package com.spendwise.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "spendwise_bank";

    private static final Pattern BANK_PATTERN = Pattern.compile(
        "debited|debit|credited|credit|deducted|Rs\\.?\\s*[\\d,]+|INR\\s*[\\d,]+|₹\\s*[\\d,]+|" +
        "Avail\\s*Bal|Avl\\s*Bal|available\\s*balance|transaction|payment|" +
        "spent|purchase|\\bEMI\\b|UPI|A/C|account|withdrawn|deposit|refund",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d+)?)|" +
        "(?:Amt|Amount)[:\\s]+(?:Rs\\.?|INR|₹)?\\s*([\\d,]+(?:\\.\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null || pdus.length == 0) return;

        StringBuilder fullBody = new StringBuilder();
        String sender = "";

        for (Object pdu : pdus) {
            SmsMessage sms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }
            if (sms != null) {
                fullBody.append(sms.getMessageBody());
                if (sender.isEmpty()) {
                    String addr = sms.getOriginatingAddress();
                    if (addr != null) sender = addr;
                }
            }
        }

        String body = fullBody.toString();
        if (!BANK_PATTERN.matcher(body).find()) return;

        // Store SMS in bridge so JS can consume it via AndroidBridge.getPendingSMS()
        String smsJson = null;
        try {
            JSONObject obj = new JSONObject();
            obj.put("body", body);
            obj.put("sender", sender);
            obj.put("time", System.currentTimeMillis());
            smsJson = obj.toString();
            SMSBridge.setPendingSms(smsJson);
        } catch (Exception e) {
            // JSON failed — continue to show notification anyway
        }
        // Append to SharedPreferences queue (JSONArray) so simultaneous SMS are not lost.
        // commit() is synchronous — guarantees write completes before onReceive() returns.
        if (smsJson != null) {
            android.content.SharedPreferences prefs =
                context.getSharedPreferences("spendwise_sms", Context.MODE_PRIVATE);
            try {
                String existing = prefs.getString("pending_sms_queue", "[]");
                org.json.JSONArray arr = new org.json.JSONArray(existing);
                arr.put(smsJson);
                prefs.edit().putString("pending_sms_queue", arr.toString()).commit();
            } catch (Exception e) {
                prefs.edit().putString("pending_sms_queue", "[" + smsJson + "]").commit();
            }
        }

        // Create notification channel (required Android 8.0+)
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Bank Transactions", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Tap to tag bank debits and credits in SpendWise");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        // Intent: open SpendWise and deliver the SMS for tagging
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra("from_sms_notification", true);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(context, 0, launch, piFlags);

        String title = buildNotificationTitle(body);
        String preview = body.length() > 120 ? body.substring(0, 117) + "…" : body;

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi);

        nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), nb.build());
    }

    private String buildNotificationTitle(String body) {
        String lower = body.toLowerCase();
        boolean isCredit = lower.contains("credited") || lower.contains("credit")
                        || lower.contains("salary") || lower.contains("received")
                        || lower.contains("refund") || lower.contains("deposit");
        Matcher m = AMOUNT_PATTERN.matcher(body);
        if (m.find()) {
            // group(1) from Rs./INR/₹ pattern; group(2) from Amt/Amount pattern
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            if (raw != null) {
                raw = raw.replace(",", "");
                try {
                    double amt = Double.parseDouble(raw);
                    return String.format("₹%.0f %s — Tap to tag in SpendWise",
                        amt, isCredit ? "Credited" : "Debited");
                } catch (NumberFormatException ignored) {}
            }
        }
        return isCredit ? "Money Credited — Tap to tag" : "Money Debited — Tap to tag";
    }
}
