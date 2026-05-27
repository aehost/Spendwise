# Keep JavaScript bridge methods (called via reflection from WebView)
-keepclassmembers class com.spendwise.app.SMSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Keep WebView JS interface
-keepattributes JavascriptInterface

# ── R8 / errorprone annotation fix ──────────────────────────────────────────
# androidx.security:security-crypto pulls in com.google.crypto.tink, which
# references compile-time errorprone annotation classes that are not present
# in the final APK.  These annotations have zero runtime behaviour — it is
# safe to tell R8 to stop looking for them.
# (This is also what the auto-generated missing_rules.txt would recommend.)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Keep Tink primitives used at runtime by security-crypto EncryptedSharedPreferences
-keep class com.google.crypto.tink.** { *; }

# ── Standard Android rules ────────────────────────────────────────────────────
-dontwarn android.webkit.**

# Suppress warnings from OkHttp/Retrofit platform-detection code
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
