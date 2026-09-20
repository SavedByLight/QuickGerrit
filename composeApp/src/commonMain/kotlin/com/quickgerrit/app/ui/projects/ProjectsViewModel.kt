package com.quickgerrit.app.ui.projects

import com.quickgerrit.app.data.model.ChangeInput
import com.quickgerrit.app.data.model.ProjectInfo
import com.quickgerrit.app.data.repository.GerritRepository
import com.quickgerrit.app.platform.PlatformViewModel
import com.quickgerrit.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: String = "",
    val creating: Boolean = false,
    val createError: String? = null,
    /** Creating a new project/repo (not a change). */
    val creatingProject: Boolean = false,
    val createProjectError: String? = null
)

class ProjectsViewModel(private val repo: GerritRepository) : PlatformViewModel() {

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
                // Gerrit omits the "name" field when projects are returned as a map
                // (the project name is the map key). Populate name/id from the key.
                val list = map.map { (key, info) ->
                    info.copy(
                        id = info.id.ifBlank { key },
                        name = info.name.ifBlank { key }
                    )
                }.sortedBy { it.name.lowercase() }
                _ui.update { it.copy(projects = list, isLoading = false) }
            } catch (e: Exception) {
                AppLog.e("Failed to load projects", e)
                _ui.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(q: String) {
        _ui.update { it.copy(filter = q) }
    }

    fun clearCreateResult() {
        _ui.update { it.copy(createError = null) }
    }

    fun clearCreateProjectResult() {
        _ui.update { it.copy(createProjectError = null) }
    }

    /**
     * Create a new Gerrit project/repository when the user has permission.
     * On success, refreshes the project list and invokes [onSuccess] with the project name.
     */
    fun createProject(
        name: String,
        description: String = "",
        parent: String = "",
        createEmptyCommit: Boolean = true,
        initialBranch: String = "master",
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(creatingProject = true, createProjectError = null) }
            try {
                val branches = initialBranch.trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { listOf(it.removePrefix("refs/heads/")) }
                val created = repo.createProject(
                    name = name,
                    description = description,
                    parent = parent.ifBlank { null },
                    createEmptyCommit = createEmptyCommit,
                    branches = branches
                )
                val projectName = created.name.ifBlank { created.id.ifBlank { name.trim() } }
                // Refresh list so the new project appears
                load()
                _ui.update { it.copy(creatingProject = false) }
                onSuccess(projectName)
            } catch (e: Exception) {
                AppLog.e("createProject failed", e)
                val msg = e.message.orEmpty()
                val friendly = when {
                    msg.contains("403") || msg.contains("Forbidden", ignoreCase = true) ->
                        "Permission denied — you need the Create Project capability on this server."
                    msg.contains("409") || msg.contains("already exists", ignoreCase = true) ->
                        "A project with that name already exists."
                    msg.contains("400") ->
                        "Invalid project name or options. Check the name and try again."
                    else -> e.message ?: "Create project failed"
                }
                _ui.update {
                    it.copy(creatingProject = false, createProjectError = friendly)
                }
            }
        }
    }

    fun createChange(
        project: String,
        branch: String,
        subject: String,
        topic: String = "",
        workInProgress: Boolean = true,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(creating = true, createError = null) }
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
                _ui.update { it.copy(creating = false) }
                onSuccess(created.id)
            } catch (e: Exception) {
                AppLog.e("createChange from projects failed", e)
                _ui.update {
                    it.copy(creating = false, createError = e.message ?: "Create failed")
                }
            }
        }
    }
}
