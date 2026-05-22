# SpendWise Android — Build Instructions

## What you need (one-time install)
1. **Android Studio** — download free from developer.android.com/studio
2. **Java 17** — comes bundled with Android Studio
3. **Android SDK** — Android Studio installs this automatically

---

## Step 1 — Copy the web app into the project

Copy `index.html` from the SpendWise folder into the assets folder:

```
SpendWise/index.html  →  android/app/src/main/assets/index.html
```

Do this every time you update index.html.

---

## Step 2 — Open project in Android Studio

1. Open Android Studio
2. File → Open → select the `android/` folder
3. Wait for Gradle sync to finish (first time takes 2-3 minutes, downloads dependencies)

---

## Step 3 — Create app icon (optional but recommended)

1. Right-click `app/src/main/res` → New → Image Asset
2. Choose a launcher icon (you can use the SpendWise gradient colors #6C63FF → #EC4899)
3. Android Studio generates all mipmap sizes automatically

---

## Step 4 — Build and install (Debug APK — for personal use)

### Via Android Studio UI:
- Connect your Android phone via USB
- Enable USB Debugging: Settings → Developer Options → USB Debugging
- Click the green ▶ Run button in Android Studio
- App installs and opens on your phone

### Via command line:
```bash
cd android
./gradlew installDebug
```

The APK is saved at:
`android/app/build/outputs/apk/debug/app-debug.apk`

Transfer this APK to your phone and install it. You may need to enable:
**Settings → Security → Install unknown apps → Allow from this source**

---

## Step 5 — Build Release APK (for sharing / trusted install)

### Generate a keystore (do this once — keep it safe forever):
```bash
keytool -genkey -v -keystore spendwise.keystore -alias spendwise \
  -keyalg RSA -keysize 2048 -validity 10000
```
Remember the passwords you set — you need them to update the app later.

### Configure signing in app/build.gradle:
```groovy
signingConfigs {
    release {
        storeFile file('../spendwise.keystore')
        storePassword 'YOUR_STORE_PASSWORD'
        keyAlias 'spendwise'
        keyPassword 'YOUR_KEY_PASSWORD'
    }
}
```

Then uncomment `signingConfig signingConfigs.release` in the release buildType.

### Build signed APK:
```bash
./gradlew assembleRelease
```

APK location: `app/build/outputs/apk/release/app-release.apk`

---

## Permissions the app requests

| Permission | Why |
|---|---|
| READ_SMS | Read bank messages to auto-detect transactions |
| INTERNET | Cloud sync with Firebase Realtime Database |
| ACCESS_NETWORK_STATE | Check if online before syncing |
| WRITE_EXTERNAL_STORAGE | Export CSV/PDF reports (Android 8 only) |

---

## Security features built in

- **Biometric lock** — fingerprint or face unlock required to open app
- **Screenshot prevention** — FLAG_SECURE prevents screen capture
- **HTTPS only** — network_security_config blocks all HTTP traffic
- **No data sent anywhere** — all data is local (except Firebase if you set it up)
- **ProGuard** — release APK is obfuscated

---

## Updating the app

1. Edit `SpendWise/index.html`
2. Copy it to `android/app/src/main/assets/index.html`
3. Increment `versionCode` in app/build.gradle
4. Build and install — user data is preserved (stored in WebView localStorage)

---

## Sharing with others

Send them the `app-release.apk` file. They install it and get a fresh database.
Their data never mixes with yours — each phone has its own localStorage.

For cloud sync: each person sets up their own Firebase project with their own sync key.
