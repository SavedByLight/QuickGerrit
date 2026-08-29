package com.quickgerrit.app

import android.app.Application
import com.quickgerrit.app.platform.AndroidContextHolder

class QuickGerritApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextHolder.appContext = applicationContext
        // Warm up shared container
        AppContainer.repository
    }
}
