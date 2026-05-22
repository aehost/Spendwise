package com.spendwise.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int SMS_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── SECURITY: prevent screenshots & screen recording ──
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // ── Match app dark theme with system bars ─────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(0xFF0A0A0F);
            getWindow().setNavigationBarColor(0xFF0A0A0F);
            // Light icons on dark background
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(0); // dark icons = off (white icons on dark bg)
        }

        setContentView(R.layout.activity_main);

        // ── BIOMETRIC AUTH before showing the app ─────────────
        authenticateThenInit();
    }

    private void authenticateThenInit() {
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG |
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        );

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        initWebView();
                    }
                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence err) {
                        // User cancelled or no hardware — close app
                        finish();
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        // Wrong fingerprint — prompt stays open, handled by system
                    }
                });

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("SpendWise")
                .setSubtitle("Authenticate to access your finances")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

            prompt.authenticate(info);
        } else {
            // No biometric enrolled — proceed directly
            initWebView();
        }
    }

    private void initWebView() {
        webView = findViewById(R.id.webview);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(false);

        // JavaScript bridge — exposes AndroidBridge object to the web app
        webView.addJavascriptInterface(new SMSBridge(this), "AndroidBridge");

        // Handle external links (Firebase console, etc.)
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!url.startsWith("file://")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                // Silent in production
                return true;
            }
        });

        // Load the SpendWise app from assets
        webView.loadUrl("file:///android_asset/index.html");

        // Request SMS permission after WebView loads
        requestSMSPermission();
    }

    private void requestSMSPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_SMS},
                SMS_PERMISSION_CODE);
        } else {
            notifySMSReady();
        }
    }

    private void notifySMSReady() {
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript(
                "if(typeof onAndroidSMSReady==='function')onAndroidSMSReady();", null));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code,
            @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        if (code == SMS_PERMISSION_CODE) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                notifySMSReady();
            } else {
                // Permission denied — SMS button will show a message in the app
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
