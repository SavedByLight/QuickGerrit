package com.quickgerrit.app.ui.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.data.model.ChangeInput
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChangeTab(val status: String, val label: String) {
    OPEN("open", "Open"),
    MERGED("merged", "Merged"),
    ABANDONED("abandoned", "Abandoned")
}

data class ChangesUiState(
    val tab: ChangeTab = ChangeTab.OPEN,
    val changes: List<ChangeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val search: String = "",
    val activeAccount: GerritAccount? = null,
    val hasAccounts: Boolean = false,
    val creating: Boolean = false,
    val createError: String? = null,
    val createdChangeId: String? = null,
    /** True when Gerrit indicated more results (_more_changes). */
    val hasMore: Boolean = false,
    val nextStart: Int = 0
)

class ChangesViewModel(private val repo: GerritRepository) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 100
        /** Delay before live-search fires so typing stays smooth. */
        const val SEARCH_DEBOUNCE_MS = 350L

        /** Common Gerrit query operators / templates for autocomplete suggestions. */
        val QUERY_SUGGESTIONS: List<String> = listOf(
            "owner:self",
            "owner:self is:open",
            "reviewer:self",
            "is:wip",
            "is:starred",
            "is:mergeable",
            "project:",
            "branch:",
            "topic:",
            "message:",
            "file:",
            "label:Code-Review=+2",
            "label:Verified=+1",
            "status:open",
            "after:2024-01-01",
            "-is:wip",
            "hashtag:"
        )
    }

    private val _ui = MutableStateFlow(ChangesUiState())
    val ui: StateFlow<ChangesUiState> = _ui.asStateFlow()

    /** Cancels in-flight debounced search when the query changes again. */
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.activeAccount.collect { acc ->
                _ui.update { it.copy(activeAccount = acc, hasAccounts = acc != null) }
                if (acc != null) {
                    AppLog.d("Active account changed → ${acc.name}; reloading changes")
                    load()
                }
            }
        }
        viewModelScope.launch {
            repo.accounts.collect { list ->
                _ui.update { it.copy(hasAccounts = list.isNotEmpty()) }
            }
        }
    }

    fun selectTab(tab: ChangeTab) {
        if (_ui.value.tab == tab) return
        AppLog.d("selectTab ${tab.label}")
        // Clear previous results so the loading indicator appears immediately
        // and stale data from another status is not shown while the new query runs.
        searchJob?.cancel()
        _ui.update {
            it.copy(
                tab = tab,
                changes = emptyList(),
                isLoading = true,
                error = null,
                nextStart = 0,
                hasMore = false
            )
        }
        load()
    }

    /**
     * Updates the search query and debounces a live reload so results refresh
     * while the user types (no need to press the search icon).
     */
    fun setSearch(q: String) {
        _ui.update { it.copy(search = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load()
        }
    }

    /** Immediate search (e.g. suggestion picked or explicit refresh). */
    fun searchNow() {
        searchJob?.cancel()
        load()
    }

    /** Fresh load from start (tab change, refresh, search). */
    fun load() {
        viewModelScope.launch {
            if (_ui.value.activeAccount == null) {
                AppLog.d("load skipped – no active account")
                return@launch
            }
            _ui.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    nextStart = 0,
                    hasMore = false
                )
            }
            try {
                val list = repo.queryChanges(
                    status = _ui.value.tab.status,
                    queryExtra = _ui.value.search.trim(),
                    limit = PAGE_SIZE,
                    start = 0
                )
                val hasMore = list.lastOrNull()?.moreChanges == true
                _ui.update {
                    it.copy(
                        changes = list,
                        isLoading = false,
                        hasMore = hasMore,
                        nextStart = list.size
                    )
                }
            } catch (e: Exception) {
                AppLog.e("Failed to load changes", e)
                _ui.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load changes"
                    )
                }
            }
        }
    }

    /** Append the next page of results. */
    fun loadMore() {
        viewModelScope.launch {
            val state = _ui.value
            if (state.activeAccount == null || !state.hasMore || state.isLoadingMore || state.isLoading) {
                return@launch
            }
            _ui.update { it.copy(isLoadingMore = true, error = null) }
            try {
                val page = repo.queryChanges(
                    status = state.tab.status,
                    queryExtra = state.search.trim(),
                    limit = PAGE_SIZE,
                    start = state.nextStart
                )
                val hasMore = page.lastOrNull()?.moreChanges == true
                _ui.update {
                    it.copy(
                        changes = it.changes + page,
                        isLoadingMore = false,
                        hasMore = hasMore,
                        nextStart = it.nextStart + page.size
                    )
                }
            } catch (e: Exception) {
                AppLog.e("Failed to load more changes", e)
                _ui.update {
                    it.copy(
                        isLoadingMore = false,
                        error = e.message ?: "Failed to load more"
                    )
                }
            }
        }
    }

    fun clearCreateResult() {
        _ui.update { it.copy(createError = null, createdChangeId = null) }
    }

    fun createChange(
        project: String,
        branch: String,
        subject: String,
        topic: String = "",
        workInProgress: Boolean = false,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, createError = null, createdChangeId = null) }
            try {
                val created = repo.createChange(
                    ChangeInput(
                        project = project.trim(),
                        branch = branch.trim().ifBlank { "master" },
                        subject = subject.trim(),
                        topic = topic.trim().ifBlank { null },
                        workInProgress = workInProgress
                    )
                )
                _ui.update {
                    it.copy(
                        creating = false,
                        createdChangeId = created.id
                    )
                }
                load()
                onSuccess(created.id)
            } catch (e: Exception) {
                AppLog.e("createChange failed", e)
                _ui.update {
                    it.copy(creating = false, createError = e.message ?: "Create failed")
                }
            }
        }
    }

    class Factory(private val repo: GerritRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChangesViewModel(repo) as T
    }
}
