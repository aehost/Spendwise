package com.spendwise.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.worker.BillReminderWorker
import com.spendwise.app.data.worker.DailyPulseWorker
import com.spendwise.app.data.worker.GmailImapWorker
import com.spendwise.app.data.worker.PredictiveAlertWorker
import com.spendwise.app.data.worker.WeeklyReviewWorker
import com.spendwise.app.presentation.navigation.Screen
import com.spendwise.app.presentation.navigation.SpendWiseNavGraph
import com.spendwise.app.presentation.theme.Background
import com.spendwise.app.presentation.theme.SpendWiseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Status/nav bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor     = 0xFF0A0A0F.toInt()
            window.navigationBarColor = 0xFF0A0A0F.toInt()
        }

        requestPermissions()
        BillReminderWorker.schedule(this)
        DailyPulseWorker.schedule(this)
        PredictiveAlertWorker.schedule(this)
        WeeklyReviewWorker.schedule(this)
        GmailImapWorker.schedule(this)
        authenticateThenLaunch()
    }

    private fun authenticateThenLaunch() {
        // Seed reactive session state from storage before composing the UI.
        tokenManager.syncLoggedInState()

        // Biometric only guards an EXISTING logged-in session. If the user isn't
        // logged in (or setup isn't done), go straight to the normal start screen.
        if (!tokenManager.isLoggedIn()) {
            launchApp(forceLogin = false)
            return
        }

        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Fingerprint OK → skip login, straight to the app.
                    launchApp(forceLogin = false)
                }
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    // Cancelled / locked-out / failed → show Login (do NOT close app).
                    launchApp(forceLogin = true)
                }
                override fun onAuthenticationFailed() { /* wrong finger — allow retry */ }
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock SpendWise")
                    .setSubtitle("Use your fingerprint to continue")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ).build()
            )
        } else {
            // No biometric available — proceed (the session is already valid).
            launchApp(forceLogin = false)
        }
    }

    private fun launchApp(forceLogin: Boolean) {
        val startRoute = when {
            !tokenManager.setupDone   -> Screen.Setup.route
            forceLogin                -> Screen.Auth.route
            tokenManager.isLoggedIn() -> Screen.Home.route
            else                      -> Screen.Auth.route
        }

        setContent {
            SpendWiseTheme {
                Box(Modifier.fillMaxSize().background(Background)) {
                    SpendWiseNavGraph(startRoute = startRoute)
                }
            }
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_SMS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
        }
    }
}
