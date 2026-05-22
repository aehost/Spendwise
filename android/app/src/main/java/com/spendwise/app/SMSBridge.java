package com.spendwise.app;

import android.content.Context;
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

    // Matches bank/financial SMS messages
    private static final Pattern BANK_PATTERN = Pattern.compile(
        "debited|credited|Rs\\.?\\s*[\\d,]+|INR\\s*[\\d,]+|" +
        "Avail\\s*Bal|available\\s*balance|transaction|payment|EMI|UPI|" +
        "A/C|account|withdrawn|deposit",
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
}
