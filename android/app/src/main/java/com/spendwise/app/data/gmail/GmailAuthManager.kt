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
 * We only need the user's email + scope grant here.
 * The actual OAuth2 access token for Gmail REST API is obtained at sync time
 * via GoogleAuthUtil.getToken() in GmailSyncWorker — no serverAuthCode needed.
 */
@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    }

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(GMAIL_SCOPE))
        .build()

    private val client by lazy { GoogleSignIn.getClient(context, signInOptions) }

    fun getSignInIntent(): Intent = client.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isGmailConnected(): Boolean {
        val account = getLastSignedInAccount() ?: return false
        return account.grantedScopes.any { it.scopeUri == GMAIL_SCOPE }
    }

    fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(data).result
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signOut() {
        try { client.signOut() } catch (_: Exception) {}
    }

    suspend fun revokeAccess() {
        try { client.revokeAccess() } catch (_: Exception) {}
    }
}
