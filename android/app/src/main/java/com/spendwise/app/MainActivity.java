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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private boolean pageLoaded = false;
    private boolean pendingSmsDelivery = false;

    private static final int SMS_PERMISSION_CODE  = 101;
    private static final int NOTIF_PERMISSION_CODE = 102;

    // ── LIFECYCLE ─────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prevent screenshots / screen recording
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        // Match dark theme with system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(0xFF0A0A0F);
            getWindow().setNavigationBarColor(0xFF0A0A0F);
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        setContentView(R.layout.activity_main);

        // Check if we were launched from a bank-SMS notification
        if (getIntent() != null && getIntent().getBooleanExtra("from_sms_notification", false)) {
            pendingSmsDelivery = true;
        }

        authenticateThenInit();
    }

    // Called when app is already running (singleTop) and a new intent arrives
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("from_sms_notification", false)) {
            pendingSmsDelivery = true;
            // If page already loaded, deliver immediately; otherwise onPageFinished will do it
            if (pageLoaded) deliverPendingSms();
        }
    }

    // ── BIOMETRIC ─────────────────────────────────────────────────
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
                        finish();
                    }
                    @Override
                    public void onAuthenticationFailed() {}
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
            initWebView();
        }
    }

    // ── WEBVIEW ───────────────────────────────────────────────────
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

        webView.addJavascriptInterface(new SMSBridge(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                requestSMSPermission();      // ask for READ_SMS + RECEIVE_SMS
                requestNotificationPermission(); // ask for POST_NOTIFICATIONS (API 33+)
                deliverPendingSms();         // hand real-time SMS to JS if pending
            }
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
            public boolean onConsoleMessage(ConsoleMessage msg) { return true; }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    // ── SMS DELIVERY ──────────────────────────────────────────────
    /** Passes a pending bank SMS (from SMSReceiver) into JavaScript. */
    private void deliverPendingSms() {
        if (!pendingSmsDelivery || webView == null) return;
        pendingSmsDelivery = false;
        webView.post(() -> webView.evaluateJavascript(
            "if(typeof onIncomingSMS==='function'){" +
            "  var d=AndroidBridge.getPendingSMS();" +
            "  if(d&&d!=='null')onIncomingSMS(JSON.parse(d));" +
            "}", null));
    }

    // ── PERMISSIONS ───────────────────────────────────────────────
    private void requestSMSPermission() {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECEIVE_SMS);
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]), SMS_PERMISSION_CODE);
        } else {
            notifySMSReady();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERMISSION_CODE);
            }
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
            for (int i = 0; i < perms.length; i++) {
                if (Manifest.permission.READ_SMS.equals(perms[i])
                        && grants[i] == PackageManager.PERMISSION_GRANTED) {
                    notifySMSReady();
                }
            }
            // Request notification permission after SMS is resolved
            requestNotificationPermission();
        }
        // NOTIF_PERMISSION_CODE: nothing extra needed — system handles it
    }

    // ── BACK / LIFECYCLE ──────────────────────────────────────────
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            // Re-scan SMS inbox every time the app comes to foreground.
            // This is the reliable fallback when SMSReceiver is killed by battery optimizers
            // (common on Xiaomi/MIUI, Samsung One UI, OnePlus OxygenOS).
            if (pageLoaded) {
                webView.post(() -> webView.evaluateJavascript(
                    "if(typeof autoImportNewSMS==='function'&&typeof _fbUser!=='undefined'&&_fbUser)autoImportNewSMS();",
                    null));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
