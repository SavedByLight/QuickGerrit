package com.quickgerrit.app.platform

/**
 * Version and repo come from GeneratedVersion (Gradle task generateDesktopVersion).
 * System properties can override for local debugging.
 */
actual object AppConfig {
    actual val DEBUG: Boolean =
        System.getProperty("quickgerrit.debug", "true").toBooleanStrictOrNull() ?: true

    actual val VERSION_NAME: String =
        System.getProperty("quickgerrit.version")
            ?.takeIf { it.isNotBlank() && it != "0.0.0" }
            ?: GeneratedVersion.VERSION_NAME.takeIf { it.isNotBlank() && it != "0.0.0" }
            ?: "1.0.0"

    actual val VERSION_CODE: Int =
        System.getProperty("quickgerrit.versionCode")?.toIntOrNull()
            ?: GeneratedVersion.VERSION_CODE.takeIf { it > 0 }
            ?: 1

    actual val GITHUB_REPO: String =
        System.getenv("GITHUB_REPOSITORY")
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("quickgerrit.githubRepo")
                ?.takeIf { it.isNotBlank() }
            ?: GeneratedVersion.GITHUB_REPO
}
