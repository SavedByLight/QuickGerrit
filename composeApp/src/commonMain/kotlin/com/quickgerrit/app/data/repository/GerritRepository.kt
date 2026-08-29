package com.quickgerrit.app.data.repository

import com.quickgerrit.app.data.api.GerritApi
import com.quickgerrit.app.data.api.GerritClientFactory
import com.quickgerrit.app.data.local.AccountStore
import com.quickgerrit.app.data.model.*
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class GerritRepository(
    private val accountStore: AccountStore
) {
    val accounts: Flow<List<GerritAccount>> = accountStore.accounts
    val activeAccountId: Flow<String?> = accountStore.activeAccountId

    val activeAccount: Flow<GerritAccount?> = combine(accounts, activeAccountId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }

    private suspend fun api(): GerritApi {
        val account = activeAccount.first()
            ?: throw IllegalStateException("No account configured")
        return GerritClientFactory.create(account)
    }

    suspend fun testLogin(account: GerritAccount): AccountInfo {
        // Normalize the same way the HTTP client does — avoids desktop paste whitespace issues
        val normalized = account.copy(
            username = account.username.trim(),
            httpPassword = account.httpPassword.trim(),
            baseUrl = account.baseUrl.trim().trimEnd('/')
        )
        AppLog.i(
            "Testing login for ${normalized.username} @ ${normalized.baseUrl} " +
                "(userLen=${normalized.username.length} passLen=${normalized.httpPassword.length})"
        )
        return try {
            val client = GerritClientFactory.create(normalized)
            val self = try {
                client.getSelf()
            } catch (e: retrofit2.HttpException) {
                // Some old Gerrit builds are picky about the trailing slash on /accounts/self
                if (e.code() == 404 || e.code() == 403) {
                    AppLog.w("getSelf failed HTTP ${e.code()}; retrying with trailing slash")
                    client.getSelfTrailingSlash()
                } else {
                    throw e
                }
            }
            AppLog.i("Login OK: ${self.displayName ?: self.name ?: self.username} (id=${self.accountId})")
            self
        } catch (e: Exception) {
            AppLog.e("Login failed for ${normalized.username} @ ${normalized.baseUrl}", e)
            throw e
        }
    }

    suspend fun addAccount(account: GerritAccount) = accountStore.addAccount(account).also {
        GerritClientFactory.invalidate()
        AppLog.i("Account added: ${it.name} (${it.id})")
    }

    suspend fun updateAccount(account: GerritAccount) {
        accountStore.updateAccount(account)
        GerritClientFactory.invalidate(account.id)
        AppLog.i("Account updated: ${account.name} (${account.id})")
    }

    suspend fun removeAccount(id: String) {
        accountStore.removeAccount(id)
        GerritClientFactory.invalidate(id)
        AppLog.i("Account removed: $id")
    }

    suspend fun setActive(id: String) {
        accountStore.setActiveAccount(id)
        // Client is keyed by account; no need to clear all, but next api() picks new active
        AppLog.i("Active account set to: $id")
    }

    suspend fun queryChanges(status: String, queryExtra: String = "", limit: Int = 50, start: Int = 0): List<ChangeInfo> {
        val q = buildString {
            append("status:$status")
            if (queryExtra.isNotBlank()) append(" $queryExtra")
        }
        return queryChangesRaw(q, limit, start)
    }

    /** Arbitrary Gerrit change query (e.g. dashboard sections). */
    suspend fun queryChangesRaw(query: String, limit: Int = 25, start: Int = 0): List<ChangeInfo> {
        AppLog.d("queryChangesRaw q='$query' limit=$limit start=$start")
        return try {
            val result = api().queryChanges(query = query, limit = limit, start = start)
            AppLog.d("queryChangesRaw returned ${result.size} changes")
            result
        } catch (e: Exception) {
            AppLog.e("queryChangesRaw failed for q='$query'", e)
            throw e
        }
    }

    suspend fun getChangeDetail(changeId: String): ChangeInfo {
        AppLog.d("getChangeDetail $changeId")
        return try {
            val detail = api().getChangeDetail(changeId)
            AppLog.d("getChangeDetail OK: ${detail.subject} status=${detail.status}")
            detail
        } catch (e: Exception) {
            AppLog.e("getChangeDetail failed for $changeId", e)
            throw e
        }
    }

    suspend fun listFiles(changeId: String, revisionId: String): Map<String, FileInfo> {
        AppLog.d("listFiles change=$changeId rev=$revisionId")
        return try {
            val files = api().listFiles(changeId, revisionId)
            AppLog.d("listFiles returned ${files.size} files")
            files
        } catch (e: Exception) {
            AppLog.e("listFiles failed for $changeId/$revisionId", e)
            throw e
        }
    }


    /**
     * Search file paths in the full tree of a revision (not only changed files).
     * Uses Gerrit files/?q= which matches path substrings.
     */
    suspend fun searchRevisionFiles(changeId: String, revisionId: String, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        AppLog.d("searchRevisionFiles change=$changeId rev=$revisionId q='$q'")
        return try {
            api().searchRevisionFiles(changeId, revisionId, q)
                .filter { it != "/COMMIT_MSG" && it != "/MERGE_LIST" }
                .sorted()
        } catch (e: Exception) {
            AppLog.e("searchRevisionFiles failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun getDiff(changeId: String, revisionId: String, filePath: String): DiffInfo {
        // Gerrit requires each path segment to be percent-encoded and '/' → %2F
        // (see REST API {file-id}). @Path(encoded=true) means we must encode here.
        val encodedFileId = encodeGerritFileId(filePath)
        AppLog.d("getDiff $changeId/$revisionId path=$filePath encoded=$encodedFileId")
        return try {
            api().getDiff(changeId, revisionId, encodedFileId)
        } catch (e: Exception) {
            AppLog.e("getDiff failed for $filePath", e)
            throw e
        }
    }

    /**
     * Encode a file path for use as Gerrit {file-id} in the URL.
     * Each path component is URL-encoded; '/' is turned into %2F.
     */
    private fun encodeGerritFileId(path: String): String {
        if (path.isEmpty()) return path
        return path.split('/').joinToString("%2F") { segment ->
            java.net.URLEncoder.encode(segment, Charsets.UTF_8.name())
                .replace("+", "%20")
        }
    }

    suspend fun listComments(changeId: String): Map<String, List<CommentInfo>> {
        AppLog.d("listComments $changeId")
        return try {
            api().listComments(changeId)
        } catch (e: Exception) {
            AppLog.e("listComments failed for $changeId", e)
            throw e
        }
    }

    suspend fun listDrafts(changeId: String): Map<String, List<CommentInfo>> {
        AppLog.d("listDrafts $changeId")
        return try {
            api().listDrafts(changeId)
        } catch (e: Exception) {
            AppLog.e("listDrafts failed for $changeId", e)
            throw e
        }
    }

    suspend fun setReview(changeId: String, revisionId: String, input: ReviewInput) {
        AppLog.i("setReview change=$changeId rev=$revisionId labels=${input.labels} message=${input.message?.take(40)}")
        try {
            val resp = api().setReview(changeId, revisionId, input)
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code()} ${resp.errorBody()?.string().orEmpty().take(300)}")
            }
            AppLog.i("setReview succeeded")
        } catch (e: Exception) {
            AppLog.e("setReview failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun abandon(changeId: String, message: String = ""): ChangeInfo {
        AppLog.i("abandon $changeId")
        return try {
            api().abandon(changeId, if (message.isBlank()) emptyMap() else mapOf("message" to message)).also {
                AppLog.i("abandon succeeded")
            }
        } catch (e: Exception) {
            AppLog.e("abandon failed", e)
            throw e
        }
    }

    suspend fun restore(changeId: String, message: String = ""): ChangeInfo {
        AppLog.i("restore $changeId")
        return try {
            api().restore(changeId, if (message.isBlank()) emptyMap() else mapOf("message" to message)).also {
                AppLog.i("restore succeeded")
            }
        } catch (e: Exception) {
            AppLog.e("restore failed", e)
            throw e
        }
    }

    /**
     * Submit (merge) the change into its destination branch.
     * Gerrit term is "submit"; this merges the approved change.
     */
    suspend fun submit(changeId: String, waitForMerge: Boolean = true): ChangeInfo {
        AppLog.i("submit (merge) $changeId waitForMerge=$waitForMerge")
        return try {
            api().submit(changeId, SubmitInput(waitForMerge = waitForMerge)).also {
                AppLog.i("submit (merge) OK status=${it.status}")
            }
        } catch (e: Exception) {
            val detail = httpErrorDetail(e)
            AppLog.e("submit (merge) failed: $detail", e)
            throw Exception(detail, e)
        }
    }


    suspend fun createChange(input: ChangeInput): ChangeInfo {
        // Normalize branch: Gerrit wants the short name (no refs/heads/)
        val branch = input.branch
            .removePrefix("refs/heads/")
            .removePrefix("refs/")
            .trim()
            .ifBlank { "master" }
        val normalized = input.copy(
            project = input.project.trim(),
            branch = branch,
            subject = input.subject.trim(),
            topic = input.topic?.trim()?.ifBlank { null },
            status = input.status ?: "NEW"
        )
        AppLog.i(
            "createChange project=${normalized.project} branch=${normalized.branch} " +
                "subject=${normalized.subject} wip=${normalized.workInProgress}"
        )
        return try {
            api().createChange(normalized).also {
                AppLog.i("createChange OK id=${it.id} number=${it.number}")
            }
        } catch (e: Exception) {
            val detail = httpErrorDetail(e)
            AppLog.e("createChange failed: $detail", e)
            throw Exception(detail, e)
        }
    }

    /** Prefer Gerrit response body text over generic "HTTP 400 Bad Request". */
    private fun httpErrorDetail(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val body = try {
                e.response()?.errorBody()?.string()?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            // Gerrit often returns plain text, sometimes JSON {"message":"..."}
            val fromBody = when {
                body.isBlank() -> null
                body.startsWith("{") -> {
                    Regex("\"message\"\\s*:\\s*\"([^\"]+)\"")
                        .find(body)?.groupValues?.getOrNull(1)
                        ?: body.take(300)
                }
                else -> body.take(300)
            }
            if (!fromBody.isNullOrBlank()) {
                return "HTTP ${e.code()}: $fromBody"
            }
            return "HTTP ${e.code()} ${e.message()}"
        }
        return e.message ?: e.toString()
    }

    suspend fun setWorkInProgress(changeId: String, message: String = "") {
        AppLog.i("setWorkInProgress $changeId")
        try {
            val resp = api().setWorkInProgress(
                changeId,
                if (message.isBlank()) emptyMap() else mapOf("message" to message)
            )
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code()} ${resp.errorBody()?.string().orEmpty().take(300)}")
            }
            AppLog.i("setWorkInProgress succeeded")
        } catch (e: Exception) {
            AppLog.e("setWorkInProgress failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun setReadyForReview(changeId: String, message: String = "") {
        AppLog.i("setReadyForReview $changeId")
        try {
            val resp = api().setReadyForReview(
                changeId,
                if (message.isBlank()) emptyMap() else mapOf("message" to message)
            )
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code()} ${resp.errorBody()?.string().orEmpty().take(300)}")
            }
            AppLog.i("setReadyForReview succeeded")
        } catch (e: Exception) {
            AppLog.e("setReadyForReview failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }


    suspend fun listProjects(): Map<String, ProjectInfo> {
        AppLog.d("listProjects")
        return try {
            val map = api().listProjects()
            AppLog.d("listProjects returned ${map.size} projects")
            map
        } catch (e: Exception) {
            AppLog.e("listProjects failed", e)
            throw e
        }
    }

    suspend fun listBranches(project: String): List<BranchInfo> {
        val proj = encodeGerritFileId(project.trim())
        AppLog.d("listBranches project=$project encoded=$proj")
        return try {
            // Gerrit returns a JSON array of BranchInfo
            api().listBranches(proj)
                .sortedBy { it.shortName.lowercase() }
                .also { AppLog.d("listBranches returned ${it.size} branches") }
        } catch (e: Exception) {
            AppLog.e("listBranches failed for $project", e)
            throw e
        }
    }

    /**
     * Create a branch on [project].
     * @param branch short name (no refs/heads/)
     * @param revision base commit SHA, existing branch name, or "HEAD"
     */
    suspend fun createBranch(project: String, branch: String, revision: String): BranchInfo {
        val proj = encodeGerritFileId(project.trim())
        val short = branch.trim().removePrefix("refs/heads/")
        val br = encodeGerritFileId(short)
        val rev = revision.trim().ifBlank { "HEAD" }
        AppLog.d("createBranch project=$project branch=$short revision=$rev")
        return try {
            api().createBranch(proj, br, BranchInput(revision = rev)).also {
                AppLog.i("createBranch OK ${it.ref} @ ${it.revision}")
            }
        } catch (e: Exception) {
            AppLog.e("createBranch failed for $project/$short", e)
            throw e
        }
    }

    /**
     * Fetch file content as UTF-8 text.
     * Gerrit returns base64; we decode it. Binary files may produce garbage —
     * the editor is intended for text sources.
     */

    /**
     * Load file text for the editor.
     * 1) Try the change revision (works for files already in the change).
     * 2) Else try project branch tip (any file in the repo on that branch).
     * 3) Else return empty string (new file to be created in the change edit).
     */
    suspend fun getFileContentForEdit(
        changeId: String,
        revisionId: String,
        filePath: String,
        project: String,
        branch: String
    ): String {
        // Prefer content from the change revision when the file is part of the change
        try {
            return getFileContent(changeId, revisionId, filePath)
        } catch (e: Exception) {
            AppLog.d("getFileContent from change failed for $filePath: ${e.message}")
        }
        // Any other path: load from branch tip of the project
        try {
            return getBranchFileContent(project, branch, filePath)
        } catch (e: Exception) {
            AppLog.d("getBranchFileContent failed for $filePath: ${e.message}")
        }
        // New file
        return ""
    }

    /** File content at branch HEAD (any path in the repo). */
    suspend fun getBranchFileContent(project: String, branch: String, filePath: String): String {
        val proj = encodeGerritFileId(project) // same encoding rules for path segments
        val br = branch.removePrefix("refs/heads/").trim()
        val encoded = encodeGerritFileId(filePath)
        AppLog.d("getBranchFileContent project=$project branch=$br path=$filePath")
        return try {
            val resp = api().getBranchFileContent(proj, br, encoded)
            parseFileContentResponse(resp, filePath)
        } catch (e: Exception) {
            AppLog.e("getBranchFileContent failed for $filePath", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    /**
     * Turn Gerrit file-content response into editable text.
     *
     * Gerrit *usually* sends base64 (header X-FYI-Content-Encoding: base64), but some
     * proxies / versions return plain text. Android Base64.decode is lenient and will
     * happily "decode" a shell script into binary garbage — so we only base64-decode
     * when the payload looks like base64 and the decoded form is better text.
     */
    private fun decodeBase64Content(raw: String, filePath: String = ""): String {
        val trimmed = raw.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) return ""

        // data:text/plain;base64,....
        val dataUriMatch = Regex("^data:([^;]*);base64,(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .matchEntire(trimmed)
        if (dataUriMatch != null) {
            val b64 = dataUriMatch.groupValues[2]
            return bytesToEditorText(decodeBase64Bytes(b64.replace(Regex("\\s+"), "")) ?: return trimmed)
        }

        val compact = trimmed.replace(Regex("\\s+"), "")
        val looksBase64 = isProbablyBase64(compact)
        val plainScore = textScore(trimmed)

        if (looksBase64) {
            val decodedBytes = decodeBase64Bytes(compact)
            if (decodedBytes != null) {
                val decoded = bytesToEditorText(decodedBytes)
                val decodedScore = textScore(decoded)
                // Prefer decoded when it is clearly better text than the raw payload
                if (decodedScore >= plainScore && decodedScore >= 0.7) {
                    return decoded
                }
                // Shebang / known text path: if decoded is still mostly text, use it
                if (decoded.startsWith("#!") || (isLikelyTextPath(filePath) && decodedScore >= 0.5)) {
                    return decoded
                }
            }
        }

        // Payload is already plain text (common for some servers) — do NOT base64-decode
        if (plainScore >= 0.7 || trimmed.startsWith("#!") || isLikelyTextPath(filePath)) {
            return trimmed.replace("\u0000", "")
        }

        // Last resort: try base64 anyway, else return as-is
        val fallbackBytes = decodeBase64Bytes(compact)
        if (fallbackBytes != null) {
            val decoded = bytesToEditorText(fallbackBytes)
            if (textScore(decoded) >= plainScore) return decoded
        }
        return trimmed.replace("\u0000", "")
    }

    private fun isProbablyBase64(compact: String): Boolean {
        if (compact.length < 8) return false
        // Base64 alphabet only; length typically multiple of 4 (padding)
        if (!compact.matches(Regex("^[A-Za-z0-9+/=_-]+$"))) return false
        // Reject strings that look like source code
        if (compact.contains("#!/") || compact.contains("<?")) return false
        return true
    }

    private fun textScore(s: String): Double {
        if (s.isEmpty()) return 1.0
        var good = 0
        for (ch in s) {
            val c = ch.code
            if (ch == '\n' || ch == '\r' || ch == '\t' || c in 32..126 || c >= 160) good++
        }
        return good.toDouble() / s.length
    }

    private fun bytesToEditorText(bytes: ByteArray): String {
        val asUtf8 = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        val replacementChar = '\uFFFD'
        val text = when {
            asUtf8 != null && !asUtf8.contains(replacementChar) -> asUtf8
            else -> String(bytes, Charsets.ISO_8859_1)
        }
        val noBom = if (text.startsWith('\uFEFF')) text.substring(1) else text
        return buildString(noBom.length) {
            for (ch in noBom) if (ch != '\u0000') append(ch)
        }
    }

    private fun decodeBase64Bytes(compact: String): ByteArray? {
        if (compact.isEmpty()) return ByteArray(0)
        val flags = listOf(
            android.util.Base64.DEFAULT,
            android.util.Base64.NO_WRAP,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        for (flag in flags) {
            try {
                return android.util.Base64.decode(compact, flag)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isLikelyTextPath(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
        val textExts = setOf(
            "sh", "bash", "zsh", "fish", "csh", "ksh", "bat", "cmd", "ps1",
            "py", "pyw", "rb", "pl", "pm", "php", "js", "jsx", "ts", "tsx", "mjs", "cjs",
            "java", "kt", "kts", "groovy", "gradle", "xml", "html", "htm", "css", "scss",
            "json", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env",
            "md", "txt", "csv", "tsv", "sql", "r", "go", "rs", "c", "h", "cc", "cpp", "hpp",
            "m", "mm", "swift", "scala", "clj", "lua", "vim", "dockerfile", "makefile",
            "cmake", "mk", "am", "ac", "in", "service", "timer", "socket", "desktop",
            "gitignore", "gitattributes", "editorconfig", "rc", "profile"
        )
        if (ext in textExts) return true
        return name in setOf(
            "makefile", "dockerfile", "gemfile", "rakefile", "procfile",
            "vagrantfile", "jenkinsfile", "brewfile", "readme", "license", "changelog",
            "vendorsetup.sh", "vendorsetup"
        )
    }

    suspend fun getFileContent(changeId: String, revisionId: String, filePath: String): String {
        val encoded = encodeGerritFileId(filePath)
        AppLog.d("getFileContent $changeId/$revisionId path=$filePath")
        return try {
            val resp = api().getFileContent(changeId, revisionId, encoded)
            parseFileContentResponse(resp, filePath)
        } catch (e: Exception) {
            AppLog.e("getFileContent failed for $filePath", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    private fun parseFileContentResponse(
        resp: retrofit2.Response<okhttp3.ResponseBody>,
        filePath: String
    ): String {
        if (!resp.isSuccessful) {
            val err = resp.errorBody()?.string().orEmpty()
            throw Exception("HTTP ${resp.code()} ${err.take(300)}")
        }
        val body = resp.body()?.string().orEmpty()
        val encoding = resp.headers()["X-FYI-Content-Encoding"]
            ?: resp.headers()["x-fyi-content-encoding"]
            ?: ""
        // Gerrit /content always base64-encodes file bytes. Prefer that path.
        // Heuristic decode only if the header is absent AND body is clearly plain text.
        val text = when {
            encoding.equals("base64", ignoreCase = true) -> forceDecodeBase64(body)
            isProbablyBase64(body.replace(Regex("\\s+"), "")) -> forceDecodeBase64(body)
            else -> decodeBase64Content(body, filePath)
        }
        val normalized = normalizeNewlines(maybeJsonUnescape(text))
        AppLog.d(
            "file content path=$filePath encoding='$encoding' " +
                "raw=${body.length} text=${normalized.length} lines=${normalized.count { it == '\n' } + 1}"
        )
        return normalized
    }

    /** Unconditional base64 decode of Gerrit file body → editor text with real newlines. */
    private fun forceDecodeBase64(raw: String): String {
        var payload = raw.trim()
        val dataUri = Regex("^data:[^;]*;base64,", RegexOption.IGNORE_CASE)
        payload = payload.replace(dataUri, "")
        // Whitespace only removed from the *base64 transport*, not from file contents
        val compact = payload.replace(Regex("\\s+"), "")
        val bytes = decodeBase64Bytes(compact)
            ?: return normalizeNewlines(raw.trim().replace("\u0000", ""))
        return normalizeNewlines(bytesToEditorText(bytes))
    }

    /** Normalize CR/LF so the editor always sees Unix newlines. */
    private fun normalizeNewlines(s: String): String {
        return s.replace("\r\n", "\n").replace("\r", "\n")
    }



    /**
     * Some responses deliver file text as a JSON-escaped string
     * (literal \n, \t, \u003d, \") instead of real characters. Unescape those.
     */
    private fun maybeJsonUnescape(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return s

        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                return kotlinx.serialization.json.Json.decodeFromString<String>(trimmed)
            } catch (_: Exception) {
                return jsonUnescape(trimmed.substring(1, trimmed.length - 1))
            }
        }

        val realNewlines = s.count { it == '\n' }
        val hasEscapedNewline = s.contains("\\n")
        val hasUnicodeEsc = s.contains("\\u")
        if (realNewlines <= 1 && (hasEscapedNewline || hasUnicodeEsc || s.contains("\\t"))) {
            return jsonUnescape(s)
        }
        return s
    }

    /** Unescape JSON string body (\n \t \r \" \\ \/ \uXXXX). */
    private fun jsonUnescape(escaped: String): String {
        val sb = StringBuilder(escaped.length)
        var i = 0
        while (i < escaped.length) {
            val c = escaped[i]
            if (c == '\\' && i + 1 < escaped.length) {
                when (val n = escaped[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\'' -> { sb.append('\''); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000c'); i += 2 }
                    'u' -> if (i + 5 < escaped.length) {
                        val hex = escaped.substring(i + 2, i + 6)
                        val cp = hex.toIntOrNull(16)
                        if (cp != null) {
                            sb.append(cp.toChar())
                            i += 6
                        } else {
                            sb.append(c)
                            i++
                        }
                    } else {
                        sb.append(c)
                        i++
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * Write file content into the change edit (creates edit if needed).
     * @return true if Gerrit accepted a real change; false if content was identical
     *         ("no changes were made") so no edit was opened.
     */
    suspend fun putEditFile(changeId: String, filePath: String, content: String): Boolean {
        val encoded = encodeGerritFileId(filePath)
        AppLog.i("putEditFile $changeId path=$filePath encoded=$encoded (${content.length} chars)")
        try {
            val mediaType = "text/plain; charset=UTF-8".toMediaType()
            val body = content.toRequestBody(mediaType)
            val resp = api().putEditFile(changeId, encoded, body)
            if (!resp.isSuccessful) {
                val err = resp.errorBody()?.string().orEmpty().trim()
                // Gerrit: identical content → 409 "no changes were made"
                if (resp.code() == 409 && err.contains("no changes were made", ignoreCase = true)) {
                    AppLog.w("putEditFile: content identical to current revision ($err)")
                    return false
                }
                throw Exception("HTTP ${resp.code()} $err".trim())
            }
            AppLog.i("putEditFile OK HTTP ${resp.code()}")
            val open = hasEdit(changeId)
            AppLog.i("putEditFile hasEdit after save → $open")
            return true
        } catch (e: Exception) {
            val detail = httpErrorDetail(e)
            if (detail.contains("no changes were made", ignoreCase = true)) {
                AppLog.w("putEditFile: content identical ($detail)")
                return false
            }
            AppLog.e("putEditFile failed", e)
            throw Exception(detail, e)
        }
    }

    /** True if Gerrit currently has an open change-edit for this change. */
    suspend fun hasEdit(changeId: String): Boolean {
        return try {
            val resp = api().getEdit(changeId)
            // 200 = edit exists; 204/404 = none
            val exists = resp.isSuccessful && resp.code() != 204 && resp.body() != null
            AppLog.d("hasEdit $changeId → $exists (HTTP ${resp.code()})")
            exists
        } catch (e: Exception) {
            AppLog.w("hasEdit check failed: ${e.message}")
            false
        }
    }

    /** Publish the change edit as a new patch set. */
    suspend fun publishEdit(changeId: String) {
        AppLog.i("publishEdit $changeId")
        try {
            if (!hasEdit(changeId)) {
                throw Exception(
                    "No open change edit to publish. Save a file or change the commit message first."
                )
            }
            val resp = api().publishEdit(changeId)
            // 204 No Content is success
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code()} ${resp.errorBody()?.string().orEmpty().take(300)}")
            }
            AppLog.i("publishEdit OK")
        } catch (e: Exception) {
            AppLog.e("publishEdit failed", e)
            // Don't double-wrap our own clear messages
            if (e.message?.contains("No open change edit") == true) throw e
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun deleteEdit(changeId: String) {
        AppLog.i("deleteEdit $changeId")
        try {
            val resp = api().deleteEdit(changeId)
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code()} ${resp.errorBody()?.string().orEmpty().take(300)}")
            }
            AppLog.i("deleteEdit OK")
        } catch (e: Exception) {
            AppLog.e("deleteEdit failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun setTopic(changeId: String, topic: String) {
        AppLog.i("setTopic $changeId → $topic")
        try {
            api().setTopic(changeId, mapOf("topic" to topic))
            AppLog.i("setTopic OK")
        } catch (e: Exception) {
            AppLog.e("setTopic failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    /**
     * Update commit message via change edit (must publishEdit afterwards).
     * If Gerrit says the message is unchanged (HTTP 409), that is treated as OK —
     * an edit is only created when the message actually differs or a file was edited.
     * @return true if the message was written into an edit; false if it was already the same
     */
    suspend fun putEditMessage(changeId: String, message: String): Boolean {
        AppLog.i("putEditMessage $changeId")
        try {
            val resp = api().putEditMessage(changeId, mapOf("message" to message))
            // 204 No Content = success (message applied / edit created)
            if (!resp.isSuccessful) {
                val err = resp.errorBody()?.string().orEmpty()
                val detail = "HTTP ${resp.code()} $err"
                if (err.contains("same as existing", ignoreCase = true)) {
                    AppLog.w("putEditMessage: message unchanged ($detail)")
                    return false
                }
                throw Exception(detail)
            }
            AppLog.i("putEditMessage OK (HTTP ${resp.code()})")
            return true
        } catch (e: Exception) {
            val detail = httpErrorDetail(e)
            // Gerrit: "New commit message cannot be same as existing commit message"
            if (detail.contains("same as existing", ignoreCase = true)) {
                AppLog.w("putEditMessage: message unchanged ($detail)")
                return false
            }
            AppLog.e("putEditMessage failed", e)
            throw Exception(detail, e)
        }
    }

    /**
     * Apply commit message (if changed) and publish the open edit as a new patch set.
     * Handles the common case where the message is identical and only file edits exist.
     */
    suspend fun updateCommitMessageAndPublish(changeId: String, message: String) {
        val msg = message.trim()
        if (msg.isEmpty()) throw Exception("Commit message cannot be empty")
        val wrote = putEditMessage(changeId, msg)
        val editOpen = hasEdit(changeId)
        if (!editOpen) {
            if (!wrote) {
                throw Exception(
                    "Nothing to publish — commit message is unchanged and there is no open change edit. " +
                        "Edit a file (Save) or change the message first."
                )
            }
            // Message write claimed success but hasEdit is false — still try publish
        }
        publishEdit(changeId)
    }
}

// (okhttp extensions used above: toMediaType / toRequestBody)
