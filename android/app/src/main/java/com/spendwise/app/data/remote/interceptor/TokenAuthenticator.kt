package com.spendwise.app.data.remote.interceptor

import com.spendwise.app.core.Constants
import com.spendwise.app.data.local.preferences.TokenManager
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] that automatically refreshes the JWT access token
 * when the server returns **401 Unauthorized**.
 *
 * Flow:
 *  1. First 401 for a request → try `POST auth/refresh` with the stored refresh token.
 *  2. On success → persist the new access token and retry the original request.
 *  3. On failure (refresh token expired / network error) → clear auth so the app
 *     can navigate the user back to the login screen.
 *
 * A separate bare [OkHttpClient] is used for the refresh call to avoid the circular
 * dependency: main client → [AuthInterceptor] → (this authenticator) → main client.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager
) : Authenticator {

    // Plain client with no interceptors — only used for the /auth/refresh call
    private val refreshClient = OkHttpClient()

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Guard: do not retry if we already refreshed once for this request
        if (response.request.header("X-Token-Refreshed") != null) return null

        // If another coroutine already refreshed the token, just retry with the new one
        val currentStored = tokenManager.accessToken
        val sentToken     = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
        if (currentStored != null && currentStored != sentToken) {
            // Token was refreshed by a concurrent call — retry straight away
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentStored")
                .header("X-Token-Refreshed", "true")
                .build()
        }

        val refreshToken = tokenManager.refreshToken ?: return null   // no refresh token → can't help

        return try {
            val body = """{"refresh_token":"$refreshToken"}"""
                .toRequestBody("application/json".toMediaTypeOrNull())
            val req = Request.Builder()
                .url("${Constants.BASE_URL}auth/refresh")
                .post(body)
                .build()

            val resp = refreshClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                // Refresh token is invalid or expired — force re-login
                tokenManager.clearAuth()
                return null
            }

            val json     = JSONObject(resp.body?.string() ?: "{}")
            val newToken = json.optJSONObject("data")?.optString("access_token")
            if (newToken.isNullOrBlank()) {
                tokenManager.clearAuth()
                return null
            }

            tokenManager.accessToken = newToken
            // Retry the original request with the freshly issued access token
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("X-Token-Refreshed", "true")
                .build()
        } catch (_: Exception) {
            null   // network error during refresh — let the original error propagate
        }
    }
}
