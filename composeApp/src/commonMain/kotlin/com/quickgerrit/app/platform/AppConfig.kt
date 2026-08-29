package com.quickgerrit.app.platform

/**
 * Multiplatform replacement for Android BuildConfig.
 */
expect object AppConfig {
    val DEBUG: Boolean
    val VERSION_NAME: String
    val VERSION_CODE: Int
    val GITHUB_REPO: String
    /** Preferred asset name when checking GitHub Releases (Android APK or desktop package). */
    val UPDATE_ASSET_NAME: String
}
