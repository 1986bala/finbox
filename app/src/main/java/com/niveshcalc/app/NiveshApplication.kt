package com.niveshcalc.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class NiveshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob once, at app startup.
        MobileAds.initialize(this) { /* initialization status, ignored */ }
    }
}
