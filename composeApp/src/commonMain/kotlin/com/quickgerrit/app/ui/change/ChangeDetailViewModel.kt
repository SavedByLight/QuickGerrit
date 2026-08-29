package com.quickgerrit.app.ui.change

import com.quickgerrit.app.data.model.*
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.platform.PlatformViewModel
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class ChangeDetailUiState(
    val change: ChangeInfo? = null,
    val files: Map<String, FileInfo> = emptyMap(),
    val comments: Map<String, List<CommentInfo>> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedRevision: String? = null,
    val reviewMessage: String = "",
    val codeReviewScore: Int = 0,
    val verifiedScore: Int = 0,
    val actionInProgress: Boolean = false,
    val snackbar: String? = null,
    val repoFileQuery: String = "",
    val repoFileMatches: List<String> = emptyList(),
    val repoFileSearching: Boolean = false,
    val repoFileSearchError: String? = null,
    /** Active account base URL for web links (e.g. https://gerrit.example.com). */
    val baseUrl: String = ""
)

class ChangeDetailViewModel(
    private val repo: GerritRepository,
    private val changeId: String
) : PlatformViewModel() {

    private val _ui = MutableStateFlow(ChangeDetailUiState())
    val ui: StateFlow<ChangeDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            AppLog.d("ChangeDetail load $changeId")
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val accountBase = runCatching {
                    repo.activeAccount.first()?.baseUrl.orEmpty().trimEnd('/')
                }.getOrDefault("")
                // Detail first so the screen can render subject/status ASAP
                val detail = repo.getChangeDetail(changeId)
                val rev = detail.currentRevision ?: detail.revisions?.keys?.firstOrNull()
                _ui.update {
                    it.copy(
                        change = detail,
                        selectedRevision = rev,
                        isLoading = false,
                        baseUrl = accountBase
                    )
                }
                // Files + comments in parallel (independent endpoints)
                coroutineScope {
                    val filesDef = async {
                        if (rev != null) {
                            runCatching { repo.listFiles(changeId, rev) }.getOrElse { emptyMap() }
                        } else emptyMap()
                    }
                    val commentsDef = async {
                        runCatching { repo.listComments(changeId) }.getOrElse { emptyMap() }
                    }
                    val files = filesDef.await()
                    val comments = commentsDef.await()
                    _ui.update { it.copy(files = files, comments = comments) }
                }
            } catch (e: Exception) {
                AppLog.e("ChangeDetail load failed for $changeId", e)
                _ui.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectRevision(rev: String) {
        viewModelScope.launch {
            AppLog.d("selectRevision $rev")
            _ui.update { it.copy(selectedRevision = rev) }
            try {
                val files = repo.listFiles(changeId, rev)
                _ui.update { it.copy(files = files) }
            } catch (e: Exception) {
                AppLog.e("Failed to list files for rev $rev", e)
            }
        }
    }

    fun setReviewMessage(msg: String) {
        _ui.update { it.copy(reviewMessage = msg) }
    }

    fun setCodeReview(score: Int) {
        _ui.update { it.copy(codeReviewScore = score) }
    }

    fun setVerified(score: Int) {
        _ui.update { it.copy(verifiedScore = score) }
    }

    fun submitReview() {
        val rev = _ui.value.selectedRevision ?: return
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                val labels = mutableMapOf<String, Int>()
                if (_ui.value.codeReviewScore != 0) labels["Code-Review"] = _ui.value.codeReviewScore
                if (_ui.value.verifiedScore != 0) labels["Verified"] = _ui.value.verifiedScore
                val input = ReviewInput(
                    message = _ui.value.reviewMessage.ifBlank { null },
                    labels = labels.ifEmpty { null }
                )
                repo.setReview(changeId, rev, input)
                _ui.update {
                    it.copy(
                        actionInProgress = false,
                        reviewMessage = "",
                        codeReviewScore = 0,
                        verifiedScore = 0,
                        snackbar = "Review submitted"
                    )
                }
                load()
            } catch (e: Exception) {
                AppLog.e("submitReview failed", e)
                _ui.update {
                    it.copy(actionInProgress = false, snackbar = e.message ?: "Review failed")
                }
            }
        }
    }

    fun abandon(message: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.abandon(changeId, message)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Abandoned") }
                load()
            } catch (e: Exception) {
                AppLog.e("abandon failed", e)
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    fun restore(message: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.restore(changeId, message)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Restored") }
                load()
            } catch (e: Exception) {
                AppLog.e("restore failed", e)
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    /** Submit / merge the change into its target branch. */
    fun submit() {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                val result = repo.submit(changeId, waitForMerge = true)
                val status = result.status.ifBlank { "MERGED" }
                _ui.update {
                    it.copy(
                        actionInProgress = false,
                        snackbar = "Change merged ($status)"
                    )
                }
                load()
            } catch (e: Exception) {
                AppLog.e("submit (merge) failed", e)
                _ui.update {
                    it.copy(
                        actionInProgress = false,
                        snackbar = e.message ?: "Merge failed"
                    )
                }
            }
        }
    }

    fun setWip(message: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.setWorkInProgress(changeId, message)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Marked as Work in Progress") }
                load()
            } catch (e: Exception) {
                AppLog.e("setWip failed", e)
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    fun setReady(message: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.setReadyForReview(changeId, message)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Marked as Ready for Review") }
                load()
            } catch (e: Exception) {
                AppLog.e("setReady failed", e)
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    fun clearSnackbar() {
        _ui.update { it.copy(snackbar = null) }
    }

    fun showSnackbar(message: String) {
        _ui.update { it.copy(snackbar = message) }
    }

    fun setRepoFileQuery(q: String) {
        _ui.update { it.copy(repoFileQuery = q) }
    }

    /** Search full revision tree by path substring (Gerrit files/?q=). */
    fun searchRepoFiles(query: String = _ui.value.repoFileQuery) {
        val rev = _ui.value.selectedRevision ?: return
        val q = query.trim()
        if (q.isEmpty()) {
            _ui.update {
                it.copy(repoFileMatches = emptyList(), repoFileSearching = false, repoFileSearchError = null)
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(repoFileSearching = true, repoFileSearchError = null, repoFileQuery = q) }
            try {
                val matches = repo.searchRevisionFiles(changeId, rev, q)
                _ui.update {
                    it.copy(repoFileMatches = matches, repoFileSearching = false)
                }
            } catch (e: Exception) {
                AppLog.e("searchRepoFiles failed", e)
                _ui.update {
                    it.copy(
                        repoFileSearching = false,
                        repoFileSearchError = e.message ?: "Search failed",
                        repoFileMatches = emptyList()
                    )
                }
            }
        }
    }

    /** Update topic on the open change (immediate). */
    fun updateTopic(topic: String) {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.setTopic(changeId, topic.trim())
                _ui.update { it.copy(actionInProgress = false, snackbar = "Topic updated") }
                load()
            } catch (e: Exception) {
                AppLog.e("updateTopic failed", e)
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message ?: "Topic update failed") }
            }
        }
    }

    /**
     * Write commit message into the change edit, then publish as a new patch set.
     * This is how you change the subject / body of an open change.
     */
    fun updateCommitMessageAndPublish(message: String) {
        val msg = message.trim()
        if (msg.isEmpty()) {
            _ui.update { it.copy(snackbar = "Commit message cannot be empty") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                // Handles "message unchanged" 409 and "no edit" clearly
                repo.updateCommitMessageAndPublish(changeId, msg)
                _ui.update {
                    it.copy(actionInProgress = false, snackbar = "Published as new patch set")
                }
                load()
            } catch (e: Exception) {
                AppLog.e("updateCommitMessageAndPublish failed", e)
                _ui.update {
                    it.copy(actionInProgress = false, snackbar = e.message ?: "Failed to update message")
                }
            }
        }
    }

    /** Publish any pending change edit (e.g. after file edits) as a new patch set. */
    fun publishChangeEdit() {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.publishEdit(changeId)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Change edit published") }
                load()
            } catch (e: Exception) {
                AppLog.e("publishChangeEdit failed", e)
                _ui.update {
                    it.copy(actionInProgress = false, snackbar = e.message ?: "Publish edit failed")
                }
            }
        }
    }

    /** Discard the current change edit without publishing. */
    fun discardChangeEdit() {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.deleteEdit(changeId)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Change edit discarded") }
                load()
            } catch (e: Exception) {
                AppLog.e("discardChangeEdit failed", e)
                _ui.update {
                    it.copy(actionInProgress = false, snackbar = e.message ?: "Discard failed")
                }
            }
        }
    }
}
