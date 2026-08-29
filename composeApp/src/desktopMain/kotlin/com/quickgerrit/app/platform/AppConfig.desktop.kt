package com.quickgerrit.app.platform

actual object AppConfig {
    actual val DEBUG: Boolean = true
    actual val VERSION_NAME: String = System.getProperty("quickgerrit.version", "1.0.59")
    actual val VERSION_CODE: Int = System.getProperty("quickgerrit.versionCode", "59").toIntOrNull() ?: 59
    actual val GITHUB_REPO: String = System.getenv("GITHUB_REPOSITORY")
        ?: System.getProperty("quickgerrit.githubRepo", "")
    actual val UPDATE_ASSET_NAME: String = "QuickGerrit.msi" // best-effort; checkForUpdate matches by extension too
}
