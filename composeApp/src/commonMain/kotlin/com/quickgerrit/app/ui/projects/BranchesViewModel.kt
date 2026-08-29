package com.quickgerrit.app.ui.projects

import com.quickgerrit.app.data.model.BranchInfo
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.platform.PlatformViewModel
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BranchesUiState(
    val project: String = "",
    val branches: List<BranchInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: String = "",
    val creating: Boolean = false,
    val createError: String? = null
)

class BranchesViewModel(
    private val repo: GerritRepository,
    private val project: String
) : PlatformViewModel() {

    private val _ui = MutableStateFlow(BranchesUiState(project = project))
    val ui: StateFlow<BranchesUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val list = repo.listBranches(project)
                _ui.update { it.copy(branches = list, isLoading = false) }
            } catch (e: Exception) {
                AppLog.e("Failed to load branches for $project", e)
                _ui.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load branches")
                }
            }
        }
    }

    fun setFilter(q: String) {
        _ui.update { it.copy(filter = q) }
    }

    fun clearCreateResult() {
        _ui.update { it.copy(createError = null) }
    }

    fun createBranch(
        name: String,
        revision: String,
        onSuccess: (BranchInfo) -> Unit = {}
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, createError = null) }
            try {
                val created = repo.createBranch(project, name, revision)
                _ui.update { it.copy(creating = false) }
                load()
                onSuccess(created)
            } catch (e: Exception) {
                AppLog.e("createBranch failed", e)
                _ui.update {
                    it.copy(
                        creating = false,
                        createError = e.message ?: "Create branch failed"
                    )
                }
            }
        }
    }
}
