# ── Gson / Retrofit ─────────────────────────────────────────────────────────
# R8 obfuscates field names by default. Without explicit keep rules Gson
# serialises request bodies as {} (empty) and the server returns 400
# "email and password required" because it can't find the JSON keys.
#
# Keep all declared fields on every DTO class so Gson can map them.
-keepclassmembers class com.spendwise.app.data.remote.dto.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.spendwise.app.domain.model.** {
    <fields>;
    <init>(...);
}

# Preserve generic type signatures (used by Retrofit + Gson TypeToken)
-keepattributes Signature

# Preserve all annotations (needed for @SerializedName, @Body, @GET, etc.)
-keepattributes *Annotation*

# Keep Retrofit API interface methods (annotated with @GET, @POST, etc.)
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── Keep JavaScript bridge methods (called via reflection from WebView) ────────
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

# ── Tink (via androidx.security:security-crypto) ─────────────────────────────
# Do NOT use a broad "-keep class com.google.crypto.tink.** { *; }" rule.
# That would force R8 to retain KeysDownloader, which references server-side
# libraries (google-api-client, joda-time) that are not on the Android
# classpath, causing an endless chain of "missing class" R8 errors.
#
# security-crypto ships its own consumer ProGuard rules inside the AAR that
# already protect every Tink class EncryptedSharedPreferences / EncryptedFile
# actually calls at runtime — no extra keep rule is needed here.
#
# Suppress warnings for Tink's optional server-side dependencies that are
# never exercised on Android:
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# ── Standard Android rules ────────────────────────────────────────────────────
-dontwarn android.webkit.**

# OkHttp/Retrofit TLS platform-detection code probes for JVM TLS providers
# that are not available on Android — safe to suppress.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
