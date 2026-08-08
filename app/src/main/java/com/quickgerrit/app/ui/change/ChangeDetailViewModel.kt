package com.quickgerrit.app.ui.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.*
import com.quickgerrit.app.data.repository.GerritRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val snackbar: String? = null
)

class ChangeDetailViewModel(
    private val repo: GerritRepository,
    private val changeId: String
) : ViewModel() {

    private val _ui = MutableStateFlow(ChangeDetailUiState())
    val ui: StateFlow<ChangeDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repo.getChangeDetail(changeId)
                val rev = detail.currentRevision ?: detail.revisions?.keys?.firstOrNull()
                val files = if (rev != null) {
                    runCatching { repo.listFiles(changeId, rev) }.getOrElse { emptyMap() }
                } else emptyMap()
                val comments = runCatching { repo.listComments(changeId) }.getOrElse { emptyMap() }
                _ui.update {
                    it.copy(
                        change = detail,
                        files = files,
                        comments = comments,
                        selectedRevision = rev,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectRevision(rev: String) {
        viewModelScope.launch {
            _ui.update { it.copy(selectedRevision = rev) }
            try {
                val files = repo.listFiles(changeId, rev)
                _ui.update { it.copy(files = files) }
            } catch (_: Exception) {}
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
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    fun submit() {
        viewModelScope.launch {
            _ui.update { it.copy(actionInProgress = true) }
            try {
                repo.submit(changeId)
                _ui.update { it.copy(actionInProgress = false, snackbar = "Submitted") }
                load()
            } catch (e: Exception) {
                _ui.update { it.copy(actionInProgress = false, snackbar = e.message) }
            }
        }
    }

    fun clearSnackbar() {
        _ui.update { it.copy(snackbar = null) }
    }

    class Factory(
        private val repo: GerritRepository,
        private val changeId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChangeDetailViewModel(repo, changeId) as T
    }
}
