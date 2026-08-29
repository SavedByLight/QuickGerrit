package com.quickgerrit.app

import android.app.Application
import com.quickgerrit.app.data.local.AccountStore
import com.quickgerrit.app.data.repository.GerritRepository

class QuickGerritApp : Application() {
    lateinit var accountStore: AccountStore
        private set
    lateinit var repository: GerritRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Platform-specific AccountStore / Repository wiring belongs here once
        // expect/actual (or multiplatform-settings) is fully implemented.
        // Placeholder to satisfy the AndroidManifest application class.
        try {
            accountStore = AccountStore(this)
            repository = GerritRepository(accountStore)
        } catch (t: Throwable) {
            // Keep app launchable while commonMain adapters are finished
            android.util.Log.w("QuickGerrit", "Init deferred: ${t.message}")
        }
    }
}
