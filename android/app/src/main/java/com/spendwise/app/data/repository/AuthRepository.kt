package com.spendwise.app.data.repository

import com.spendwise.app.core.Result
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.AuthApi
import com.spendwise.app.data.remote.dto.LoginRequest
import com.spendwise.app.data.remote.dto.LogoutRequest
import com.spendwise.app.data.remote.dto.RefreshRequest
import com.spendwise.app.data.remote.dto.RegisterRequest
import com.spendwise.app.domain.model.AuthTokens
import com.spendwise.app.domain.model.User
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenManager: TokenManager
) {
    suspend fun login(email: String, password: String): Result<AuthTokens> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                saveTokens(data.accessToken, data.refreshToken, data.user.id, data.user.email, data.user.name)
                Result.Success(AuthTokens(
                    accessToken  = data.accessToken,
                    refreshToken = data.refreshToken,
                    user = data.user.toDomain()
                ))
            } else {
                val err = response.body()?.error ?: parseErrorBody(response) ?: "Login failed"
                Result.Error(err, response.body()?.code)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun register(email: String, password: String, name: String): Result<AuthTokens> {
        return try {
            val response = api.register(RegisterRequest(email, password, name))
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                saveTokens(data.accessToken, data.refreshToken, data.user.id, data.user.email, data.user.name)
                Result.Success(AuthTokens(
                    accessToken  = data.accessToken,
                    refreshToken = data.refreshToken,
                    user = data.user.toDomain()
                ))
            } else {
                val err = response.body()?.error ?: parseErrorBody(response) ?: "Registration failed"
                Result.Error(err, response.body()?.code)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun refreshToken(): Result<String> {
        val refresh = tokenManager.refreshToken ?: return Result.Error("No refresh token")
        return try {
            val response = api.refresh(RefreshRequest(refresh))
            if (response.isSuccessful && response.body()?.success == true) {
                val newToken = response.body()!!.data!!["accessToken"] ?: return Result.Error("No token in response")
                tokenManager.accessToken = newToken
                Result.Success(newToken)
            } else {
                tokenManager.clearAuth()
                Result.Error("Session expired")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun logout() {
        try {
            val refresh = tokenManager.refreshToken
            if (refresh != null) api.logout(LogoutRequest(refresh))
        } catch (_: Exception) { }
        tokenManager.clearAuth()
    }

    fun isLoggedIn() = tokenManager.isLoggedIn()

    fun currentUser(): User? {
        val id    = tokenManager.userId    ?: return null
        val email = tokenManager.userEmail ?: return null
        return User(id, email, tokenManager.userName ?: "")
    }

    private fun saveTokens(access: String, refresh: String, id: String, email: String, name: String) {
        tokenManager.accessToken  = access
        tokenManager.refreshToken = refresh
        tokenManager.userId       = id
        tokenManager.userEmail    = email
        tokenManager.userName     = name
    }

    /** Parse the error message from a non-2xx response body (e.g. 400 / 409 / 500). */
    private fun parseErrorBody(response: Response<*>): String? {
        return try {
            val raw = response.errorBody()?.string()
            if (raw.isNullOrBlank()) null
            else JSONObject(raw).optString("error").ifBlank { null }
        } catch (_: Exception) { null }
    }
}
