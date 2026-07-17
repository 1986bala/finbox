# Nivesh Calc — Android app

A WebView-wrapped Android app showing the Nivesh Calc financial calculators (SIP, Step-up SIP,
Lumpsum, FD, RD, SWP, Loan/EMI), with a native AdMob banner ad.

## 1. Open and run it

1. Install **Android Studio** (Hedgehog or newer): https://developer.android.com/studio
2. File → Open → select the `NiveshCalcApp` folder.
3. Let Gradle sync (needs internet — it downloads the Android Gradle Plugin and the
   AdMob SDK from Google's Maven repo the first time).
4. Click **Run ▶** with a phone plugged in (USB debugging on) or an emulator.
   This installs a debug build straight to the device — nothing to "test" further,
   it behaves exactly like the final app.

Right now it loads Google's **official test ad unit IDs**, so you'll see AdMob's
sample test ads immediately — this confirms the integration works before you have
your own account approved.

## 2. Before you publish: swap in your real ad IDs

You need your own AdMob account (free, instant) at https://admob.google.com:
1. Create an app in AdMob → get your **App ID** (`ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy`).
2. Create a **Banner ad unit** → get your **Ad unit ID** (`ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz`).
3. Replace both test IDs:
   - `app/src/main/AndroidManifest.xml` → the `com.google.android.gms.ads.APPLICATION_ID` meta-data value
   - `app/src/main/res/layout/activity_main.xml` → the `ads:adUnitId` value
4. **Important:** Google can suspend your AdMob account if real ad units ship with test IDs,
   or vice versa — always confirm both are swapped before release.

## 3. Build a signed release APK / AAB

In Android Studio: **Build → Generate Signed App Bundle / APK**
- Choose **Android App Bundle (.aab)** — this is what the Play Store requires for new apps.
- Create a new keystore the first time (back it up — losing it means you can never
  update the app again under the same listing).
- Choose the `release` build variant.

## 4. Publish on the Play Store — checklist

- Google Play Console account: one-time **$25 registration** at https://play.google.com/console
- App content: complete the content rating questionnaire, target audience, data
  safety form (declare that AdMob collects advertising data — Play requires this)
- A **privacy policy URL** is mandatory once you show ads — even a simple hosted
  page stating "we show ads via Google AdMob, which may collect device/ad identifiers;
  no personal financial data you enter is stored or transmitted" is enough. Any free
  privacy-policy generator or a one-page site works.
- Store listing assets needed: app icon (512×512) and feature graphic
  (1024×500), both included in `store-assets/`, plus 2+ phone screenshots
  (take these from your Run ▶ session).
- Listing copy (short/full description, category, keywords) is drafted in
  `store-assets/store-listing.md` — paste it into Play Console.
- Upload the `.aab` from step 3, fill the listing, submit for review
  (review typically takes a few hours to a couple of days).

## What's included

- `app/src/main/assets/nivesh-calc.html` — the calculator UI (same one from the web version)
- `app/src/main/java/com/niveshcalc/app/MainActivity.kt` — loads the calculator in a WebView,
  loads the AdMob banner
- `app/src/main/java/com/niveshcalc/app/NiveshApplication.kt` — initializes the AdMob SDK
- `store-assets/play-store-icon-512.png` — ready-to-upload Play Store listing icon
- `store-assets/feature-graphic-1024x500.png` — ready-to-upload Play Store feature graphic
- `store-assets/store-listing.md` — drafted store listing copy (title, descriptions, keywords)
