package com.finbox.app

import android.annotation.SuppressLint
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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val AD_UNIT_ID = "ca-app-pub-9726520012934500/1001393927"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var adView: AdView

    // Updated from JS (see ScreenBridge below) whenever the SPA navigates. Used so the
    // Android back button goes to the app's own home screen first, then exits on a
    // second press, instead of exiting immediately from any screen.
    @Volatile private var currentScreen: String = "home"

    private inner class ScreenBridge {
        @JavascriptInterface
        fun onScreenChanged(screenId: String) {
            currentScreen = screenId
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 15+ (targetSdk 35) draws content edge-to-edge under the status/nav
        // bars by default. The header is an HTML element the WebView can't request
        // insets for on its own, so pad the native root view for the status bar only -
        // the bottom ad banner is meant to sit flush at the screen edge, same as most
        // AdMob-integrated apps, so no bottom inset padding is added.
        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
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
