package com.spendwise.app.sms

import android.content.Context
import android.provider.Telephony
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
            // Permission not granted or query failed
        }

        return results
    }
}
