# Keep JavaScript bridge methods (called via reflection from WebView)
-keepclassmembers class com.spendwise.app.SMSBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Keep WebView JS interface
-keepattributes JavascriptInterface

# Standard Android rules
-dontwarn android.webkit.**
