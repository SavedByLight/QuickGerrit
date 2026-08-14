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
        AppLog.i("Testing login for ${account.username} @ ${account.baseUrl}")
        return try {
            val client = GerritClientFactory.create(account)
            val self = client.getSelf()
            AppLog.i("Login OK: ${self.displayName ?: self.name ?: self.username} (id=${self.accountId})")
            self
        } catch (e: Exception) {
            AppLog.e("Login failed for ${account.username} @ ${account.baseUrl}", e)
            throw e
        }
    }

    suspend fun addAccount(account: GerritAccount) = accountStore.addAccount(account).also {
        AppLog.i("Account added: ${it.name} (${it.id})")
    }

    suspend fun updateAccount(account: GerritAccount) {
        accountStore.updateAccount(account)
        AppLog.i("Account updated: ${account.name} (${account.id})")
    }

    suspend fun removeAccount(id: String) {
        accountStore.removeAccount(id)
        AppLog.i("Account removed: $id")
    }

    suspend fun setActive(id: String) {
        accountStore.setActiveAccount(id)
        AppLog.i("Active account set to: $id")
    }

    suspend fun queryChanges(status: String, queryExtra: String = "", limit: Int = 50, start: Int = 0): List<ChangeInfo> {
        val q = buildString {
            append("status:$status")
            if (queryExtra.isNotBlank()) append(" $queryExtra")
        }
        AppLog.d("queryChanges q='$q' limit=$limit start=$start")
        return try {
            val result = api().queryChanges(query = q, limit = limit, start = start)
            AppLog.d("queryChanges returned ${result.size} changes")
            result
        } catch (e: Exception) {
            AppLog.e("queryChanges failed for q='$q'", e)
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

    suspend fun setReview(changeId: String, revisionId: String, input: ReviewInput): Any {
        AppLog.i("setReview change=$changeId rev=$revisionId labels=${input.labels} message=${input.message?.take(40)}")
        return try {
            val result = api().setReview(changeId, revisionId, input)
            AppLog.i("setReview succeeded")
            result
        } catch (e: Exception) {
            AppLog.e("setReview failed", e)
            throw e
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

    suspend fun submit(changeId: String): ChangeInfo {
        AppLog.i("submit $changeId")
        return try {
            api().submit(changeId).also {
                AppLog.i("submit succeeded")
            }
        } catch (e: Exception) {
            AppLog.e("submit failed", e)
            throw e
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

    suspend fun setWorkInProgress(changeId: String, message: String = ""): Any {
        AppLog.i("setWorkInProgress $changeId")
        return try {
            api().setWorkInProgress(
                changeId,
                if (message.isBlank()) emptyMap() else mapOf("message" to message)
            ).also { AppLog.i("setWorkInProgress succeeded") }
        } catch (e: Exception) {
            AppLog.e("setWorkInProgress failed", e)
            throw e
        }
    }

    suspend fun setReadyForReview(changeId: String, message: String = ""): Any {
        AppLog.i("setReadyForReview $changeId")
        return try {
            api().setReadyForReview(
                changeId,
                if (message.isBlank()) emptyMap() else mapOf("message" to message)
            ).also { AppLog.i("setReadyForReview succeeded") }
        } catch (e: Exception) {
            AppLog.e("setReadyForReview failed", e)
            throw e
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
            val body = api().getBranchFileContent(proj, br, encoded)
            decodeBase64Content(body.string().trim(), filePath)
        } catch (e: Exception) {
            AppLog.e("getBranchFileContent failed for $filePath", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    /**
     * Decode Gerrit file content to editable text.
     * Gerrit returns base64 (sometimes with newlines or a data: URI prefix).
     * Shell scripts and other text files must always open as text, never as "binary".
     */
    private fun decodeBase64Content(raw: String, filePath: String = ""): String {
        var payload = raw.trim()
        val dataUri = Regex("^data:[^;]*;base64,", RegexOption.IGNORE_CASE)
        payload = payload.replace(dataUri, "")
        val compact = payload.replace(Regex("\\s+"), "")

        val bytes = decodeBase64Bytes(compact) ?: return raw

        val asUtf8 = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        val replacementChar = '\uFFFD'
        val text = when {
            asUtf8 != null && !asUtf8.contains(replacementChar) -> asUtf8
            else -> String(bytes, Charsets.ISO_8859_1)
        }
        val bom = '\uFEFF'
        val noBom = if (text.startsWith(bom)) text.substring(1) else text
        val cleaned = buildString(noBom.length) {
            for (ch in noBom) {
                if (ch != '\u0000') append(ch)
            }
        }
        return cleaned
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

    suspend fun getFileContent(changeId: String, revisionId: String, filePath: String): String {
        val encoded = encodeGerritFileId(filePath)
        AppLog.d("getFileContent $changeId/$revisionId path=$filePath")
        return try {
            val body = api().getFileContent(changeId, revisionId, encoded)
            decodeBase64Content(body.string().trim(), filePath)
        } catch (e: Exception) {
            AppLog.e("getFileContent failed for $filePath", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    /** Write file content into the change edit (creates edit if needed). */
    suspend fun putEditFile(changeId: String, filePath: String, content: String) {
        val encoded = encodeGerritFileId(filePath)
        AppLog.i("putEditFile $changeId path=$filePath (${content.length} chars)")
        try {
            val mediaType = "text/plain; charset=UTF-8".toMediaType()
            val body = content.toRequestBody(mediaType)
            api().putEditFile(changeId, encoded, body)
            AppLog.i("putEditFile OK")
        } catch (e: Exception) {
            AppLog.e("putEditFile failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    /** Publish the change edit as a new patch set. */
    suspend fun publishEdit(changeId: String) {
        AppLog.i("publishEdit $changeId")
        try {
            api().publishEdit(changeId)
            AppLog.i("publishEdit OK")
        } catch (e: Exception) {
            AppLog.e("publishEdit failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }

    suspend fun deleteEdit(changeId: String) {
        AppLog.i("deleteEdit $changeId")
        try {
            api().deleteEdit(changeId)
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

    /** Update commit message via change edit (must publishEdit afterwards). */
    suspend fun putEditMessage(changeId: String, message: String) {
        AppLog.i("putEditMessage $changeId")
        try {
            api().putEditMessage(changeId, mapOf("message" to message))
            AppLog.i("putEditMessage OK")
        } catch (e: Exception) {
            AppLog.e("putEditMessage failed", e)
            throw Exception(httpErrorDetail(e), e)
        }
    }
}

// (okhttp extensions used above: toMediaType / toRequestBody)
