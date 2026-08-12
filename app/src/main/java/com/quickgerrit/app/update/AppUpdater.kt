package com.quickgerrit.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.quickgerrit.app.BuildConfig
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer APK and downloads/installs it.
 *
 * Expects releases published by the CI workflow with a stable asset name
 * [BuildConfig.UPDATE_APK_NAME] (default: QuickGerrit-release.apk).
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

    fun isConfigured(): Boolean = BuildConfig.GITHUB_REPO.isNotBlank()

    /**
     * Query the latest non-draft GitHub Release and return update info if newer
     * than the currently installed app.
     */
    suspend fun checkForUpdate(context: Context): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) {
            return@withContext Result.failure(IllegalStateException("GITHUB_REPO is not set (CI must pass -PgithubRepo=owner/name)"))
        }

        try {
            val url = "https://api.github.com/repos/$repo/releases/latest"
            AppLog.d("AppUpdater: checking $url")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "QuickGerrit/${BuildConfig.VERSION_NAME}")
                .build()

            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("GitHub API ${response.code}: ${response.message}")
                }
                response.body?.string() ?: throw IllegalStateException("Empty response")
            }

            val release = json.decodeFromString<GhRelease>(body)
            if (release.draft) return@withContext Result.success(null)

            val preferred = BuildConfig.UPDATE_APK_NAME
            val asset = release.assets.firstOrNull { it.name == preferred }
                ?: release.assets.firstOrNull { it.name.endsWith("-release.apk") }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }
                ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

            if (asset == null) {
                AppLog.w("AppUpdater: no APK asset in release ${release.tagName}")
                return@withContext Result.success(null)
            }

            val remoteVersionName = release.tagName.removePrefix("v")
            val remoteCode = parseVersionCode(remoteVersionName)
            val localCode = BuildConfig.VERSION_CODE
            val localName = BuildConfig.VERSION_NAME.removeSuffix("-debug")

            val isNewer = when {
                remoteCode != null && remoteCode > localCode -> true
                remoteCode != null && remoteCode == localCode -> false
                else -> isVersionNameNewer(remoteVersionName, localName)
            }

            AppLog.i("AppUpdater: local=$localName($localCode) remote=$remoteVersionName($remoteCode) newer=$isNewer")

            if (!isNewer) return@withContext Result.success(null)

            Result.success(
                UpdateInfo(
                    tag = release.tagName,
                    versionName = remoteVersionName,
                    versionCode = remoteCode,
                    downloadUrl = asset.browserDownloadUrl,
                    assetName = asset.name,
                    releaseNotes = release.body,
                    htmlUrl = release.htmlUrl
                )
            )
        } catch (e: Exception) {
            AppLog.e("AppUpdater: check failed", e)
            Result.failure(e)
        }
    }

    /**
     * Download the APK into app cache and return a content:// URI suitable for install.
     */
    suspend fun downloadApk(context: Context, info: UpdateInfo): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, info.assetName)
            if (out.exists()) out.delete()

            AppLog.i("AppUpdater: downloading ${info.downloadUrl}")
            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "QuickGerrit/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/octet-stream")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                response.body?.byteStream()?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Empty download body")
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                out
            )
            AppLog.i("AppUpdater: downloaded to $out → $uri")
            Result.success(uri)
        } catch (e: Exception) {
            AppLog.e("AppUpdater: download failed", e)
            Result.failure(e)
        }
    }

    /** Launch the system package installer for the given APK content URI. */
    fun installApk(context: Context, apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Open the release page in a browser as a fallback. */
    fun openReleasePage(context: Context, info: UpdateInfo) {
        val url = info.htmlUrl ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun parseVersionCode(versionName: String): Int? {
        // Supports 1.0.42 → 42, or plain integer tags
        val parts = versionName.trim().split('.', '-', '+')
        return parts.lastOrNull { it.all(Char::isDigit) && it.isNotEmpty() }?.toIntOrNull()
            ?: versionName.filter { it.isDigit() }.toIntOrNull()
    }

    private fun isVersionNameNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }
}
