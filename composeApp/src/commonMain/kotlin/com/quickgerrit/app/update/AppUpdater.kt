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
 * Checks GitHub Releases for a newer build.
 * On desktop, opens the release page / download URL in the browser.
 * On Android, the platform layer can still download+install an APK.
 */
object AppUpdater {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
        val versionCode: Int?,
        val downloadUrl: String,
        val assetName: String,
        val releaseNotes: String?,
        val htmlUrl: String?
    )

    fun isConfigured(): Boolean = AppConfig.GITHUB_REPO.isNotBlank()

    suspend fun checkForUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val repo = AppConfig.GITHUB_REPO
        if (repo.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("GITHUB_REPO is not set")
            )
        }
        try {
            val url = "https://api.github.com/repos/$repo/releases/latest"
            AppLog.d("AppUpdater: checking $url")
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
            if (release.draft) return@withContext Result.success(null)

            val preferred = AppConfig.UPDATE_ASSET_NAME
            val asset = release.assets.firstOrNull { it.name == preferred }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".msi", true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".deb", true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".AppImage", true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".rpm", true) }
                ?: release.assets.firstOrNull()

            if (asset == null) {
                AppLog.w("AppUpdater: no downloadable asset on ${release.tagName}")
                return@withContext Result.success(null)
            }

            val remoteTag = release.tagName.removePrefix("v")
            val local = AppConfig.VERSION_NAME
            if (!isNewer(remoteTag, local)) {
                AppLog.d("AppUpdater: up to date (local=$local remote=$remoteTag)")
                return@withContext Result.success(null)
            }

            val info = UpdateInfo(
                tag = release.tagName,
                versionName = remoteTag,
                versionCode = remoteTag.substringAfterLast('.').toIntOrNull(),
                downloadUrl = asset.browserDownloadUrl,
                assetName = asset.name,
                releaseNotes = release.body,
                htmlUrl = release.htmlUrl
            )
            AppLog.i("AppUpdater: update available ${info.versionName} (${info.assetName})")
            Result.success(info)
        } catch (t: Throwable) {
            AppLog.e("AppUpdater: check failed", t)
            Result.failure(t)
        }
    }

    /** Open download or release page in the system browser / handler. */
    fun openUpdate(info: UpdateInfo) {
        val url = info.downloadUrl.ifBlank { info.htmlUrl.orEmpty() }
        if (url.isNotBlank()) openUrl(url)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
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
