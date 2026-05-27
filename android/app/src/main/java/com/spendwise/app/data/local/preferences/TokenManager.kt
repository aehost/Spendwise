package com.spendwise.app.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.spendwise.app.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        try {
            EncryptedSharedPreferences.create(
                ctx, "sw_secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to plain prefs if encryption fails (e.g. emulator)
            ctx.getSharedPreferences("sw_plain_prefs", Context.MODE_PRIVATE)
        }
    }

    var accessToken: String?
        get() = prefs.getString(Constants.TOKEN_KEY, null)
        set(v) = prefs.edit().putString(Constants.TOKEN_KEY, v).apply()

    var refreshToken: String?
        get() = prefs.getString(Constants.REFRESH_KEY, null)
        set(v) = prefs.edit().putString(Constants.REFRESH_KEY, v).apply()

    var userId: String?
        get() = prefs.getString(Constants.USER_ID_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_ID_KEY, v).apply()

    var userEmail: String?
        get() = prefs.getString(Constants.USER_EMAIL_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_EMAIL_KEY, v).apply()

    var userName: String?
        get() = prefs.getString(Constants.USER_NAME_KEY, null)
        set(v) = prefs.edit().putString(Constants.USER_NAME_KEY, v).apply()

    var setupDone: Boolean
        get() = prefs.getBoolean(Constants.SETUP_DONE_KEY, false)
        set(v) = prefs.edit().putBoolean(Constants.SETUP_DONE_KEY, v).apply()

    var smsScanFromMs: Long
        get() = prefs.getLong(Constants.SMS_SCAN_FROM_MS, 0L)
        set(v) = prefs.edit().putLong(Constants.SMS_SCAN_FROM_MS, v).apply()

    fun isLoggedIn(): Boolean = accessToken != null && userId != null

    fun clearAuth() {
        prefs.edit()
            .remove(Constants.TOKEN_KEY)
            .remove(Constants.REFRESH_KEY)
            .remove(Constants.USER_ID_KEY)
            .remove(Constants.USER_EMAIL_KEY)
            .remove(Constants.USER_NAME_KEY)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
