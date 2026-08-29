package com.quickgerrit.app.platform

/**
 * Desktop version is injected at build time via [GeneratedVersion]
 * (see composeApp/build.gradle.kts generateDesktopVersion task).
 * System properties remain as a runtime override for local runs.
 */
actual object AppConfig {
    actual val DEBUG: Boolean =
        System.getProperty("quickgerrit.debug", "true").toBoolean()

    actual val VERSION_NAME: String =
        System.getProperty("quickgerrit.version")
            ?: GeneratedVersion.VERSION_NAME

    actual val VERSION_CODE: Int =
        System.getProperty("quickgerrit.versionCode")?.toIntOrNull()
            ?: GeneratedVersion.VERSION_CODE

    actual val GITHUB_REPO: String =
        System.getenv("GITHUB_REPOSITORY")
            ?: System.getProperty("quickgerrit.githubRepo")
            ?: GeneratedVersion.GITHUB_REPO
}
