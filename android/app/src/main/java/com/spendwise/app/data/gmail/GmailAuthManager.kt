package com.spendwise.app.data.gmail

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gmail account manager using Android's device AccountManager.
 *
 * Design decision: We do NOT use the Google Sign-In SDK because it requires
 * SHA-1 fingerprint registration in Google Cloud Console and a configured
 * google-services.json — neither of which exists in this project.
 *
 * Instead we use the device's own Account Manager which:
 *   1. Lists all Google accounts already logged into the device
 *   2. Gets OAuth tokens via GoogleAuthUtil.getToken() — handled at OS level
 *   3. Shows a one-time permission grant dialog on first sync
 *   4. Requires only GET_ACCOUNTS permission (declared in manifest)
 *
 * The user simply tells us which Gmail to scan (their existing device account).
 * GmailSyncWorker then obtains fresh tokens at each sync cycle.
 */
@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        const val OAUTH_SCOPE = "oauth2:$GMAIL_SCOPE"
        private const val TAG = "GmailAuthManager"
    }

    /**
     * Returns all Google accounts currently signed into this Android device.
     * The user picks one; no OAuth flow is needed — we use its token at sync time.
     */
    fun getDeviceGoogleAccounts(): List<String> {
        return try {
            AccountManager.get(context)
                .getAccountsByType("com.google")
                .map { it.name }
                .filter { it.contains("@") }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list device accounts: ${e.message}")
            emptyList()
        }
    }

    /**
     * Eagerly fetches an OAuth token for [gmailEmail] to verify the account
     * exists on this device. Returns the token or null.
     *
     * BLOCKING — must be called from a background thread / coroutine.
     */
    fun getTokenBlocking(gmailEmail: String): String? {
        return try {
            GoogleAuthUtil.getToken(
                context,
                Account(gmailEmail, "com.google"),
                OAUTH_SCOPE
            )
        } catch (e: UserRecoverableAuthException) {
            // User needs to grant the Gmail scope — this will happen on first
            // GmailSyncWorker run via a foreground notification if needed.
            Log.w(TAG, "User action required for $gmailEmail: ${e.intent}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot get token for $gmailEmail: ${e.message}")
            null
        }
    }

    /**
     * Invalidates a cached token so the next call to getToken returns a fresh one.
     */
    fun invalidateToken(gmailEmail: String, token: String) {
        try {
            GoogleAuthUtil.invalidateToken(context, token)
        } catch (_: Exception) {}
    }
}
