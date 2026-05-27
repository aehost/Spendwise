# Keep JavaScript bridge methods (called via reflection from WebView)
-keepclassmembers class com.spendwise.app.SMSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Keep WebView JS interface
-keepattributes JavascriptInterface

# ── R8 / missing compile-time annotation classes ─────────────────────────────
#
# androidx.security:security-crypto pulls in com.google.crypto.tink.
# Tink itself depends on Guava.  Both libraries annotate their source with
# compile-time-only annotation libraries (errorprone, j2objc, checkerframework,
# javax.annotation) that are NOT present in the final APK.
# R8 trips over the missing class references during release shrinking.
# All four libraries contain zero runtime behaviour — it is safe to suppress.
#
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# Keep Tink primitives used at runtime by EncryptedSharedPreferences / EncryptedFile
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }

# ── Standard Android rules ────────────────────────────────────────────────────
-dontwarn android.webkit.**

# OkHttp/Retrofit TLS platform-detection code probes for JVM TLS providers
# that are not available on Android — safe to suppress.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
