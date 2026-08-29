package com.quickgerrit.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quickgerrit.app.AppContainer
import com.quickgerrit.app.ui.accounts.AccountsScreen
import com.quickgerrit.app.ui.accounts.AccountsViewModel
import com.quickgerrit.app.ui.change.ChangeDetailScreen
import com.quickgerrit.app.ui.change.ChangeDetailViewModel
import com.quickgerrit.app.ui.change.DiffScreen
import com.quickgerrit.app.ui.change.FileEditorScreen
import com.quickgerrit.app.ui.changes.ChangesScreen
import com.quickgerrit.app.ui.changes.ChangesViewModel
import com.quickgerrit.app.ui.dashboard.DashboardScreen
import com.quickgerrit.app.ui.dashboard.DashboardViewModel
import com.quickgerrit.app.ui.logs.LogsScreen
import com.quickgerrit.app.ui.projects.BranchesScreen
import com.quickgerrit.app.ui.projects.BranchesViewModel
import com.quickgerrit.app.ui.projects.ProjectsScreen
import com.quickgerrit.app.ui.projects.ProjectsViewModel

sealed class Screen {
    data object Dashboard : Screen()
    data object Changes : Screen()
    data object Projects : Screen()
    data class Branches(val project: String) : Screen()
    data object Accounts : Screen()
    data object Logs : Screen()
    data class ChangeDetail(val changeId: String) : Screen()
    data class Diff(val changeId: String, val revisionId: String, val filePath: String) : Screen()
    data class FileEditor(
        val changeId: String,
        val revisionId: String,
        val filePath: String,
        val project: String = "",
        val branch: String = "master"
    ) : Screen()
}

@Composable
fun QuickGerritNavGraph() {
    val repo = AppContainer.repository
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Dashboard)) }
    val current = stack.last()

    fun navigate(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun popTo(predicate: (Screen) -> Boolean) {
        val idx = stack.indexOfLast(predicate)
        if (idx >= 0) stack = stack.take(idx + 1)
        else pop()
    }

    when (val screen = current) {
        is Screen.Dashboard -> {
            val vm = remember { DashboardViewModel(repo) }
            DashboardScreen(
                viewModel = vm,
                onOpenChange = { id -> navigate(Screen.ChangeDetail(id)) },
                onOpenAccounts = { navigate(Screen.Accounts) },
                onOpenChanges = { navigate(Screen.Changes) },
                onOpenProjects = { navigate(Screen.Projects) },
                onOpenLogs = { navigate(Screen.Logs) }
            )
        }
        is Screen.Changes -> {
            val vm = remember { ChangesViewModel(repo) }
            ChangesScreen(
                viewModel = vm,
                onOpenChange = { id -> navigate(Screen.ChangeDetail(id)) },
                onOpenAccounts = { navigate(Screen.Accounts) },
                onOpenProjects = { navigate(Screen.Projects) },
                onOpenLogs = { navigate(Screen.Logs) },
                onOpenDashboard = {
                    stack = listOf(Screen.Dashboard)
                }
            )
        }
        is Screen.Projects -> {
            val vm = remember { ProjectsViewModel(repo) }
            ProjectsScreen(
                viewModel = vm,
                onBack = { pop() },
                onOpenChange = { id -> navigate(Screen.ChangeDetail(id)) },
                onOpenBranches = { project -> navigate(Screen.Branches(project)) }
            )
        }
        is Screen.Branches -> {
            val vm = remember(screen.project) { BranchesViewModel(repo, screen.project) }
            BranchesScreen(
                viewModel = vm,
                onBack = { pop() }
            )
        }
        is Screen.Accounts -> {
            val vm = remember { AccountsViewModel(repo) }
            AccountsScreen(
                viewModel = vm,
                onBack = { pop() }
            )
        }
        is Screen.Logs -> {
            LogsScreen(onBack = { pop() })
        }
        is Screen.ChangeDetail -> {
            val vm = remember(screen.changeId) { ChangeDetailViewModel(repo, screen.changeId) }
            ChangeDetailScreen(
                viewModel = vm,
                onBack = { pop() },
                onOpenDiff = { rev, path ->
                    navigate(Screen.Diff(screen.changeId, rev, path))
                },
                onOpenEditor = { rev, path, project, branch ->
                    navigate(
                        Screen.FileEditor(
                            changeId = screen.changeId,
                            revisionId = rev,
                            filePath = path,
                            project = project,
                            branch = branch
                        )
                    )
                }
            )
        }
        is Screen.Diff -> {
            DiffScreen(
                changeId = screen.changeId,
                revisionId = screen.revisionId,
                filePath = screen.filePath,
                repository = repo,
                onBack = { pop() },
                onEdit = {
                    navigate(
                        Screen.FileEditor(
                            changeId = screen.changeId,
                            revisionId = screen.revisionId,
                            filePath = screen.filePath
                        )
                    )
                }
            )
        }
        is Screen.FileEditor -> {
            FileEditorScreen(
                changeId = screen.changeId,
                revisionId = screen.revisionId,
                filePath = screen.filePath,
                project = screen.project,
                branch = screen.branch,
                repository = repo,
                onBack = { pop() },
                onPublished = {
                    popTo { it is Screen.ChangeDetail && it.changeId == screen.changeId }
                }
            )
        }
    }
}
