package com.spendwise.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.spendwise.app.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class RawSms(
    val id: String,
    val address: String,
    val body: String,
    val dateMs: Long
)

class SmsScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scanSince(lastScanMs: Long, lookbackDays: Int = 2): List<RawSms> {
        // BUG FIX: explicitly verify READ_SMS before querying the content
        // provider. Without the grant the query throws SecurityException which
        // was previously swallowed silently — now we short-circuit cleanly so
        // the caller (worker) treats it as "no new messages" rather than retry.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("SmsScanner", "READ_SMS not granted — skipping inbox scan")
            return emptyList()
        }

        val cutoff = if (lastScanMs > 0) lastScanMs
                     else System.currentTimeMillis() - lookbackDays * 24 * 60 * 60 * 1000L

        val results = mutableListOf<RawSms>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection     = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(cutoff.toString())
        val sortOrder     = "${Telephony.Sms.DATE} ASC"

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id      = cursor.getString(0)  ?: continue
                    val address = cursor.getString(1)  ?: ""
                    val body    = cursor.getString(2)  ?: continue
                    // Telephony.Sms.DATE is documented as milliseconds.
                    // A small number of legacy/OEM ROMs store it in seconds;
                    // guard by checking whether the value is plausibly a year ≥ 2000
                    // in ms (> 946684800000 ms = Jan 1 2000).
                    val rawDate = cursor.getLong(3)
                    val date    = if (rawDate < 946_684_800_000L) rawDate * 1_000L else rawDate

                    if (Constants.BANK_PATTERN.containsMatchIn(body)) {
                        results.add(RawSms(id, address, body, date))
                    }
                    if (results.size >= 300) break
                }
            }
        } catch (e: Exception) {
            // Query failed (revoked mid-scan, provider error) — log and return
            // whatever we collected so far rather than failing silently.
            Log.w("SmsScanner", "SMS inbox query failed: ${e.message}")
        }

        return results
    }
}
