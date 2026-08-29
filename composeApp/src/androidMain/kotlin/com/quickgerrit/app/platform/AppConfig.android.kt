package com.quickgerrit.app.platform

import com.quickgerrit.app.BuildConfig

actual object AppConfig {
    actual val DEBUG: Boolean = BuildConfig.DEBUG
    actual val VERSION_NAME: String = BuildConfig.VERSION_NAME
        .removePrefix("v")
        .removeSuffix("-debug")
    actual val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    actual val GITHUB_REPO: String = BuildConfig.GITHUB_REPO
}
