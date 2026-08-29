package com.quickgerrit.app.update

import com.quickgerrit.app.platform.AppConfig
import com.quickgerrit.app.platform.openUrl
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer build and notifies the user.
 * Does **not** download or install updates inside the app — the user
 * chooses "Download now" (opens browser) or "Later".
 */
object AppUpdater {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class GhRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val assets: List<GhAsset> = emptyList(),
        val prerelease: Boolean = false,
        val draft: Boolean = false
    )

    @Serializable
    data class GhAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        val size: Long = 0
    )

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val releaseNotes: String?,
        val htmlUrl: String?,
        /** Best matching asset download URL (optional; prefer htmlUrl for the release page). */
        val downloadUrl: String?
    )

    fun isConfigured(): Boolean = AppConfig.GITHUB_REPO.isNotBlank()

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val repo = AppConfig.GITHUB_REPO
        if (repo.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("GITHUB_REPO is not set (pass -PgithubRepo=owner/name)")
            )
        }
        try {
            val url = "https://api.github.com/repos/$repo/releases/latest"
            AppLog.d("AppUpdater: checking $url (local=${AppConfig.VERSION_NAME} code=${AppConfig.VERSION_CODE})")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "QuickGerrit/${AppConfig.VERSION_NAME}")
                .build()

            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub API ${response.code}: ${response.message}")
                }
                response.body?.string() ?: throw IllegalStateException("Empty response")
            }

            val release = json.decodeFromString<GhRelease>(body)
            if (release.draft || release.prerelease) {
                return@withContext Result.success(null)
            }

            val remoteName = release.tagName.removePrefix("v").trim()
            val localName = AppConfig.VERSION_NAME.removePrefix("v").removeSuffix("-debug").trim()
            val remoteCode = parseVersionCode(remoteName)
            val localCode = AppConfig.VERSION_CODE

            val isNewer = when {
                remoteCode != null && remoteCode > localCode -> true
                remoteCode != null && remoteCode == localCode -> false
                else -> isVersionNameNewer(remoteName, localName)
            }

            AppLog.i(
                "AppUpdater: local=$localName($localCode) remote=$remoteName($remoteCode) newer=$isNewer"
            )

            if (!isNewer) return@withContext Result.success(null)

            // Prefer release page; asset URL is optional helper
            val assetUrl = release.assets
                .firstOrNull { it.name.contains("AppImage", true) }
                ?.browserDownloadUrl
                ?: release.assets.firstOrNull { it.name.endsWith(".msi", true) }?.browserDownloadUrl
                ?: release.assets.firstOrNull { it.name.endsWith(".deb", true) }?.browserDownloadUrl
                ?: release.assets.firstOrNull { it.name.endsWith(".apk", true) }?.browserDownloadUrl
                ?: release.assets.firstOrNull()?.browserDownloadUrl

            Result.success(
                UpdateInfo(
                    tag = release.tagName,
                    versionName = remoteName,
                    releaseNotes = release.body,
                    htmlUrl = release.htmlUrl,
                    downloadUrl = assetUrl
                )
            )
        } catch (t: Throwable) {
            AppLog.e("AppUpdater: check failed", t)
            Result.failure(t)
        }
    }

    /** Open the GitHub release page (or asset URL) in the system browser. No in-app download. */
    fun openDownloadPage(info: UpdateInfo) {
        val url = info.htmlUrl?.takeIf { it.isNotBlank() }
            ?: info.downloadUrl?.takeIf { it.isNotBlank() }
            ?: return
        AppLog.i("AppUpdater: opening $url")
        openUrl(url)
    }

    /** @deprecated Use [openDownloadPage] — kept for call-site compatibility. */
    fun openUpdate(info: UpdateInfo) = openDownloadPage(info)

    private fun parseVersionCode(versionName: String): Int? {
        // "1.0.70" -> 70, "1.0.70-debug" -> 70
        val cleaned = versionName.removePrefix("v").substringBefore('-')
        val parts = cleaned.split('.')
        return when {
            parts.size >= 3 -> parts[2].filter { it.isDigit() }.toIntOrNull()
            parts.size == 2 -> {
                val major = parts[0].toIntOrNull() ?: return null
                val minor = parts[1].toIntOrNull() ?: return null
                major * 100 + minor
            }
            else -> cleaned.filter { it.isDigit() }.toIntOrNull()
        }
    }

    private fun isVersionNameNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-')
            .split('.')
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
