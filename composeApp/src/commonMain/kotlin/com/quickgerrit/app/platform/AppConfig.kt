package com.quickgerrit.app.platform

/**
 * Multiplatform replacement for Android BuildConfig.
 * Desktop values come from GeneratedVersion (written by Gradle at build time).
 */
expect object AppConfig {
    val DEBUG: Boolean
    val VERSION_NAME: String
    val VERSION_CODE: Int
    val GITHUB_REPO: String
}
