package com.quickgerrit.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.ChangeInfo
import com.quickgerrit.app.data.model.GerritAccount
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One section of Gerrit's classic "Your Dashboard" / My Reviews. */
data class DashboardSectionDef(
    val id: String,
    val name: String,
    val query: String,
    val hideIfEmpty: Boolean = false,
    val limit: Int = 25
)

data class DashboardSectionState(
    val def: DashboardSectionDef,
    val changes: List<ChangeInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class DashboardUiState(
    val sections: List<DashboardSectionState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeAccount: GerritAccount? = null,
    val hasAccounts: Boolean = false
)

/**
 * Default sections mirror Gerrit's polygerrit dashboard-util.ts
 * (Your turn, WIP, Outgoing, Incoming, CCed, Recently closed).
 */
val DEFAULT_DASHBOARD_SECTIONS = listOf(
    DashboardSectionDef(
        id = "your_turn",
        name = "Your turn",
        query = "attention:self",
        hideIfEmpty = false,
        limit = 25
    ),
    DashboardSectionDef(
        id = "wip",
        name = "Work in progress",
        query = "is:open owner:self is:wip",
        hideIfEmpty = true,
        limit = 25
    ),
    DashboardSectionDef(
        id = "outgoing",
        name = "Outgoing reviews",
        query = "is:open owner:self -is:wip",
        hideIfEmpty = false,
        limit = 25
    ),
    DashboardSectionDef(
        id = "incoming",
        name = "Incoming reviews",
        query = "is:open -owner:self -is:wip reviewer:self",
        hideIfEmpty = false,
        limit = 25
    ),
    DashboardSectionDef(
        id = "cced",
        name = "CCed on",
        query = "is:open -owner:self -reviewer:self cc:self",
        hideIfEmpty = true,
        limit = 10
    ),
    DashboardSectionDef(
        id = "closed",
        name = "Recently closed",
        query = "is:closed (owner:self OR reviewer:self OR cc:self) -age:4w",
        hideIfEmpty = false,
        limit = 10
    )
)

class DashboardViewModel(private val repo: GerritRepository) : ViewModel() {

    private val _ui = MutableStateFlow(
        DashboardUiState(
            sections = DEFAULT_DASHBOARD_SECTIONS.map { DashboardSectionState(def = it) }
        )
    )
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.activeAccount.collect { acc ->
                _ui.update { it.copy(activeAccount = acc, hasAccounts = acc != null) }
                if (acc != null) {
                    AppLog.d("Dashboard: active account → ${acc.name}; reloading")
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

    fun load() {
        viewModelScope.launch {
            val account = _ui.value.activeAccount
            if (account == null) {
                _ui.update { it.copy(isLoading = false, error = null) }
                return@launch
            }
            _ui.update { state ->
                state.copy(
                    isLoading = true,
                    error = null,
                    sections = state.sections.map { it.copy(isLoading = true, error = null) }
                )
            }
            try {
                val results = coroutineScope {
                    DEFAULT_DASHBOARD_SECTIONS.map { def ->
                        async {
                            try {
                                val changes = repo.queryChangesRaw(def.query, limit = def.limit)
                                DashboardSectionState(
                                    def = def,
                                    changes = changes,
                                    isLoading = false,
                                    error = null
                                )
                            } catch (e: Exception) {
                                // attention:self / cc: may fail on older Gerrit — show empty section
                                AppLog.e("Dashboard section '${def.name}' failed: ${e.message}", e)
                                DashboardSectionState(
                                    def = def,
                                    changes = emptyList(),
                                    isLoading = false,
                                    error = e.message
                                )
                            }
                        }
                    }.awaitAll()
                }
                _ui.update {
                    it.copy(
                        sections = results,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                AppLog.e("Dashboard load failed", e)
                _ui.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load dashboard",
                        sections = it.sections.map { s -> s.copy(isLoading = false) }
                    )
                }
            }
        }
    }

    class Factory(private val repo: GerritRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DashboardViewModel(repo) as T
    }
}
