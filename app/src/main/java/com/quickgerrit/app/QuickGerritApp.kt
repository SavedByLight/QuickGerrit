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
        accountStore = AccountStore(this)
        repository = GerritRepository(accountStore)
    }
}
