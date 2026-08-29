package com.quickgerrit.app.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Multiplatform stand-in for androidx.lifecycle.ViewModel.
 * Uses Default dispatcher so it works on JVM without a Main dispatcher installed.
 * UI collectors still hop to the Compose UI thread via collectAsState.
 */
open class PlatformViewModel {
    private val job = SupervisorJob()
    val viewModelScope: CoroutineScope = CoroutineScope(job + Dispatchers.Default)

    open fun onCleared() {
        job.cancel()
    }
}
