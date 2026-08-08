package com.quickgerrit.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quickgerrit.app.data.model.ProjectInfo
import com.quickgerrit.app.data.repository.GerritRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: String = ""
)

class ProjectsViewModel(private val repo: GerritRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ProjectsUiState())
    val ui: StateFlow<ProjectsUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val map = repo.listProjects()
                val list = map.values.sortedBy { it.name.lowercase() }
                _ui.update { it.copy(projects = list, isLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(q: String) {
        _ui.update { it.copy(filter = q) }
    }

    class Factory(private val repo: GerritRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectsViewModel(repo) as T
    }
}
