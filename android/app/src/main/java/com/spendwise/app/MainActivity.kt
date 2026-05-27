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
        authenticateThenLaunch()
    }

    private fun authenticateThenLaunch() {
        val bm = BiometricManager.from(this)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    launchApp()
                }
                override fun onAuthenticationError(code: Int, err: CharSequence) {
                    finish()
                }
                override fun onAuthenticationFailed() {}
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("SpendWise")
                    .setSubtitle("Authenticate to access your finances")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    ).build()
            )
        } else {
            launchApp()
        }
    }

    private fun launchApp() {
        val startRoute = when {
            !tokenManager.setupDone -> Screen.Setup.route
            tokenManager.isLoggedIn() -> Screen.Home.route
            else -> Screen.Auth.route
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
