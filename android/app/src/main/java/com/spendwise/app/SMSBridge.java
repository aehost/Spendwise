package com.spendwise.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * JavaScript bridge exposed as window.AndroidBridge in the WebView.
 * All @JavascriptInterface methods are callable from JavaScript.
 */
public class SMSBridge {

    private final Context ctx;

    // Real-time SMS queue — supports simultaneous bank SMS without data loss
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> smsQueue =
        new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void setPendingSms(String json) { smsQueue.offer(json); }

    @JavascriptInterface
    public String getPendingSMS() {
        // Drain in-process queue first
        String result = smsQueue.poll();
        if (result != null) {
            removeFromPrefsQueue(ctx, result);
            return result;
        }
        // Process restart recovery — read SharedPreferences queue
        android.content.SharedPreferences prefs =
            ctx.getSharedPreferences("spendwise_sms", Context.MODE_PRIVATE);
        String queueJson = prefs.getString("pending_sms_queue", null);
        if (queueJson != null) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray(queueJson);
                if (arr.length() > 0) {
                    result = arr.getString(0);
                    org.json.JSONArray remaining = new org.json.JSONArray();
                    for (int i = 1; i < arr.length(); i++) remaining.put(arr.getString(i));
                    if (remaining.length() > 0) {
                        prefs.edit().putString("pending_sms_queue", remaining.toString()).commit();
                    } else {
                        prefs.edit().remove("pending_sms_queue").commit();
                    }
                    return result;
                }
            } catch (Exception ignored) {}
        }
        return "null";
    }

    private static void removeFromPrefsQueue(Context ctx, String json) {
        try {
            android.content.SharedPreferences prefs =
                ctx.getSharedPreferences("spendwise_sms", Context.MODE_PRIVATE);
            String queueJson = prefs.getString("pending_sms_queue", null);
            if (queueJson == null) return;
            org.json.JSONArray arr = new org.json.JSONArray(queueJson);
            org.json.JSONArray remaining = new org.json.JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (!arr.getString(i).equals(json)) remaining.put(arr.getString(i));
            }
            if (remaining.length() > 0) {
                prefs.edit().putString("pending_sms_queue", remaining.toString()).commit();
            } else {
                prefs.edit().remove("pending_sms_queue").commit();
            }
        } catch (Exception ignored) {}
    }

    // Matches bank/financial SMS messages
    private static final Pattern BANK_PATTERN = Pattern.compile(
        "debited|debit|credited|credit|deducted|Rs\\.?\\s*[\\d,]+|INR\\s*[\\d,]+|₹\\s*[\\d,]+|" +
        "Avail\\s*Bal|Avl\\s*Bal|available\\s*balance|transaction|payment|" +
        "spent|purchase|\\bEMI\\b|UPI|A/C|account|withdrawn|deposit|refund",
        Pattern.CASE_INSENSITIVE
    );

    public SMSBridge(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Returns JSON array of bank-related SMS from the last N days.
     * Called from JS: AndroidBridge.getBankSMS(90)
     */
    @JavascriptInterface
    public String getBankSMS(int daysBack) {
        JSONArray result = new JSONArray();

        if (!hasSMSPermission()) return result.toString();

        try {
            Uri smsUri = Uri.parse("content://sms/inbox");
            String[] cols = {"_id", "address", "body", "date"};
            long cutoff = System.currentTimeMillis() - ((long) daysBack * 86_400_000L);

            Cursor cursor = ctx.getContentResolver().query(
                smsUri, cols,
                "date > ?",
                new String[]{String.valueOf(cutoff)},
                "date DESC"
            );

            if (cursor != null) {
                while (cursor.moveToNext() && result.length() < 300) {
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    if (body != null && BANK_PATTERN.matcher(body).find()) {
                        JSONObject sms = new JSONObject();
                        sms.put("id",      cursor.getString(cursor.getColumnIndexOrThrow("_id")));
                        sms.put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                        sms.put("body",    body);
                        sms.put("date",    cursor.getLong(cursor.getColumnIndexOrThrow("date")));
                        result.put(sms);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Return empty array — app handles gracefully
        }

        return result.toString();
    }

    /** Returns true if READ_SMS permission is granted. */
    @JavascriptInterface
    public boolean hasSMSPermission() {
        return ctx.checkSelfPermission(android.Manifest.permission.READ_SMS)
               == PackageManager.PERMISSION_GRANTED;
    }

    /** Platform identifier so the web app can switch behaviour. */
    @JavascriptInterface
    public String getPlatform() {
        return "android";
    }

    /** Device info for debugging. */
    @JavascriptInterface
    public String getDeviceModel() {
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
    }

    /** Android OS version. */
    @JavascriptInterface
    public int getAndroidVersion() {
        return android.os.Build.VERSION.SDK_INT;
    }

    /** Share plain-text report via Android share sheet (WhatsApp, Gmail, Drive, etc.) */
    @JavascriptInterface
    public void shareText(String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        Intent chooser = Intent.createChooser(intent, "Share Report via...");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }

    /** Open email composer pre-filled with the monthly report. */
    @JavascriptInterface
    public void emailReport(String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(ctx.getPackageManager()) != null) {
            ctx.startActivity(intent);
        } else {
            // Fallback to generic share if no email app installed
            shareText(subject, body);
        }
    }
}
