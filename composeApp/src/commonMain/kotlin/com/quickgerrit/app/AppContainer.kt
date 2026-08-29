package com.quickgerrit.app

import com.quickgerrit.app.data.local.AccountStore
import com.quickgerrit.app.data.repository.GerritRepository

/**
 * Simple service locator shared by Android and Desktop.
 */
object AppContainer {
    val accountStore: AccountStore by lazy { AccountStore() }
    val repository: GerritRepository by lazy { GerritRepository(accountStore) }
}
