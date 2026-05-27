package com.spendwise.app.data.gmail

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Sign-In with the Gmail readonly scope.
 *
 * Flow:
 *  1. Call [getSignInIntent] and launch it from an Activity with startActivityForResult
 *  2. On Activity result, call [handleSignInResult] → returns [GoogleSignInAccount] or null
 *  3. Use [account.serverAuthCode] to call your backend, or use [account.idToken] + Gmail REST API
 *
 * NOTE: Replace [WEB_CLIENT_ID] with your OAuth 2.0 Web Client ID from
 *       Google Cloud Console → APIs & Services → Credentials.
 */
@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Gmail read-only OAuth scope */
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

        /**
         * Replace with your OAuth Web Client ID from Google Cloud Console.
         * Project: SpendWise → APIs & Services → Credentials → OAuth 2.0 Client IDs
         */
        const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    }

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GMAIL_SCOPE))
        .requestServerAuthCode(WEB_CLIENT_ID, /* forceCodeForRefreshToken = */ true)
        .build()

    private val client by lazy { GoogleSignIn.getClient(context, signInOptions) }

    /** Returns an Intent that should be launched via startActivityForResult */
    fun getSignInIntent(): Intent = client.signInIntent

    /** Returns the last successfully signed-in account, or null if not signed in */
    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** True if a Google account is signed in AND has the Gmail scope granted */
    fun isGmailConnected(): Boolean {
        val account = getLastSignedInAccount() ?: return false
        return account.grantedScopes.any { it.scopeUri == GMAIL_SCOPE }
    }

    /** Handle the result intent from Google Sign-In activity */
    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.result
        } catch (e: Exception) {
            null
        }
    }

    /** Sign out the current account */
    suspend fun signOut() {
        try { client.signOut() } catch (_: Exception) {}
    }

    /** Revoke access (full disconnect) */
    suspend fun revokeAccess() {
        try { client.revokeAccess() } catch (_: Exception) {}
    }
}
