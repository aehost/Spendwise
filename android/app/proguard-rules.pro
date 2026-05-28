# ── Gson + Retrofit + R8 ─────────────────────────────────────────────────────
#
# Three things must be preserved for Retrofit + Gson to work under R8:
#
#  1. DTO class NAMES (not just members) — Gson does reflection-based casts
#     using the fully-qualified class name. If the name is obfuscated the cast
#     throws ClassCastException (e.g. "h2.o cannot be cast to T2.b").
#
#  2. Field NAMES inside DTOs — Gson maps JSON keys to field names (or
#     @SerializedName). Obfuscated field names produce empty {} bodies so the
#     server returns "email and password required".
#
#  3. Generic type SIGNATURES — Retrofit reads the return type of each API
#     interface method (e.g. Response<ApiResponse<AuthResponse>>) to build the
#     Gson TypeAdapter. Without Signature attributes the type parameter T is
#     erased to Object and Gson falls back to LinkedTreeMap.

# Keep full DTO classes: class name + all fields + constructors
-keep class com.spendwise.app.data.remote.dto.** { *; }
-keep class com.spendwise.app.domain.model.** { *; }

# Keep Retrofit API interfaces and all their method signatures
-keep interface com.spendwise.app.data.remote.api.** { *; }

# Keep Gson TypeToken hierarchy (required for generic response deserialization)
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Preserve generic signatures, annotations, and inner-class metadata
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

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
