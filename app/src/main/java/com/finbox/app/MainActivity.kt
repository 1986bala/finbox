package com.finbox.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val AD_UNIT_ID = "ca-app-pub-9726520012934500/3007586069"
// TODO: replace with a real Interstitial ad unit ID from the AdMob console (create one
// under the same FinBox app) before release. This is Google's public test ID - it always
// serves a "Test Ad"-labeled placeholder and earns nothing.
private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

// Show at most every 3rd return to home, and never more than once per 90s even if that
// threshold is hit quickly - keeps this from feeling like it's punishing normal use.
private const val INTERSTITIAL_EVERY_N_HOME_RETURNS = 3
private const val INTERSTITIAL_MIN_INTERVAL_MS = 90_000L

// A second, independent trigger: after a user has settled on 3 calculations within the
// *same* calculator (slider drags, debounced on the JS side), show one interstitial for
// that screen. Fires once per screen per session, and still respects the shared cooldown
// above so this can't stack back-to-back with the home-return trigger.
private const val CALC_INTERSTITIAL_THRESHOLD = 3

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adView: AdView
    private var interstitialAd: InterstitialAd? = null
    private var homeReturnCount = 0
    private var lastInterstitialShownAt = 0L
    private val calcUseCounts = mutableMapOf<String, Int>()
    private val calcInterstitialShownFor = mutableSetOf<String>()

    // Updated from JS (see ScreenBridge below) whenever the SPA navigates. Used so the
    // Android back button goes to the app's own home screen first, then exits on a
    // second press, instead of exiting immediately from any screen.
    @Volatile private var currentScreen: String = "home"

    private inner class ScreenBridge {
        @JavascriptInterface
        fun onScreenChanged(screenId: String) {
            val previousScreen = currentScreen
            currentScreen = screenId
            // Only on the way back to home, and never right after Top Funds - an
            // interstitial landing right next to the Groww affiliate link would be
            // both bad UX and look manipulative.
            if (screenId == "home" && previousScreen != "home" && previousScreen != "topfunds") {
                runOnUiThread { maybeShowInterstitial() }
            }
        }

        // Opens affiliate/outbound links in the system browser rather than the
        // in-app WebView - affiliate tracking cookies/referrers are unreliable
        // inside an embedded WebView. Scheme is restricted to https so page JS
        // can't use this bridge to launch arbitrary intents.
        @JavascriptInterface
        fun openExternalLink(url: String) {
            if (!url.startsWith("https://")) return
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: ActivityNotFoundException) {
                // No browser available to handle the link - nothing to fall back to.
            }
        }

        // JS already debounces this to one call per settled slider drag, not per drag
        // tick - see notifyCalculationSettled() in nivesh-calc.html.
        @JavascriptInterface
        fun onCalculationSettled(screenId: String) {
            runOnUiThread { maybeShowCalcInterstitial(screenId) }
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            runOnUiThread {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("Referral code", text))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15+ (targetSdk 35) draws content edge-to-edge under the status/nav
        // bars by default. The header is an HTML element the WebView can't request
        // insets for on its own, so pad the native root view for both bars - bottom
        // padding also matters whenever the ad banner hasn't loaded yet (or fails to
        // load), since without it the WebView expands to fill that space and its
        // bottom-most HTML content (nav row, calculator buttons) ends up drawn under
        // the system gesture/nav bar.
        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(ScreenBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/nivesh-calc.html")

        val adContainer: FrameLayout = findViewById(R.id.adContainer)
        adView = AdView(this)
        adView.adUnitId = AD_UNIT_ID
        adView.setAdSize(adaptiveBannerAdSize())
        adContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())

        loadInterstitialAd()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    webView.canGoBack() -> webView.goBack()
                    currentScreen != "home" -> webView.evaluateJavascript("goHome();", null)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun loadInterstitialAd() {
        InterstitialAd.load(
            this,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadInterstitialAd() // preload the next one
                        }
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            loadInterstitialAd()
                        }
                    }
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun maybeShowInterstitial() {
        homeReturnCount++
        if (homeReturnCount % INTERSTITIAL_EVERY_N_HOME_RETURNS != 0) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS) return
        val ad = interstitialAd ?: return
        lastInterstitialShownAt = now
        ad.show(this)
    }

    private fun maybeShowCalcInterstitial(screenId: String) {
        val count = (calcUseCounts[screenId] ?: 0) + 1
        calcUseCounts[screenId] = count
        if (count < CALC_INTERSTITIAL_THRESHOLD) return
        if (calcInterstitialShownFor.contains(screenId)) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS) return
        val ad = interstitialAd ?: return
        calcInterstitialShownFor.add(screenId)
        lastInterstitialShownAt = now
        ad.show(this)
    }

    // Adaptive banners size themselves from the device's screen width at runtime —
    // there's no valid static XML value for this, it must be computed and set in code
    // before loadAd() is called.
    private fun adaptiveBannerAdSize(): AdSize {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val density = displayMetrics.density
        val adWidthPixels = displayMetrics.widthPixels
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
    }

    override fun onDestroy() {
        adView.destroy()
        webView.destroy()
        super.onDestroy()
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
    }

}
