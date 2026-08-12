package com.quickgerrit.app.ui.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.data.model.ChangeInput
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.util.AppLog
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
    }

    private val _ui = MutableStateFlow(ChangesUiState())
    val ui: StateFlow<ChangesUiState> = _ui.asStateFlow()

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
        AppLog.d("selectTab ${tab.label}")
        _ui.update { it.copy(tab = tab) }
        load()
    }

    fun setSearch(q: String) {
        _ui.update { it.copy(search = q) }
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
