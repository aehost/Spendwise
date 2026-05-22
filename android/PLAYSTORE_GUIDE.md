# SpendWise — Complete Play Store Submission Guide

This guide takes you from zero to a live Play Store listing.
Estimated total time: 2–4 hours (most of it waiting for Android Studio to build).

---

## Overview of steps

1. Install Android Studio
2. Build a signed AAB (Android App Bundle)
3. Generate your keystore (signing certificate)
4. Create a Google Play Developer account
5. Create the app listing
6. Handle the SMS permission declaration
7. Create a privacy policy
8. Prepare store assets
9. Submit for review
10. After launch — how to push updates

---

## Step 1 — Install Android Studio

Download from: https://developer.android.com/studio

During install, let it download:
- Android SDK
- Android SDK Platform 34 (API 34)
- Android Emulator (optional)

Java 17 is bundled — no separate install needed.

---

## Step 2 — Copy index.html into the project

Every time you update the web app:

```
SpendWise/index.html  →  android/app/src/main/assets/index.html
```

---

## Step 3 — Generate your keystore (do this ONCE, keep it FOREVER)

A keystore is your app's permanent identity on the Play Store.
**If you lose it, you can never update your app. Back it up to Google Drive.**

Open Command Prompt (not PowerShell) in the `android/` folder:

```cmd
keytool -genkey -v -keystore spendwise.keystore -alias spendwise ^
  -keyalg RSA -keysize 2048 -validity 10000
```

You will be asked:
- **Keystore password** — choose something strong, write it down
- **Key password** — can be the same as keystore password
- **Your name** — can be your real name or "SpendWise"
- **Organization / City / State / Country** — fill in honestly

This creates `android/spendwise.keystore`. Back this up now.

### Wire the keystore into build.gradle

Open `android/app/build.gradle` and fill in the `signingConfigs` block:

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

Then uncomment `signingConfig signingConfigs.release` in the `release` buildType:

```groovy
release {
    minifyEnabled true
    shrinkResources true
    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                  'proguard-rules.pro'
    signingConfig signingConfigs.release   // ← uncomment this line
}
```

---

## Step 4 — Build the AAB (Android App Bundle)

Play Store requires AAB format (not APK) for new apps.

### Via Android Studio:
1. Open Android Studio → File → Open → select `android/` folder
2. Wait for Gradle sync to finish
3. Menu: **Build → Generate Signed Bundle / APK**
4. Choose **Android App Bundle**, click Next
5. Select your keystore file, enter passwords, select `release` variant
6. Click **Finish**

Output file: `android/app/build/outputs/bundle/release/app-release.aab`

### Via command line (Windows):
```cmd
cd android
gradlew.bat bundleRelease
```

---

## Step 5 — Create Google Play Developer Account

1. Go to: https://play.google.com/console
2. Sign in with your Google account
3. Click **Get Started**
4. Pay the **one-time $25 USD registration fee**
5. Fill in developer name (shown publicly), email, phone number, website (optional)
6. Accept the Developer Distribution Agreement
7. Account is ready within minutes

---

## Step 6 — Create your app in Play Console

1. In Play Console click **Create app**
2. Fill in:
   - **App name**: SpendWise
   - **Default language**: English (or your language)
   - **App or game**: App
   - **Free or paid**: Free
3. Accept policies → **Create app**

---

## Step 7 — Fill in the Store Listing

Go to **Store presence → Main store listing**

### Short description (max 80 chars)
```
Track your spending, loans, credit cards & investments — all offline.
```

### Full description (max 4000 chars — use something like this)
```
SpendWise is a personal finance tracker that works completely offline.
Your financial data stays on your device — nothing is sent to any server
unless you choose to enable cloud sync.

Features:
• Track daily expenses with categories
• Monitor bank balance from SMS messages
• Credit card tracking with available limit display
• Loan tracking — see how transactions affect your loan
• SIP / Angel One investment tracking
• Mandatory monthly bills reminder (electricity, water, internet)
• Transfer money to / receive from family and friends
• Monthly reports — download or view by category
• Pending transactions alert until properly tagged
• Biometric lock (fingerprint / face unlock)
• Cloud backup via Firebase (optional — your own key)
• JSON export / import for data portability

No account required. No ads. No subscriptions.
```

---

## Step 8 — Prepare store assets

You need these images:

| Asset | Size | Where to get |
|---|---|---|
| App icon | 512 × 512 px PNG | Create in Canva / Figma |
| Feature graphic | 1024 × 500 px PNG | Create in Canva |
| Phone screenshots | min 2, max 8 | Take on real device or emulator |

### Quick icon in Canva:
1. Go to canva.com → Create design → Custom size 512×512
2. Use gradient background: #6C63FF → #EC4899
3. Add white text "SW" or "₹" symbol
4. Download as PNG

### Screenshots:
Connect your phone via USB, install the debug APK, take screenshots from:
- Home/dashboard screen
- Transactions list
- Monthly report
- Settings / credit cards view

---

## Step 9 — Handle the READ_SMS permission (most important step)

READ_SMS is a **sensitive permission**. Google requires you to:
1. Justify why you need it
2. Host a privacy policy
3. Submit to a permissions review (takes 1–7 days extra)

### In Play Console → App content → Permissions:

Select **READ_SMS** and choose:

> **Core functionality** — The app reads bank SMS to automatically detect and suggest transactions. Users can still add transactions manually without granting this permission.

### Declaration to write:
```
SpendWise reads SMS messages solely to detect bank transaction notifications
(debits/credits) and pre-fill transaction amounts. The app only reads messages
from known bank sender patterns. No SMS content is transmitted off the device.
Users who decline the SMS permission can still use the full app manually.
```

---

## Step 10 — Create and host a Privacy Policy

Google requires a privacy policy URL for apps that read SMS.

### Easiest free option — GitHub Pages:

1. Create a free GitHub account at github.com
2. Create a new repository called `spendwise-privacy`
3. Add a file `index.html` with this content:

```html
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>SpendWise Privacy Policy</title></head>
<body style="font-family:sans-serif;max-width:800px;margin:40px auto;padding:0 20px">
<h1>SpendWise Privacy Policy</h1>
<p><strong>Last updated: May 2025</strong></p>

<h2>Data Collection</h2>
<p>SpendWise stores all data locally on your device. We do not collect, transmit,
or store any personal or financial data on our servers.</p>

<h2>SMS Permission</h2>
<p>If you grant the SMS permission, SpendWise reads SMS messages to detect bank
transaction notifications. This data is processed on-device only and is never
transmitted anywhere.</p>

<h2>Optional Cloud Sync</h2>
<p>If you choose to enable cloud sync, your transaction data is stored in a
Firebase Realtime Database project you control, using an encryption key you
provide. We have no access to this data.</p>

<h2>Contact</h2>
<p>Questions: gvp211093@gmail.com</p>
</body>
</html>
```

4. Go to repository Settings → Pages → Deploy from branch (main)
5. Your policy URL will be: `https://YOUR-USERNAME.github.io/spendwise-privacy`

Paste this URL in Play Console → Store listing → Privacy policy.

---

## Step 11 — Fill in Data Safety section

Go to **App content → Data safety**

| Question | Answer |
|---|---|
| Does your app collect/share user data? | No (unless Firebase sync enabled, which is user-controlled) |
| Is all data encrypted in transit? | Yes |
| Can users request data deletion? | Yes (Settings → Export/Delete in app) |

For SMS:
- Collected: No (processed on device, not collected)
- Shared: No

---

## Step 12 — Content rating questionnaire

Go to **App content → App content rating**

- Category: **Finance**
- Violence: None
- Sexual content: None
- Profanity: None

This gives you a **PEGI 3 / Everyone** rating.

---

## Step 13 — Upload your AAB and submit

1. Go to **Release → Production → Create new release**
2. Click **Upload** → select `app-release.aab`
3. Release notes (what's new):
   ```
   Initial release of SpendWise — personal finance tracker with bank SMS
   auto-detection, credit cards, loans, investments, and monthly reports.
   ```
4. Click **Review release** → **Start rollout to Production**

---

## Review timeline

| Stage | Time |
|---|---|
| Initial review | 3–7 days (sometimes up to 14 for SMS permission) |
| SMS permission review | Additional 1–7 days |
| App goes live | Immediately after approval |

You'll get an email at gvp211093@gmail.com when approved or if action is required.

---

## Step 14 — After launch: pushing updates

1. Edit `SpendWise/index.html`
2. Copy to `android/app/src/main/assets/index.html`
3. Open `android/app/build.gradle` → increment `versionCode` (1 → 2, etc.)
4. Also increment `versionName` ("4.0.0" → "4.1.0")
5. Build new AAB: **Build → Generate Signed Bundle**
6. In Play Console → Production → **Create new release** → Upload new AAB
7. Write release notes → Roll out

User data is preserved — it's in the phone's app storage (WebView localStorage).

---

## Common Play Store rejection reasons (and fixes)

| Rejection | Fix |
|---|---|
| SMS permission not justified | Strengthen the declaration — be very specific |
| Privacy policy URL broken | Make sure GitHub Pages is published |
| App crashes on review device | Test on Android 8.0 (API 26) specifically |
| Missing screenshots | Add at least 2 screenshots |
| App name already taken | Try "SpendWise - Finance Tracker" |

---

## Files reference

```
android/
  spendwise.keystore          ← YOUR KEY — back up to Google Drive
  app/build.gradle            ← versionCode and signingConfigs go here
  app/build/outputs/
    bundle/release/app-release.aab    ← upload this to Play Store
    apk/debug/app-debug.apk           ← install this on your phone for testing
```
