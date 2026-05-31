package com.spendwise.app.presentation.navigation

import androidx.lifecycle.ViewModel
import com.spendwise.app.data.local.preferences.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the reactive login state so the nav graph can redirect to the Auth
 * screen the moment the session is cleared (e.g. an expired refresh token),
 * instead of leaving the user stuck on a "couldn't connect" error.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    tokenManager: TokenManager
) : ViewModel() {
    val loggedIn: StateFlow<Boolean> = tokenManager.loggedIn
}
