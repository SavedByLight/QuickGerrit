package com.quickgerrit.app.platform

import com.quickgerrit.app.BuildConfig

actual object AppConfig {
    actual val DEBUG: Boolean = BuildConfig.DEBUG
    actual val VERSION_NAME: String = BuildConfig.VERSION_NAME
    actual val VERSION_CODE: Int = BuildConfig.VERSION_CODE
    actual val GITHUB_REPO: String = BuildConfig.GITHUB_REPO
    actual val UPDATE_ASSET_NAME: String = BuildConfig.UPDATE_APK_NAME
}
